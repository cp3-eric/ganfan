package com.ganfan.backend.feature.nearby;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 高德开放平台 Web 服务 API 适配器。
 *
 * <p>接口：{@code https://restapi.amap.com/v3/place/around}（周边搜索）。
 *
 * <p>要点：
 * <ul>
 *   <li>{@code location} 参数为「经度,纬度」，顺序与本项目相反，需注意转换。</li>
 *   <li>{@code extensions=all} 才会返回 {@code biz_ext.rating}、{@code biz_ext.cost}、{@code photos} 等深度字段。</li>
 *   <li>返回坐标为 GCJ-02，需转换为 WGS-84 后再写入 Venue。</li>
 * </ul>
 *
 * <p>Key 通过环境变量注入，不落库、不写死在代码中。
 */
@Component
@ConditionalOnProperty(prefix = "app.nearby.map.amap", name = "enabled", havingValue = "true")
public class AmapVenueProvider extends AbstractMapVenueProvider {
    private static final String DEFAULT_ENDPOINT = "https://restapi.amap.com/v3/place/around";

    /** 高德 POI 分类码：餐饮服务。 */
    private static final String TYPE_FOOD = "050000";

    private final String apiKey;
    private final String keywords;
    private final String types;
    private final int offset;
    private final String endpoint;

    public AmapVenueProvider(
            ObjectMapper objectMapper,
            @Value("${app.nearby.map.amap.key:}") String apiKey,
            @Value("${app.nearby.map.amap.keywords:}") String keywords,
            @Value("${app.nearby.map.amap.types:" + TYPE_FOOD + "}") String types,
            @Value("${app.nearby.map.amap.offset:25}") int offset,
            @Value("${app.nearby.map.amap.endpoint:" + DEFAULT_ENDPOINT + "}") String endpoint,
            @Value("${app.nearby.map.cache-ttl-seconds:300}") long cacheTtlSeconds,
            @Value("${app.nearby.map.max-concurrent-requests:4}") int maxConcurrentRequests) {
        super(objectMapper, "amap", cacheTtlSeconds * 1000L, maxConcurrentRequests);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.keywords = keywords == null ? "" : keywords.trim();
        this.types = types == null || types.isBlank() ? TYPE_FOOD : types.trim();
        this.offset = Math.min(25, Math.max(1, offset));
        this.endpoint = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint.trim();
    }

    @Override
    protected String providerName() {
        return "amap";
    }

    @Override
    protected boolean enabled() {
        return !apiKey.isBlank();
    }

    @Override
    protected String buildUrl(double lat, double lng, double radiusMeters) {
        // 高德要求「经度,纬度」，且坐标需为 GCJ-02。
        double[] gcj = GeoCoordinates.wgs84ToGcj02(lat, lng);
        double[] center = {gcj[1], gcj[0]};
        int radius = (int) Math.min(50000d, Math.max(100d, radiusMeters));

        StringBuilder url = new StringBuilder(endpoint)
                .append("?key=").append(encode(apiKey))
                .append("&location=").append(format(center[0])).append(",").append(format(center[1]))
                .append("&radius=").append(radius)
                .append("&types=").append(encode(types))
                .append("&offset=").append(offset)
                .append("&page=1")
                .append("&sortrule=distance")
                .append("&extensions=all")
                .append("&output=JSON");
        if (!keywords.isBlank()) {
            url.append("&keywords=").append(encode(keywords));
        }
        return url.toString();
    }

    @Override
    protected List<Venue> parse(String body) {
        List<Venue> venues = new ArrayList<>();
        try {
            JsonNode root = mapper().readTree(body);
            // 高德 v3 用字符串 "1"/"0" 表示成功失败。
            if (!"1".equals(root.path("status").asText())) {
                return venues;
            }
            for (JsonNode poi : root.path("pois")) {
                Venue venue = toVenue(poi);
                if (venue != null) venues.add(venue);
            }
        } catch (Exception e) {
            return List.of();
        }
        return venues;
    }

    private Venue toVenue(JsonNode poi) {
        String name = text(poi, "name");
        if (name.isBlank()) return null;

        double[] location = parseLocation(text(poi, "location"));
        if (location == null) return null;
        // GCJ-02 -> WGS-84，与本地种子数据对齐。
        double[] wgs = GeoCoordinates.gcj02ToWgs84(location[1], location[0]);

        JsonNode bizExt = poi.path("biz_ext");
        double rating = bizExt.path("rating").asDouble(0);
        // 实测 biz_ext.cost 有三种形态：空数组 []（缺失）、字符串 "14.00"、数字。
        // 字符串直接用 asInt 会把 "33.50" 截断成 33，这里统一按四舍五入解析。
        int price = parseCost(bizExt.path("cost"));

        String typeCode = text(poi, "typecode");
        String category = text(poi, "type");
        String address = text(poi, "address");
        String businessArea = text(poi, "business_area");

        List<String> tags = new ArrayList<>();
        for (String part : category.split(";")) {
            String trimmed = part.trim();
            // 「大类;中类;小类」去掉宽泛的「餐饮服务」，其余去重后作为标签。
            if (!trimmed.isBlank() && !trimmed.equals("餐饮服务") && !tags.contains(trimmed)) {
                tags.add(trimmed);
            }
        }
        if (!businessArea.isBlank() && !tags.contains(businessArea)) tags.add(businessArea);

        // 高德的 tag 字段是特色菜列表，映射为 dishes。
        List<String> dishes = textList(poi, "tag");
        // keytag 是高德给出的品类关键词（如「小吃快餐」），tag 为空时用它兜底。
        String keyTag = text(poi, "keytag");
        String tagText = dishes.isEmpty() ? keyTag : String.join(",", dishes);
        if (dishes.isEmpty() && !keyTag.isBlank()) dishes = List.of(keyTag);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("provider", "amap");
        attributes.put("external", true);
        attributes.put("externalId", text(poi, "id"));
        attributes.put("address", address);
        attributes.put("tel", text(poi, "tel"));
        attributes.put("typeCode", typeCode);
        attributes.put("category", category);
        attributes.put("keyTag", keyTag);
        attributes.put("businessArea", businessArea);
        attributes.put("province", text(poi, "pname"));
        attributes.put("city", text(poi, "cityname"));
        attributes.put("district", text(poi, "adname"));
        attributes.put("canteen", businessArea.isBlank() ? address : businessArea);
        attributes.put("floor", "");
        attributes.put("checkins", 0);
        attributes.put("remain", 0);
        attributes.put("spicy", "");
        attributes.put("delivery", 30);
        attributes.put("night", false);
        // 地图 POI 不提供拼桌/剩余份数语义，用 available 标注为可到店消费。
        attributes.put("available", true);

        String id = "amap-" + text(poi, "id");
        return new Venue(
                "stall",
                id.isBlank() || id.equals("amap-") ? "amap-" + Integer.toHexString(name.hashCode()) : id,
                name,
                emojiFor(typeCode, category, tagText),
                firstPhoto(poi),
                placeOf(address, businessArea),
                price,
                wgs[0],
                wgs[1],
                rating,
                tags,
                dishes,
                attributes);
    }

    private String placeOf(String address, String businessArea) {
        if (!address.isBlank()) return address;
        if (!businessArea.isBlank()) return businessArea;
        return "地图商户";
    }

    /** 解析高德的 "经度,纬度" 字符串。 */
    private double[] parseLocation(String location) {
        if (location.isBlank()) return null;
        String[] parts = location.split(",");
        if (parts.length < 2) return null;
        try {
            double lng = Double.parseDouble(parts[0].trim());
            double lat = Double.parseDouble(parts[1].trim());
            if (Double.isNaN(lat) || Double.isNaN(lng)) return null;
            return new double[] {lng, lat};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 取第一张照片 URL。实测高德图片 URL 混用 http 与 https，两者都接受。 */
    private String firstPhoto(JsonNode poi) {
        JsonNode photos = poi.path("photos");
        if (!photos.isArray() || photos.isEmpty()) return "";
        String url = text(photos.get(0), "url");
        return url.startsWith("http://") || url.startsWith("https://") ? url : "";
    }

    /**
     * 解析人均消费。实测高德返回三种形态：
     * <ul>
     *   <li>缺失时为空数组 {@code []}</li>
     *   <li>字符串，例如 {@code "14.00"}、{@code "33.50"}</li>
     *   <li>数字</li>
     * </ul>
     * 字符串需按四舍五入取整，直接 asInt 会把 33.50 截断成 33。
     */
    private int parseCost(JsonNode cost) {
        if (cost == null || cost.isMissingNode() || cost.isNull()) return 0;
        if (cost.isArray() || cost.isObject()) return 0;
        if (cost.isNumber()) return (int) Math.round(cost.asDouble());
        String raw = cost.asText("").trim();
        if (raw.isEmpty()) return 0;
        try {
            return (int) Math.round(Double.parseDouble(raw));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 依据 POI 分类码挑选贴切的表情符号。
     * 实测餐饮 POI 的 typecode 常统一为 050100（中餐厅），
     * 因此再结合 tag（特色菜/品类，如「煲仔饭」）细分。
     */
    private String emojiFor(String typeCode, String category, String tag) {
        String code = typeCode == null ? "" : typeCode;
        if (code.startsWith("050500")) return "☕";
        if (code.startsWith("050300")) return "🍜";
        if (code.startsWith("050700")) return "🥤";
        if (code.startsWith("050800")) return "🍰";
        if (code.startsWith("050900")) return "🍢";
        if (code.startsWith("051100")) return "🍲";
        if (code.startsWith("051300")) return "🍻";
        if (code.startsWith("051000")) return "🍜";
        if (code.startsWith("051200")) return "🍱";
        if (code.startsWith("050100")) return emojiByTag(tag);

        String pool = (category == null ? "" : category) + (tag == null ? "" : tag);
        if (pool.contains("咖啡")) return "☕";
        if (pool.contains("茶")) return "🧋";
        if (pool.contains("烧烤")) return "🍻";
        if (pool.contains("火锅")) return "🍲";
        if (pool.contains("馄饨")) return "🥟";
        if (pool.contains("煲仔饭")) return "🍲";
        if (pool.contains("小吃")) return "🍢";
        if (pool.contains("快餐")) return "🍱";
        if (pool.contains("面")) return "🍜";
        if (code.startsWith("0502")) return "🍝";
        return "🍽";
    }

    /** 按特色菜/品类关键词细分表情符号。 */
    private String emojiByTag(String tag) {
        String value = tag == null ? "" : tag;
        if (value.contains("咖啡") || value.contains("coffee")) return "☕";
        if (value.contains("茶") || value.contains("奶茶")) return "🥤";
        if (value.contains("烧烤")) return "🍻";
        if (value.contains("火锅")) return "🍲";
        if (value.contains("馄饨")) return "🥟";
        if (value.contains("煲仔饭")) return "🍲";
        if (value.contains("麻辣烫") || value.contains("冒菜")) return "🍲";
        if (value.contains("快餐")) return "🍱";
        if (value.contains("米粉") || value.contains("米线")) return "🍜";
        if (value.contains("面")) return "🍜";
        if (value.contains("饺子")) return "🥟";
        if (value.contains("蛋糕")) return "🍰";
        if (value.contains("冷饮") || value.contains("冰淇淋")) return "🥤";
        if (value.contains("小吃") || value.contains("快餐店")) return "🍢";
        if (value.contains("盖饭") || value.contains("简餐") || value.contains("饭")) return "🍱";
        if (value.contains("粥")) return "🥣";
        if (value.contains("烧烤店")) return "🍻";
        return "🍚";
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
