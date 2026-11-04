package com.ganfan.backend.feature.nearby;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class NearbyDtos {
    private NearbyDtos() {}

    public record LocationRequest(
            @DecimalMin("-90") @DecimalMax("90") double lat,
            @DecimalMin("-180") @DecimalMax("180") double lng,
            @Min(0) @Max(50000) Integer accuracy,
            @NotBlank String source) {}

    public record DiscoverRequest(
            @DecimalMin("-90") @DecimalMax("90") double lat,
            @DecimalMin("-180") @DecimalMax("180") double lng,
            @Min(0) @Max(50000) Integer accuracy,
            String source,
            Double radiusMeters,
            Integer limit,
            String seed,
            List<String> excludeIds) {}
}
