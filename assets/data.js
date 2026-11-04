/* 干饭搭子 · 演示数据（浙江工业大学屏峰校区）
 * 校园信息参考：学校地址/坐标来自 OpenStreetMap（留和路 288 号，310023）；
 * 食堂信息参考公开的新生攻略与在校生问答（家和食堂 1F 经济窗口 / 2F 自助 / 3F 美食街，
 * 养贤府为美食广场，含麻辣香锅、煎包、排骨饭等）。价格为演示数据，非官方定价。
 */
window.SEED = {
  school: {
    id: 'zjut-pingfeng',
    university: '浙江工业大学',
    campus: '屏峰校区',
    city: '杭州市',
    district: '西湖区',
    address: '留和路 288 号',
    postcode: '310023',
    lat: 30.2310563,
    lng: 120.0364832,
    note: '小和山高教园区 · 屏峰校区'
  },

  schools: [
    { id: 'zjut-pingfeng', university: '浙江工业大学', campus: '屏峰校区', city: '杭州', status: 'open', lat: 30.2310563, lng: 120.0364832, canteens: 2, students: '约 2.2 万人', note: '小和山高教园区 · 留和路 288 号' },
    { id: 'zjut-chaohui', university: '浙江工业大学', campus: '朝晖校区', city: '杭州', status: 'open', lat: 30.295896, lng: 120.1617104, canteens: 2, students: '约 1.1 万人', note: '拱墅区潮王路 18 号 · 毓秀堂等' },
    { id: 'zjut-moganshan', university: '浙江工业大学', campus: '莫干山校区', city: '湖州', status: 'prep', lat: 30.894, lng: 119.977, canteens: 1, students: '约 0.6 万人', note: '德清 · 新校区，数据整理中' }
  ],

  canteens: [
    { id: 'jh', name: '家和食堂', campus: '屏峰校区', floors: 3, note: '1F 经济窗口 / 2F 自助 / 3F 美食街', lat: 30.2309, lng: 120.0353, walk: 180 },
    { id: 'yx', name: '养贤府', campus: '屏峰校区', floors: 2, note: '美食广场：麻辣香锅、煎包、排骨饭', lat: 30.2318, lng: 120.0372, walk: 320 },
    { id: 'xm', name: '西门夜市', campus: '屏峰校区', floors: 1, note: '留和路西门，21:00 后最热闹', lat: 30.2298, lng: 120.0339, walk: 560 },
    { id: 'tsg', name: '图书馆 B1 咖啡', campus: '屏峰校区', floors: 1, note: '咖啡与简餐，自习续命', lat: 30.2323, lng: 120.0358, walk: 260 }
  ],

  takeouts: [
    { id: 't1', name: '留和路麻辣烫 · 外卖', emoji: '🍲', img: 'assets/img/malaxiangguo.jpg', campus: '屏峰校区', price: 22, delivery: 25, rating: 4.7, lat: 30.2286, lng: 120.0312, spicy: '中辣', tags: ['麻辣烫', '满 20 减 4'], dishes: ['麻辣烫', '冒菜'], night: true },
    { id: 't2', name: '小和山黄焖鸡 · 外卖', emoji: '🍗', img: 'assets/img/huangmenji.jpg', campus: '屏峰校区', price: 18, delivery: 22, rating: 4.6, lat: 30.2341, lng: 120.039, spicy: '微辣', tags: ['黄焖鸡', '米饭管够'], dishes: ['黄焖鸡'], night: false },
    { id: 't3', name: '工大快餐便当 · 外卖', emoji: '🍱', img: 'assets/img/shiguobanfan.jpg', campus: '屏峰校区', price: 15, delivery: 20, rating: 4.5, lat: 30.2299, lng: 120.0335, spicy: '不辣', tags: ['便当', '两荤一素'], dishes: ['便当', '炒饭'], night: false },
    { id: 't4', name: '西门螺蛳粉 · 外卖', emoji: '🍜', img: 'assets/img/luosifen.jpg', campus: '屏峰校区', price: 16, delivery: 26, rating: 4.5, lat: 30.2302, lng: 120.034, spicy: '特辣', tags: ['螺蛳粉', '酸笋'], dishes: ['螺蛳粉'], night: true },
    { id: 't5', name: '屏峰奶茶 · 外卖', emoji: '🥤', img: 'assets/img/latte.jpg', campus: '屏峰校区', price: 8, delivery: 20, rating: 4.6, lat: 30.2312, lng: 120.0378, spicy: '不辣', tags: ['饮品', '第二杯半价'], dishes: ['奶茶', '拿铁'], night: false },
    { id: 't6', name: '早餐豆浆酱香饼 · 外卖', emoji: '🍙', img: 'assets/img/jiangxiangbing.jpg', campus: '屏峰校区', price: 9, delivery: 18, rating: 4.4, lat: 30.2308, lng: 120.0345, spicy: '不辣', tags: ['早餐', '豆浆'], dishes: ['酱香饼', '豆浆'], night: false }
  ],

  profile: {
    name: '干饭小分队',
    avatar: '🍚',
    college: '计算机学院',
    grade: '大三',
    campus: '屏峰校区',
    level: 7,
    spicy: '微辣',
    budget: [15, 25],
    dislikes: ['香菜', '折耳根'],
    haunts: ['家和食堂', '西门夜市'],
    sleep: '夜猫子',
    social: '愿意拼桌',
    updatedAt: '10-31 12:30',
    sources: '4 次问卷、12 条打卡'
  },

  stalls: [
    { id: 's1', img: 'assets/img/huiguorou.jpg', name: '川味小炒', emoji: '🍜', canteen: '家和食堂', floor: '3F', campus: '屏峰校区', price: 15, walk: 195, rating: 4.8, checkins: 128, remain: 12, spicy: '微辣', tags: ['川湘', '出餐 5 分钟', '可免香菜'], dishes: ['麻辣香锅', '回锅肉', '土豆丝'] },
    { id: 's2', img: 'assets/img/huangmenji.jpg', name: '黄焖鸡米饭', emoji: '🍗', canteen: '家和食堂', floor: '3F', campus: '屏峰校区', price: 15, walk: 190, rating: 4.7, checkins: 128, remain: 20, spicy: '微辣', tags: ['米饭管够', '续饭免费'], dishes: ['黄焖鸡', '黄焖排骨'] },
    { id: 's3', img: 'assets/img/shiguobanfan.jpg', name: '石锅拌饭', emoji: '🥘', canteen: '养贤府', floor: '2F', campus: '屏峰校区', price: 16, walk: 330, rating: 4.6, checkins: 96, remain: 15, spicy: '中辣', tags: ['夜宵营业', '锅巴'], dishes: ['石锅拌饭', '石锅豆腐'] },
    { id: 's4', img: 'assets/img/shala.jpg', name: '鸡胸肉沙拉', emoji: '🥗', canteen: '养贤府', floor: '1F', campus: '屏峰校区', price: 12, walk: 315, rating: 4.8, checkins: 74, remain: 18, spicy: '不辣', tags: ['减脂友好', '高蛋白 32g'], dishes: ['鸡胸肉沙拉', '无糖豆浆'] },
    { id: 's5', img: 'assets/img/malaxiangguo.jpg', name: '麻辣香锅', emoji: '🍲', canteen: '养贤府', floor: '2F', campus: '屏峰校区', price: 18, walk: 320, rating: 4.9, checkins: 203, remain: 9, spicy: '中辣', tags: ['自选配菜', '拼桌热门'], dishes: ['麻辣香锅', '冒菜'] },
    { id: 's6', img: 'assets/img/luosifen.jpg', name: '螺蛳粉', emoji: '🍜', canteen: '养贤府', floor: '1F', campus: '屏峰校区', price: 14, walk: 310, rating: 4.5, checkins: 88, remain: 11, spicy: '特辣', tags: ['重口', '酸笋'], dishes: ['螺蛳粉', '老友粉'] },
    { id: 's7', img: 'assets/img/kaolengmian.jpg', name: '烤冷面', emoji: '🥟', canteen: '西门夜市', floor: '1F', campus: '屏峰校区', price: 10, walk: 560, rating: 4.4, checkins: 61, remain: 30, spicy: '微辣', tags: ['夜宵', '现做'], dishes: ['烤冷面', '烤肠'] },
    { id: 's8', img: 'assets/img/latte.jpg', name: '桂花拿铁', emoji: '☕', canteen: '图书馆 B1 咖啡', floor: 'B1', campus: '屏峰校区', price: 9.9, walk: 260, rating: 4.6, checkins: 74, remain: 25, spicy: '不辣', tags: ['学生价', '咖啡'], dishes: ['桂花拿铁', '美式'] },
    { id: 's9', img: 'assets/img/jiangxiangbing.jpg', name: '豆浆酱香饼', emoji: '🍙', canteen: '家和食堂', floor: '1F', campus: '屏峰校区', price: 5.5, walk: 180, rating: 4.5, checkins: 143, remain: 40, spicy: '不辣', tags: ['早餐', '5 分钟出餐'], dishes: ['酱香饼', '豆浆'] },
    { id: 's10', img: 'assets/img/qingshi.jpg', name: '轻食拼盘', emoji: '🥙', canteen: '养贤府', floor: '1F', campus: '屏峰校区', price: 12, walk: 315, rating: 4.7, checkins: 96, remain: 16, spicy: '不辣', tags: ['低卡', '高纤维'], dishes: ['轻食拼盘', '希腊酸奶'] }
  ],

  meetups: [
    { id: 'm1', owner: '小鹿同学', avatar: '🦌', college: '外国语学院', grade: '大二', when: '今晚 18:30', today: true, canteen: '养贤府', floor: '2F', dish: '麻辣香锅', want: 2, seat: 2, size: 4, budget: 30, base: 92, campus: '屏峰校区', schoolId: 'zjut-pingfeng', distance: '步行 5 分钟', tags: ['同不吃香菜', '夜宵党', '女生优先'], mode: '拼桌', featured: true, postedAt: '2 小时前' },
    { id: 'm2', owner: '阿哲', avatar: '💻', college: '计算机学院', grade: '大三', when: '明天 12:10', today: false, canteen: '家和食堂', floor: '3F', dish: '石锅拌饭', want: 1, seat: 1, size: 2, budget: 16, base: 85, campus: '屏峰校区', schoolId: 'zjut-pingfeng', distance: '距你 1.2km', tags: ['减脂友好', '不拼酒'], mode: '自习搭子', featured: false, postedAt: '30 分钟前' },
    { id: 'm3', owner: '小林', avatar: '🐯', college: '机械学院', grade: '大一', when: '今晚 21:30', today: true, canteen: '西门夜市', floor: '1F', dish: '烤冷面', want: 3, seat: 1, size: 3, budget: 35, base: 78, campus: '屏峰校区', schoolId: 'zjut-pingfeng', distance: '距你 800m', tags: ['夜宵党', '游戏搭子'], mode: '夜宵局', featured: false, postedAt: '1 小时前' },
    { id: 'm4', owner: '周周', avatar: '🐼', college: '新闻学院', grade: '大二', when: '今晚 19:00', today: true, canteen: '养贤府', floor: '1F', dish: '麻辣香锅', want: 2, seat: 1, size: 3, budget: 25, base: 81, campus: '屏峰校区', schoolId: 'zjut-pingfeng', distance: '步行 6 分钟', tags: ['同校区', '女生优先'], mode: '拼单', featured: false, postedAt: '3 小时前' },
    { id: 'm5', owner: '晓晓', avatar: '🍓', college: '数学学院', grade: '大一', when: '明天 18:30', today: false, canteen: '家和食堂', floor: '2F', dish: '鸡胸肉沙拉', want: 1, seat: 1, size: 2, budget: 15, base: 88, campus: '屏峰校区', schoolId: 'zjut-pingfeng', distance: '步行 8 分钟', tags: ['减脂友好', '自习搭子'], mode: '拼桌', featured: false, postedAt: '5 小时前' },
    { id: 'm6', owner: '大鹏', avatar: '🦅', college: '土木学院', grade: '大四', when: '今晚 20:30', today: true, canteen: '西门夜市', floor: '1F', dish: '烤冷面', want: 2, seat: 2, size: 4, budget: 40, base: 74, campus: '屏峰校区', schoolId: 'zjut-pingfeng', distance: '距你 1.5km', tags: ['夜宵党', 'AA 制'], mode: '夜宵局', featured: false, postedAt: '4 小时前' }
  ],

  posts: [
    { id: 'p1', img: 'assets/img/malaxiangguo.jpg', author: '干饭王小王', avatar: '🐟', emoji: '🍲', time: '2 小时前', canteen: '养贤府', campus: '屏峰校区', schoolId: 'zjut-pingfeng', meta: '养贤府 2F · 人均 18', text: '养贤府隐藏吃法：麻辣香锅加一份土豆粉，看到跺脚，人均才 18！', tags: ['#隐藏吃法', '麻辣香锅', '人均 18'], likes: 128, comments: 23, favs: 45, verified: true, topic: '隐藏吃法', code: 'C-1031-031', clock: '12:08' },
    { id: 'p2', img: 'assets/img/shala.jpg', author: '轻食小分队', avatar: '🥗', emoji: '🥗', time: '昨天', canteen: '养贤府', campus: '屏峰校区', schoolId: 'zjut-pingfeng', meta: '养贤府 1F · 人均 12', text: '减脂期也能吃食堂：养贤府 1F 的鸡胸肉沙拉 12 元，蛋白足够，配无糖豆浆刚好。', tags: ['#减脂期', '轻食', '人均 12'], likes: 86, comments: 12, favs: 31, verified: true, topic: '减脂期', code: 'C-1031-032', clock: '11:52' },
    { id: 'p3', img: 'assets/img/latte.jpg', author: '咖啡续命党', avatar: '☕', emoji: '☕', time: '4 小时前', canteen: '图书馆 B1 咖啡', campus: '屏峰校区', schoolId: 'zjut-pingfeng', meta: '图书馆 B1 · 学生价', text: '图书馆 B1 新出的桂花拿铁 9.9 元，拿学生证再减 2 元，早八续命水。', tags: ['#咖啡', '学生价', '图书馆 B1'], likes: 76, comments: 12, favs: 31, verified: false, topic: '咖啡', code: 'C-1031-029', clock: '10:40' },
    { id: 'p4', img: 'assets/img/jiangxiangbing.jpg', author: '早八生存指南', avatar: '🍙', emoji: '🍙', time: '昨天', canteen: '家和食堂', campus: '屏峰校区', schoolId: 'zjut-pingfeng', meta: '家和食堂 1F · 5.5 元', text: '早八人的救命早餐：家和 1F 的豆浆加酱香饼 5.5 元，5 分钟出餐，打包带走刚好。', tags: ['#早八', '早餐', '5.5 元'], likes: 143, comments: 27, favs: 66, verified: true, topic: '早八', code: 'C-1031-027', clock: '09:18' },
    { id: 'p5', img: 'assets/img/luwei.jpg', author: '卤味研究所', avatar: '🥩', emoji: '🍖', time: '昨天', canteen: '家和食堂', campus: '屏峰校区', schoolId: 'zjut-pingfeng', meta: '家和食堂 3F · 人均 22', text: '家和 3F 美食街的卤味窗口：卤蛋 + 豆干 + 鸡腿 22 元，微波炉加热 40 秒更入味。', tags: ['#隐藏吃法', '卤味', '人均 22'], likes: 58, comments: 9, favs: 12, verified: true, topic: '隐藏吃法', code: 'C-1030-118', clock: '18:24' },
    { id: 'p6', img: 'assets/img/kaolengmian.jpg', author: '夜宵巡逻队', avatar: '🍢', emoji: '🍢', time: '1 小时前', canteen: '西门夜市', campus: '屏峰校区', schoolId: 'zjut-pingfeng', meta: '西门夜市 · 人均 12', text: '西门夜市新摊位：烤冷面加双蛋 12 元，21:30 后排队 10 分钟，建议两个人分。', tags: ['#夜宵', '烤冷面', '人均 12'], likes: 97, comments: 18, favs: 20, verified: false, topic: '夜宵', code: 'C-1031-035', clock: '21:06' }
  ],

  topics: [
    { name: '# 食堂隐藏吃法', tag: '隐藏吃法', people: 3286, today: 128, hot: 1 },
    { name: '# 早八早餐吃什么', tag: '早八', people: 1204, today: 56, hot: 2 },
    { name: '# 人均 20 以内的快乐', tag: '人均', people: 986, today: 43, hot: 3 },
    { name: '# 期末考续命餐', tag: '期末', people: 754, today: 31, hot: 4 },
    { name: '# 工大周边美食', tag: '周边', people: 512, today: 22, hot: 5 }
  ],

  kings: [
    { name: '干饭王小王', avatar: '🐟', posts: 128, days: 21, level: 7 },
    { name: '轻食小分队', avatar: '🥗', posts: 96, days: 14, level: 5 },
    { name: '咖啡续命党', avatar: '☕', posts: 88, days: 9, level: 5 },
    { name: '早八生存指南', avatar: '🍙', posts: 76, days: 7, level: 4 }
  ],

  ranks: {
    口味榜: [
      { name: '麻辣香锅', q: '养贤府 2F', trend: 3 },
      { name: '黄焖鸡米饭', q: '家和食堂 3F', trend: 2 },
      { name: '石锅拌饭', q: '养贤府 2F', trend: 0 },
      { name: '螺蛳粉', q: '养贤府 1F', trend: 1 },
      { name: '烤冷面', q: '西门夜市', trend: -1 }
    ],
    好评榜: [
      { name: '麻辣香锅', q: '养贤府 2F', trend: 1 },
      { name: '川味小炒', q: '家和食堂 3F', trend: 4 },
      { name: '鸡胸肉沙拉', q: '养贤府 1F', trend: 2 },
      { name: '桂花拿铁', q: '图书馆 B1', trend: 0 },
      { name: '酱香饼', q: '家和食堂 1F', trend: -2 }
    ],
    性价比: [
      { name: '豆浆酱香饼', q: '家和食堂 1F', trend: 2 },
      { name: '桂花拿铁', q: '图书馆 B1', trend: 1 },
      { name: '烤冷面', q: '西门夜市', trend: 1 },
      { name: '鸡胸肉沙拉', q: '养贤府 1F', trend: 0 },
      { name: '黄焖鸡米饭', q: '家和食堂 3F', trend: -1 }
    ],
    夜宵榜: [
      { name: '烤冷面', q: '西门夜市', trend: 3 },
      { name: '麻辣香锅', q: '养贤府 2F', trend: 0 },
      { name: '螺蛳粉', q: '养贤府 1F', trend: 1 },
      { name: '石锅拌饭', q: '养贤府 2F', trend: -1 },
      { name: '川味小炒', q: '家和食堂 3F', trend: 2 }
    ]
  },

  mapBlocks: [
    { name: '图书馆', left: 12, top: 10, w: 78, h: 46 },
    { name: '家和食堂', left: 120, top: 10, w: 104, h: 50, hot: true },
    { name: '操场', left: 152, top: 86, w: 98, h: 48 },
    { name: '养贤府', left: 10, top: 88, w: 96, h: 46, hot: true },
    { name: '西门夜市', left: 10, top: 158, w: 104, h: 30 }
  ],

  challenge: { title: '今日挑战 · 隐藏吃法', tag: '隐藏吃法', joined: 328, hoursLeft: 6, percent: 64 }
};
