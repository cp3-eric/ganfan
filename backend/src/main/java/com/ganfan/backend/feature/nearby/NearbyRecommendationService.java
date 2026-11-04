package com.ganfan.backend.feature.nearby;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganfan.backend.common.ApiException;
import com.ganfan.backend.feature.nearby.VenueProvider.Venue;
import com.ganfan.backend.service.GanfanService.UserRow;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NearbyRecommendationService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final double DEFAULT_RADIUS = 2500d;
    private static final int DEFAULT_LIMIT = 12;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final List<VenueProvider> venueProviders;

    public NearbyRecommendationService(JdbcTemplate jdbc, ObjectMapper objectMapper, List<VenueProvider> venueProviders) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.venueProviders = venueProviders;
    }

    @Transactional
    public Map<String, Object> updateLocation(UserRow user, double lat, double lng, Integer accuracy, String source) {
        validateCoordinate(lat, lng);
        jdbc.update("DELETE FROM user_locations WHERE user_id=?", user.id());
        jdbc.update("INSERT INTO user_locations(id, user_id, school_id, lat, lng, accuracy, source, updated_at) VALUES(?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), user.id(), user.schoolId(), lat, lng,
                accuracy == null ? 0 : accuracy, normalizeSource(source), Instant.now());
        Map<String, Object> context = context(user, lat, lng, normalizeSource(source));
        return Map.of("location", locationMap(lat, lng, accuracy, normalizeSource(source)), "context", context);
    }

    public Map<String, Object> discover(UserRow user, double lat, double lng, Integer accuracy, String source,
                                        Double radiusMeters, Integer limit, String seed, List<String> excludeIds, boolean persist) {
        validateCoordinate(lat, lng);
        if (persist) updateLocation(user, lat, lng, accuracy, source);
        double radius = normalizeRadius(radiusMeters);
        int size = normalizeLimit(limit);
        Map<String, Object> context = context(user, lat, lng, normalizeSource(source));
        HistorySignals history = history(user);
        Set<String> excluded = normalizeExcludeIds(excludeIds);
        List<Map<String, Object>> recommendations = rank(user, history, lat, lng, radius, size, null, seed, excluded);
        List<Map<String, Object>> venues = new ArrayList<>(recommendations);
        venues.sort(Comparator.comparingDouble(item -> ((Number) item.get("distance")).doubleValue()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("location", locationMap(lat, lng, accuracy, normalizeSource(source)));
        result.put("context", context);
        result.put("recommendations", recommendations);
        result.put("venues", venues);
        result.put("batch", seed == null ? "0" : seed);
        result.put("excludedCount", excluded.size());
        result.put("hasMore", true);
        return result;
    }

    public Map<String, Object> nearby(UserRow user, double lat, double lng, Double radiusMeters, Integer limit, String type) {
        validateCoordinate(lat, lng);
        double radius = normalizeRadius(radiusMeters);
        int size = normalizeLimit(limit);
        HistorySignals history = history(user);
        List<Map<String, Object>> venues = rank(user, history, lat, lng, radius, size, normalizeType(type), null, Set.of());
        venues.sort(Comparator.comparingDouble(item -> ((Number) item.get("distance")).doubleValue()));
        return Map.of(
                "location", locationMap(lat, lng, null, "query"),
                "context", context(user, lat, lng, "query"),
                "venues", venues
        );
    }

    public Map<String, Object> recommend(UserRow user, double lat, double lng, Double radiusMeters, Integer limit, String type) {
        validateCoordinate(lat, lng);
        double radius = normalizeRadius(radiusMeters);
        int size = normalizeLimit(limit);
        HistorySignals history = history(user);
        return Map.of(
                "location", locationMap(lat, lng, null, "query"),
                "context", context(user, lat, lng, "query"),
                "recommendations", rank(user, history, lat, lng, radius, size, normalizeType(type), null, Set.of())
        );
    }

    private List<Venue> candidateVenues(double lat, double lng, double radius) {
        Map<String, Venue> merged = new LinkedHashMap<>();
        for (VenueProvider provider : venueProviders) {
            for (Venue venue : provider.findCandidates(lat, lng, radius)) {
                String key = venue.type() + "|" + venue.name().toLowerCase(Locale.ROOT) + "|"
                        + Math.round(venue.lat() * 1000) + "|" + Math.round(venue.lng() * 1000);
                merged.putIfAbsent(key, venue);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<Map<String, Object>> rank(UserRow user, HistorySignals history, double lat, double lng,
                                           double radius, int limit, String typeFilter, String seed, Set<String> excludeIds) {
        Profile profile = profile(user.profileJson());
        LocalTime now = LocalTime.now(ZONE);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Venue venue : candidateVenues(lat, lng, radius)) {
            if (typeFilter != null && !typeFilter.equals(venue.type())) continue;
            double distance = haversine(lat, lng, venue.lat(), venue.lng());
            Score score = score(venue, distance, radius, profile, history, now);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", venue.type());
            item.put("id", venue.id());
            item.put("name", venue.name());
            item.put("emoji", venue.emoji());
            item.put("img", venue.img());
            item.put("place", venue.place());
            item.put("price", venue.price());
            item.put("rating", venue.rating());
            item.put("distance", Math.round(distance));
            item.put("km", distance / 1000d);
            item.put("score", score.total());
            item.put("rank", score.total());
            item.put("reasons", score.reasons());
            item.put("scoreParts", score.parts());
            item.put("tags", venue.tags());
            item.put("dishes", venue.dishes());
            item.put("attributes", venue.attributes());
            item.put("source", sourceOf(venue));
            items.add(item);
        }
        items.sort(Comparator.comparingDouble(item -> -((Number) item.get("score")).doubleValue()));
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> item : items) {
            if (!excludeIds.contains(String.valueOf(item.get("id")))) filtered.add(item);
        }
        // 本地/外部数据不足时允许少量重叠，保证刷新后仍有新一波内容可展示
        if (filtered.isEmpty() && !excludeIds.isEmpty()) filtered = items;
        if (seed != null && !seed.isBlank()) {
            filtered.sort(Comparator.comparingDouble(item -> {
                String id = String.valueOf(item.get("id"));
                return -(((Number) item.get("score")).doubleValue() + batchJitter(id, seed));
            }));
        }
        return filtered.size() > limit ? new ArrayList<>(filtered.subList(0, limit)) : filtered;
    }

    private double batchJitter(String id, String seed) {
        int hash = (id + "|" + seed).hashCode();
        return (Math.abs(hash % 1000)) / 1000d * 8d;
    }

    private Set<String> normalizeExcludeIds(List<String> excludeIds) {
        if (excludeIds == null || excludeIds.isEmpty()) return Set.of();
        Set<String> values = new HashSet<>();
        for (String id : excludeIds) {
            if (id != null && !id.isBlank()) values.add(id.trim());
        }
        return values;
    }

    private Score score(Venue venue, double distance, double radius, Profile profile, HistorySignals history, LocalTime now) {
        double distanceScore = clamp(100d - (distance / radius) * 85d, 10d, 100d);
        double preferenceScore = preferenceScore(venue, profile);
        double historyScore = historyScore(venue, history);
        double qualityScore = qualityScore(venue);
        double contextScore = contextScore(venue, profile, now);
        int total = (int) Math.round(clamp(
                distanceScore * 0.32d + preferenceScore * 0.28d + historyScore * 0.20d
                        + qualityScore * 0.10d + contextScore * 0.10d, 1d, 99d));

        List<String> reasons = new ArrayList<>();
        if (distance <= 800) reasons.add("距你约 " + Math.round(distance) + "m");
        else reasons.add("步行范围约 " + Math.round(distance) + "m");
        if (venue.price() <= 0) {
            reasons.add("地图商户，人均以到店为准");
        } else if (venue.price() >= profile.budgetMin && venue.price() <= profile.budgetMax) {
            reasons.add("人均 " + venue.price() + " 元在预算内");
        } else if (venue.price() > profile.budgetMax) {
            reasons.add("人均 " + venue.price() + " 元，略高于预算");
        } else {
            reasons.add("人均 " + venue.price() + " 元，价格友好");
        }
        if (historyScore >= 80) reasons.add("与你的历史口味/收藏相近");
        else if (preferenceScore >= 85) reasons.add("辣度和口味偏好匹配");
        if (contextScore >= 85) reasons.add("适合当前时段");
        if (venue.rating() >= 4.7) reasons.add("评分 " + String.format(Locale.ROOT, "%.1f", venue.rating()));
        if ("takeout".equals(venue.type()) && number(venue.attributes().get("delivery")) <= 22) reasons.add("配送较快");

        Map<String, Integer> parts = new LinkedHashMap<>();
        parts.put("distance", (int) Math.round(distanceScore));
        parts.put("preference", (int) Math.round(preferenceScore));
        parts.put("history", (int) Math.round(historyScore));
        parts.put("quality", (int) Math.round(qualityScore));
        parts.put("context", (int) Math.round(contextScore));
        return new Score(total, reasons.subList(0, Math.min(3, reasons.size())), parts);
    }

    /**
     * 质量分。地图 POI 常常没有评分或打卡数，此时不应按「最差」计分，
     * 否则外部数据会被本地档口完全压制，改为中性基准分。
     */
    private double qualityScore(Venue venue) {
        double rating = venue.rating();
        double checkins = number(venue.attributes().get("checkins"));
        if (rating <= 0 && checkins <= 0) return 60d;
        return clamp(rating * 16d + Math.min(20d, checkins / 12d), 25d, 100d);
    }

    private double preferenceScore(Venue venue, Profile profile) {
        double score = 70d;
        if (venue.price() <= 0) {
            // 地图 POI 通常没有人均价格，不参与预算加减分。
            score += 8d;
        } else if (venue.price() >= profile.budgetMin && venue.price() <= profile.budgetMax) score += 20d;
        else if (venue.price() < profile.budgetMin) score += 12d;
        else score -= Math.min(35d, (venue.price() - profile.budgetMax) * 2.5d);

        String pool = (String.join(" ", venue.tags()) + " " + String.join(" ", venue.dishes())).toLowerCase(Locale.ROOT);
        for (String dislike : profile.dislikes) {
            if (!dislike.isBlank() && pool.contains(dislike.toLowerCase(Locale.ROOT))) score -= 35d;
        }
        String spicy = String.valueOf(venue.attributes().getOrDefault("spicy", ""));
        if (spicy.equals(profile.spicy)) score += 12d;
        else if (spicy.equals("不辣") || spicy.equals("微辣")) score += 5d;
        if (profile.haunts.contains(venue.place())) score += 12d;
        return clamp(score, 5d, 100d);
    }

    private double historyScore(Venue venue, HistorySignals history) {
        if (history.interactedIds.contains(venue.id())) return 100d;
        double score = 45d;
        String canteen = String.valueOf(venue.attributes().getOrDefault("canteen", venue.place()));
        if (history.canteens.contains(canteen)) score += 25d;
        for (String dish : venue.dishes()) if (history.dishes.contains(dish)) score += 20d;
        for (String tag : venue.tags()) if (history.tags.contains(tag)) score += 12d;
        if (history.orderedTakeouts.contains(venue.id())) score += 35d;
        return clamp(score, 20d, 100d);
    }

    private double contextScore(Venue venue, Profile profile, LocalTime now) {
        double score = 65d;
        boolean nightVenue = Boolean.TRUE.equals(venue.attributes().get("night"))
                || venue.tags().contains("夜宵") || venue.tags().contains("夜宵营业");
        boolean breakfast = venue.tags().contains("早餐") || String.join(" ", venue.dishes()).contains("豆浆");
        if (now.getHour() >= 21 || now.getHour() < 5) score += nightVenue ? 28 : (profile.sleep.contains("夜猫子") ? 10 : -8);
        if (now.getHour() < 10) score += breakfast ? 28 : -4;
        if ("takeout".equals(venue.type()) && number(venue.attributes().get("delivery")) <= 22) score += 8d;
        return clamp(score, 20d, 100d);
    }

    private HistorySignals history(UserRow user) {
        Set<String> ids = new HashSet<>();
        Set<String> canteens = new HashSet<>();
        Set<String> dishes = new HashSet<>();
        Set<String> tags = new HashSet<>();
        Set<String> ordered = new HashSet<>();
        jdbc.query("SELECT target_id FROM user_actions WHERE user_id=?", (RowCallbackHandler) rs -> ids.add(rs.getString(1)), user.id());
        jdbc.query("SELECT takeout_id FROM takeout_orders WHERE user_id=?", (RowCallbackHandler) rs -> {
            ids.add(rs.getString(1));
            ordered.add(rs.getString(1));
        }, user.id());
        jdbc.query("SELECT type, payload_json FROM content_items WHERE owner_id=? AND type IN ('MEETUP','POST')", (RowCallbackHandler) rs -> {
            JsonNode payload = parse(rs.getString(2));
            addIfPresent(canteens, payload.path("canteen").asText(""));
            addIfPresent(dishes, payload.path("dish").asText(""));
            payload.path("tags").forEach(tag -> addIfPresent(tags, tag.asText("")));
            payload.path("dishes").forEach(dish -> addIfPresent(dishes, dish.asText("")));
        }, user.id());
        return new HistorySignals(ids, canteens, dishes, tags, ordered);
    }

    private Profile profile(String json) {
        JsonNode node = parse(json);
        List<String> dislikes = new ArrayList<>();
        node.path("dislikes").forEach(item -> addIfPresent(dislikes, item.asText("")));
        List<String> haunts = new ArrayList<>();
        node.path("haunts").forEach(item -> addIfPresent(haunts, item.asText("")));
        JsonNode budget = node.path("budget");
        int min = budget.isArray() && budget.size() > 0 ? budget.get(0).asInt(15) : 15;
        int max = budget.isArray() && budget.size() > 1 ? budget.get(1).asInt(25) : 25;
        return new Profile(min, max, node.path("spicy").asText("微辣"), node.path("sleep").asText("规律"),
                dislikes, haunts);
    }

    private Map<String, Object> context(UserRow user, double lat, double lng, String source) {
        Map<String, Object> nearest = nearestSchool(lat, lng);
        Map<String, Object> current = findSchool(user.schoolId());
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("source", source);
        context.put("currentSchoolId", user.schoolId());
        context.put("currentCampus", current == null ? "" : current.getOrDefault("campus", ""));
        context.put("nearestSchoolId", nearest.get("id"));
        context.put("nearestCampus", nearest.get("campus"));
        context.put("distanceToNearestSchool", nearest.get("distance"));
        context.put("shouldSwitchSchool", nearest.get("id") != null && !nearest.get("id").equals(user.schoolId())
                && ((Number) nearest.get("distance")).doubleValue() <= 5000d);
        return context;
    }

    private Map<String, Object> nearestSchool(double lat, double lng) {
        Map<String, Object> best = new LinkedHashMap<>();
        best.put("id", null);
        best.put("campus", "");
        best.put("distance", Integer.MAX_VALUE);
        List<Map<String, Object>> schools = new ArrayList<>();
        jdbc.query("SELECT payload_json FROM content_items WHERE type='SCHOOL'", rs -> {
            JsonNode payload = parse(rs.getString(1));
            if (payload.path("status").asText("").equals("prep")) return;
            if (!payload.has("lat") || !payload.has("lng")) return;
            Map<String, Object> school = new LinkedHashMap<>();
            school.put("id", payload.path("id").asText(""));
            school.put("campus", payload.path("campus").asText(""));
            school.put("distance", (int) Math.round(haversine(lat, lng, payload.path("lat").asDouble(), payload.path("lng").asDouble())));
            schools.add(school);
        });
        for (Map<String, Object> school : schools) {
            if (((Number) school.get("distance")).intValue() < ((Number) best.get("distance")).intValue()) best = school;
        }
        return best;
    }

    private Map<String, Object> findSchool(String schoolId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.query("SELECT payload_json FROM content_items WHERE type='SCHOOL' AND content_id=?", rs -> {
            JsonNode payload = parse(rs.getString(1));
            Map<String, Object> school = new LinkedHashMap<>();
            school.put("id", payload.path("id").asText(schoolId));
            school.put("campus", payload.path("campus").asText(""));
            rows.add(school);
        }, schoolId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> locationMap(double lat, double lng, Integer accuracy, String source) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lat", lat);
        map.put("lng", lng);
        map.put("accuracy", accuracy == null ? 0 : accuracy);
        map.put("source", source);
        return map;
    }

    private void validateCoordinate(double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw ApiException.badRequest("经纬度不合法");
        }
    }

    private double normalizeRadius(Double radius) {
        if (radius == null) return DEFAULT_RADIUS;
        if (radius < 100 || radius > 20000) throw ApiException.badRequest("搜索半径需在 100 至 20000 米之间");
        return radius;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) return DEFAULT_LIMIT;
        if (limit < 1 || limit > 50) throw ApiException.badRequest("返回数量需在 1 至 50 之间");
        return limit;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank() || type.equals("all")) return null;
        if (type.equals("dining") || type.equals("stall")) return "stall";
        if (type.equals("takeout")) return "takeout";
        throw ApiException.badRequest("类型仅支持 dining、takeout 或 all");
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) return "manual";
        String value = source.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "browser", "school", "manual", "search" -> value;
            default -> "manual";
        };
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private void addIfPresent(Collection<String> target, String value) {
        if (value != null && !value.isBlank()) target.add(value);
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0d;
    }

    /**
     * 标记数据来源，便于前端区分本地档口与地图商户。
     */
    private String sourceOf(Venue venue) {
        Object provider = venue.attributes().get("provider");
        if (provider == null || provider.toString().isBlank()) return "local-catalog";
        return switch (provider.toString()) {
            case "amap" -> "amap-poi";
            case "tencent" -> "tencent-poi";
            case "external" -> "external-gateway";
            default -> "local-catalog";
        };
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Profile(int budgetMin, int budgetMax, String spicy, String sleep, List<String> dislikes, List<String> haunts) {}

    private record HistorySignals(Set<String> interactedIds, Set<String> canteens, Set<String> dishes,
                                  Set<String> tags, Set<String> orderedTakeouts) {}

    private record Score(int total, List<String> reasons, Map<String, Integer> parts) {}
}
