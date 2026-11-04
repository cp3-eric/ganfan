package com.ganfan.backend.feature.nearby;

import java.util.List;
import java.util.Map;

public interface VenueProvider {
    record Venue(
            String type,
            String id,
            String name,
            String emoji,
            String img,
            String place,
            int price,
            double lat,
            double lng,
            double rating,
            List<String> tags,
            List<String> dishes,
            Map<String, Object> attributes) {}

    List<Venue> findCandidates(double lat, double lng, double radiusMeters);
}
