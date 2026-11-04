/* 干饭搭子 · 后端 API 客户端
 * 开发环境：Node 静态服务(5173) -> Spring Boot(8080)
 * 生产环境：Nginx 同源反向代理 /api
 */
(function () {
  'use strict';

  var TOKEN_KEY = 'ganfan_access_token_v1';
  var USER_KEY = 'ganfan_current_user_v1';
  var query = new URLSearchParams(location.search);
  var apiBase = window.GANFAN_API_BASE || query.get('api') ||
    ((location.protocol === 'file:' || location.port === '5173') ? 'http://127.0.0.1:8080/api' : '/api');
  apiBase = apiBase.replace(/\/$/, '');
  var token = null;
  try { token = localStorage.getItem(TOKEN_KEY); } catch (e) {}

  function ApiError(message, status, code, errors) {
    this.name = 'ApiError';
    this.message = message || '请求失败';
    this.status = status || 0;
    this.code = code == null ? -1 : code;
    this.errors = errors || null;
  }
  ApiError.prototype = Object.create(Error.prototype);

  function saveSession(data) {
    token = data.token;
    try {
      localStorage.setItem(TOKEN_KEY, token);
      localStorage.setItem(USER_KEY, JSON.stringify(data.user || {}));
    } catch (e) {}
    return data;
  }

  function clearSession() {
    token = null;
    try { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); } catch (e) {}
  }

  async function request(path, options) {
    options = options || {};
    var headers = Object.assign({ 'Accept': 'application/json' }, options.headers || {});
    if (options.body != null) headers['Content-Type'] = 'application/json';
    if (token) headers.Authorization = 'Bearer ' + token;
    var controller = typeof AbortController !== 'undefined' ? new AbortController() : null;
    var timer = controller ? setTimeout(function () { controller.abort(); }, options.timeout || 15000) : null;
    try {
      var response = await fetch(apiBase + path, {
        method: options.method || 'GET',
        headers: headers,
        body: options.body == null ? undefined : JSON.stringify(options.body),
        signal: controller ? controller.signal : undefined,
        cache: 'no-store'
      });
      var payload = null;
      try { payload = await response.json(); } catch (e) {
        throw new ApiError('服务器返回了无法解析的响应（HTTP ' + response.status + '）', response.status);
      }
      if (!response.ok || !payload || payload.code !== 0) {
        var error = new ApiError(payload && payload.message ? payload.message : ('HTTP ' + response.status), response.status, payload && payload.code, payload && payload.errors);
        if (response.status === 401 && path.indexOf('/auth/') !== 0) {
          clearSession();
          window.dispatchEvent(new CustomEvent('ganfan:unauthorized', { detail: error }));
        }
        throw error;
      }
      return payload.data;
    } catch (e) {
      if (e && e.name === 'AbortError') throw new ApiError('请求超时，请检查后端服务与网络连接', 0);
      if (e instanceof ApiError) throw e;
      throw new ApiError('无法连接后端，请确认 Spring Boot 已在 ' + apiBase.replace(/\/api$/, '') + ' 启动', 0);
    } finally {
      if (timer) clearTimeout(timer);
    }
  }

  window.GanfanAPI = {
    base: apiBase,
    ApiError: ApiError,
    request: request,
    hasToken: function () { return !!token; },
    clearSession: clearSession,
    currentUser: function () {
      try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null'); } catch (e) { return null; }
    },
    health: function () { return request('/health'); },
    login: async function (body) { return saveSession(await request('/auth/login', { method: 'POST', body: body })); },
    register: async function (body) { return saveSession(await request('/auth/register', { method: 'POST', body: body })); },
    me: function () { return request('/auth/me'); },
    bootstrap: function () { return request('/bootstrap'); },
    updateLocation: function (body) { return request('/me/location', { method: 'PUT', body: body }); },
    discoverNearby: function (body) { return request('/nearby/discover', { method: 'POST', body: body }); },
    nearbyVenues: function (params) {
      var q = new URLSearchParams(params || {});
      return request('/nearby/venues?' + q.toString());
    },
    nearbyRecommendations: function (params) {
      var q = new URLSearchParams(params || {});
      return request('/nearby/recommendations?' + q.toString());
    },
    action: function (path, method, body) { return request(path, { method: method || 'GET', body: body }); }
  };
})();
