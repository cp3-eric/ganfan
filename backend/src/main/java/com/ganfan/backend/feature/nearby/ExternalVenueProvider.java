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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 可配置的外部商户数据源。适合接入自建聚合服务、地图 POI API 或外卖开放平台网关。
 * URL 返回 JSON 数组，或 {"venues":[...]}。字段可以缺省，服务端会做归一化。
 */
@Component
@ConditionalOnProperty(prefix = "app.nearby.external", name = "enabled", havingValue = "true")
public class ExternalVenueProvider implements VenueProvider {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpoint;
    private final String token;

    public ExternalVenueProvider(
            ObjectMapper objectMapper,
            @Value("${app.nearby.external.url:}") String endpoint,
            @Value("${app.nearby.external.token:}") String token) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.token = token == null ? "" : token.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public List<Venue> findCandidates(double lat, double lng, double radiusMeters) {
        if (endpoint.isBlank()) return List.of();
        try {
            String separator = endpoint.contains("?") ? "&" : "?";
            String url = endpoint + separator + "lat=" + encode(String.valueOf(lat))
                    + "&lng=" + encode(String.valueOf(lng))
                    + "&radiusMeters=" + encode(String.valueOf((int) radiusMeters));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET();
            if (!token.isBlank()) builder.header("Authorization", "Bearer " + token);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return List.of();
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode array = root.isArray() ? root : root.path("venues");
            List<Venue> venues = new ArrayList<>();
            if (array.isArray()) {
                for (JsonNode item : array) {
                    Venue venue = normalize(item);
                    if (venue != null) venues.add(venue);
                }
            }
            return venues;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Venue normalize(JsonNode item) {
        String name = item.path("name").asText("");
        if (name.isBlank()) return null;
        String type = item.path("type").asText("");
        if (type.isBlank()) type = name.toLowerCase(Locale.ROOT).contains("外卖") ? "takeout" : "stall";
        if (type.equals("dining")) type = "stall";
        double lat = item.path("lat").asDouble(Double.NaN);
        double lng = item.path("lng").asDouble(Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lng)) return null;
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("provider", item.path("provider").asText("external"));
        attrs.put("address", item.path("address").asText(""));
        attrs.put("delivery", item.path("delivery").asInt(30));
        attrs.put("night", item.path("night").asBoolean(false));
        attrs.put("spicy", item.path("spicy").asText(""));
        attrs.put("canteen", item.path("canteen").asText(item.path("address").asText("")));
        attrs.put("floor", item.path("floor").asText(""));
        attrs.put("checkins", item.path("checkins").asInt(0));
        attrs.put("remain", item.path("remain").asInt(0));
        attrs.put("externalId", item.path("id").asText(""));
        return new Venue(
                type,
                item.path("id").asText("ext-" + name.hashCode()),
                name,
                item.path("emoji").asText("🍽"),
                item.path("img").asText(""),
                item.path("place").asText(item.path("address").asText("")),
                item.path("price").asInt(0),
                lat,
                lng,
                item.path("rating").asDouble(0),
                strings(item, "tags"),
                strings(item, "dishes"),
                attrs);
    }

    private List<String> strings(JsonNode item, String field) {
        List<String> values = new ArrayList<>();
        item.path(field).forEach(node -> {
            String value = node.asText("");
            if (!value.isBlank()) values.add(value);
        });
        return values;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
