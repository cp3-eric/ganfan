package com.ganfan.backend.feature.nearby;

import com.ganfan.backend.common.ApiResponse;
import com.ganfan.backend.feature.nearby.NearbyDtos.DiscoverRequest;
import com.ganfan.backend.feature.nearby.NearbyDtos.LocationRequest;
import com.ganfan.backend.service.CurrentUser;
import com.ganfan.backend.service.GanfanService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NearbyController {
    private final NearbyRecommendationService service;
    private final GanfanService ganfanService;
    private final CurrentUser currentUser;

    public NearbyController(NearbyRecommendationService service, GanfanService ganfanService, CurrentUser currentUser) {
        this.service = service;
        this.ganfanService = ganfanService;
        this.currentUser = currentUser;
    }

    @PutMapping("/me/location")
    public ApiResponse<Map<String, Object>> updateLocation(@Valid @RequestBody LocationRequest request) {
        return ApiResponse.ok(service.updateLocation(user(), request.lat(), request.lng(), request.accuracy(), request.source()));
    }

    @PostMapping("/nearby/discover")
    public ApiResponse<Map<String, Object>> discover(@Valid @RequestBody DiscoverRequest request) {
        return ApiResponse.ok(service.discover(user(), request.lat(), request.lng(), request.accuracy(), request.source(),
                request.radiusMeters(), request.limit(), request.seed(), request.excludeIds(), true));
    }

    @GetMapping("/nearby/venues")
    public ApiResponse<Map<String, Object>> venues(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Double radiusMeters,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(service.nearby(user(), lat, lng, radiusMeters, limit, type));
    }

    @GetMapping("/nearby/recommendations")
    public ApiResponse<Map<String, Object>> recommendations(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) Double radiusMeters,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(service.recommend(user(), lat, lng, radiusMeters, limit, type));
    }

    private GanfanService.UserRow user() {
        return ganfanService.loadUser(currentUser.require().id());
    }
}
