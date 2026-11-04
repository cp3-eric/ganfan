# 地图 POI 接入说明（高德 / 腾讯 Web Service Key）

本文档说明如何申请高德或腾讯位置服务的 **Web 服务 API Key**，并通过后端适配器把地图 POI 转换成项目统一的 `Venue` 格式。

> 对应实现：
> - `backend/src/main/java/com/ganfan/backend/feature/nearby/AmapVenueProvider.java`
> - `backend/src/main/java/com/ganfan/backend/feature/nearby/TencentVenueProvider.java`
> - `backend/src/main/java/com/ganfan/backend/feature/nearby/GeoCoordinates.java`

---

## 一、总体架构

项目原有的 `VenueProvider` 接口保持不变，地图数据源以「并列 Provider」的方式接入，与本地档口数据并存：

```
                    ┌──────────────────────────┐
   /api/nearby/* ───▶ NearbyRecommendationService │  统一评分与排序
                    └────────────┬─────────────┘
                                 │ List<VenueProvider>
              ┌──────────────────┼──────────────────┐
              ▼                  ▼                  ▼
      LocalVenueProvider   AmapVenueProvider   TencentVenueProvider
        (本地档口/外卖)      (高德 Web Service)   (腾讯 Web Service)
                                 │                  │
                                 └──── GCJ-02 → WGS-84 ────┘
                                        (GeoCoordinates)
```

两个地图 Provider 都继承 `AbstractMapVenueProvider`，统一处理：HTTP 超时、失败降级（返回空列表，不影响主流程）、结果缓存、并发限流。

**降级策略**：地图服务不可用时，`findCandidates` 返回空列表，接口照常返回本地档口数据，不会因为外部服务故障而 500。

---

## 二、申请 Key

### 2.1 高德开放平台（推荐，POI 深度字段更丰富）

1. 打开 <https://console.amap.com/dev/key/app>，用高德账号登录。
2. 完成**开发者认证**（个人实名认证即可）。未认证账号只能拿到测试 Key，配额很低。
3. 进入「应用管理 → 我的应用 → 创建应用」，应用名可填 `干饭搭子`，应用类型按实际业务选择。
4. 在应用下点击「添加 Key」，**服务平台必须选择「Web服务」**。
   - 「Web服务」是后端直连的 Key；「Web端(JS API)」是给浏览器用的，Key 类型选错会直接返回 `INVALID_USER_SKEY`。
5. 保存生成的 Key。
6. 安全设置：
   - 「Web服务」类型 Key 建议填写 **IP 白名单**，把后端服务器出口 IP 加进去。本地开发可留空。
   - 若要开启**数字签名（sig）**校验，需在 Key 设置里生成「数字私钥」，本项目当前实现未启用 sig，需要时可在 `AmapVenueProvider` 中按官方算法补上。

**配额**：个人认证约 1 万次/天（以控制台「配额管理」显示为准），企业认证更高。建议在控制台开启用量告警。

### 2.2 腾讯位置服务

1. 打开 <https://lbs.qq.com/dev/console/key/console>，用微信/QQ 登录并完成实名认证。
2. 「应用管理 → 我的应用 → 创建应用」，应用类型选个人或企业。
3. 「添加 Key」，**产品类型必须勾选「WebServiceAPI」**。漏勾会导致调用直接失败。
4. 「配额管理 → 账户额度 → 一键分配」，把「地点搜索」等接口的配额分配给该 Key。
   - **这一步最容易被忽略**：不分配配额时调用会报额度不足，而不是「Key 无效」。
5. 可选：勾选「数字签名（sig）」校验，SK 在控制台重新生成。

**配额**：单账号最多 10 个 Key；超出日调用量或并发数会在控制台申请提额，也可办理商业授权。

### 2.3 二选一还是都开？

建议**先只开一家**。两家同时开启时，两个 Provider 的结果会按「类型+名称+坐标」去重合并，重复度低；但会消耗双份配额。代码已按 `enabled` 开关隔离，互不影响。

---

## 三、配置与启用

Key 一律通过**环境变量**注入，不写进 `application.yml`，也不提交到仓库。

### 3.1 高德

```bash
export AMAP_ENABLED=true
export AMAP_WEB_SERVICE_KEY=你的高德Web服务Key
```

### 3.2 腾讯

```bash
export TENCENT_MAP_ENABLED=true
export TENCENT_MAP_WEB_SERVICE_KEY=你的腾讯WebServiceAPIKey
```

### 3.3 全部可调参数

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `AMAP_ENABLED` | `false` | 是否启用高德 Provider |
| `AMAP_WEB_SERVICE_KEY` | 空 | 高德 Web 服务 Key |
| `AMAP_TYPES` | `050000` | POI 分类码，`050000`=餐饮服务 |
| `AMAP_KEYWORDS` | 空 | 关键字过滤，留空只按分类搜 |
| `AMAP_OFFSET` | `25` | 每页条数，上限 25（超过可能报错） |
| `TENCENT_MAP_ENABLED` | `false` | 是否启用腾讯 Provider |
| `TENCENT_MAP_WEB_SERVICE_KEY` | 空 | 腾讯 WebServiceAPI Key |
| `TENCENT_MAP_KEYWORD` | `餐厅` | 搜索关键词 |
| `TENCENT_MAP_CATEGORY` | `餐饮` | 分类筛选 |
| `TENCENT_MAP_PAGE_SIZE` | `20` | 每页条数，上限 20 |
| `NEARBY_MAP_CACHE_TTL_SECONDS` | `300` | 结果缓存时长（秒） |
| `NEARBY_MAP_MAX_CONCURRENT_REQUESTS` | `4` | 同时对外发起的最大请求数 |

### 3.4 本机一键启动：local-env.bat

项目根目录提供 `local-env.bat`，存放本机私密配置（含真实 Key）。
`启动.bat` 启动时会自动 `call` 它，因此**双击 `启动.bat` 即可使用地图 POI，无需手动 export**。

```
local-env.bat 内容示例：
  set "AMAP_ENABLED=true"
  set "AMAP_WEB_SERVICE_KEY=你的高德Web服务Key"
  set "TENCENT_MAP_ENABLED=false"
```

安全边界：

- 该文件**不在 `backend/src/main/resources` 下，不会被打进 JAR**（已用 `unzip -p` 核验 JAR 内无 Key）；
- 已被 `.gitignore` 忽略，不会误提交；
- 删除该文件即可完全关闭地图 POI，后端仍以纯本地数据源正常启动。

> ⚠️ **Windows 编码注意**：`.bat` 文件必须保存为 **ANSI/GBK 或纯 ASCII**。
> cmd 默认按 GBK 解析 bat，若文件是 UTF-8 且含中文注释，会把乱码当成命令执行导致启动失败。
> 本项目的 `local-env.bat` 与 `启动.bat` 均已改为纯 ASCII 注释。

### 3.5 验证是否接通

启动后端后调用附近接口，返回结果的 `source` 字段会区分数据来源：

```bash
curl -H "Authorization: Bearer <token>" \
  "http://127.0.0.1:8080/api/nearby/venues?lat=30.2310563&lng=120.0364832&radiusMeters=2000"
```

- `source` 为 `local-catalog` → 本地档口
- `source` 为 `amap-poi` → 高德数据
- `source` 为 `tencent-poi` → 腾讯数据

---

## 四、字段映射

### 4.1 高德 POI → Venue

| Venue 字段 | 高德来源 | 说明 |
|---|---|---|
| `id` | `pois[].id` | 拼 `amap-` 前缀避免与本地 id 冲突 |
| `name` | `pois[].name` | |
| `type` | 固定 `stall` | 地图 POI 统一视为「到店消费」，与本地档口同类 |
| `place` | `pois[].address` | 缺省回退 `business_area` |
| `price` | `biz_ext.cost` | 人均消费；无值时为 0 |
| `rating` | `biz_ext.rating` | 评分；无值时为 0 |
| `lat`/`lng` | `pois[].location` | `"经度,纬度"` 字符串，需拆分并转 WGS-84 |
| `tags` | `pois[].type` | `"大类;中类;小类"`，去重且去掉宽泛的「餐饮服务」 |
| `dishes` | `pois[].tag` | 特色菜列表，如「招牌红烧肉,米饭」；缺失时回退 `pois[].keytag` |
| `img` | `pois[].photos[0].url` | 需 `extensions=all` 才返回；http/https 均接受 |
| `emoji` | `typecode` + `tag` | 按分类码挑表情，再按品类关键词细分 |
| `attributes.tel` | `pois[].tel` | |
| `attributes.businessArea` | `pois[].business_area` | 商圈 |
| `attributes.canteen` | `business_area` 或 `address` | 供历史画像做食堂维度匹配 |

### 4.2 腾讯 POI → Venue

| Venue 字段 | 腾讯来源 | 说明 |
|---|---|---|
| `id` | `data[].id` | 拼 `tencent-` 前缀 |
| `name` | `data[].title` | 回退 `data[].name` |
| `type` | 固定 `stall` | |
| `place` | `data[].address` | 缺省回退 `category` |
| `price` | — | 地点搜索不返回人均，置 0 |
| `rating` | — | 不返回评分，置 0 |
| `lat`/`lng` | `data[].location.lat/lng` | 需转 WGS-84 |
| `tags` | `data[].category` | 分号分隔；附加 `ad_info.district` |
| `attributes.city`/`district` | `data[].ad_info` | 行政区划 |

---

## 五、三个必须知道的坑

### 坑 1：坐标系不一致（最容易出错）

- 浏览器 `navigator.geolocation` 返回 **WGS-84**；
- 本项目本地种子坐标（源自 OpenStreetMap）也是 **WGS-84**；
- 高德、腾讯 Web Service 返回的是 **GCJ-02（火星坐标）**。

两者在中国境内有约 **300–600 米**的系统性偏移（杭州地区实测约 527 米）。若直接混用，地图 POI 会整体偏离本地档口几十米到几百米，距离排序全乱。

**处理方式**：`GeoCoordinates` 负责转换，约定为 **Provider 边界统一**——
- 入口 `findCandidates(lat, lng, radius)` 收 WGS-84；
- 调地图接口前转成 GCJ-02；
- 拿到响应后转回 WGS-84 再写入 `Venue`。

业务层（`NearbyRecommendationService`）完全无感，不用改一行。

### 坑 2：腾讯 `nearby` 半径上限只有 1000 米

本项目默认搜索半径是 **2500 米**（`DEFAULT_RADIUS`），上限 20000 米。若用 `boundary=nearby(lat,lng,radius)`，超过 1000 米会直接失败。

**处理方式**：改用 `boundary=rectangle(西南角纬度,西南角经度,东北角纬度,东北角经度)` 矩形范围搜索，矩形由半径换算得到；返回结果再由上层按距离过滤。

### 坑 3：两家接口的参数顺序和字段名都不一样

| | 高德 | 腾讯 |
|---|---|---|
| 坐标顺序 | **经度,纬度** | **纬度,经度** |
| 响应数组 | `pois` | `data` |
| 名称字段 | `name` | `title` |
| 距离字段 | `distance` | `_distance` |
| 成功标识 | `status == "1"`（字符串） | `status == 0`（数字） |

高德的 `status` 是字符串 `"1"`，用 `equals("1")` 判断，不能用 `== 1`。

另外，高德想拿到评分、人均、照片、特色菜，必须加 `extensions=all`，否则这些字段全部缺失。

### 坑 4：高德真实响应里的「空数组」形态（实测补充）

以下字段缺失时返回的是**空数组 `[]`**，不是空字符串，也不是 `null`：

| 字段 | 缺失时的形态 | 真实样例 |
|---|---|---|
| `biz_ext.cost` | `[]` | `"14.00"` / `[]` / `33.50` |
| `biz_ext.rating` | `[]` | `"2.9"` / `[]` |
| `tel` | `[]` | `"0571-85290995"` |
| `tag` | `[]` | `"煲仔饭"` / `[]` |
| `photos` | `[]` | `[{title, url}]` |

两个必须注意的解析细节：

1. **`biz_ext.cost` 是字符串且带小数**。用 `asInt()` 会把 `"33.50"` **截断成 33**（而不是四舍五入成 34），
   导致人均 33.5 元的商户在「预算内 / 略高于预算」判断上出错。
   本项目用 `parseCost()` 统一按四舍五入解析，并兼容数组、数字、字符串三种形态。
2. **餐饮 POI 的 `typecode` 几乎统一是 `050100`（中餐厅）**，
   只按分类码选表情会让所有商户都显示成同一个图标。
   本项目叠加 `tag` / `keytag`（高德给出的品类关键词，如「小吃快餐」「江西菜」）做二次细分。

另外 `type` 字段形如 `"餐饮服务;中餐厅;中餐厅"`，**同一层级会重复**，需去重后再作为标签。

### 坑 5：图片 URL 混用 http 与 https

实测 `photos[].url` 两种协议都出现过（`store.is.autonavi.com` 用 https，`aos-comment.amap.com` 有 http）。
若只判断 `startsWith("https")` 会丢掉部分商户图片。

---

## 六、评分模型的适配

地图 POI 缺少本地档口那样的打卡数、剩余份数、辣度等字段，若沿用原评分公式会被严重压分，导致地图数据永远排不上来。本项目做了两处调整：

1. **质量分**：`rating` 与 `checkins` 都缺失时取中性基准分 60，而不是按最差的 25 分算。
2. **偏好分**：无 `price` 时不参与预算加减分（避免「人均 0 元」误判为价格友好）。

推荐理由文案也做了区分：无价格时显示「地图商户，人均以到店为准」。

---

## 七、安全与合规

- Key 只通过环境变量注入，**不写入仓库、不打印到日志**。
- 建议在服务商控制台配置 IP 白名单 + 数字签名（sig）校验。
- 地图 POI 仅用于展示商户名称、地址、坐标等公开商业信息；用户定位坐标属个人信息，**只保存在本机/自建数据库**，不外传给地图服务商之外的第三方，也不公开发布。
- 本项目涉及的坐标与地图渲染均使用境内合规服务商（高德/腾讯），符合地图合规要求。

---

## 八、测试

```bash
cd backend
mvn test
```

`MapVenueProviderTest` 用本地 HTTP 打桩服务器模拟两家真实响应体，覆盖：
字段映射、GCJ-02→WGS-84 转换、参数顺序（经纬顺序）、矩形范围边界、Key 缺失时禁用、上游 5xx 降级、非法 JSON 降级、缓存命中、境外坐标不偏移。

`AmapRealResponseTest` 直接使用**真实接口抓取的响应结构**做回归，覆盖文档未写明的「空数组」形态：
`biz_ext.cost` 为 `[]` 或字符串 `"33.50"`（四舍五入而非截断）、`rating` 为 `[]`、
`tel`/`tag` 为 `[]`、`tag` 为 `keytag` 兜底、`type` 层级重复去重、http 图片 URL、真实 POI 坐标转换。

**真实接口实测（2026-11-02，屏峰校区）**：以真实 Key 启动 JAR 后调用 `/api/nearby/venues`，
返回 25 条 venues（11 条真实高德 POI + 14 条本地档口），距离 38m–152m，
POI 为家和堂、茶百道、古茗、瑞幸咖啡、幸运咖等真实商户，字段与坐标均正确。
