/* 干饭搭子 · 可运行前端 Demo
 * 纯原生 JS：无框架、无网络依赖、数据存 localStorage。
 * 覆盖：推荐排序/换一批/搜索、收藏、约饭筛选/排序/加入/退出/发起、打招呼/
 * 评论、打卡发布、话题筛选、画像编辑、榜单切换、我的收藏/约饭/记录抽屉。
 */
(function () {
  'use strict';

  var LS_KEY = 'ganfan_demo_v2';
  var SEED = window.SEED;
  var pageErrors = [];
  window.addEventListener('error', function (e) { pageErrors.push(e.message || 'script error'); });
  window.addEventListener('unhandledrejection', function (e) { pageErrors.push('promise: ' + (e.reason && e.reason.message ? e.reason.message : e.reason)); });

  /* ---------------- 工具 ---------------- */
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function uid(prefix) { return prefix + Date.now().toString(36) + Math.random().toString(36).slice(2, 6); }
  function clamp(n, a, b) { return Math.max(a, Math.min(b, n)); }
  function nowLabel() { return new Date().toLocaleString('zh-CN', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).replace(/\//g, '-'); }
  function clone(o) { return JSON.parse(JSON.stringify(o)); }
  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }

  function loadState() {
    try {
      var raw = localStorage.getItem(LS_KEY);
      if (!raw) return null;
      var s = JSON.parse(raw);
      if (!s || typeof s !== 'object' || !s.profile || typeof s.profile !== 'object') return null;
      var base = freshState();
      var merged = Object.assign(base, s);
      // 逐字段兜底：旧版本或手动改过的 localStorage 不应导致白屏
      merged.favorites = Array.isArray(s.favorites) ? s.favorites.filter(function (x) { return typeof x === 'string'; }) : [];
      merged.joined = s.joined && typeof s.joined === 'object' ? s.joined : {};
      merged.likes = s.likes && typeof s.likes === 'object' ? s.likes : {};
      merged.collects = s.collects && typeof s.collects === 'object' ? s.collects : {};
      merged.comments = s.comments && typeof s.comments === 'object' ? s.comments : {};
      merged.greetings = s.greetings && typeof s.greetings === 'object' ? s.greetings : {};
      merged.messages = Array.isArray(s.messages) ? s.messages : [];
      merged.createdMeetups = Array.isArray(s.createdMeetups) ? s.createdMeetups : [];
      merged.createdPosts = Array.isArray(s.createdPosts) ? s.createdPosts : [];
      merged.checks = Array.isArray(s.checks) ? s.checks : [];
      merged.applications = Array.isArray(s.applications) ? s.applications : [];
      merged.schoolId = typeof s.schoolId === 'string' && SEED.schools.some(function (x) { return x.id === s.schoolId; }) ? s.schoolId : SEED.school.id;
      merged.geo = s.geo && typeof s.geo.lat === 'number' && typeof s.geo.lng === 'number' ? s.geo : null;
      merged.profile = Object.assign(freshState().profile, s.profile);
      if (!Array.isArray(merged.profile.dislikes)) merged.profile.dislikes = [];
      if (!Array.isArray(merged.profile.budget) || merged.profile.budget.length !== 2) merged.profile.budget = [15, 25];
      // 以本机新增打卡统一修正累计打卡，兼容旧 localStorage 数据
      merged.totalCheckins = 23 + merged.createdPosts.length;
      return merged;
    } catch (e) { return null; }
  }
  function saveState() {
    try { localStorage.setItem(LS_KEY, JSON.stringify(state)); } catch (e) { /* file:// 下也能用内存态 */ }
  }
  function freshState() {
    return {
      profile: clone(SEED.profile),
      favorites: [], joined: {}, likes: {}, collects: {}, comments: {},
      greetings: {}, messages: [], createdMeetups: [], createdPosts: [],
      checks: [], totalCheckins: 23, reroll: 0, edited: 0,
      schoolId: SEED.school.id, geo: null, applications: []
    };
  }

  // 启动阶段不直接使用旧 localStorage 业务数据，登录后由后端 bootstrap 覆盖
  var state = freshState();
  var ui = {
    view: 'home', search: '', chip: '全部', sort: 'latest', budget: 'all',
    rankCat: '口味榜', topic: '', communityChip: '全部', demoTarget: null, nearbyChip: '全部', nearbySort: '匹配', nearbyPick: null,
    nearbyRemote: null, nearbyLoading: false, nearbyError: '', nearbyBatch: 0, nearbySeenIds: []
  };
  var drawerKind = null;
  var backendReady = false;
  var selfTestMode = false;
  var remoteQueue = Promise.resolve();

  /* ---------------- 后端状态同步 ---------------- */
  function applyRemoteState(remoteState) {
    if (!remoteState || !remoteState.profile) return;
    var localGeo = state.geo;
    var localReroll = state.reroll;
    state = Object.assign(freshState(), remoteState);
    // 精确定位仍只保存在浏览器；换一批属于本地展示状态
    state.geo = localGeo || null;
    state.reroll = localReroll || 0;
    if (window.__ganfan) window.__ganfan.state = state;
    saveState();
  }

  function refreshBackend() {
    if (!window.GanfanAPI || !GanfanAPI.hasToken()) return Promise.reject(new Error('未登录'));
    return GanfanAPI.bootstrap().then(function (payload) {
      SEED = payload.catalog;
      window.SEED = SEED;
      if (window.__ganfan) window.__ganfan.SEED = SEED;
      applyRemoteState(payload.state);
      backendReady = true;
      render(true);
      return payload;
    });
  }

  function syncState(path, method, body) {
    if (selfTestMode || !backendReady) return Promise.resolve(null);
    remoteQueue = remoteQueue.catch(function () {}).then(function () {
      return GanfanAPI.action(path, method, body);
    }).then(function (remoteState) {
      applyRemoteState(remoteState);
      if (!selfTestMode) afterMutation();
      if (!selfTestMode && (ui.view === 'nearby' || ui.nearbyRemote)) {
        loadNearbyFromBackend({ source: 'history' });
      }
      return remoteState;
    }).catch(function (error) {
      if (!selfTestMode) {
        toast('操作未保存：' + error.message, false);
        if (error.status !== 401) refreshBackend().catch(function () {});
      }
      return null;
    });
    return remoteQueue;
  }

  /* ---------------- 附近数据与地点推荐 ---------------- */
  function nearbyPoint() {
    if (state.geo && typeof state.geo.lat === 'number') return state.geo;
    var school = curSchool();
    return typeof school.lat === 'number' ? { lat: school.lat, lng: school.lng, accuracy: 0, source: 'school' } : null;
  }

  function normalizeNearbyItem(item) {
    return {
      type: item.type, id: item.id, name: item.name, img: item.img, emoji: item.emoji,
      place: item.place, price: item.price, rating: item.rating, distance: item.distance,
      km: item.km, score: item.score, rank: item.rank || item.score,
      reasons: item.reasons || [], scoreParts: item.scoreParts || null,
      tags: item.tags || [], dishes: item.dishes || [], raw: item.attributes || item.raw || item
    };
  }

  function loadNearbyFromBackend(options) {
    options = options || {};
    if (selfTestMode || !window.GanfanAPI || !GanfanAPI.hasToken()) return Promise.resolve(null);
    var point = nearbyPoint();
    if (!point) return Promise.resolve(null);
    if (options.reset) ui.nearbySeenIds = [];
    if (options.refresh) ui.nearbyBatch += 1;
    var excludeIds = options.refresh ? ui.nearbySeenIds.slice(-120) : [];
    ui.nearbyLoading = true;
    ui.nearbyError = '';
    return GanfanAPI.discoverNearby({
      lat: point.lat,
      lng: point.lng,
      accuracy: point.accuracy || 0,
      source: options.source || 'manual',
      radiusMeters: options.radiusMeters || 2500,
      limit: options.refresh ? 8 : (options.limit || 16),
      seed: String(ui.nearbyBatch || 0),
      excludeIds: excludeIds
    }).then(function (data) {
      ui.nearbyLoading = false;
      ui.nearbyRemote = data;
      ui.nearbyPick = null;
      var freshIds = ((data && data.recommendations) || []).map(function (item) { return item.id; });
      ui.nearbySeenIds = ui.nearbySeenIds.concat(freshIds).filter(function (id, index, arr) {
        return id && arr.indexOf(id) === index;
      }).slice(-120);
      var context = data && data.context;
      if (context && context.shouldSwitchSchool && (options.source === 'browser' || options.source === 'search')) {
        var nextSchool = context.nearestSchoolId;
        if (nextSchool && nextSchool !== state.schoolId) {
          state.schoolId = nextSchool;
          p().campus = campusName();
          saveState();
          GanfanAPI.action('/me/preferences', 'PUT', { schoolId: nextSchool })
            .then(applyRemoteState)
            .catch(function () {});
          toast('位置已切换到 ' + campusName() + '，周边推荐已更新');
        }
      }
      if (options.go) go('nearby');
      else render(true);
      return data;
    }).catch(function (error) {
      ui.nearbyLoading = false;
      ui.nearbyError = error.message || '周边推荐加载失败';
      if (!selfTestMode) toast('周边推荐加载失败：' + ui.nearbyError, false);
      render(true);
      return null;
    });
  }

  /* ---------------- 派生数据 ---------------- */
  function meetups() { return state.createdMeetups.concat(SEED.meetups); }
  function posts() { return state.createdPosts.concat(SEED.posts); }
  // 档口严格按当前校区过滤，避免推荐/附近/搜索混入其他校区内容
  function stalls() {
    return SEED.stalls.filter(function (s) { return !s.campus || s.campus === campusName(); });
  }
  function p() { return state.profile; }

  /* ---------------- 校区 / 定位 ---------------- */
  function curSchool() {
    return (SEED.schools || []).filter(function (s) { return s.id === state.schoolId; })[0] || SEED.schools[0];
  }
  function campusName() { return curSchool().campus; }
  function isOpenCampus() { return curSchool().status === 'open'; }
  function canteensOfCampus() {
    return (SEED.canteens || []).filter(function (c) { return c.campus === campusName(); });
  }
  function haversine(a, b) {
    var R = 6371000, rad = Math.PI / 180;
    var dLat = (b.lat - a.lat) * rad, dLng = (b.lng - a.lng) * rad;
    var s = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * R * Math.asin(Math.min(1, Math.sqrt(s)));
  }
  function geoDistanceTo(target) {
    if (!state.geo || !target || typeof target.lat !== 'number') return null;
    return Math.round(haversine(state.geo, target) / 10) * 10;
  }
  function nearestCanteen() {
    if (!state.geo) return null;
    var best = null, bestD = Infinity;
    canteensOfCampus().forEach(function (c) {
      var d = geoDistanceTo(c);
      if (d != null && d < bestD) { bestD = d; best = { canteen: c, distance: d }; }
    });
    return best;
  }
  function geoLabel() {
    if (!state.geo) return '';
    var near = nearestCanteen();
    return near ? '已定位 · 距' + near.canteen.name + '约 ' + near.distance + 'm' : '已定位 · 校内服务已开启';
  }
  function locateMe() {
    if (!navigator.geolocation) { toast('当前浏览器不支持定位，请在学校弹窗里手动选择校区'); return; }
    toast('正在获取定位…');
    navigator.geolocation.getCurrentPosition(function (pos) {
      state.geo = {
        lat: pos.coords.latitude,
        lng: pos.coords.longitude,
        accuracy: Math.round(pos.coords.accuracy || 0),
        ts: Date.now()
      };
      var nearest = null, nd = Infinity;
      SEED.schools.forEach(function (s) {
        if (s.status !== 'open') return;
        var d = haversine(state.geo, s);
        if (d < nd) { nd = d; nearest = s; }
      });
      if (nearest && nd < 5000 && nearest.id !== state.schoolId) {
        state.schoolId = nearest.id;
        p().campus = campusName();
      }
      saveState();
      var n2 = nearestCanteen();
      toast(n2 ? '定位成功：距' + n2.canteen.name + '约 ' + n2.distance + 'm' : '定位成功，已切换到 ' + campusName());
      if (!selfTestMode && window.GanfanAPI && GanfanAPI.hasToken() && backendReady) {
        loadNearbyFromBackend({ source: 'browser', go: true, reset: true });
      } else {
        render(true);
        setTimeout(function () {
          ui.nearbyPick = null;
          if (ui.view === 'nearby') render();
          else go('nearby');
        }, 500);
      }
    }, function () {
      toast('定位未开启：可在学校弹窗手动选择校区，其余功能不受影响');
    }, { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 });
  }
  function isJoined(id) { return !!state.joined[id]; }
  function seatsOf(m) { return m.seat + (isJoined(m.id) ? 1 : 0); }
  function isFav(id) { return state.favorites.indexOf(id) >= 0; }
  function liked(postId) { return !!state.likes[postId]; }
  function collected(postId) { return !!state.collects[postId]; }
  function commentsOf(postId) { return state.comments[postId] || []; }
  function likesOf(post) { return (post.likes || 0) + (liked(post.id) ? 1 : 0); }
  function commentsCount(post) {
    // 后端 bootstrap 已返回“基础评论数 + 当前校区实际评论数”
    return backendReady ? (post.comments || 0) : (post.comments || 0) + commentsOf(post.id).length;
  }
  function favsOf(post) { return (post.favs || 0) + (collected(post.id) ? 1 : 0); }
  function joinedCount() { return Object.keys(state.joined).filter(function (k) { return state.joined[k]; }).length; }
  function createdCount() { return state.createdMeetups.length; }
  function myMeetupCount() { return 2 + joinedCount() + createdCount(); }
  function checkinCount() { return (typeof state.totalCheckins === 'number' ? state.totalCheckins : 23); }
  function favCount() { return 36 + state.favorites.length; }
  function completeness() { return clamp(80 + state.edited * 2, 0, 100); }

  function dislikeHit(obj) {
    var dis = p().dislikes || [];
    if (!dis.length) return null;
    var pool = (obj.tags || []).concat(obj.dishes || []).join(' ');
    for (var i = 0; i < dis.length; i++) { if (pool.indexOf(dis[i]) >= 0) return dis[i]; }
    return null;
  }
  function budgetFit(price) { return price >= p().budget[0] && price <= p().budget[1]; }

  /* ---------------- 匹配度与推荐依据 ---------------- */
  function stallScore(s) {
    if (typeof s.remoteScore === 'number') return clamp(Math.round(s.remoteScore), 1, 99);
    var sc = 70;
    sc += budgetFit(s.price) ? 8 : (s.price > p().budget[1] ? -5 : 2);
    sc += dislikeHit(s) ? -9 : 8;
    if ((p().haunts || []).indexOf(s.canteen) >= 0) sc += 5;
    if (s.spicy === p().spicy) sc += 4; else if (s.spicy === '中辣' || s.spicy === '微辣') sc += 2;
    if (s.walk > 400) sc -= 2;
    if (s.canteen === '图书馆 B1' && p().sleep === '夜猫子') sc += 1;
    return clamp(Math.round(sc), 60, 99);
  }

  function meetupScore(m) {
    var sc = m.base;
    sc += budgetFit(m.budget) ? 4 : -5;
    sc += dislikeHit(m) ? -8 : 3;
    if (m.campus === p().campus) sc += 2;
    if (/夜宵|拼单/.test(m.mode + (m.tags || []).join('')) && p().sleep === '夜猫子') sc += 2;
    return clamp(Math.round(sc), 60, 98);
  }

  function reasonsOf(s) {
    if (Array.isArray(s.reasons) && s.reasons.length) {
      return s.reasons.map(function (text) { return { t: '推荐理由', d: text }; }).slice(0, 3);
    }
    var out = [];
    if ((p().dislikes || []).length) {
      out.push({ t: '你不吃' + p().dislikes[0], d: dislikeHit(s) ? '这道菜默认含' + dislikeHit(s) + '，点单时需备注去掉' : '招牌菜可免' + p().dislikes[0] + '，辣度可调至' + p().spicy });
    }
    out.push({ t: '预算 ' + p().budget[0] + ' 至 ' + p().budget[1] + ' 元', d: '人均 ' + s.price + ' 元，' + (budgetFit(s.price) ? '正落在你的预算区间' : '略高于预算，可作为加餐') });
    if ((p().haunts || []).indexOf(s.canteen) >= 0) {
      out.push({ t: '常出没' + s.canteen, d: s.floor + ' 档口，步行 ' + s.walk + ' 米，出餐快' });
    } else {
      out.push({ t: '口味标签命中', d: s.tags.join('、') + '；评分 ' + s.rating + '，' + s.checkins + ' 人打卡' });
    }
    return out.slice(0, 3);
  }

  function recommendStalls() {
    if (ui.nearbyRemote && Array.isArray(ui.nearbyRemote.recommendations) && ui.nearbyRemote.recommendations.length) {
      return ui.nearbyRemote.recommendations.map(function (item) {
        return normalizeNearbyItem(item);
      }).map(function (item) {
        return {
          id: item.id, name: item.name, img: item.img, emoji: item.emoji,
          canteen: (item.raw && item.raw.canteen) || item.place,
          floor: (item.raw && item.raw.floor) || (item.type === 'takeout' ? '外卖' : ''),
          walk: item.distance || (item.raw && item.raw.walk) || 0,
          price: item.price, rating: item.rating || 0,
          checkins: (item.raw && item.raw.checkins) || 0,
          remain: (item.raw && item.raw.remain) || 0,
          spicy: (item.raw && item.raw.spicy) || '不辣',
          tags: item.tags || [], dishes: item.dishes || [],
          campus: campusName(), score: item.score, rank: item.rank || item.score,
          remoteScore: item.score, reasons: item.reasons || [], type: item.type,
          raw: item.raw
        };
      });
    }
    var list = stalls().slice().sort(function (a, b) { return stallScore(b) - stallScore(a); });
    if (!list.length) return list;
    var offset = state.reroll % list.length;
    return list.slice(offset).concat(list.slice(0, offset));
  }

  /* ---------------- 附近吃饭 / 随机选店 ---------------- */
  function canteenOf(name) {
    return canteensOfCampus().filter(function (c) { return c.name === name; })[0] || null;
  }
  function takeoutsOfCampus() {
    return (SEED.takeouts || []).filter(function (t) { return t.campus === campusName(); });
  }
  function takeoutScore(t) {
    var sc = 66;
    sc += budgetFit(t.price) ? 9 : (t.price > p().budget[1] ? -5 : 2);
    sc += dislikeHit(t) ? -10 : 6;
    if (t.spicy === p().spicy) sc += 5; else if (t.spicy === '微辣' || t.spicy === '中辣') sc += 2;
    if (p().sleep === '夜猫子' && t.night) sc += 3;
    if (t.delivery <= 22) sc += 2;
    return clamp(Math.round(sc), 60, 97);
  }
  function nearbyItems() {
    if (ui.nearbyRemote && Array.isArray(ui.nearbyRemote.venues)) {
      return ui.nearbyRemote.venues.map(normalizeNearbyItem);
    }
    if (ui.nearbyRemote && Array.isArray(ui.nearbyRemote.recommendations)) {
      return ui.nearbyRemote.recommendations.map(normalizeNearbyItem);
    }
    var arr = [];
    stalls().forEach(function (s) {
      var c = canteenOf(s.canteen);
      var d = state.geo && c ? geoDistanceTo(c) : null;
      var score = stallScore(s);
      var penalty = d != null ? Math.min(14, d / 80) : 0;
      arr.push({ type: 'stall', id: s.id, name: s.name, img: s.img, emoji: s.emoji, place: s.canteen + ' ' + s.floor, distance: d, price: s.price, score: score, rank: score - penalty, raw: s });
    });
    takeoutsOfCampus().forEach(function (t) {
      var d = state.geo ? geoDistanceTo(t) : null;
      var km = d != null ? d / 1000 : 1.2;
      var score = takeoutScore(t);
      var penalty = Math.min(10, km * 3);
      arr.push({ type: 'takeout', id: t.id, name: t.name, img: t.img, emoji: t.emoji, place: '外卖 · 约 ' + t.delivery + ' 分钟送达', distance: d, km: km, price: t.price, score: score, rank: score - penalty, raw: t });
    });
    // 主排序看匹配分，分数相同再按距离/价格修正分从高到低
    return arr.sort(function (a, b) { return b.score - a.score || b.rank - a.rank; });
  }
  function nearbyDistance(it) {
    if (it.distance != null) {
      return it.distance < 1000 ? '距你 ' + it.distance + 'm' : '距你 ' + (it.distance / 1000).toFixed(1) + 'km';
    }
    if (it.type === 'stall' && it.raw && it.raw.walk != null) return '校内 · 约 ' + it.raw.walk + 'm';
    return '距校区约 1.2km';
  }
  function nearbyWhy(it) {
    if (Array.isArray(it.reasons) && it.reasons.length) return it.reasons.join(' · ');
    var s = it.raw || {}, bits = [];
    if (it.type === 'stall') {
      bits.push(budgetFit(s.price) ? '人均 ' + s.price + ' 元在预算内' : '人均 ' + s.price + ' 元，略高于预算');
      if (dislikeHit(s)) bits.push('含' + dislikeHit(s) + '，记得备注去掉');
      else if ((p().dislikes || []).length) bits.push('可避开' + p().dislikes[0]);
      if ((p().haunts || []).indexOf(s.canteen) >= 0) bits.push('你常去' + s.canteen);
    } else {
      bits.push(budgetFit(s.price) ? '人均 ' + s.price + ' 元，比堂食更省时间' : '人均 ' + s.price + ' 元');
      bits.push('约 ' + s.delivery + ' 分钟送达');
      if ((s.tags || []).length) bits.push(s.tags[0]);
    }
    return bits.join(' · ');
  }
  function pickNearby(list, rnd) {
    var pool = list.slice(0, Math.min(list.length, 10));
    if (!pool.length) return null;
    var r = (typeof rnd === 'function' ? rnd() : Math.random()) * pool.length;
    return pool[Math.min(pool.length - 1, Math.floor(r))];
  }
  function nearbyRow(it) {
    return '<div class="nb-row" data-act="nearby-open" data-type="' + it.type + '" data-id="' + it.id + '" tabindex="0">' +
      '<div class="nb-thumb">' + (it.img ? '<img src="' + esc(it.img) + '" alt="">' : it.emoji) + '</div>' +
      '<div class="grow"><div class="nb-name">' + esc(it.name) + ' <span class="nb-type">' + (it.type === 'stall' ? '校内' : '外卖') + '</span></div>' +
      '<div class="nb-sub">' + esc(it.place) + ' · ' + nearbyDistance(it) + '</div></div>' +
      '<div class="nb-right"><b>' + it.score + '%</b><span>¥' + it.price + '</span></div></div>';
  }
  function openNearby(auto) {
    var list = nearbyItems();
    var best = list[0];
    var pick = ui.nearbyPick ? list.filter(function (x) { return x.id === ui.nearbyPick; })[0] : null;
    var campus = curSchool().university + ' · ' + campusName();
    var near = nearestCanteen();
    var sub = state.geo ? ('已定位' + (near ? ' · 距' + near.canteen.name + '约 ' + near.distance + 'm' : '') + ' · 按匹配度与距离排序')
      : '未授权定位，按校区步行距离估算 · 可点页面右上角「定位」';
    var html = '';
    if (auto && state.geo) {
      html += '<div class="nb-note">定位成功，已自动搜索附近 ' + (stalls().length + takeoutsOfCampus().length) + ' 个吃饭地点：校内档口 ' + stalls().length + ' 家，外卖 ' + takeoutsOfCampus().length + ' 家。</div>';
    }
    if (!list.length) {
      html += '<div class="empty">当前校区还没有可推荐的档口。<br><a class="link" data-act="join-school">提交校区资料 →</a></div>';
      openModal({ title: '附近吃饭 · ' + campus, sub: sub, body: html }, true);
      return;
    }
    if (best) {
      html += '<div class="nb-best"><div class="serial">给你的建议 · 先吃这家</div>' +
        '<div class="nb-best-main"><div class="nb-thumb lg">' + (best.img ? '<img src="' + esc(best.img) + '" alt="">' : best.emoji) + '</div>' +
        '<div class="grow"><div class="nb-name">' + esc(best.name) + ' <span class="nb-type">' + (best.type === 'stall' ? '校内' : '外卖') + '</span></div>' +
        '<div class="nb-sub">' + esc(best.place) + ' · ' + nearbyDistance(best) + ' · ¥' + best.price + '</div>' +
        '<div class="nb-why">' + esc(nearbyWhy(best)) + '</div></div>' +
        '<div class="nb-right"><b>' + best.score + '%</b><span>匹配</span></div></div>' +
        '<div class="nb-actions"><button class="btn btn-primary" data-act="nearby-open" data-type="' + best.type + '" data-id="' + best.id + '">看看这家</button>' +
        '<button class="btn" data-act="nearby-random">🎲 换一家</button></div></div>';
    }
    html += '<div class="nb-random"><div><b>还在纠结？</b><span>把校内档口和外卖放在一起，随机抽一家，今天就别想了</span></div>' +
      '<button class="btn btn-primary" data-act="nearby-random">🎲 随机帮我选</button></div>';
    if (pick) {
      html += '<div class="nb-pick"><div class="serial">本次抽到</div>' +
        '<div class="nb-best-main"><div class="nb-thumb">' + (pick.img ? '<img src="' + esc(pick.img) + '" alt="">' : pick.emoji) + '</div>' +
        '<div class="grow"><div class="nb-name">' + esc(pick.name) + ' <span class="nb-type">' + (pick.type === 'stall' ? '校内' : '外卖') + '</span></div>' +
        '<div class="nb-sub">' + esc(pick.place) + ' · ' + nearbyDistance(pick) + ' · ¥' + pick.price + '</div></div>' +
        '<div class="nb-right"><b>' + pick.score + '%</b><span>匹配</span></div></div>' +
        '<div class="nb-actions"><button class="btn btn-primary" data-act="nearby-open" data-type="' + pick.type + '" data-id="' + pick.id + '">就吃这家</button>' +
        '<button class="btn" data-act="nearby-random">再抽一次</button></div></div>';
    }
    var stallCount = list.filter(function (x) { return x.type === 'stall'; }).length;
    var takeoutCount = list.length - stallCount;
    html += '<div class="nb-sec"><div class="nb-sec-head"><span>附近吃饭 · 校内 + 外卖一起排</span><span>共 ' + list.length + ' 家（校内 ' + stallCount + ' · 外卖 ' + takeoutCount + '）</span></div>' +
      list.slice(0, 10).map(nearbyRow).join('') + '</div>';
    html += '<div class="nb-note small">外卖商家、配送时长为课程演示的合成数据；定位只保存在本机 localStorage。</div>';
    openModal({ title: '附近吃饭 · ' + campus, sub: sub, body: html }, true);
  }
  function openNearbyItem(type, id) {
    var remote = nearbyItems().filter(function (x) { return x.id === id && x.type === type; })[0];
    if (type === 'stall') {
      var s = SEED.stalls.filter(function (x) { return x.id === id; })[0];
      if (!s && remote) {
        s = Object.assign({
          id: remote.id, name: remote.name, img: remote.img, emoji: remote.emoji,
          price: remote.price, rating: remote.rating, tags: remote.tags, dishes: remote.dishes,
          walk: remote.distance || (remote.raw && remote.raw.walk) || 0
        }, remote.raw || {});
      }
      if (s) stallModal(s);
    } else {
      var t = (SEED.takeouts || []).filter(function (x) { return x.id === id; })[0];
      if (!t && remote) {
        t = Object.assign({
          id: remote.id, name: remote.name, img: remote.img, emoji: remote.emoji,
          price: remote.price, rating: remote.rating, tags: remote.tags, dishes: remote.dishes,
          delivery: (remote.raw && remote.raw.delivery) || 30
        }, remote.raw || {});
      }
      if (t) takeoutModal(t);
    }
  }
  function takeoutModal(t) {
    var body = '<div class="list">' +
      '<div class="list-row"><span class="grow"><b>推荐依据</b><br><span style="color:var(--ink-3)">' + esc(nearbyWhy({ type: 'takeout', raw: t })) + '</span></span></div>' +
      '<div class="list-row"><span class="grow"><b>外卖信息</b><br><span style="color:var(--ink-3)">' + esc((t.tags || []).join('、')) + ' · 人均 ' + t.price + ' 元 · 评分 ' + t.rating + ' · 约 ' + t.delivery + ' 分钟送达</span></span></div>' +
      '<div class="list-row"><span class="grow"><b>距离</b><br><span style="color:var(--ink-3)">' + (state.geo ? ('距你约 ' + (geoDistanceTo(t) / 1000).toFixed(1) + ' km') : '校外商家') + ' · 演示数据</span></span></div>' +
      '</div><div class="modal-actions"><button class="btn" data-act="nearby-random">🎲 换一家</button>' +
      '<button class="btn btn-primary" data-act="takeout-order" data-id="' + t.id + '">去点单（演示）</button></div>';
    openModal({ title: t.name, sub: '外卖 · ' + takeoutScore(t) + '% 匹配', body: body }, true);
  }

  /* ---------------- 通用组件 ---------------- */
  function navMarkup() {
    return ''; // 顶部导航是静态的，由 router 更新 .menu a.on
  }

  function matchBadge(score, suffix) {
    return '<span class="matchbadge"><b>' + score + '</b><span>' + (suffix || '% 匹配') + '</span></span>';
  }
  function stallTags(s) {
    return s.tags.map(function (t) { return '<span class="tag tag-plain">' + esc(t) + '</span>'; }).join('');
  }
  function quickItem(kind, icon, label, count) {
    return '<a class="quick" data-act="quick" data-kind="' + kind + '" tabindex="0">' +
      '<span class="ic">' + icon + '</span>' + esc(label) + '<b>' + esc(count) + '</b></a>';
  }

  /* ---------------- 附近吃饭页 ---------------- */
  function nearbyDistValue(it) {
    if (it.distance != null) return it.distance;
    return it.type === 'stall' ? it.raw.walk : 1200;
  }
  function nearbyBest() {
    var list = nearbyItems();
    return list[0] || null;
  }
  function drawNearby(navigate) {
    var it = pickNearby(nearbyItems());
    if (!it) return null;
    ui.nearbyPick = it.id;
    if (ui.view === 'nearby') render(true);
    else if (navigate !== false) go('nearby');
    toast('抽到：' + it.name + ' · ' + it.score + '% 匹配');
    return it;
  }
  function nearbySuggestions(list) {
    var picks = [], seen = {};
    function push(it) { if (it && !seen[it.id] && picks.length < 3) { seen[it.id] = 1; picks.push(it); } }
    push(list[0]);
    push(list.filter(function (x) { return x.type === 'takeout'; })[0]);
    list.forEach(push);
    return picks;
  }
  function nearbySuggestionCard(it, i) {
    return '<article class="card nb-sug" data-act="nearby-open" data-type="' + it.type + '" data-id="' + it.id + '" tabindex="0">' +
      '<div class="nb-sug-img">' + (it.img ? '<img src="' + esc(it.img) + '" alt="">' : it.emoji) + '</div>' +
      '<div class="nb-sug-body"><div class="nb-sug-top"><span class="nb-type">' + (it.type === 'stall' ? '校内' : '外卖') + '</span>' +
      '<span class="nb-sug-score">' + it.score + '%</span></div>' +
      '<h4>' + esc(it.name) + '</h4>' +
      '<div class="nb-sub">' + esc(it.place) + '</div>' +
      '<div class="nb-sug-meta">' + nearbyDistance(it) + ' · ¥' + it.price + '</div>' +
      '<div class="nb-sug-why">' + esc(nearbyWhy(it)) + '</div></div></article>';
  }
  function renderNearby() {
    var list = nearbyItems();
    var best = list[0] || null;
    var pick = ui.nearbyPick ? list.filter(function (x) { return x.id === ui.nearbyPick; })[0] : null;
    var chip = ui.nearbyChip || '全部';
    var suggestions = nearbySuggestions(list);
    var suggestedIds = {};
    suggestions.forEach(function (x) { suggestedIds[x.id] = 1; });
    var sorted = list.slice();
    if (ui.nearbySort === '距离') sorted.sort(function (a, b) { return nearbyDistValue(a) - nearbyDistValue(b); });
    var filtered = sorted.filter(function (x) {
      if (suggestedIds[x.id]) return false; // 上方“给你的建议”已展示，避免清单重复
      if (chip === '校内') return x.type === 'stall';
      if (chip === '外卖') return x.type === 'takeout';
      return true;
    });
    var stallCount = list.filter(function (x) { return x.type === 'stall'; }).length;
    var takeCount = list.length - stallCount;
    var near = nearestCanteen();
    var html = '<div class="wrap">';
    html += '<div class="page-head"><div><div class="kicker">附近吃饭</div>' +
      '<h1>附近吃饭<small>校内档口 + 外卖合并 · 按画像与距离排序 · 纠结时随机抽一家</small></h1></div>' +
      '<div class="right"><span class="dot"></span> ' + esc(curSchool().university + ' · ' + campusName()) +
      ' <a class="link" data-act="school">切换</a> <a class="link" data-act="locate">📍 ' + (state.geo ? '重新定位' : '定位') + '</a></div></div>';

    if (ui.nearbyLoading) {
      html += '<div class="geo-bar">正在获取当前地点周边餐饮与外卖，并按画像、预算和历史行为重新排序…</div>';
    } else if (ui.nearbyError) {
      html += '<div class="geo-bar">周边推荐加载失败：' + esc(ui.nearbyError) + ' <a class="link" data-act="nearby-page-refresh">重试</a></div>';
    } else if (ui.nearbyRemote) {
      var ctx = ui.nearbyRemote.context || {};
      html += '<div class="geo-bar">已按当前位置聚合 ' + list.length + ' 个餐饮/外卖地点' +
        (ctx.nearestCampus ? ' · 识别到 ' + esc(ctx.nearestCampus) : '') +
        ' · 推荐结合距离、口味偏好、预算与历史行为 <a class="link" data-act="nearby-page-refresh">换一批</a></div>';
    }

    html += '<section class="nb-stage"><div class="nb-stage-main">' +
      '<div class="serial">DECIDE FOR ME · 附近 ' + list.length + ' 家</div>' +
      '<h2>今天不想选？<br>让系统替你决定。</h2>' +
      '<p>把校内 ' + stallCount + ' 家档口和附近 ' + takeCount + ' 家外卖放在一起，结合你的口味、预算和距离，随机抽一家。</p>' +
      '<div class="nb-stage-actions"><button class="btn nb-cta" data-act="nearby-draw">🎲 随机帮我选一家</button>' +
      '<button class="btn" data-act="' + (state.geo ? 'nearby-page-refresh' : 'locate') + '" style="background:rgba(255,255,255,.14);border-color:rgba(255,255,255,.4);color:#fff">' + (state.geo ? '↻ 换一批档口' : '📍 定位并搜索附近') + '</button></div>' +
      '<div class="nb-stage-meta">' + (state.geo ? ('已定位' + (near ? ' · 距' + esc(near.canteen.name) + '约 ' + near.distance + 'm' : '') + ' · 位置仅用于当前推荐') : '未授权定位，当前按校区位置估算') + '</div>' +
      '</div><div class="nb-stage-side">' +
      (pick ? ('<div class="nb-draw-card"><div class="serial">本次抽到</div>' +
        '<div class="nb-draw-name">' + esc(pick.name) + '</div>' +
        '<div class="nb-draw-sub">' + esc(pick.place) + ' · ' + nearbyDistance(pick) + ' · ¥' + pick.price + '</div>' +
        '<div class="nb-draw-score">' + pick.score + '% 匹配</div>' +
        '<div class="nb-draw-why">' + esc(nearbyWhy(pick)) + '</div>' +
        '<div class="nb-draw-actions"><button class="btn nb-cta sm" data-act="nearby-open" data-type="' + pick.type + '" data-id="' + pick.id + '">就吃这家</button>' +
        '<button class="btn nb-ghost sm" data-act="nearby-draw">再抽一次</button></div></div>')
        : ('<div class="nb-draw-empty"><div class="nb-dice">🎲</div><div class="nb-draw-empty-t">按一下左边，<br>今天就交给随机。</div>' +
          (best ? '<div class="nb-draw-empty-s">先吃这家也不错：' + esc(best.name) + ' · ' + best.score + '%</div>' : '') + '</div>')) +
      '</div></section>';

    html += '<section class="nb-suggest-wrap"><div class="nb-sec-head"><span>给你的建议</span><span>按匹配度与距离，前 3 家 · <a class="link" data-act="nearby-page-refresh">换一批档口</a></span></div>' +
      '<div class="nb-suggest-grid">' + suggestions.map(nearbySuggestionCard).join('') + '</div></section>';

    html += '<section class="card nb-listcard"><div class="nb-list-head"><div class="left"><span class="sec-title">其余地点</span>' +
      '<span class="sec-note">共 ' + filtered.length + ' 家 · 已排除上方推荐 ' + suggestions.length + ' 家</span></div>' +
      '<div class="nb-filters">' +
      ['全部', '校内', '外卖'].map(function (c) { return '<button class="nb-chip ' + (chip === c ? 'on' : '') + '" data-act="nearby-chip" data-value="' + c + '">' + c + '</button>'; }).join('') +
      '<span class="nb-sort">' + ['匹配', '距离'].map(function (c) { return '<button class="nb-sortbtn ' + (ui.nearbySort === c ? 'on' : '') + '" data-act="nearby-sort" data-value="' + c + '">按' + c + '</button>'; }).join('') + '</span></div></div>' +
      '<div class="nb-rows">' + (filtered.length ? filtered.map(nearbyRow).join('') : '<div class="empty">该筛选下暂无更多结果，推荐项已在上方展示。</div>') + '</div></section>';
    html += '<p class="img-note">当前 Demo 使用项目内餐饮/外卖数据 Provider；接真实平台时仅替换 Provider。位置用于距离推荐，默认搜索半径 2.5km。</p>';
    html += '</div>';
    return html;
  }
  function nearbyBand() {
    var list = nearbyItems();
    var best = list[0];
    if (!best) return '';
    return '<section class="nb-band"><div class="nb-band-main"><div class="serial">NEARBY · 附近 ' + list.length + ' 家</div>' +
      '<h2>附近吃饭，纠结就交给随机。</h2>' +
      '<p>' + (state.geo ? '已定位' : '未定位') + ' · 校内 + 外卖合并 · 先吃这家：<b>' + esc(best.name) + '</b> ' + best.score + '%</p></div>' +
      '<div class="nb-band-actions"><button class="btn nb-cta" data-act="nearby-draw" data-page="1">🎲 帮我决定</button>' +
      '<button class="btn nb-ghost" data-act="nearby-page">看看附近 ' + list.length + ' 家 →</button></div></section>';
  }

  /* ---------------- 首页 ---------------- */
  function renderHome() {
    var list = recommendStalls();
    var q = ui.search.trim();
    var searched = q ? stalls().filter(function (s) {
      var pool = (s.name + s.canteen + s.tags.join(' ') + s.dishes.join(' ')).toLowerCase();
      return pool.indexOf(q.toLowerCase()) >= 0;
    }) : null;

    var hero, recs = [];
    if (q) {
      hero = searched[0] || null;
      recs = searched.slice(1, 5);
    } else {
      hero = list[0];
      recs = list.slice(1, 5);
    }

    var html = '';
    html += '<div class="wrap">';
    html += '<div class="page-head"><div><div class="kicker">首页</div>' +
      '<h1>今天中午，吃什么？<small>你的口味画像已更新 · 12,806 位同学已完成</small></h1></div>' +
      '<div class="right"><span class="dot"></span> ' + esc(curSchool().university + ' · ' + campusName()) + ' <a class="link" data-act="school">切换</a> <a class="link" data-act="nearby">🎲 帮我选</a> <a class="link" data-act="locate">📍 定位</a></div></div>';
    if (geoLabel()) html += '<div class="geo-bar">📍 ' + esc(geoLabel()) + ' <span class="serial">定位仅保存在本机，不上传</span></div>';
    html += nearbyBand();

    html += '<div class="home">';
    html += profileCard();

    html += '<div class="main-col">';
    if (q) {
      html += '<div class="sec-head"><div class="left"><span class="sec-title">搜索「' + esc(q) + '」</span>' +
        '<span class="sec-note">' + (searched ? searched.length : 0) + ' 个结果 · 来自 36 家档口</span></div>' +
        '<div class="left"><a class="link" data-act="clear-search">清除搜索</a></div></div>';
    } else {
      html += '<div class="sec-head"><div class="left"><span class="sec-title">今日推荐</span>' +
        '<span class="sec-note">基于你的画像与本周 36 家档口</span></div>' +
        '<div class="left"><span class="mono" style="font-size:10px;color:var(--ink-3);letter-spacing:.14em">DEMO DATA</span>' +
        '<a class="link" data-act="reroll">换一批 ↻</a></div></div>';
    }

    if (hero) {
      html += heroCard(hero);
      if (recs.length) {
        html += '<div class="rec-grid">' + recs.map(recCard).join('') + '</div>';
      }
    } else if (q) {
      html += '<div class="empty-state">没有找到「' + esc(q) + '」相关档口。<br>试试 <b>麻辣香锅</b>、<b>沙拉</b> 或 <b>西门夜市</b>。<br><a class="link" data-act="clear-search">清除搜索</a></div>';
    } else if (ui.nearbyLoading) {
      html += '<div class="empty-state">正在获取 ' + esc(campusName()) + ' 周边商户和餐饮信息…<br>推荐会结合当前位置、预算、口味和历史行为。</div>';
    } else if (ui.nearbyError) {
      html += '<div class="empty-state">周边商户数据加载失败：' + esc(ui.nearbyError) + '<br><a class="link" data-act="nearby-page-refresh">重新获取</a></div>';
    } else if (ui.nearbyRemote) {
      html += '<div class="empty-state">当前地点暂未返回商户数据。<br>请确认已接入外部商户数据源，或 <a class="link" data-act="nearby-page-refresh">重新获取</a>。</div>';
    } else {
      html += '<div class="empty-state">当前校区还没有可推荐的档口。<br>切换已入驻校区，或 <a class="link" data-act="join-school">提交校区资料 →</a></div>';
    }
    html += '</div></div>'; // main-col / home

    html += '<div class="quickgrid">';
    html += quickItem('map', '📍', '食堂地图', canteensOfCampus().length + ' 个食堂');
    html += quickItem('ranks', '🏆', '今日榜单', stalls().length ? '4 个维度' : '待补充');
    html += quickItem('meetups', '🤝', '一起拼桌', filteredMeetups().length + ' 个饭局');
    html += quickItem('community', '💬', '美食话题', visiblePosts().length + ' 条');
    html += quickItem('favorites', '🔖', '我的收藏', favCount());
    html += quickItem('joined', '📅', '我的约饭', myMeetupCount() + '');
    html += quickItem('join-school', '🏫', '学校入驻', '申请开放');
    html += '</div></div>';
    return html;
  }

  function profileCard() {
    var seedRecent = [
      { type: '打卡', title: '麻辣香锅 · 养贤府 2F', ts: '10-30', score: 128, icon: '🍲' },
      { type: '打卡', title: '黄焖鸡米饭 · 家和食堂 3F', ts: '10-28', score: 96, icon: '🍗' },
      { type: '打卡', title: '桂花拿铁 · 图书馆 B1', ts: '10-27', score: 74, icon: '☕' }
    ];
    var recent = state.checks.slice(-3).reverse();
    while (recent.length < 3) recent.push(seedRecent[recent.length]);
    var rows = [
      ['辣度', p().spicy + (p().spicy === '微辣' ? '，能接受中辣' : '')],
      ['预算', p().budget[0] + ' 至 ' + p().budget[1] + ' 元 / 餐'],
      ['忌口', p().dislikes.length ? p().dislikes.join('、') : '无'],
      ['常出没', (p().haunts || []).join('、') || '未填写'],
      ['作息', p().sleep + (p().sleep === '夜猫子' ? '，23:30 后仍活跃' : '，作息规律')],
      ['社交', p().social + '，想找 1 至 2 人']
    ];
    var pills = [p().spicy, p().sleep, '预算敏感'].concat(p().dislikes.length ? [p().dislikes[0] + '绝缘'] : []);
    var html = '<aside class="card profile">' +
      '<div class="profile-top"><span class="serial">口味画像</span><span class="serial">NO.0721</span></div>' +
      '<div class="profile-id"><div class="photo">' + esc(p().avatar) + '</div><div>' +
      '<div class="profile-name">' + esc(p().name) + '</div>' +
      '<div class="profile-meta">' + esc(p().college) + ' · ' + esc(p().grade) + '</div>' +
      '<div class="verified"><svg viewBox="0 0 24 24" fill="none" stroke="#0F6B5E" stroke-width="2.2" stroke-linecap="round"><path d="M20 6 9 17l-5-5"/></svg> 校园认证 · 学号已核验</div>' +
      '</div></div>' +
      '<div class="taste-pills">' + pills.map(function (t, i) { return '<span class="tag' + (i ? ' tag-plain' : '') + '">' + esc(t) + '</span>'; }).join('') + '</div>' +
      '<div class="profile-rows">' + rows.map(function (r) {
        return '<div class="prow"><span class="k">' + r[0] + '</span><span class="v">' + esc(r[1]) + '</span></div>';
      }).join('') + '</div>' +
      '<div class="profile-code"><div class="barcode"></div><div class="no">GF-2026-0721-01<br>2026-09 入学</div></div>' +
      '<div class="stamp profile-stamp">已核验 · 画像</div>' +
      '<div class="serial" style="margin-top:6px">最近更新 ' + esc(p().updatedAt) + ' · 来源：' + esc(p().sources) + '</div>' +
      '<div class="pstats"><div><b>' + checkinCount() + '</b><span>打卡</span></div><div><b>' + (8 + joinedCount() + createdCount()) + '</b><span>约饭</span></div><div><b>' + favCount() + '</b><span>收藏</span></div></div>';

    html += '<div class="recent"><div class="recent-head"><span>最近打卡</span><a class="link" data-act="drawer" data-kind="messages" style="margin-right:10px">消息</a><a class="link" data-act="drawer" data-kind="history">全部 →</a></div>';
    if (!recent.length) {
      html += '<div class="serial" style="padding:8px 0">还没有新记录，加入一个饭局或收藏一家档口试试。</div>';
    } else {
      recent.forEach(function (c) {
        html += '<div class="recent-row"><span class="rimg">' + (c.icon || '🍽') + '</span><div><div class="rn">' + esc(c.title) + '</div>' +
          '<div class="rt">' + esc(c.ts) + ' · ' + esc(c.type) + '</div></div><b>' + (c.score || '') + '</b></div>';
      });
    }
    html += '</div>';

    html += '<div class="complete"><div class="complete-head"><span>画像完整度</span><b>' + completeness() + '%</b></div>' +
      '<div class="bar"><i style="width:' + completeness() + '%"></i></div>' +
      '<div class="complete-head" style="margin-top:9px"><span>补全作息与口味强度</span><a class="link" data-act="edit-profile">去完善 →</a></div></div>';
    html += '<div style="margin-top:10px"><button class="btn" style="width:100%" data-act="edit-profile">编辑口味画像</button></div>';
    html += '</aside>';
    return html;
  }

  function heroCard(s) {
    var score = stallScore(s);
    var rs = reasonsOf(s);
    return '<article class="card hero" data-act="open-stall" data-id="' + s.id + '">' +
      '<div class="hero-photo' + (s.img ? ' has-img' : '') + '">' +
      (s.img ? '<img class="hero-img" src="' + esc(s.img) + '" alt="' + esc(s.name) + ' 菜品图">' : '<div class="dish">' + s.emoji + '</div>') +
      '<span class="stamp">今日首推</span>' +
      '<div class="loc"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 21s7-6.1 7-11a7 7 0 1 0-14 0c0 4.9 7 11 7 11z"/><circle cx="12" cy="10" r="2.4"/></svg> ' + esc(s.canteen) + ' ' + esc(s.floor) + ' · 步行 ' + s.walk + 'm</div></div>' +
      '<div class="hero-body"><div class="hero-title"><h2>' + esc(s.name) + '</h2><span class="tag">档口</span>' + matchBadge(score) + '</div>' +
      '<div class="hero-tags">' + stallTags(s) + '<span class="tag tag-plain">人均 ' + s.price + '</span></div>' +
      '<div class="hero-reason"><div class="rh"><span>WHY THIS · 推荐依据</span><span>命中 ' + rs.length + ' / 3</span></div><div class="reason-list">' +
      rs.map(function (r, i) { return '<div><span class="idx">0' + (i + 1) + '</span><span><b>' + esc(r.t) + '</b> — ' + esc(r.d) + '</span></div>'; }).join('') +
      '</div></div>' +
      '<div class="hero-foot"><div class="meta"><span><b>' + s.checkins + '</b> 人打卡</span><span>评分 <b>' + s.rating + '</b></span><span>剩 <b>' + s.remain + '</b> 份</span></div>' +
      '<div style="display:flex;gap:8px">' +
      '<button class="btn ' + (isFav(s.id) ? 'faved' : '') + '" data-act="fav" data-id="' + s.id + '">' + (isFav(s.id) ? '已收藏' : '收藏') + '</button>' +
      '<button class="btn btn-primary" data-act="eat-together" data-id="' + s.id + '">约人一起吃</button></div></div></div></article>';
  }

  function recCard(s) {
    var score = stallScore(s);
    var rs = reasonsOf(s)[1] || reasonsOf(s)[0];
    return '<article class="card rec" data-act="open-stall" data-id="' + s.id + '">' +
      '<div class="rec-photo">' + (s.img ? '<img class="food-img" src="' + esc(s.img) + '" alt="' + esc(s.name) + '">' : s.emoji) + '</div><div class="rec-body">' +
      '<div class="rec-top"><div><div class="rec-name">' + esc(s.name) + '</div>' +
      '<div class="rec-sub">' + esc(s.canteen) + ' ' + esc(s.floor) + ' · 人均 ' + s.price + ' · 步行 ' + s.walk + 'm</div></div>' + matchBadge(score, '%') + '</div>' +
      '<div class="rec-reason">' + esc(rs.t) + ' — ' + esc(rs.d) + '</div>' +
      '<div class="rec-foot"><span><b>' + s.rating + '</b> 分</span><span>' + s.checkins + ' 人打卡</span></div>' +
      '</div></article>';
  }

  /* ---------------- 约饭广场 ---------------- */
  function filteredMeetups() {
    var list = meetups().filter(function (m) { return !m.campus || m.campus === campusName(); });
    if (ui.chip === '同校区') list = list.filter(function (m) { return (m.campus || campusName()) === campusName(); });
    if (ui.chip === '今晚') list = list.filter(function (m) { return m.today; });
    if (ui.chip === '女生优先') list = list.filter(function (m) { return (m.tags || []).indexOf('女生优先') >= 0; });
    if (ui.chip === '拼单') list = list.filter(function (m) { return /拼单/.test(m.mode) || (m.tags || []).indexOf('拼单') >= 0; });
    if (ui.budget === '30') list = list.filter(function (m) { return m.budget <= 30; });
    if (ui.budget === '20') list = list.filter(function (m) { return m.budget <= 20; });
    if (ui.chip !== '全部' && !list.length) {
      // 空结果由视图提示，避免误筛选后一片空白
    }
    if (ui.sort === 'match') list.sort(function (a, b) { return meetupScore(b) - meetupScore(a); });
    else if (ui.sort === 'budget') list.sort(function (a, b) { return a.budget - b.budget; });
    else list.sort(function (a, b) { return (b.created ? 1 : 0) - (a.created ? 1 : 0); });
    return list;
  }

  function renderMeetup() {
    var list = filteredMeetups();
    var html = '<div class="wrap">';
    html += '<div class="page-head"><div><div class="kicker">约饭广场</div>' +
      '<h1>饭搭子广场<small>' + (12 + joinedCount() + createdCount()) + ' 位同学今晚想约饭</small></h1></div>' +
      '<div class="right">已按你的画像筛出 <b>' + list.length + '</b> 个高匹配饭局 <a class="link" data-act="drawer" data-kind="joined">我的约饭 ' + myMeetupCount() + ' →</a></div></div>';

    html += '<div class="filters">' +
      ['全部', '同校区', '今晚', '女生优先', '拼单'].map(function (c) {
        return '<span class="chip ' + (ui.chip === c ? 'on' : '') + '" data-act="chip" data-value="' + c + '">' + c + '</span>';
      }).join('') +
      '<span class="spacer"><select id="budgetSel" data-act="budget" style="border:0;background:transparent;font:inherit;color:inherit;cursor:pointer">' +
      option('all', '人均不限', ui.budget) + option('30', '人均 ≤ 30', ui.budget) + option('20', '人均 ≤ 20', ui.budget) + '</select>' +
      ' &nbsp; 排序：<select id="sortSel" data-act="sort" style="border:0;background:transparent;font:inherit;color:inherit;cursor:pointer">' +
      option('latest', '最新发布', ui.sort) + option('match', '匹配度优先', ui.sort) + option('budget', '人均从低到高', ui.sort) + '</select></span></div>';

    html += '<div class="meetup"><div class="mlist">';
    if (!list.length) {
      html += '<div class="empty-state">当前筛选下没有饭局。<br><button class="btn btn-primary" style="margin-top:12px" data-act="publish-meetup">发起一个饭局</button></div>';
    } else {
      html += list.map(meetupCard).join('');
    }
    html += '</div>';

    html += '<div class="rail">';
    html += mapCard();
    html += rankCard();
    html += '<div class="cta"><div><div class="t">发起你的饭局</div><div class="s">最快 30 秒找到愿意拼桌的同学</div>' +
      '<div class="cta-stat"><span>本周 ' + (128 + state.createdMeetups.length) + ' 局拼桌成功</span><span>平均 2.3 人成局</span></div></div>' +
      '<button class="btn" data-act="publish-meetup">立即发起</button></div>';
    html += '</div></div></div>';
    return html;
  }

  function option(v, label, cur) {
    return '<option value="' + v + '"' + (cur === v ? ' selected' : '') + '>' + label + '</option>';
  }

  function meetupCard(m) {
    var joined = isJoined(m.id);
    var score = meetupScore(m);
    var seat = seatsOf(m);
    var full = seat >= m.size;
    var seatHtml = '';
    for (var i = 0; i < m.size; i++) seatHtml += '<i class="' + (i < seat ? 'f' : '') + '"></i>';
    var distIcon = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 21s7-6.1 7-11a7 7 0 1 0-14 0c0 4.9 7 11 7 11z"/><circle cx="12" cy="10" r="2.4"/></svg>';
    return '<article class="card mcard' + (m.featured ? ' featured' : '') + '">' +
      '<div class="side"><span class="mavatar">' + m.avatar + '</span><span class="serial">ID ' + esc(m.id.replace('m', '07') + '1-0') + '</span></div>' +
      '<div class="mbody"><div class="mhead"><span class="mname">' + esc(m.owner) + '</span>' +
      '<span class="mmeta"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M20 6 9 17l-5-5"/></svg> ' + esc(m.college) + ' · ' + esc(m.grade) + '</span>' +
      (m.created ? '<span class="tag" style="margin-left:auto">我发起</span>' : (m.mode ? '<span class="tag tag-plain" style="margin-left:auto">' + esc(m.mode) + '</span>' : '')) + '</div>' +
      '<div class="mline"><span class="time">' + esc(m.when) + '</span><span>' + esc(m.canteen) + ' ' + esc(m.floor) + '</span>' +
      '<span class="dish">' + esc(m.dish) + '</span><span style="color:var(--ink-3);font-size:12px">想找 ' + m.want + ' 人' + (m.mode === '自习搭子' ? ' · 聊保研经验' : m.mode === '夜宵局' ? ' · AA 制' : '拼桌') + '</span></div>' +
      '<div class="mtags">' + m.tags.map(function (t, i) { return '<span class="tag' + (i ? ' tag-plain' : '') + '">' + esc(t) + '</span>'; }).join('') +
      '<span class="tag tag-plain">人均 ' + m.budget + '</span></div>' +
      '<div class="mseat"><span class="seats">' + seatHtml + '</span> ' + seat + ' / ' + m.size + ' 人 · ' + (full ? '已满员' : '还差 ' + (m.size - seat) + ' 人') + ' · ' + esc(m.postedAt || '刚刚发布') + '</div></div>' +
      '<div class="mside">' +
      (m.featured ? '<span class="stamp">最匹配</span>' : '') + matchBadge(score) +
      '<span class="dist">' + distIcon + ' ' + esc(m.distance) + '</span>' +
      '<button class="btn ' + (m.created ? '' : (joined ? 'joined' : 'btn-primary')) + '" data-act="join" data-id="' + m.id + '"' +
      (m.created ? ' disabled' : (full && !joined ? ' disabled' : '')) + '>' +
      (m.created ? '我发起的' : (joined ? '已加入 · 退出' : (full ? '已满员' : '加入饭局 →'))) + '</button>' +
      '<button class="btn btn-ghost" data-act="greet" data-id="' + m.id + '"' + (state.greetings[m.id] ? ' disabled' : '') + '>' +
      (state.greetings[m.id] ? '已打招呼' : '打招呼') + '</button></div></article>';
  }

  function mapCard() {
    if (!isOpenCampus() || !canteensOfCampus().length) {
      return '<section class="card map"><div class="map-head"><span class="t">校园地图 · ' + esc(campusName()) + '</span><span class="mono">待完善</span></div>' +
        '<div class="map-empty">该校区地图与食堂数据正在整理。<br><a class="link" data-act="join-school">申请补充校区数据 →</a></div></section>';
    }
    var blocks = SEED.mapBlocks.map(function (b) {
      return '<div class="blk' + (b.hot ? ' hot' : '') + '" style="left:' + b.left + 'px;top:' + b.top + 'px;width:' + b.w + 'px;height:' + b.h + 'px">' + esc(b.name) + '</div>';
    }).join('');
    return '<section class="card map"><div class="map-head"><span class="t">校园地图 · ' + esc(campusName()) + '</span><span class="mono">' + canteensOfCampus().length + ' 个食堂 ' + (8 + joinedCount() + createdCount()) + ' 个饭局</span></div>' +
      (geoLabel() ? '<div class="geo-line">📍 ' + esc(geoLabel()) + '</div>' : '') +
      '<div class="map-canvas">' + blocks +
      '<div class="route" style="left:50px;top:58px;width:90px;transform:rotate(8deg)"></div>' +
      '<div class="route" style="left:56px;top:60px;width:46px;transform:rotate(74deg)"></div>' +
      '<div class="route" style="left:60px;top:136px;width:74px;transform:rotate(24deg)"></div>' +
      '<div class="pin" style="left:160px;top:66px;z-index:3"><i></i>家和 3F</div>' +
      '<div class="pin" style="left:40px;top:140px;z-index:3"><i></i>养贤 2F</div>' +
      '<div class="pin" style="left:128px;top:158px;z-index:3"><i></i>西门</div></div></section>';
  }

  function rankCard() {
    if (!isOpenCampus() || !stalls().length) {
      return '<section class="card rail-sec"><div class="rail-title"><span>本周热度榜</span><span class="serial">筹备中</span></div>' +
        '<div class="map-empty">' + esc(campusName()) + ' 的榜单还没开放。<br><a class="link" data-act="join-school">提交校区资料 →</a></div></section>';
    }
    var cats = Object.keys(SEED.ranks);
    var rows = SEED.ranks[ui.rankCat] || [];
    return '<section class="card rail-sec"><div class="rail-title"><span>本周热度榜</span><a class="link" data-act="drawer" data-kind="ranks" style="font-size:11.5px">完整榜单 →</a></div>' +
      '<div class="rank-tabs">' + cats.map(function (c) {
        return '<span class="rank-tab ' + (ui.rankCat === c ? 'on' : '') + '" data-act="rank" data-cat="' + c + '">' + c + '</span>';
      }).join('') + '</div><div class="rank">' + rows.map(function (r, i) {
        var up = r.trend > 0 ? '↑' + r.trend : r.trend < 0 ? '↓' + (-r.trend) : '—';
        return '<div class="rank-row"><span class="no">' + String(i + 1).padStart(2, '0') + '</span><span class="nm">' + esc(r.name) + '</span><span class="q">' + esc(r.q) + '</span><span class="up">' + up + '</span></div>';
      }).join('') + '</div></section>';
  }

  /* ---------------- 美食社区 ---------------- */
  function visiblePosts() {
    var list = posts().filter(function (po) { return !po.campus || po.campus === campusName(); });
    var display = ui.topic || '# 食堂隐藏吃法';
    var kw = '';
    SEED.topics.forEach(function (t) { if (t.name === display) kw = t.tag; });
    if (!kw) kw = display.replace('#', '').replace(/\s/g, '');
    if (ui.topic) {
      list = list.filter(function (po) {
        var blob = (po.topic || '') + ' ' + po.tags.join(' ');
        return blob.indexOf(kw) >= 0;
      });
    }
    if (ui.communityChip === '口味') list = list.filter(function (po) { return /辣|轻食|咖啡|酸甜|口味/.test(po.tags.join('')); });
    if (ui.communityChip === '场景') list = list.filter(function (po) { return /早八|夜宵|隐藏吃法|自习|期末/.test(po.tags.join('') + po.topic); });
    if (ui.communityChip === '人均') list = list.filter(function (po) { return /人均|元/.test(po.tags.join('')); });
    return list;
  }

  function renderCommunity() {
    var list = visiblePosts();
    var topicTitle = ui.topic || '# 食堂隐藏吃法';
    var html = '<div class="wrap">';
    html += '<div class="page-head"><div><div class="kicker">美食社区</div>' +
      '<h1>美食社区<small>' + ((12000 + posts().length - 6) / 10000).toFixed(1) + ' 万条校园打卡</small></h1></div>' +
      '<div class="right">今日新增 <b>128</b> 条 <span style="color:var(--line)">|</span> 全部已核验 <a class="link" data-act="publish-post">发布打卡 →</a></div></div>';

    html += '<header class="topic card"><span class="hash">' + esc(topicTitle) + '</span>' +
      '<span class="meta"><span><b>' + (3286 + posts().length - 6) + '</b> 人参与</span><span>今日新增 <b>128</b> 条</span><span>含 46 家档口</span></span>' +
      '<span class="chips">' + ['口味', '场景', '人均'].map(function (c) {
        return '<span class="chip2 ' + (ui.communityChip === c ? 'on' : '') + '" data-act="community-chip" data-value="' + c + '">' + c + '</span>';
      }).join('') + '</span><span class="barcode"></span>' +
      '<button class="btn" data-act="publish-post">参与话题</button></header>';

    html += '<div class="community"><div class="posts">';
    if (!list.length) {
      html += '<div class="empty-state">这个话题下还没有打卡，来发第一条吧。<br><button class="btn btn-primary" style="margin-top:12px" data-act="publish-post">发布打卡</button></div>';
    } else {
      html += list.map(postCard).join('');
    }
    html += '</div>';

    html += '<div class="crail">';
    html += '<section class="card rail-sec"><div class="rail-title"><span>热门话题</span><a class="link" data-act="drawer" data-kind="topics" style="font-size:11.5px">全部 →</a></div><div class="rank">' +
      SEED.topics.map(function (t, i) {
        return '<div class="hot-row" data-act="topic" data-tag="' + esc(t.name) + '" style="cursor:pointer"><span class="no">' + String(i + 1).padStart(2, '0') + '</span>' +
          '<span class="nm">' + esc(t.name) + '</span><span class="ct">' + t.people.toLocaleString() + ' 人</span></div>';
      }).join('') + '</div></section>';

    html += '<section class="card rail-sec"><div class="rail-title"><span>活跃打卡王</span><a class="link" data-act="drawer" data-kind="kings" style="font-size:11.5px">榜单 →</a></div>' +
      SEED.kings.map(function (k, i) {
        return '<div class="king" style="' + (i === SEED.kings.length - 1 ? 'border-bottom:0' : '') + '"><span class="av">' + k.avatar + '</span><div><div class="nm">' + esc(k.name) + '</div>' +
          '<div class="sub">' + k.posts + ' 条 · 连续 ' + k.days + ' 天</div></div><span class="ct">Lv.' + k.level + '</span></div>';
      }).join('') + '</section>';

    html += '<section class="challenge"><div style="display:flex;align-items:center;justify-content:space-between"><span class="t">' + esc(SEED.challenge.title) + '</span><span class="serial">DAY 05</span></div>' +
      '<div class="s">拍一张「食堂隐藏吃法」并标注档口与价格，参与即可抽校园美食券。</div>' +
      '<div class="bar"><i style="width:' + SEED.challenge.percent + '%"></i></div>' +
      '<div class="row"><span>' + (SEED.challenge.joined + state.createdPosts.length) + ' 人已参与</span><span>还剩 ' + SEED.challenge.hoursLeft + ' 小时</span></div>' +
      '<button class="btn" data-act="challenge">立即挑战</button></section>';
    html += '</div></div>';
    html += '<p class="img-note">菜品图来自小红书博主实拍，仅课程演示；版权与来源见 assets/img/CREDITS.md。</p></div>';
    return html;
  }

  function postCard(po) {
    var lk = liked(po.id), cl = collected(po.id);
    return '<article class="card post">' +
      '<div class="post-strip"><span class="serial">' + esc(po.code || 'C-1024-000') + '</span><span class="serial">' + esc(po.clock || '刚刚') + '</span></div>' +
      '<div class="post-author"><span class="post-avatar">' + po.avatar + '</span><div><div class="post-name">' + esc(po.author) + '</div>' +
      '<div class="post-ts">' + esc(po.time) + ' · ' + esc(po.canteen) + '</div></div>' + (po.verified ? '<span class="tag" style="margin-left:auto">今日打卡</span>' : '') + '</div>' +
      '<div class="post-photo">' + (po.img ? '<img class="food-img" src="' + esc(po.img) + '" alt="' + esc(po.dish || '打卡菜品') + '">' : po.emoji) + '</div>' +
      '<div class="post-text">' + esc(po.text) + '</div>' +
      '<div class="post-tags">' + po.tags.map(function (t, i) { return '<span class="tag' + (t.charAt(0) === '#' ? '' : ' tag-plain') + '">' + esc(t) + '</span>'; }).join('') + '</div>' +
      '<div class="post-meta">' + esc(po.meta) + '</div>' +
      '<div class="post-foot"><button class="metabtn ' + (lk ? 'on' : '') + '" data-act="like" data-id="' + po.id + '">♡ ' + likesOf(po) + '</button>' +
      '<button class="metabtn" data-act="comment" data-id="' + po.id + '">◌ ' + commentsCount(po) + '</button>' +
      '<button class="metabtn ' + (cl ? 'on' : '') + '" data-act="collect" data-id="' + po.id + '">⌗ ' + favsOf(po) + '</button>' +
      (po.verified ? '<span class="stamp">已核验</span>' : '') + '</div></article>';
  }

  /* ---------------- 视图路由 ---------------- */
  function setMenu(view) {
    $$('#menu a').forEach(function (a) { a.classList.toggle('on', a.dataset.nav === view); });
  }
  function render(keepScroll) {
    var view = location.hash.replace(/^#\//, '') || 'home';
    if (['home', 'nearby', 'meetup', 'community'].indexOf(view) < 0) view = 'home';
    ui.view = view;
    setMenu(view);
    var html = view === 'nearby' ? renderNearby() : view === 'meetup' ? renderMeetup() : view === 'community' ? renderCommunity() : renderHome();
    var y = window.scrollY;
    $('#view').innerHTML = html;
    if (keepScroll) window.scrollTo(0, y); else window.scrollTo({ top: 0 });
  }
  function go(view) {
    location.hash = '#/' + view;
    if (ui.view === view) render();
  }

  function afterMutation() {
    var keep = drawerKind && $('#drawer').classList.contains('open') ? drawerKind : null;
    render(true);
    if (keep) openDrawer(keep);
  }

  /* ---------------- Toast ---------------- */
  function toast(msg, ok) {
    var root = $('#toast-root');
    var el = document.createElement('div');
    el.className = 'toast' + (ok === false ? '' : ' ok');
    el.textContent = msg;
    root.appendChild(el);
    setTimeout(function () { el.remove(); }, 2600);
  }

  /* ---------------- Modal ---------------- */
  function closeModal() {
    $('#modalMask').classList.remove('open');
    $('#modal').innerHTML = '';
  }
  function openModal(html, wide) {
    var m = $('#modal');
    m.className = 'modal' + (wide ? ' wide' : '');
    m.innerHTML = '<div class="modal-head"><h3>' + esc(html.title || '') + '</h3>' + (html.sub ? '<div class="modal-sub">' + esc(html.sub) + '</div>' : '') +
      '<button class="close" data-close type="button" aria-label="关闭">✕</button></div><div class="modal-body">' + html.body + '</div>';
    $('#modalMask').classList.add('open');
    var first = m.querySelector('input, select, textarea');
    if (first) setTimeout(function () { first.focus(); }, 30);
  }

  function formRow(label, input, full) {
    return '<div class="field' + (full ? ' full' : '') + '"><label>' + label + '</label>' + input + '</div>';
  }

  /* ---------------- 登录 / 注册 ---------------- */
  function authModal(register) {
    backendReady = false;
    var body = register ?
      '<form id="authRegisterForm"><div class="form-grid">' +
      formRow('昵称', '<input name="name" required maxlength="12" value="干饭同学">') +
      formRow('邮箱', '<input name="email" type="email" required maxlength="80" placeholder="you@example.com">') +
      formRow('密码（至少 8 位）', '<input name="password" type="password" required minlength="8" maxlength="72" autocomplete="new-password">', true) +
      '</div><div class="modal-actions"><button type="button" class="btn" data-act="show-login">已有账号</button><button type="submit" class="btn btn-primary">注册并登录</button></div></form>' :
      '<form id="authLoginForm"><div class="form-grid">' +
      formRow('邮箱', '<input name="email" type="email" required maxlength="80" value="demo@ganfan.local">') +
      formRow('密码', '<input name="password" type="password" required maxlength="72" value="demo123456" autocomplete="current-password">') +
      '</div><div class="modal-actions"><button type="button" class="btn" data-act="show-register">注册账号</button><button type="button" class="btn" data-act="demo-login">使用演示账号</button><button type="submit" class="btn btn-primary">登录</button></div></form>';
    openModal({
      title: register ? '注册干饭搭子' : '登录干饭搭子',
      sub: 'Spring Boot + MySQL 真实账号体系；演示账号 demo@ganfan.local / demo123456',
      body: body
    });
  }

  function submitAuth(form, register) {
    var fd = new FormData(form);
    var body = {
      email: (fd.get('email') || '').toString().trim(),
      password: (fd.get('password') || '').toString()
    };
    if (register) body.name = (fd.get('name') || '').toString().trim();
    var button = form.querySelector('[type="submit"]');
    if (button) button.disabled = true;
    var promise = register ? GanfanAPI.register(body) : GanfanAPI.login(body);
    promise.then(function () {
      toast(register ? '注册成功，正在进入…' : '登录成功，正在进入…');
      location.reload();
    }).catch(function (error) {
      if (button) button.disabled = false;
      toast(error.message, false);
    });
  }

  function publishMeetupModal(prefill) {
    prefill = prefill || {};
    var canteenOpts = canteensOfCampus().map(function (c) {
      return '<option' + (prefill && prefill.canteen === c.name ? ' selected' : '') + '>' + c.name + '</option>';
    }).join('');
    if (!canteenOpts) canteenOpts = '<option>该校区食堂待补充</option>';
    var tagPool = ['拼桌', '夜宵党', '减脂友好', '女生优先', '自习搭子', '游戏搭子', 'AA 制'];
    var body = '<form id="publishMeetupForm"><div class="form-grid">' +
      formRow('菜品', '<input name="dish" required maxlength="20" value="' + esc(prefill.dish || '') + '" placeholder="如 麻辣香锅">') +
      formRow('食堂', '<select name="canteen">' + canteenOpts + '</select>') +
      formRow('时间', '<select name="when"><option>今晚</option><option>明天</option><option>后天</option></select>') +
      formRow('具体时间', '<input name="time" type="time" value="18:30" required>') +
      formRow('想找几人', '<select name="want"><option>1</option><option>2</option><option>3</option></select>') +
      formRow('人均预算', '<input name="budget" type="number" min="5" max="100" value="' + esc(prefill.price || 20) + '">') +
      '<div class="field full"><label>标签（可多选）</label><div class="chips-row" data-multi="tags">' +
      tagPool.map(function (t) { return '<span class="chip" data-tag="' + t + '">' + t + '</span>'; }).join('') + '</div></div>' +
      formRow('补充说明', '<input name="note" maxlength="40" placeholder="如：想边走边聊，介意勿扰">', true) +
      '</div><div class="modal-actions"><button type="button" class="btn" data-close>取消</button><button type="submit" class="btn btn-primary">发布约饭</button></div></form>';
    openModal({ title: '发起饭局', sub: '发布后立即出现在约饭广场，并计入「我的约饭」', body: body });
  }

  function publishPostModal(prefillTag) {
    var canteenOpts = canteensOfCampus().map(function (c) { return '<option>' + c.name + '</option>'; }).join('');
    if (!canteenOpts) canteenOpts = '<option>该校区食堂待补充</option>';
    var tagPool = ['隐藏吃法', '减脂期', '早八', '夜宵', '咖啡', '人均'];
    var body = '<form id="publishPostForm"><div class="form-grid">' +
      formRow('打卡内容', '<textarea name="text" required maxlength="120" placeholder="写下隐藏吃法、价格和推荐理由"></textarea>', true) +
      formRow('食堂', '<select name="canteen">' + canteenOpts + '</select>') +
      formRow('档口 / 楼层', '<input name="place" maxlength="20" placeholder="如 学二食堂 3F">') +
      formRow('人均价格', '<input name="price" type="number" min="1" max="100" value="18">') +
      '<div class="field"><label>话题标签（可多选）</label><div class="chips-row" data-multi="tags">' +
      tagPool.map(function (t) {
        return '<span class="chip' + (prefillTag === t ? ' on' : '') + '" data-tag="' + t + '">' + t + '</span>';
      }).join('') + '</div></div>' +
      '</div><div class="modal-actions"><button type="button" class="btn" data-close>取消</button><button type="submit" class="btn btn-primary">发布打卡</button></div></form>';
    openModal({ title: '发布打卡', sub: '支持话题标签；发布后进入社区信息流与「我的打卡」', body: body });
  }

  /* ---------------- 学校 / 校区入驻 ---------------- */
  function schoolModal() {
    var rows = SEED.schools.map(function (s) {
      var on = s.id === state.schoolId;
      var open = s.status === 'open';
      return '<button class="school-row' + (on ? ' on' : '') + '" data-act="pick-school" data-id="' + s.id + '">' +
        '<span class="s-ic">' + (open ? '🏫' : '🚧') + '</span>' +
        '<span class="s-main"><b>' + esc(s.university) + ' · ' + esc(s.campus) + '</b>' +
        '<span>' + esc(s.city) + ' · ' + esc(s.note) + ' · ' + esc(s.students) + '</span></span>' +
        '<span class="s-tag' + (open ? ' ok' : '') + '">' + (open ? '已入驻' : '筹备中') + (on ? ' · 当前' : '') + '</span></button>';
    }).join('');
    var apps = (state.applications || []).slice(0, 3).map(function (a) {
      return '<div class="apply-row"><span>📨</span><div><b>' + esc(a.university) + ' · ' + esc(a.campus) + '</b><span>' + esc(a.ts) + ' 提交 · ' + esc(a.status) + '</span></div></div>';
    }).join('');
    var body =
      '<div class="school-note">选择后，首页推荐、约饭广场与美食社区都只展示该校区的内容。</div>' +
      '<div class="school-list">' + rows + '</div>' +
      '<div class="school-actions"><button class="btn" data-act="locate">📍 用定位推荐校区</button>' +
      '<button class="btn btn-primary" data-act="join-school">+ 我的学校也要入驻</button></div>' +
      (apps ? '<div class="apply-list"><div class="apply-title">我的入驻申请</div>' + apps + '</div>' : '') +
      '<div class="school-note small">定位只用于计算你与食堂的距离，保存在本机 localStorage，不上传；拒绝授权不影响手动选校区。</div>';
    openModal({ title: '选择学校 / 校区', sub: '已入驻校区可直接使用；未收录学校可提交入驻申请', body: body }, true);
  }

  function applicationModal(prefill) {
    prefill = prefill || {};
    var body = '<form id="schoolApplyForm"><div class="form-grid">' +
      formRow('学校全称', '<input name="university" required maxlength="30" value="' + esc(prefill.university || '') + '" placeholder="如 浙江工业大学">') +
      formRow('校区', '<input name="campus" required maxlength="20" value="' + esc(prefill.campus || '') + '" placeholder="如 屏峰校区">') +
      formRow('城市', '<input name="city" required maxlength="20" value="' + esc(prefill.city || '杭州') + '">') +
      formRow('联系人', '<input name="contact" required maxlength="20" placeholder="学生组织 / 社团负责人">') +
      formRow('手机号', '<input name="phone" required maxlength="20" placeholder="仅用于入驻联系">') +
      formRow('食堂数量', '<select name="canteens"><option>1</option><option>2</option><option>3</option><option>4 个及以上</option></select>') +
      formRow('学校邮箱 / 公众号', '<input name="email" maxlength="40" placeholder="选填，便于核验">') +
      formRow('补充说明', '<textarea name="note" maxlength="120" placeholder="食堂名称、特色档口、希望上线时间"></textarea>', true) +
      '</div><div class="apply-tip">入驻流程：提交资料 → 核验学校身份 → 补充食堂/档口数据 → 开放该校区内容与约饭。</div>' +
      '<div class="modal-actions"><button type="button" class="btn" data-close>取消</button><button type="submit" class="btn btn-primary">提交入驻申请</button></div></form>';
    openModal({ title: '学校入驻申请', sub: '其他学校也可通过这里加入，审核通过后开放该校区', body: body }, true);
  }

  function submitApplication(data) {
    var app = {
      id: uid('a'), university: data.university, campus: data.campus, city: data.city,
      contact: data.contact, phone: data.phone, email: data.email || '',
      canteens: data.canteens || '', note: data.note || '',
      status: '待审核', ts: nowLabel()
    };
    state.applications.unshift(app);
    saveState();
    syncState('/school-applications', 'POST', data);
    return app;
  }

  function editProfileModal() {
    var dislikes = ['香菜', '折耳根', '海鲜', '花生', '乳制品'];
    var body = '<form id="editProfileForm"><div class="form-grid">' +
      formRow('昵称', '<input name="name" maxlength="12" value="' + esc(p().name) + '">') +
      formRow('辣度', '<select name="spicy">' + ['不辣', '微辣', '中辣', '重辣'].map(function (s) { return '<option' + (p().spicy === s ? ' selected' : '') + '>' + s + '</option>'; }).join('') + '</select>') +
      formRow('单餐预算下限（元）', '<input name="budgetMin" type="number" min="5" max="80" value="' + p().budget[0] + '">') +
      formRow('单餐预算上限（元）', '<input name="budgetMax" type="number" min="5" max="120" value="' + p().budget[1] + '">') +
      formRow('作息', '<select name="sleep">' + ['早八人', '夜猫子', '作息规律'].map(function (s) { return '<option' + (p().sleep === s ? ' selected' : '') + '>' + s + '</option>'; }).join('') + '</select>') +
      formRow('社交偏好', '<select name="social">' + ['愿意拼桌', '仅限熟人', '都可以'].map(function (s) { return '<option' + (p().social === s ? ' selected' : '') + '>' + s + '</option>'; }).join('') + '</select>') +
      '<div class="field full"><label>忌口（可多选）</label><div class="chips-row" data-multi="dislikes">' +
      dislikes.map(function (d) {
        return '<span class="chip' + (p().dislikes.indexOf(d) >= 0 ? ' on' : '') + '" data-tag="' + d + '">' + d + '</span>';
      }).join('') + '</div><div class="hint">保存后会立即影响推荐依据与约饭匹配度。</div></div>' +
      '</div><div class="modal-actions"><button type="button" class="btn" data-close>取消</button><button type="submit" class="btn btn-primary">保存画像</button></div></form>';
    openModal({ title: '编辑口味画像', sub: '这些字段全部参与推荐与匹配计算，保存后立即生效', body: body }, true);
  }

  function greetModal(m) {
    var body = '<form id="greetForm"><div class="field full"><label>给 ' + esc(m.owner) + ' 发消息</label>' +
      '<textarea name="msg" required maxlength="80" placeholder="如：我也想吃这个，18:30 在学一 2F 见？"></textarea>' +
      '<div class="hint">演示环境：消息会记入本地「我的消息」。</div></div>' +
      '<div class="modal-actions"><button type="button" class="btn" data-close>取消</button><button type="submit" class="btn btn-primary">发送</button></div></form>';
    openModal({ title: '打招呼', sub: '与 ' + esc(m.owner) + ' · ' + esc(m.dish) + ' · ' + esc(m.when), body: body });
  }

  function stallModal(s) {
    var rs = reasonsOf(s);
    var body = '<div class="list">' +
      rs.map(function (r) { return '<div class="list-row"><span class="grow"><b>' + esc(r.t) + '</b><br><span style="color:var(--ink-3)">' + esc(r.d) + '</span></span></div>'; }).join('') +
      '<div class="list-row"><span class="grow"><b>档口信息</b><br><span style="color:var(--ink-3)">' + esc(s.canteen) + ' ' + esc(s.floor) + ' · 人均 ' + s.price + ' · 评分 ' + s.rating + ' · ' + s.checkins + ' 人打卡</span></span></div>' +
      '</div><div class="modal-actions">' +
      '<button class="btn ' + (isFav(s.id) ? 'faved' : '') + '" data-act="fav" data-id="' + s.id + '">' + (isFav(s.id) ? '已收藏' : '收藏') + '</button>' +
      '<button class="btn btn-primary" data-act="eat-together" data-id="' + s.id + '">约人一起吃</button></div>';
    openModal({ title: s.name + ' · ' + stallScore(s) + '% 匹配', sub: '推荐依据由你的画像实时计算，修改画像后会同步变化', body: body });
  }

  function commentsModal(post) {
    var list = commentsOf(post.id);
    var body = '<div class="list">' + (list.length ? '' : '<div class="empty">还没有评论，来写第一条。</div>') +
      list.map(function (c) { return '<div class="comment-row"><b>' + esc(c.user) + '</b><span class="ts">' + esc(c.ts) + '</span><br>' + esc(c.text) + '</div>'; }).join('') +
      '</div><form id="commentForm" data-target="' + post.id + '"><div class="field full" style="margin-top:12px"><label>写评论</label>' +
      '<textarea name="text" required maxlength="80" placeholder="友善发言，支持原创"></textarea></div>' +
      '<div class="modal-actions"><button type="button" class="btn" data-close>关闭</button><button type="submit" class="btn btn-primary">发送评论</button></div></form>';
    openModal({ title: '评论 · ' + post.author, sub: post.text.slice(0, 30) + '…', body: body });
  }

  /* ---------------- Drawer ---------------- */
  function closeDrawer() {
    $('#drawer').classList.remove('open');
    $('#drawerMask').classList.remove('open');
    drawerKind = null;
  }
  function openDrawer(kind) {
    var d = $('#drawer'), title = '', sub = '', rows = '';
    if (kind === 'favorites') {
      var collectedPosts = posts().filter(function (po) { return collected(po.id); });
      title = '我的收藏'; sub = '本机收藏 ' + state.favorites.length + ' 家 · ' + collectedPosts.length + ' 条帖子（另含 36 家演示收藏）';
      rows = state.favorites.length ? state.favorites.map(function (id) {
        var s = SEED.stalls.filter(function (x) { return x.id === id; })[0];
        return s ? drawerRow(s.emoji, s.name, s.canteen + ' ' + s.floor + ' · 人均 ' + s.price, '<button class="btn sm" data-act="fav" data-id="' + s.id + '">取消</button>') : '';
      }).join('') : '';
      rows += collectedPosts.map(function (po) {
        return drawerRow('💬', po.author + ' · 收藏的打卡', po.text.slice(0, 20) + '…', '<button class="btn sm" data-act="collect" data-id="' + po.id + '">取消</button>');
      }).join('');
      if (!rows) rows = '<div class="empty">还没有收藏，去首页点「收藏」或社区点「⌗」试试。</div>';
    } else if (kind === 'joined') {
      var joinedList = meetups().filter(function (m) { return isJoined(m.id); });
      var mineList = state.createdMeetups;
      title = '我的约饭'; sub = '已加入 ' + joinedList.length + ' 个 · 我发起 ' + mineList.length + ' 个';
      rows = mineList.map(function (m) {
        return drawerRow(m.avatar, m.owner + ' · ' + m.dish, m.when + ' · ' + m.canteen + ' ' + m.floor + ' · ' + seatsOf(m) + '/' + m.size + ' 人 · 我发起', '<span class="serial">进行中</span>');
      }).join('') + joinedList.map(function (m) {
        return drawerRow(m.avatar, m.owner + ' · ' + m.dish, m.when + ' · ' + m.canteen + ' ' + m.floor + ' · ' + seatsOf(m) + '/' + m.size + ' 人',
          '<button class="btn sm" data-act="join" data-id="' + m.id + '">退出</button>');
      }).join('');
      if (!rows) rows = '<div class="empty">还没有加入或发起饭局，去约饭广场看看。</div>';
    } else if (kind === 'history') {
      title = '我的记录'; sub = '本地演示数据，实时更新';
      rows = state.checks.length ? state.checks.slice().reverse().map(function (c) {
        return drawerRow(c.icon || '🍽', c.title, c.ts + ' · ' + c.type, '');
      }).join('') : '<div class="empty">还没有记录，收藏、约饭、打卡都会记在这里。</div>';
    } else if (kind === 'canteens') {
      var cs = canteensOfCampus();
      title = '校园食堂'; sub = campusName() + ' · ' + cs.length + ' 个食堂' + (state.geo ? ' · 已定位' : '');
      rows = cs.map(function (c) {
        var count = SEED.stalls.filter(function (s) { return s.canteen === c.name; }).length;
        var dist = geoDistanceTo(c);
        return drawerRow('🏫', c.name, c.campus + ' · ' + c.floors + ' 层 · ' + c.note,
          '<span class="serial">' + (dist != null ? '距你 ' + dist + 'm' : '步行约 ' + c.walk + 'm') + ' · ' + count + ' 个档口</span>');
      }).join('');
      if (!rows) rows = '<div class="empty">该校区食堂数据待补充。<br><a class="link" data-act="join-school">提交校区资料 →</a></div>';
    } else if (kind === 'ranks') {
      title = '本周榜单'; sub = '四个维度，点击切换';
      if (!isOpenCampus() || !stalls().length) {
        rows = '<div class="empty">' + esc(campusName()) + ' 的榜单数据待补充。<br><a class="link" data-act="join-school">提交校区资料 →</a></div>';
      } else {
        rows = Object.keys(SEED.ranks).map(function (cat) {
          return '<div class="row"><div class="grow"><div class="nm">' + cat + '</div><div class="sub">' +
            SEED.ranks[cat].slice(0, 3).map(function (r) { return r.name; }).join(' · ') + '</div></div>' +
            '<button class="btn sm" data-act="rank" data-cat="' + cat + '">查看</button></div>';
        }).join('');
      }
    } else if (kind === 'topics') {
      title = '热门话题'; sub = '点击筛选社区内容';
      rows = SEED.topics.map(function (t) {
        return drawerRow('#', t.name, t.people.toLocaleString() + ' 人参与 · 今日 +' + t.today, '<button class="btn sm" data-act="topic" data-tag="' + esc(t.name) + '">查看</button>');
      }).join('');
    } else if (kind === 'kings') {
      title = '活跃打卡王'; sub = '连续打卡与质量综合排序';
      rows = SEED.kings.map(function (k, i) {
        return drawerRow(k.avatar, 'NO.' + (i + 1) + ' ' + k.name, k.posts + ' 条 · 连续 ' + k.days + ' 天', '<span class="serial">Lv.' + k.level + '</span>');
      }).join('');
    } else if (kind === 'messages') {
      title = '我的消息'; sub = '打招呼与系统通知';
      rows = state.messages.length ? state.messages.map(function (m) {
        return drawerRow('💬', m.title, m.ts, '');
      }).join('') : '<div class="empty">还没有消息。</div>';
    }
    d.innerHTML = '<div class="drawer-head"><div><h3>' + title + '</h3><div class="sub">' + sub + '</div></div>' +
      '<button class="close" data-close-drawer type="button" aria-label="关闭" style="position:static">✕</button></div>' +
      '<div class="drawer-body">' + rows +
      '<div class="drawer-note">数据保存在浏览器 localStorage，刷新不会丢失。<br>演示结束需要重置时，点下方按钮即可清空收藏 / 约饭 / 打卡记录。' +
      '<div style="margin-top:8px"><button class="btn sm" data-act="reset-demo">重置演示数据</button></div></div>' +
      '</div>';
    d.classList.add('open'); $('#drawerMask').classList.add('open');
    drawerKind = kind;
  }
  function drawerRow(icon, name, sub, right) {
    return '<div class="row"><span class="emoji">' + icon + '</span><div class="grow"><div class="nm">' + esc(name) + '</div><div class="sub">' + esc(sub) + '</div></div>' + (right || '') + '</div>';
  }

  /* ---------------- 业务操作 ---------------- */
  function record(type, title, icon, score) {
    state.checks.push({ type: type, title: title, icon: icon || '🍽', ts: nowLabel(), score: score || '' });
    if (state.checks.length > 30) state.checks = state.checks.slice(-30);
  }
  function toggleFavorite(id) {
    var i = state.favorites.indexOf(id);
    var s = SEED.stalls.filter(function (x) { return x.id === id; })[0];
    if (i >= 0) { state.favorites.splice(i, 1); toast('已取消收藏'); }
    else { state.favorites.push(id); record('收藏', s ? s.name + ' · ' + s.canteen : id, s ? s.emoji : '🔖'); toast('已收藏，可在「我的收藏」查看'); }
    saveState();
    syncState('/favorites/stalls/' + encodeURIComponent(id), i >= 0 ? 'DELETE' : 'POST');
  }
  function toggleJoin(id) {
    var m = meetups().filter(function (x) { return x.id === id; })[0];
    if (!m) return;
    if (isJoined(id)) { state.joined[id] = false; toast('已退出「' + m.dish + '」饭局'); }
    else {
      if (seatsOf(m) >= m.size) { toast('这个饭局已经满员了', false); return; }
      state.joined[id] = true;
      record('约饭', m.owner + ' · ' + m.dish, m.avatar, seatLabel(m));
      toast('已加入「' + m.dish + '」饭局，还差 ' + (m.size - seatsOf(m)) + ' 人');
    }
    saveState();
    syncState('/meetups/' + encodeURIComponent(id) + '/join', isJoined(id) ? 'POST' : 'DELETE');
  }
  function seatLabel(m) { return seatsOf(m) + '/' + m.size; }

  function createMeetup(data) {
    var m = {
      id: 'u' + uid('m'), owner: p().name, avatar: p().avatar, college: p().college, grade: p().grade,
      when: data.when + ' ' + data.time, today: data.when === '今晚',
      canteen: data.canteen, floor: (SEED.canteens.filter(function (c) { return c.name === data.canteen; })[0] || {}).floors ? ((SEED.canteens.filter(function (c) { return c.name === data.canteen; })[0].floors) + 'F') : '1F',
      dish: data.dish, want: Number(data.want), seat: 1, size: Number(data.want) + 1, budget: Number(data.budget),
      base: 90, campus: campusName(), schoolId: state.schoolId, distance: '同校区 · 步行 5 分钟',
      tags: data.tags.length ? data.tags : ['同校区'], mode: '拼桌', featured: false,
      postedAt: '刚刚发布', created: true, note: data.note || ''
    };
    state.createdMeetups.unshift(m);
    syncState('/meetups', 'POST', data);
    return m;
  }

  function createPost(data) {
    var d = new Date();
    var clock = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
    var po = {
      id: 'u' + uid('p'), author: p().name, avatar: p().avatar, emoji: '🍽', time: '刚刚', canteen: data.canteen,
      campus: campusName(), schoolId: state.schoolId,
      meta: data.place + ' · 人均 ' + data.price, text: data.text,
      tags: ['#' + (data.tags[0] || '打卡')].concat(data.tags.slice(1)).concat(['人均 ' + data.price]),
      likes: 0, comments: 0, favs: 0, verified: true, topic: data.tags[0] || '打卡',
      code: 'C-' + String(d.getMonth() + 1).padStart(2, '0') + String(d.getDate()).padStart(2, '0') + '-' + String(d.getSeconds()).padStart(3, '0'),
      clock: clock
    };
    state.createdPosts.unshift(po);
    state.totalCheckins = checkinCount() + 1;
    syncState('/posts', 'POST', data);
    return po;
  }

  /* ---------------- 事件 ---------------- */
  function submitHandler(e) {
    var f = e.target;
    if (!f || !f.id) return;
    e.preventDefault();
    var fd = new FormData(f);

    if (f.id === 'authLoginForm') { submitAuth(f, false); return; }
    if (f.id === 'authRegisterForm') { submitAuth(f, true); return; }

    if (f.id === 'publishMeetupForm') {
      var dish = (fd.get('dish') || '').toString().trim();
      if (!dish) { toast('请填写菜品', false); return; }
      var tags = $$('.chips-row .chip.on', f).map(function (c) { return c.dataset.tag; });
      var m = createMeetup({ dish: dish, canteen: fd.get('canteen'), when: fd.get('when'), time: fd.get('time'), want: fd.get('want'), budget: fd.get('budget'), tags: tags, note: fd.get('note') });
      record('约饭', '发起 ' + m.dish + ' · ' + m.canteen, '🤝');
      saveState(); closeModal(); toast('已发布，去约饭广场看看', true);
      ui.chip = '全部'; ui.budget = 'all'; ui.sort = 'latest';
      if (ui.view !== 'meetup') go('meetup'); else render();
      return;
    }

    if (f.id === 'publishPostForm') {
      var text = (fd.get('text') || '').toString().trim();
      if (!text) { toast('请写点打卡内容', false); return; }
      var tags2 = $$('.chips-row .chip.on', f).map(function (c) { return c.dataset.tag; });
      if (!tags2.length) tags2 = ['隐藏吃法'];
      var po = createPost({ text: text, canteen: fd.get('canteen'), place: (fd.get('place') || fd.get('canteen')).toString(), price: fd.get('price'), tags: tags2 });
      record('打卡', po.text.slice(0, 16) + '…', '📸');
      saveState(); closeModal(); toast('打卡已发布，+1 条社区内容');
      if (ui.view !== 'community') go('community'); else render();
      return;
    }

    if (f.id === 'schoolApplyForm') {
      var uni = (fd.get('university') || '').toString().trim();
      var cam = (fd.get('campus') || '').toString().trim();
      var city = (fd.get('city') || '').toString().trim();
      var contact = (fd.get('contact') || '').toString().trim();
      var phone = (fd.get('phone') || '').toString().trim();
      if (!uni || !cam || !city || !contact || !phone) { toast('请把学校、校区、联系人和手机号填完整', false); return; }
      submitApplication({
        university: uni, campus: cam, city: city, contact: contact, phone: phone,
        email: fd.get('email'), canteens: fd.get('canteens'), note: fd.get('note')
      });
      closeModal();
      schoolModal();
      toast('入驻申请已提交，核验通过后会开放该校区', true);
      return;
    }

    if (f.id === 'editProfileForm') {
      var min = Number(fd.get('budgetMin')), max = Number(fd.get('budgetMax'));
      if (!(min >= 5) || !(max >= min)) { toast('预算区间不合法', false); return; }
      p().name = (fd.get('name') || '').toString().trim() || p().name;
      p().spicy = fd.get('spicy');
      p().budget = [min, max];
      p().sleep = fd.get('sleep');
      p().social = fd.get('social');
      p().dislikes = $$('.chips-row .chip.on', f).map(function (c) { return c.dataset.tag; });
      p().updatedAt = nowLabel();
      state.edited += 1;
      saveState();
      syncState('/profile', 'PUT', { name: p().name, spicy: p().spicy, budgetMin: min, budgetMax: max, sleep: p().sleep, social: p().social, dislikes: p().dislikes });
      closeModal(); toast('画像已保存，推荐与匹配度已重新计算'); render();
      return;
    }

    if (f.id === 'greetForm') {
      var msg = (fd.get('msg') || '').toString().trim();
      if (!msg) { toast('写点什么再发送', false); return; }
      var id = f.dataset.target;
      state.greetings[id] = msg;
      var target = meetups().filter(function (x) { return x.id === id; })[0];
      state.messages.unshift({ title: '给 ' + (target ? target.owner : '同学') + '：' + msg, ts: nowLabel() });
      saveState();
      syncState('/meetups/' + encodeURIComponent(id) + '/greetings', 'POST', { message: msg });
      closeModal(); toast('消息已发送', true); render();
      return;
    }

    if (f.id === 'commentForm') {
      var ctext = (fd.get('text') || '').toString().trim();
      if (!ctext) { toast('评论不能为空', false); return; }
      var pid = f.dataset.target;
      (state.comments[pid] = state.comments[pid] || []).push({ user: p().name, text: ctext, ts: nowLabel() });
      saveState();
      syncState('/posts/' + encodeURIComponent(pid) + '/comments', 'POST', { text: ctext });
      var post = posts().filter(function (x) { return x.id === pid; })[0];
      closeModal(); toast('评论已发布');
      if (post) commentsModal(post); else render();
      return;
    }
  }

  document.addEventListener('submit', submitHandler);

  document.addEventListener('click', function (e) {
    var close = e.target.closest('[data-close]');
    if (close) { closeModal(); return; }
    if (e.target.closest('[data-close-drawer]')) { closeDrawer(); return; }
    if (e.target.id === 'modalMask') { closeModal(); return; }
    if (e.target.id === 'drawerMask') { closeDrawer(); return; }

    var t = e.target.closest('[data-act]');
    if (!t) return;
    var act = t.dataset.act;

    if (act === 'reroll') {
      if (!selfTestMode && window.GanfanAPI && GanfanAPI.hasToken() && backendReady) {
        loadNearbyFromBackend({ source: 'search', refresh: true }).then(function () {
          toast('已换一批档口');
        });
      } else {
        state.reroll += 1; saveState(); render(); toast('已换一批推荐');
      }
    } else if (act === 'show-login') {
      authModal(false);
    } else if (act === 'show-register') {
      authModal(true);
    } else if (act === 'demo-login') {
      GanfanAPI.login({ email: 'demo@ganfan.local', password: 'demo123456' }).then(function () {
        toast('演示账号登录成功'); location.reload();
      }).catch(function (error) { toast(error.message, false); });
    } else if (act === 'retry-backend') {
      closeModal(); startBackend();
    } else if (act === 'fav') {
      toggleFavorite(t.dataset.id);
      if ($('#modalMask').classList.contains('open')) closeModal();
      afterMutation();
    } else if (act === 'join') {
      toggleJoin(t.dataset.id);
      afterMutation();
    } else if (act === 'greet') {
      var m = meetups().filter(function (x) { return x.id === t.dataset.id; })[0];
      if (m) { openGreet(m); }
    } else if (act === 'eat-together') {
      var s = SEED.stalls.filter(function (x) { return x.id === t.dataset.id; })[0];
      publishMeetupModal({ dish: s ? (s.dish || s.dishes[0]) : '', price: s ? s.price : 20, canteen: s ? s.canteen : '' });
    } else if (act === 'open-stall') {
      var st = SEED.stalls.filter(function (x) { return x.id === t.dataset.id; })[0];
      if (st) stallModal(st);
    } else if (act === 'open-meetup') {
      render(); // 已在卡片上展示主要信息，点击卡片保持可继续操作
    } else if (act === 'publish-meetup') {
      if (!isOpenCampus()) { applicationModal({ campus: campusName(), city: curSchool().city }); toast('该校区分支筹备中，可先提交入驻资料'); return; }
      publishMeetupModal({});
    } else if (act === 'edit-profile') {
      editProfileModal();
    } else if (act === 'chip') {
      ui.chip = t.dataset.value; render();
    } else if (act === 'rank') {
      ui.rankCat = t.dataset.cat;
      closeDrawer();
      if (ui.view === 'meetup') render(); else go('meetup');
    } else if (act === 'quick') {
      var kind = t.dataset.kind;
      if (kind === 'community') { go('community'); return; }
      if (kind === 'meetups') { ui.chip = '拼单'; go('meetup'); return; }
      if (kind === 'join-school') { applicationModal({}); return; }
      openDrawer(kind === 'ranks' ? 'ranks' : kind === 'map' ? 'canteens' : kind);
    } else if (act === 'drawer') {
      openDrawer(t.dataset.kind);
    } else if (act === 'school') {
      schoolModal();
    } else if (act === 'pick-school') {
      state.schoolId = t.dataset.id;
      p().campus = campusName();
      saveState();
      syncState('/me/preferences', 'PUT', { schoolId: state.schoolId });
      closeModal();
      ui.search = ''; ui.chip = '全部'; ui.topic = '';
      render();
      if (!selfTestMode && window.GanfanAPI && GanfanAPI.hasToken() && backendReady) {
        loadNearbyFromBackend({ source: 'school', reset: true });
      }
      toast('已切换到 ' + curSchool().university + ' · ' + campusName());
    } else if (act === 'locate') {
      closeModal();
      locateMe();
    } else if (act === 'nearby' || act === 'nearby-page') {
      go('nearby');
      if (!selfTestMode && window.GanfanAPI && GanfanAPI.hasToken() && backendReady && !ui.nearbyRemote) {
        loadNearbyFromBackend({ source: 'search' });
      }
    } else if (act === 'nearby-page-refresh') {
      loadNearbyFromBackend({ source: 'search', refresh: true });
    } else if (act === 'nearby-draw') {
      drawNearby(true);
    } else if (act === 'nearby-chip') {
      ui.nearbyChip = t.dataset.value;
      render(true);
    } else if (act === 'nearby-sort') {
      ui.nearbySort = t.dataset.value;
      render(true);
    } else if (act === 'nearby-random') {
      closeModal();
      var picked = pickNearby(nearbyItems());
      if (picked) { ui.nearbyPick = picked.id; openNearby(false); toast('抽到：' + picked.name); }
    } else if (act === 'nearby-open') {
      openNearbyItem(t.dataset.type, t.dataset.id);
    } else if (act === 'takeout-order') {
      var to = (SEED.takeouts || []).filter(function (x) { return x.id === t.dataset.id; })[0];
      if (to) {
        record('外卖', to.name, to.emoji, takeoutScore(to));
        saveState();
        syncState('/takeouts/' + encodeURIComponent(to.id) + '/orders', 'POST', {});
        closeModal(); render();
        toast('已记录：' + to.name + '（演示点单）');
      }
    } else if (act === 'join-school') {
      applicationModal({ campus: campusName(), city: curSchool().city });
    } else if (act === 'like') {
      var pid = t.dataset.id;
      if (liked(pid)) delete state.likes[pid]; else state.likes[pid] = true;
      saveState();
      syncState('/posts/' + encodeURIComponent(pid) + '/like', liked(pid) ? 'POST' : 'DELETE');
      render();
    } else if (act === 'collect') {
      var cid = t.dataset.id;
      if (collected(cid)) delete state.collects[cid]; else state.collects[cid] = true;
      saveState();
      syncState('/favorites/posts/' + encodeURIComponent(cid), collected(cid) ? 'POST' : 'DELETE');
      afterMutation();
    } else if (act === 'comment') {
      var post = posts().filter(function (x) { return x.id === t.dataset.id; })[0];
      if (post) commentsModal(post);
    } else if (act === 'topic') {
      ui.topic = t.dataset.tag; ui.communityChip = '全部';
      closeDrawer();
      if (ui.view !== 'community') go('community'); else render();
    } else if (act === 'community-chip') {
      ui.communityChip = ui.communityChip === t.dataset.value ? '全部' : t.dataset.value;
      ui.topic = '';
      render();
    } else if (act === 'challenge') {
      publishPostModal('隐藏吃法');
    } else if (act === 'publish-post') {
      var presetTag = '隐藏吃法';
      if (ui.topic) {
        SEED.topics.forEach(function (tp) { if (tp.name === ui.topic) presetTag = tp.tag; });
        if (presetTag === '隐藏吃法' && ui.topic.indexOf('隐藏') < 0) {
          presetTag = ui.topic.replace('#', '').replace(/\s/g, '').slice(0, 4);
        }
      }
      publishPostModal(presetTag);
    } else if (act === 'clear-search') {
      ui.search = ''; $('#navSearch').value = ''; render();
    } else if (act === 'reset-demo') {
      if (confirm('确定重置演示数据？所有收藏、约饭、打卡与评论都会清空。')) {
        try { localStorage.removeItem(LS_KEY); } catch (err) {}
        state = freshState();
        saveState();
        syncState('/me/demo-data', 'DELETE');
        window.__ganfan.state = state;
        closeDrawer();
        ui.search = ''; ui.chip = '全部'; ui.sort = 'latest'; ui.budget = 'all';
        ui.topic = ''; ui.communityChip = '全部'; ui.rankCat = '口味榜';
        render();
        toast('演示数据已重置');
      }
    } else if (act === 'switch-campus') {
      schoolModal();
    }
    if (act) e.stopPropagation();
  });

  function openGreet(m) {
    var body = '<form id="greetForm" data-target="' + m.id + '"><div class="field full"><label>给 ' + esc(m.owner) + ' 发消息</label>' +
      '<textarea name="msg" required maxlength="80" placeholder="如：我也想吃这个，18:30 在养贤府 2F 见？"></textarea>' +
      '<div class="hint">仅与你同一校区的同学可见；消息会记入本地「我的消息」。</div></div>' +
      '<div class="modal-actions"><button type="button" class="btn" data-close>取消</button><button type="submit" class="btn btn-primary">发送</button></div></form>';
    openModal({ title: '打招呼', sub: '与 ' + esc(m.owner) + ' · ' + esc(m.dish) + ' · ' + esc(m.when), body: body });
  }

  // 键盘可达：快捷入口/胶囊等非按钮元素支持 Enter / Space
  document.addEventListener('keydown', function (e) {
    if (e.key !== 'Enter' && e.key !== ' ') return;
    var el = e.target.closest ? e.target.closest('[data-act]') : null;
    if (!el) return;
    if (/^(BUTTON|A|INPUT|SELECT|TEXTAREA)$/.test(el.tagName)) return;
    e.preventDefault();
    el.click();
  });

  // 多选标签
  document.addEventListener('click', function (e) {
    var chip = e.target.closest('.chips-row .chip');
    if (chip) { chip.classList.toggle('on'); }
  });

  // 排序/预算
  document.addEventListener('change', function (e) {
    var t = e.target;
    if (!t.dataset || !t.dataset.act) return;
    if (t.dataset.act === 'sort') { ui.sort = t.value; render(); }
    if (t.dataset.act === 'budget') { ui.budget = t.value; render(); }
  });

  // 搜索
  function initSearch() {
    var input = $('#navSearch'), panel = $('#searchPanel');
    function draw() {
      var q = input.value.trim();
      if (!q) { panel.classList.remove('open'); panel.innerHTML = ''; return; }
      var results = [];
      stalls().forEach(function (s) {
        var hay = (s.name + ' ' + s.canteen + ' ' + s.tags.join(' ') + ' ' + s.dishes.join(' ')).toLowerCase();
        if (hay.indexOf(q.toLowerCase()) >= 0) results.push({ kind: '档口', emoji: s.emoji, t: s.name, s: s.canteen + ' ' + s.floor + ' · 人均 ' + s.price, act: 'open-stall', id: s.id });
      });
      meetups().filter(function (m) { return !m.campus || m.campus === campusName(); }).forEach(function (m) {
        var hay = (m.owner + ' ' + m.dish + ' ' + m.canteen + ' ' + m.tags.join(' ')).toLowerCase();
        if (hay.indexOf(q.toLowerCase()) >= 0) results.push({ kind: '约饭', emoji: m.avatar, t: m.owner + ' · ' + m.dish, s: m.when + ' · ' + m.canteen, act: 'go-meetup', id: m.id });
      });
      posts().filter(function (po) { return !po.campus || po.campus === campusName(); }).forEach(function (po) {
        var hay = (po.text + ' ' + po.author + ' ' + po.tags.join(' ')).toLowerCase();
        if (hay.indexOf(q.toLowerCase()) >= 0) results.push({ kind: '社区', emoji: '💬', t: po.author, s: po.text.slice(0, 22) + '…', act: 'go-community', id: po.id });
      });
      panel.innerHTML = '<div class="sp-title">找到 ' + results.length + ' 个结果 · 回车看首页搜索</div>' +
        (results.length ? results.slice(0, 6).map(function (r, i) {
          return '<div class="sp-item" data-act="' + r.act + '" data-id="' + r.id + '" data-idx="' + i + '"><span class="emoji">' + r.emoji + '</span><span><div class="t">' + esc(r.t) + '</div><div class="s">' + r.kind + ' · ' + esc(r.s) + '</div></span></div>';
        }).join('') : '<div class="sp-empty">没有匹配结果，试试「香锅」「沙拉」或「西门」。</div>');
      panel.classList.add('open');
    }
    input.addEventListener('input', draw);
    input.addEventListener('focus', draw);
    input.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') {
        e.preventDefault();
        ui.search = input.value.trim();
        panel.classList.remove('open');
        if (ui.view !== 'home') go('home'); else render();
      }
      if (e.key === 'Escape') { panel.classList.remove('open'); input.blur(); }
    });
    document.addEventListener('click', function (e) {
      if (!e.target.closest('#searchBox')) panel.classList.remove('open');
    });
    document.addEventListener('keydown', function (e) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); input.focus(); }
      if (e.key === 'Escape') { closeModal(); closeDrawer(); }
    });
    // 搜索结果点击：直接打开对应视图并过滤
    document.addEventListener('click', function (e) {
      var item = e.target.closest('.sp-item');
      if (!item) return;
      var act = item.dataset.act;
      panel.classList.remove('open');
      if (act === 'open-stall') {
        var s = SEED.stalls.filter(function (x) { return x.id === item.dataset.id; })[0];
        if (s) stallModal(s);
      } else if (act === 'go-meetup') { go('meetup'); }
      else if (act === 'go-community') { go('community'); }
    });
  }

  /* ---------------- 自检（?selftest=1） ---------------- */
  function runSelfTest() {
    selfTestMode = true;
    var snapshot = null;
    try { snapshot = localStorage.getItem(LS_KEY); } catch (e) {}
    state = freshState();
    saveState();
    render();
    var out = [];
    function ok(name, cond, detail) { out.push({ name: name, pass: !!cond, detail: detail || '' }); }

    ok('页面渲染：三个视图容器存在', !!$('#view') && !!$('#menu'), '');
    ok('推荐：川味小炒得分在 60–99', stallScore(SEED.stalls[0]) >= 60 && stallScore(SEED.stalls[0]) <= 99, stallScore(SEED.stalls[0]));
    ok('推荐：依据固定三条', reasonsOf(SEED.stalls[0]).length === 3, reasonsOf(SEED.stalls[0]).length);

    var f0 = state.favorites.length;
    toggleFavorite('s1');
    ok('收藏：切换后状态与计数变化', state.favorites.length === f0 + 1 && isFav('s1'), state.favorites.length);
    toggleFavorite('s1');

    var m = SEED.meetups[0];
    var seat0 = m.seat;
    toggleJoin(m.id);
    ok('约饭：加入后座位 +1', seatsOf(m) === seat0 + 1, seatsOf(m));
    toggleJoin(m.id);
    ok('约饭：退出后座位恢复', seatsOf(m) === seat0, seatsOf(m));

    var before = meetups().length;
    var created = createMeetup({ dish: '测试香锅', canteen: '学一食堂', when: '今晚', time: '19:00', want: 2, budget: 20, tags: ['拼桌'] });
    ok('发起饭局：写入并置顶', meetups().length === before + 1 && meetups()[0].id === created.id, created.id);
    state.createdMeetups = state.createdMeetups.filter(function (x) { return x.id !== created.id; });

    var pb = posts().length;
    var beforeCheckins = checkinCount();
    var newPost = createPost({ text: '自检打卡：窗口 A 的隐藏吃法', canteen: '学一食堂', place: '学一食堂 1F', price: 12, tags: ['隐藏吃法'] });
    ok('发布打卡：新增帖子并带话题', posts().length === pb + 1 && newPost.tags[0] === '#隐藏吃法', newPost.id);
    ok('发布打卡：画像打卡计数同步 +1', checkinCount() === beforeCheckins + 1, checkinCount());

    var oldDis = p().dislikes.slice();
    p().dislikes = ['折耳根'];
    var s8 = SEED.stalls[7];
    ok('画像：忌口变化会影响推荐依据', reasonsOf(s8)[0].t.indexOf('折耳根') >= 0, reasonsOf(s8)[0].t);
    p().dislikes = oldDis;

    saveState();
    var loaded = loadState();
    ok('持久化：localStorage 可回读', !!loaded && Array.isArray(loaded.favorites), '');

    var tonight = meetups().filter(function (x) { return x.today; });
    ok('筛选：今晚饭局均标记 today', tonight.length > 0 && tonight.every(function (x) { return x.today; }), tonight.length);
    ok('搜索：川味小炒可被检索', SEED.stalls.some(function (s) { return (s.name + s.dishes.join(' ')).indexOf('川味') >= 0; }), '');

    // ---- 真实交互（通过 DOM 点击/表单提交验证） ----
    function navTo(v) { location.hash = '#/' + v; render(); }

    navTo('home');
    var favBtn = document.querySelector('#view [data-act="fav"]');
    if (favBtn) favBtn.click();
    ok('交互：点击「收藏」后状态与按钮文案更新', state.favorites.length === 1 && document.querySelector('#view [data-act="fav"]').textContent.indexOf('已收藏') >= 0, state.favorites.length);

    navTo('meetup');
    var joinBtn = document.querySelector('#view [data-act="join"]');
    var firstMeetupId = joinBtn ? joinBtn.dataset.id : null;
    if (joinBtn) joinBtn.click();
    ok('交互：约饭卡「加入饭局」按钮生效', !!firstMeetupId && isJoined(firstMeetupId), firstMeetupId);
    var greetBtn = document.querySelector('#view [data-act="greet"]');
    ok('交互：约饭卡同时提供「打招呼」入口', !!greetBtn, '');
    if (greetBtn) {
      greetBtn.click();
      var gf = document.getElementById('greetForm');
      if (gf) { gf.querySelector('textarea').value = '我也想拼桌'; gf.requestSubmit(); }
      ok('交互：打招呼表单可提交并记入消息', !!state.greetings[firstMeetupId] && state.messages.length > 0, state.messages.length);
    }

    document.getElementById('navPublish').click();
    var pmf = document.getElementById('publishMeetupForm');
    if (pmf) {
      pmf.querySelector('[name="dish"]').value = '交互测试香锅';
      pmf.requestSubmit();
    }
    ok('交互：导航「发布约饭」表单可提交', state.createdMeetups.length === 1, state.createdMeetups.length);

    navTo('community');
    var likeBtn = document.querySelector('#view [data-act="like"]');
    var likeId = likeBtn ? likeBtn.dataset.id : null;
    if (likeBtn) likeBtn.click();
    ok('交互：社区「点赞」计数增加', !!likeId && liked(likeId), likeId);
    document.querySelector('#view [data-act="comment"]').click();
    var cf = document.getElementById('commentForm');
    if (cf) { cf.querySelector('textarea').value = '自检评论：好吃'; cf.requestSubmit(); }
    ok('交互：评论表单提交后计入对应帖子', !!likeId && commentsOf(likeId).length === 1 && !state.comments[undefined], commentsOf(likeId).length);
    closeModal();

    var q = document.getElementById('navSearch');
    q.value = '香锅'; q.dispatchEvent(new Event('input'));
    ok('交互：搜索框输入出现结果面板', document.getElementById('searchPanel').classList.contains('open'), '');
    q.value = '';

    openDrawer('joined');
    ok('交互：「我的约饭」抽屉可打开', document.getElementById('drawer').classList.contains('open'), '');
    closeDrawer();

    navTo('community');
    var topicRow = document.querySelector('#view [data-act="topic"][data-tag="# 早八早餐吃什么"]');
    if (topicRow) topicRow.click();
    ok('交互：热门话题行可筛选出内容', document.querySelectorAll('#view .post').length >= 1, document.querySelectorAll('#view .post').length);

    ui.search = '香锅';
    location.hash = '#/home';
    render();
    var secTitle = document.querySelector('#view .sec-title');
    ok('交互：搜索结果在首页正常渲染', !!secTitle && secTitle.textContent.indexOf('搜索') >= 0, secTitle ? secTitle.textContent : '');
    ui.search = '';
    var searchInput = document.getElementById('navSearch');
    if (searchInput) searchInput.value = '';

    ok('运行时：整轮交互无 JS 错误', pageErrors.length === 0, pageErrors.join('; '));

    /* ---- 校区 / 定位 / 入驻 ---- */
    ok('校区：默认浙江工业大学屏峰校区', curSchool().university === '浙江工业大学' && campusName() === '屏峰校区', curSchool().campus);
    ok('校区：屏峰校区地址与坐标已配置', SEED.school.address === '留和路 288 号' && Math.abs(SEED.school.lat - 30.231) < 0.01, SEED.school.address);
    var cs = canteensOfCampus().map(function (c) { return c.name; });
    ok('食堂：屏峰校区含家和食堂与养贤府', cs.indexOf('家和食堂') >= 0 && cs.indexOf('养贤府') >= 0, cs.join('、'));
    state.geo = { lat: 30.2297, lng: 120.0341, accuracy: 20, ts: Date.now() };
    var near = nearestCanteen();
    ok('定位：最近食堂距离在合理范围', !!near && near.distance >= 0 && near.distance <= 1200, near ? near.canteen.name + ' ' + near.distance + 'm' : 'none');
    state.geo = null;
    state.schoolId = 'zjut-chaohui';
    ok('校区隔离：切换朝晖后无屏峰内容', filteredMeetups().length === 0 && visiblePosts().length === 0, filteredMeetups().length + '/' + visiblePosts().length);
    ok('校区隔离：首页与附近不混入屏峰档口', stalls().length === 0 && nearbyItems().length === 0, stalls().length + '/' + nearbyItems().length);
    ok('校区隔离：无数据校区不显示屏峰榜单', rankCard().indexOf('还没开放') >= 0 || rankCard().indexOf('待补充') >= 0, rankCard().indexOf('还没开放') >= 0 ? '榜单空态' : '待补充');
    state.schoolId = 'zjut-pingfeng';
    var appCount = state.applications.length;
    submitApplication({ university: '浙江工业大学', campus: '屏峰校区', city: '杭州', contact: '自检', phone: '13800000000', canteens: '2', note: '自检' });
    ok('入驻：申请写入本地并进入待审核', state.applications.length === appCount + 1 && state.applications[0].status === '待审核', state.applications[0].campus);

    /* ---- 附近吃饭 / 随机选店 ---- */
    var nb = nearbyItems();
    var nbStalls = nb.filter(function (x) { return x.type === 'stall'; }).length;
    var nbTake = nb.filter(function (x) { return x.type === 'takeout'; }).length;
    ok('附近：校内档口与外卖合并到一起', nbStalls > 0 && nbTake > 0, nbStalls + ' 校内 / ' + nbTake + ' 外卖');
    ok('附近：列表按匹配度排序', nb.every(function (x, i) { return i === 0 || nb[i - 1].score >= x.score; }), nb[0] ? nb[0].score + '%' : 'none');
    ok('附近：同分时按距离/价格修正分排序', nb.every(function (x, i) {
      return i === 0 || nb[i - 1].score > x.score || (nb[i - 1].score === x.score && nb[i - 1].rank >= x.rank);
    }), nb[0] ? nb[0].score + '% / rank ' + nb[0].rank : 'none');
    var pk = pickNearby(nb, function () { return 0; });
    ok('随机：可命中候选并打开详情', !!pk && !!pk.id && (pk.type === 'stall' || pk.type === 'takeout'), pk ? pk.name : 'none');
    ok('建议：理由包含预算、距离或配送信息', /预算|价|分钟|步行|距你/.test(nearbyWhy(nb[0])), nearbyWhy(nb[0]).slice(0, 24));

    location.hash = '#/nearby'; render();
    ok('页面：附近页含决策区与合并清单', !!document.querySelector('.nb-stage') && document.querySelectorAll('.nb-row').length >= 10, document.querySelectorAll('.nb-row').length + ' 行');
    var sugIds = Array.prototype.map.call(document.querySelectorAll('.nb-sug'), function (el) { return el.dataset.id; });
    var rowIds = Array.prototype.map.call(document.querySelectorAll('.nb-row'), function (el) { return el.dataset.id; });
    ok('页面：建议卡与其余清单不重复', sugIds.length === 3 && sugIds.every(function (id) { return rowIds.indexOf(id) < 0; }), sugIds.length + ' 建议 / ' + rowIds.length + ' 清单');
    ui.nearbyPick = null; drawNearby(true);
    ok('页面：随机决策命中候选并展示结果卡', !!ui.nearbyPick && !!document.querySelector('.nb-draw-card'), ui.nearbyPick || 'none');

    // 恢复自检前的用户数据：自检不得破坏演示数据
    try {
      if (snapshot) localStorage.setItem(LS_KEY, snapshot);
      else localStorage.removeItem(LS_KEY);
    } catch (e) {}
    state = loadState() || freshState();
    saveState();
    navTo('home');

    var pass = out.filter(function (x) { return x.pass; }).length;
    var pre = document.createElement('pre');
    pre.id = 'selftest';
    pre.style.cssText = 'position:fixed;left:12px;top:80px;z-index:9999;background:#fff;border:1px solid #ccc;padding:12px;font-size:12px;max-height:70vh;overflow:auto;box-shadow:0 8px 24px rgba(0,0,0,.12)';
    pre.textContent = 'SELFTEST ' + pass + '/' + out.length + '\n' + out.map(function (x) {
      return (x.pass ? 'PASS' : 'FAIL') + '  ' + x.name + (x.detail !== '' ? '  [' + x.detail + ']' : '');
    }).join('\n');
    document.body.appendChild(pre);
    document.title = 'SELFTEST ' + pass + '/' + out.length;
    return { pass: pass, total: out.length, out: out };
  }

  /* ---------------- 启动 ---------------- */
  function updateAuthButton() {
    var button = $('#navAuth');
    if (!button) return;
    var user = window.GanfanAPI ? GanfanAPI.currentUser() : null;
    if (window.GanfanAPI && GanfanAPI.hasToken()) {
      button.textContent = '退出';
      button.title = user && user.email ? '当前账号：' + user.email : '退出登录';
    } else {
      button.textContent = '登录';
      button.title = '登录或注册';
    }
  }

  function handleDemoQuery(params) {
    var demo = params.get('demo');
    if (demo === 'publish-meetup') publishMeetupModal({});
    if (demo === 'publish-post') publishPostModal('隐藏吃法');
    if (demo === 'profile') editProfileModal();
    if (demo === 'comments') { var po = posts()[0]; if (po) commentsModal(po); }
    if (demo === 'drawer-favorites') openDrawer('favorites');
    if (demo === 'drawer-joined') openDrawer('joined');
    if (demo === 'drawer-canteens') openDrawer('canteens');
    if (demo === 'school') schoolModal();
    if (demo === 'apply-school') applicationModal({});
    if (demo === 'nearby' || demo === 'nearby-random') {
      if (!state.geo) state.geo = { lat: 30.2300, lng: 120.0355, accuracy: 20, ts: Date.now() };
      if (demo === 'nearby-random') {
        var dp = pickNearby(nearbyItems());
        ui.nearbyPick = dp ? dp.id : null;
      }
      location.hash = '#/nearby';
      render();
    }
  }

  function startBackend(params) {
    if (!window.GanfanAPI) {
      toast('API 客户端未加载', false);
      return Promise.reject(new Error('api missing'));
    }
    if (!GanfanAPI.hasToken()) {
      state = freshState();
      saveState();
      render();
      updateAuthButton();
      authModal(false);
      return Promise.resolve(null);
    }
    return refreshBackend().then(function (payload) {
      updateAuthButton();
      closeModal();
      toast('后端数据已同步');
      if (!selfTestMode) loadNearbyFromBackend({ source: 'school', reset: true });
      if (params) handleDemoQuery(params);
      return payload;
    }).catch(function (error) {
      updateAuthButton();
      if (error && error.status === 401) {
        authModal(false);
      } else {
        openModal({
          title: '后端连接失败',
          sub: 'API：' + GanfanAPI.base,
          body: '<div class="empty-state">' + esc(error && error.message ? error.message : '未知错误') +
            '<br>请确认 Spring Boot 与 MySQL 后端已启动。<br>' +
            '<button class="btn btn-primary" style="margin-top:12px" data-act="retry-backend">重新连接</button></div>'
        });
      }
      return null;
    });
  }

  function boot() {
    render();
    initSearch();
    updateAuthButton();
    $('#navPublish').addEventListener('click', function () { publishMeetupModal({}); });
    $('#navAvatar').addEventListener('click', function () { editProfileModal(); });
    $('#navAuth').addEventListener('click', function () {
      if (window.GanfanAPI && GanfanAPI.hasToken()) {
        if (confirm('确定退出当前账号？')) {
          GanfanAPI.clearSession();
          location.reload();
        }
      } else {
        authModal(false);
      }
    });
    $$('#menu a').forEach(function (a) {
      a.addEventListener('click', function () {
        ui.search = ''; ui.topic = ''; ui.communityChip = '全部';
        var box = $('#navSearch'); if (box) box.value = '';
        var target = (a.getAttribute('href') || '').replace('#/', '');
        if (ui.view === target) render();
      });
    });
    var brand = $('.brand');
    if (brand) brand.addEventListener('click', function () {
      ui.search = ''; ui.topic = ''; ui.communityChip = '全部';
      var box = $('#navSearch'); if (box) box.value = '';
      if (ui.view === 'home') render();
    });
    window.addEventListener('hashchange', function () { closeDrawer(); render(); });
    window.addEventListener('ganfan:unauthorized', function () {
      updateAuthButton();
      authModal(false);
    });

    var params = new URLSearchParams(location.search);
    if (params.get('selftest') === '1') {
      selfTestMode = true;
      setTimeout(function () { runSelfTest(); }, 50);
      return;
    }
    startBackend(params);
  }

  window.__ganfan = {
    state: state, SEED: SEED, render: render, go: go,
    score: { stall: stallScore, meetup: meetupScore },
    actions: { toggleFavorite: toggleFavorite, toggleJoin: toggleJoin, createMeetup: createMeetup, createPost: createPost, submitApplication: submitApplication },
    school: { current: curSchool, list: function () { return SEED.schools; }, distance: geoDistanceTo, nearest: nearestCanteen, locate: locateMe },
    reset: function () { localStorage.removeItem(LS_KEY); location.reload(); },
    test: runSelfTest
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
