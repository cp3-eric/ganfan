package com.ganfan.backend.feature.nearby;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 地图 Web Service 数据源的公共基类。
 *
 * <p>统一处理 HTTP 调用、超时、失败降级、结果缓存与并发限流，
 * 子类只需实现「构造请求 URL」与「解析响应为 Venue」两件事。
 *
 * <p>坐标系约定：本类与所有子类对外暴露的 {@code findCandidates} 入参/出参均为 WGS-84，
 * 子类在与地图服务端交互时自行完成 GCJ-02 转换。
 */
public abstract class AbstractMapVenueProvider implements VenueProvider {
    private static final Logger log = LoggerFactory.getLogger(AbstractMapVenueProvider.class);

    /** 缓存条目最大存活时间。 */
    private final long cacheTtlMillis;
    /** 同一时刻允许发起的外网请求数。 */
    private final int maxConcurrentRequests;
    /** 缓存键前缀，用于区分不同服务商与查询参数。 */
    private final String cacheNamespace;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Semaphore permit;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    protected AbstractMapVenueProvider(
            ObjectMapper objectMapper,
            String cacheNamespace,
            long cacheTtlMillis,
            int maxConcurrentRequests) {
        this.objectMapper = objectMapper;
        this.cacheNamespace = cacheNamespace;
        this.cacheTtlMillis = cacheTtlMillis;
        this.maxConcurrentRequests = Math.max(1, maxConcurrentRequests);
        this.permit = new Semaphore(this.maxConcurrentRequests);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 构建地图服务商的请求 URL。子类把业务参数与 Key 拼进 query string。
     *
     * @param lat WGS-84 纬度
     * @param lng WGS-84 经度
     * @param radiusMeters 搜索半径（米）
     */
    protected abstract String buildUrl(double lat, double lng, double radiusMeters);

    /**
     * 解析服务商响应体，转换为标准 Venue 列表。
     *
     * @param body 原始 JSON 字符串
     * @return 解析出的 Venue 列表；解析失败返回空列表
     */
    protected abstract java.util.List<Venue> parse(String body);

    /** 服务商标识，用于 Venue 的 attributes.provider 与缓存命名空间。 */
    protected abstract String providerName();

    @Override
    public java.util.List<Venue> findCandidates(double lat, double lng, double radiusMeters) {
        if (!enabled()) return java.util.List.of();
        String cacheKey = cacheNamespace + "|" + round(lat) + "|" + round(lng) + "|" + (int) radiusMeters;
        CacheEntry cached = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAt() < cacheTtlMillis) {
            return cached.venues();
        }
        java.util.List<Venue> venues = java.util.List.of();
        if (!permit.tryAcquire()) {
            log.warn("[{}] 达到并发上限 {}，本次跳过地图查询", providerName(), maxConcurrentRequests);
            return java.util.List.of();
        }
        try {
            String body = get(buildUrl(lat, lng, radiusMeters));
            if (body != null) {
                venues = parse(body);
                cache.put(cacheKey, new CacheEntry(venues, now));
            }
        } catch (Exception e) {
            log.warn("[{}] 地图 POI 查询失败，降级为本地数据源: {}", providerName(), e.getMessage());
        } finally {
            permit.release();
        }
        return venues;
    }

    /** 子类可覆写以支持按配置动态开关。 */
    protected boolean enabled() {
        return true;
    }

    /** 发起 GET 请求；失败返回 null，交由上层降级。 */
    protected String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[{}] 上游返回 HTTP {}", providerName(), response.statusCode());
                return null;
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[{}] 地图 POI 查询被中断", providerName());
            return null;
        } catch (Exception e) {
            log.warn("[{}] 地图 POI 请求异常: {}", providerName(), e.getMessage());
            return null;
        }
    }

    protected ObjectMapper mapper() {
        return objectMapper;
    }

    /** URL 参数编码。 */
    protected String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** 拼接 query string 的工具方法。 */
    protected static String query(String... pairs) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (builder.length() > 0) builder.append('&');
            builder.append(pairs[i]).append('=').append(pairs[i + 1]);
        }
        return builder.toString();
    }

    protected static double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    /** 读取字符串字段，缺失时返回空串。 */
    protected static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    /** 读取字符串数组字段，兼容字符串或数组两种形态。 */
    protected static java.util.List<String> textList(JsonNode node, String field) {
        java.util.List<String> values = new java.util.ArrayList<>();
        JsonNode value = node.path(field);
        if (value.isArray()) {
            value.forEach(item -> {
                String text = item.asText("");
                if (!text.isBlank()) values.add(text);
            });
        } else if (value.isTextual()) {
            for (String part : value.asText("").split("[,，]")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) values.add(trimmed);
            }
        }
        return values;
    }

    private record CacheEntry(java.util.List<Venue> venues, long fetchedAt) {}

    /** 简单的并发许可控制。 */
    private static final class Semaphore {
        private final int permits;
        private int used;

        Semaphore(int permits) {
            this.permits = permits;
        }

        synchronized boolean tryAcquire() {
            if (used >= permits) return false;
            used++;
            return true;
        }

        synchronized void release() {
            if (used > 0) used--;
        }
    }
}
