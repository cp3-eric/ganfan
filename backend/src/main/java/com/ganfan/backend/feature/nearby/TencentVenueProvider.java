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
 * 腾讯位置服务 Web Service API 适配器。
 *
 * <p>接口：{@code https://apis.map.qq.com/ws/place/v1/search}（地点搜索）。
 *
 * <p>要点：
 * <ul>
 *   <li>官方文档中「周边搜索」为 {@code boundary=nearby(lat,lng,radius)}，radius 上限 1000 米，
 *       无法覆盖本项目默认的 2500 米搜索半径，因此这里改用 {@code boundary=rectangle(西南角,东北角)}
 *       矩形范围搜索，再在本地按半径二次过滤。</li>
 *   <li>参数顺序为「纬度,经度」，与高德相反。</li>
 *   <li>响应数组字段名为 {@code data}，名称字段为 {@code title}，距离字段为 {@code _distance}。</li>
 *   <li>返回坐标为 GCJ-02，需转换为 WGS-84 后再写入 Venue。</li>
 * </ul>
 *
 * <p>Key 通过环境变量注入，不落库、不写死在代码中。
 */
@Component
@ConditionalOnProperty(prefix = "app.nearby.map.tencent", name = "enabled", havingValue = "true")
public class TencentVenueProvider extends AbstractMapVenueProvider {
    private static final String DEFAULT_ENDPOINT = "https://apis.map.qq.com/ws/place/v1/search";

    /** 腾讯分类筛选：餐饮。 */
    private static final String CATEGORY_FOOD = "餐饮";

    /** 每页最大条数。 */
    private static final int MAX_PAGE_SIZE = 20;

    private final String apiKey;
    private final String keyword;
    private final String category;
    private final int pageSize;
    private final String endpoint;

    public TencentVenueProvider(
            ObjectMapper objectMapper,
            @Value("${app.nearby.map.tencent.key:}") String apiKey,
            @Value("${app.nearby.map.tencent.keyword:餐厅}") String keyword,
            @Value("${app.nearby.map.tencent.category:" + CATEGORY_FOOD + "}") String category,
            @Value("${app.nearby.map.tencent.page-size:20}") int pageSize,
            @Value("${app.nearby.map.tencent.endpoint:" + DEFAULT_ENDPOINT + "}") String endpoint,
            @Value("${app.nearby.map.cache-ttl-seconds:300}") long cacheTtlSeconds,
            @Value("${app.nearby.map.max-concurrent-requests:4}") int maxConcurrentRequests) {
        super(objectMapper, "tencent", cacheTtlSeconds * 1000L, maxConcurrentRequests);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.keyword = keyword == null ? "" : keyword.trim();
        this.category = category == null ? "" : category.trim();
        this.pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, pageSize));
        this.endpoint = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint.trim();
    }

    @Override
    protected String providerName() {
        return "tencent";
    }

    @Override
    protected boolean enabled() {
        return !apiKey.isBlank();
    }

    @Override
    protected String buildUrl(double lat, double lng, double radiusMeters) {
        // 腾讯要求「纬度,经度」，且坐标需为 GCJ-02。
        double[] gcj = GeoCoordinates.wgs84ToGcj02(lat, lng);
        double[] center = {gcj[0], gcj[1]};

        // nearby 半径上限 1000 米，改用矩形范围搜索覆盖更大的半径。
        double radius = Math.max(500d, radiusMeters);
        double dLat = radius / 111320d;
        double dLng = radius / (111320d * Math.cos(Math.toRadians(center[0])));
        double southLat = center[0] - dLat;
        double westLng = center[1] - dLng;
        double northLat = center[0] + dLat;
        double eastLng = center[1] + dLng;

        StringBuilder url = new StringBuilder(endpoint)
                .append("?key=").append(encode(apiKey))
                .append("&boundary=rectangle(")
                .append(format(southLat)).append(",").append(format(westLng)).append(",")
                .append(format(northLat)).append(",").append(format(eastLng)).append(")")
                .append("&page_index=1")
                .append("&page_size=").append(pageSize)
                .append("&orderby=_distance")
                .append("&output=json");
        if (!keyword.isBlank()) {
            url.append("&keyword=").append(encode(keyword));
        }
        if (!category.isBlank()) {
            url.append("&filter=").append(encode("category=" + category));
        }
        return url.toString();
    }

    @Override
    protected List<Venue> parse(String body) {
        List<Venue> venues = new ArrayList<>();
        try {
            JsonNode root = mapper().readTree(body);
            // 腾讯 status 为 0 表示成功。
            if (root.path("status").asInt(-1) != 0) {
                return venues;
            }
            for (JsonNode item : root.path("data")) {
                Venue venue = toVenue(item);
                if (venue != null) venues.add(venue);
            }
        } catch (Exception e) {
            return List.of();
        }
        return venues;
    }

    private Venue toVenue(JsonNode item) {
        // 名称字段在不同接口版本中可能是 title 或 name。
        String name = text(item, "title");
        if (name.isBlank()) name = text(item, "name");
        if (name.isBlank()) return null;

        JsonNode location = item.path("location");
        double lat = location.path("lat").asDouble(Double.NaN);
        double lng = location.path("lng").asDouble(Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lng)) return null;
        // GCJ-02 -> WGS-84，与本地种子数据对齐。
        double[] wgs = GeoCoordinates.gcj02ToWgs84(lat, lng);

        String address = text(item, "address");
        String itemCategory = text(item, "category");
        JsonNode adInfo = item.path("ad_info");
        String district = text(adInfo, "district");
        String city = text(adInfo, "city");
        String province = text(adInfo, "province");

        List<String> tags = new ArrayList<>();
        for (String part : itemCategory.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) tags.add(trimmed);
        }
        if (!district.isBlank()) tags.add(district);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("provider", "tencent");
        attributes.put("external", true);
        attributes.put("externalId", text(item, "id"));
        attributes.put("address", address);
        attributes.put("tel", text(item, "tel"));
        attributes.put("category", itemCategory);
        attributes.put("province", province);
        attributes.put("city", city);
        attributes.put("district", district);
        attributes.put("canteen", address);
        attributes.put("floor", "");
        attributes.put("checkins", 0);
        attributes.put("remain", 0);
        attributes.put("spicy", "");
        attributes.put("delivery", 30);
        attributes.put("night", false);
        attributes.put("available", true);

        return new Venue(
                "stall",
                "tencent-" + text(item, "id"),
                name,
                emojiFor(itemCategory),
                "",
                address.isBlank() ? itemCategory : address,
                0,
                wgs[0],
                wgs[1],
                0,
                tags,
                List.of(),
                attributes);
    }

    /** 依据分类挑选贴切的表情符号。 */
    private String emojiFor(String category) {
        String value = category == null ? "" : category;
        if (value.contains("咖啡")) return "☕";
        if (value.contains("茶")) return "🧋";
        if (value.contains("小吃")) return "🍢";
        if (value.contains("快餐")) return "🍱";
        if (value.contains("火锅")) return "🍲";
        if (value.contains("烧烤")) return "🍻";
        if (value.contains("面")) return "🍜";
        if (value.contains("粉")) return "🍜";
        if (value.contains("米线")) return "🍜";
        if (value.contains("蛋糕")) return "🍰";
        if (value.contains("冷饮")) return "🥤";
        if (value.contains("美食")) return "🍽";
        return "🍽";
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
