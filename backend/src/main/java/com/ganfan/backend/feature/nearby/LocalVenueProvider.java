package com.ganfan.backend.feature.nearby;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LocalVenueProvider implements VenueProvider {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public LocalVenueProvider(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Venue> findCandidates(double lat, double lng, double radiusMeters) {
        Map<String, CanteenPoint> canteens = loadCanteens();
        List<Venue> result = new ArrayList<>();
        jdbc.query("SELECT type, content_id, campus, school_id, payload_json FROM content_items WHERE type IN ('STALL','TAKEOUT') ORDER BY id",
                rs -> {
                    String contentType = rs.getString(1);
                    Venue venue = "TAKEOUT".equals(contentType)
                            ? toTakeout(rs.getString(2), rs.getString(3), rs.getString(4), parse(rs.getString(5)))
                            : toStall(rs.getString(2), rs.getString(3), rs.getString(4), parse(rs.getString(5)), canteens);
                    if (venue != null && withinRadius(lat, lng, venue.lat(), venue.lng(), radiusMeters)) {
                        result.add(venue);
                    }
                });
        return result;
    }

    private Venue toStall(String id, String campus, String schoolId, JsonNode payload, Map<String, CanteenPoint> canteens) {
        String canteen = text(payload, "canteen");
        CanteenPoint point = canteens.get(canteen);
        double lat = payload.path("lat").asDouble(point == null ? Double.NaN : point.lat());
        double lng = payload.path("lng").asDouble(point == null ? Double.NaN : point.lng());
        if (Double.isNaN(lat) || Double.isNaN(lng)) return null;
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("provider", "local");
        attrs.put("external", false);
        attrs.put("canteen", canteen);
        attrs.put("floor", text(payload, "floor"));
        attrs.put("walk", payload.path("walk").asInt(point == null ? 0 : point.walk()));
        attrs.put("checkins", payload.path("checkins").asInt(0));
        attrs.put("remain", payload.path("remain").asInt(0));
        attrs.put("spicy", text(payload, "spicy"));
        attrs.put("campus", campus == null ? text(payload, "campus") : campus);
        attrs.put("schoolId", schoolId == null ? text(payload, "schoolId") : schoolId);
        return new Venue("stall", id, text(payload, "name"), text(payload, "emoji"), text(payload, "img"),
                canteen + " " + text(payload, "floor"), payload.path("price").asInt(0), lat, lng,
                payload.path("rating").asDouble(0), strings(payload, "tags"), strings(payload, "dishes"), attrs);
    }

    private Venue toTakeout(String id, String campus, String schoolId, JsonNode payload) {
        double lat = payload.path("lat").asDouble(Double.NaN);
        double lng = payload.path("lng").asDouble(Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lng)) return null;
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("provider", "local");
        attrs.put("external", false);
        attrs.put("delivery", payload.path("delivery").asInt(30));
        attrs.put("night", payload.path("night").asBoolean(false));
        attrs.put("spicy", text(payload, "spicy"));
        attrs.put("campus", campus == null ? text(payload, "campus") : campus);
        attrs.put("schoolId", schoolId == null ? text(payload, "schoolId") : schoolId);
        return new Venue("takeout", id, text(payload, "name"), text(payload, "emoji"), text(payload, "img"),
                "外卖 · 约 " + payload.path("delivery").asInt(30) + " 分钟送达",
                payload.path("price").asInt(0), lat, lng, payload.path("rating").asDouble(0),
                strings(payload, "tags"), strings(payload, "dishes"), attrs);
    }

    private Map<String, CanteenPoint> loadCanteens() {
        Map<String, CanteenPoint> map = new LinkedHashMap<>();
        jdbc.query("SELECT payload_json FROM content_items WHERE type='CANTEEN'", rs -> {
            JsonNode payload = parse(rs.getString(1));
            String name = text(payload, "name");
            if (!name.isBlank()) {
                map.put(name, new CanteenPoint(payload.path("lat").asDouble(0), payload.path("lng").asDouble(0), payload.path("walk").asInt(0)));
            }
        });
        return map;
    }

    private boolean withinRadius(double lat, double lng, double targetLat, double targetLng, double radiusMeters) {
        return distance(lat, lng, targetLat, targetLng) <= radiusMeters;
    }

    private double distance(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private List<String> strings(JsonNode node, String field) {
        List<String> values = new ArrayList<>();
        node.path(field).forEach(item -> {
            String value = item.asText("");
            if (!value.isBlank()) values.add(value);
        });
        return values;
    }

    private record CanteenPoint(double lat, double lng, int walk) {}
}
