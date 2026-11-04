package com.ganfan.backend.feature.nearby;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganfan.backend.feature.nearby.VenueProvider.Venue;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 地图 Web Service 适配器测试。
 *
 * <p>使用本地 HTTP 打桩服务器模拟高德与腾讯的真实响应体，验证：
 * <ul>
 *   <li>响应字段到 Venue 的映射是否正确；</li>
 *   <li>GCJ-02 -> WGS-84 坐标转换是否生效；</li>
 *   <li>缓存与失败降级是否正常。</li>
 * </ul>
 */
class MapVenueProviderTest {
    /** 高德周边搜索响应样例（GCJ-02 坐标，extensions=all）。 */
    private static final String AMAP_RESPONSE = """
            {
              "status": "1",
              "info": "OK",
              "count": "2",
              "pois": [
                {
                  "id": "B0FFH12345",
                  "name": "家常小炒(屏峰店)",
                  "type": "餐饮服务;中餐厅;家常菜",
                  "typecode": "050102",
                  "address": "浙江省杭州市西湖区留下街道屏峰路1号",
                  "location": "120.036500,30.231100",
                  "tel": "0571-88888888",
                  "distance": "120",
                  "business_area": "屏峰校区",
                  "tag": "招牌红烧肉,米饭",
                  "biz_ext": { "rating": "4.6", "cost": "22.0" },
                  "photos": [ { "url": "https://example.com/photo1.jpg" } ]
                },
                {
                  "id": "B0FFH67890",
                  "name": "咖啡驿站",
                  "type": "餐饮服务;咖啡厅",
                  "typecode": "050500",
                  "address": "留下街道屏峰路2号",
                  "location": "120.037000,30.232000",
                  "biz_ext": { "rating": [], "cost": [] }
                }
              ]
            }
            """;

    /** 腾讯地点搜索响应样例（GCJ-02 坐标）。 */
    private static final String TENCENT_RESPONSE = """
            {
              "status": 0,
              "message": "query ok",
              "count": 1,
              "request_id": "req-1",
              "data": [
                {
                  "id": "6621879543162709731",
                  "title": "川味小厨",
                  "address": "浙江省杭州市西湖区屏峰路3号",
                  "tel": "0571-66666666",
                  "category": "美食;中餐厅;川菜",
                  "type": 0,
                  "location": { "lat": 30.232500, "lng": 120.037500 },
                  "_distance": 260,
                  "ad_info": {
                    "adcode": 330106,
                    "province": "浙江省",
                    "city": "杭州市",
                    "district": "西湖区"
                  }
                }
              ]
            }
            """;

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicReference<String> lastQuery = new AtomicReference<>("");
    private final AtomicReference<String> responseBody = new AtomicReference<>("");
    private final AtomicInteger responseStatus = new AtomicInteger(200);

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/amap", exchange -> {
            requestCount.incrementAndGet();
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, responseBody.get());
        });
        server.createContext("/tencent", exchange -> {
            requestCount.incrementAndGet();
            lastQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, responseBody.get());
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(responseStatus.get(), payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void amapResponseIsMappedToVenueWithGcj02ConvertedToWgs84() {
        responseBody.set(AMAP_RESPONSE);
        AmapVenueProvider provider = new AmapVenueProvider(
                new ObjectMapper(), "test-key", "", "050000", 25, baseUrl + "/amap", 300, 4);

        List<Venue> venues = provider.findCandidates(30.2310563, 120.0364832, 1000);

        assertThat(venues).hasSize(2);

        Venue first = venues.get(0);
        assertThat(first.id()).isEqualTo("amap-B0FFH12345");
        assertThat(first.name()).isEqualTo("家常小炒(屏峰店)");
        assertThat(first.type()).isEqualTo("stall");
        assertThat(first.place()).isEqualTo("浙江省杭州市西湖区留下街道屏峰路1号");
        assertThat(first.price()).isEqualTo(22);
        assertThat(first.rating()).isEqualTo(4.6);
        assertThat(first.dishes()).containsExactly("招牌红烧肉", "米饭");
        assertThat(first.img()).isEqualTo("https://example.com/photo1.jpg");
        assertThat(first.tags()).contains("中餐厅", "家常菜", "屏峰校区");
        assertThat(first.attributes().get("provider")).isEqualTo("amap");
        assertThat(first.attributes().get("externalId")).isEqualTo("B0FFH12345");
        assertThat(first.attributes().get("tel")).isEqualTo("0571-88888888");

        // 上游为 GCJ-02，落库必须是 WGS-84。
        // 杭州地区 GCJ-02 相对 WGS-84：纬度偏小、经度偏大，偏移量约 527 米。
        assertThat(first.lat()).isCloseTo(30.2334678, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(first.lng()).isCloseTo(120.0317426, org.assertj.core.data.Offset.offset(1e-6));
        // 与本地种子数据（屏峰校区 WGS-84 30.2310563, 120.0364832）的差距应为真实距离量级。
        assertThat(distanceMeters(30.2334678, 120.0317426, 30.2310563, 120.0364832))
                .isBetween(400d, 700d);

        // 缺失评分的 POI 不应抛异常。
        Venue second = venues.get(1);
        assertThat(second.rating()).isEqualTo(0d);
        assertThat(second.price()).isEqualTo(0);
    }

    @Test
    void amapRequestUsesLngLatOrderAndCarriesKey() {
        responseBody.set(AMAP_RESPONSE);
        AmapVenueProvider provider = new AmapVenueProvider(
                new ObjectMapper(), "test-key", "", "050000", 25, baseUrl + "/amap", 300, 4);

        provider.findCandidates(30.2310563, 120.0364832, 1500);

        String query = lastQuery.get();
        assertThat(query).contains("key=test-key");
        assertThat(query).contains("radius=1500");
        assertThat(query).contains("types=050000");
        assertThat(query).contains("extensions=all");
        // 高德要求「经度,纬度」，且传入的是 GCJ-02 坐标。
        int comma = query.indexOf("location=") + "location=".length();
        String location = query.substring(comma, query.indexOf('&', comma));
        String[] parts = location.split(",");
        double lng = Double.parseDouble(parts[0]);
        double lat = Double.parseDouble(parts[1]);
        // WGS-84 (30.2310563, 120.0364832) -> GCJ-02 (30.2287005, 120.0412502)
        assertThat(lng).isCloseTo(120.0412502, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(lat).isCloseTo(30.2287005, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void tencentResponseIsMappedToVenue() {
        responseBody.set(TENCENT_RESPONSE);
        TencentVenueProvider provider = new TencentVenueProvider(
                new ObjectMapper(), "test-key", "餐厅", "餐饮", 20, baseUrl + "/tencent", 300, 4);

        List<Venue> venues = provider.findCandidates(30.2310563, 120.0364832, 2500);

        assertThat(venues).hasSize(1);
        Venue venue = venues.get(0);
        assertThat(venue.id()).isEqualTo("tencent-6621879543162709731");
        assertThat(venue.name()).isEqualTo("川味小厨");
        assertThat(venue.place()).isEqualTo("浙江省杭州市西湖区屏峰路3号");
        assertThat(venue.attributes().get("provider")).isEqualTo("tencent");
        assertThat(venue.attributes().get("district")).isEqualTo("西湖区");
        assertThat(venue.attributes().get("city")).isEqualTo("杭州市");
        assertThat(venue.tags()).contains("中餐厅", "川菜", "西湖区");
        // GCJ-02 (30.2325, 120.0375) -> WGS-84 (30.2348651, 120.0327403)
        assertThat(venue.lat()).isCloseTo(30.2348651, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(venue.lng()).isCloseTo(120.0327403, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void tencentUsesRectangleBoundaryToCoverRadiusBeyondNearbyLimit() {
        responseBody.set(TENCENT_RESPONSE);
        TencentVenueProvider provider = new TencentVenueProvider(
                new ObjectMapper(), "test-key", "餐厅", "餐饮", 20, baseUrl + "/tencent", 300, 4);

        provider.findCandidates(30.2310563, 120.0364832, 2500);

        // nearby 的 radius 上限只有 1000 米，必须用 rectangle 覆盖 2500 米。
        assertThat(lastQuery.get()).contains("boundary=rectangle(");
        assertThat(lastQuery.get()).doesNotContain("nearby(");
        // 中文参数必须做百分号编码，否则服务端无法正确解析。
        assertThat(lastQuery.get()).contains("keyword=%E9%A4%90%E5%8E%85");
        assertThat(lastQuery.get()).contains("filter=category%3D%E9%A4%90%E9%A5%AE");    }

    @Test
    void providerWithoutKeyIsDisabledAndReturnsEmpty() {
        responseBody.set(AMAP_RESPONSE);
        AmapVenueProvider provider = new AmapVenueProvider(
                new ObjectMapper(), "", "", "050000", 25, baseUrl + "/amap", 300, 4);

        assertThat(provider.findCandidates(30.2310563, 120.0364832, 1000)).isEmpty();
        assertThat(requestCount.get()).isZero();
    }

    @Test
    void upstreamFailureDegradesToEmptyListWithoutThrowing() {
        responseStatus.set(500);
        responseBody.set("{\"error\":\"boom\"}");
        AmapVenueProvider provider = new AmapVenueProvider(
                new ObjectMapper(), "test-key", "", "050000", 25, baseUrl + "/amap", 300, 4);

        assertThat(provider.findCandidates(30.2310563, 120.0364832, 1000)).isEmpty();
    }

    @Test
    void malformedJsonDegradesToEmptyListWithoutThrowing() {
        responseBody.set("not-json");
        AmapVenueProvider provider = new AmapVenueProvider(
                new ObjectMapper(), "test-key", "", "050000", 25, baseUrl + "/amap", 300, 4);

        assertThat(provider.findCandidates(30.2310563, 120.0364832, 1000)).isEmpty();
    }

    @Test
    void repeatedQueriesHitCache() {
        responseBody.set(AMAP_RESPONSE);
        AmapVenueProvider provider = new AmapVenueProvider(
                new ObjectMapper(), "test-key", "", "050000", 25, baseUrl + "/amap", 300, 4);

        provider.findCandidates(30.2310563, 120.0364832, 1000);
        provider.findCandidates(30.2310563, 120.0364832, 1000);

        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    void geoCoordinatesRoundTripIsStable() {
        double lat = 30.2310563;
        double lng = 120.0364832;

        double[] gcj = GeoCoordinates.wgs84ToGcj02(lat, lng);
        assertThat(gcj[0]).isNotEqualTo(lat);
        assertThat(gcj[1]).isNotEqualTo(lng);

        double[] back = GeoCoordinates.gcj02ToWgs84(gcj[0], gcj[1]);
        assertThat(back[0]).isCloseTo(lat, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(back[1]).isCloseTo(lng, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void overseasCoordinatesAreNotShifted() {
        // 东京：境外不应套用 GCJ-02 偏移算法。
        double[] gcj = GeoCoordinates.wgs84ToGcj02(35.6895, 139.6917);
        assertThat(gcj[0]).isEqualTo(35.6895);
        assertThat(gcj[1]).isEqualTo(139.6917);
        assertThat(GeoCoordinates.inChina(35.6895, 139.6917)).isFalse();
    }

    @Test
    void nullKeyProviderIsSafe() {
        AmapVenueProvider provider = new AmapVenueProvider(
                new ObjectMapper(), null, null, null, 25, null, 300, 4);

        assertThat(provider.findCandidates(30.2310563, 120.0364832, 1000)).isEmpty();
    }

    /** 两点间大圆距离（米）。 */
    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
