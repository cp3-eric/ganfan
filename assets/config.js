/* 干饭搭子 · 线上环境接口地址
 * 规则：仅当页面本身部署在 Render（*.onrender.com）上时，
 * 才把接口指向线上后端；本地开发（127.0.0.1:5173 / file://）
 * 保持 api.js 默认的 http://127.0.0.1:8080/api，互不影响。
 * 后端地址变化时，只需修改本文件中的 URL。
 */
(function () {
  'use strict';
  if (!/\.onrender\.com$/.test(location.hostname)) { return; }
  window.GANFAN_API_BASE = 'https://ganfan-qnko.onrender.com/api';
})();
