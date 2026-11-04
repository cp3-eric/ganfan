package com.ganfan.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ApiDtos {
    private ApiDtos() {}

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 20) String name) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(max = 72) String password) {}

    public record AuthUser(Long id, String email, String name, String role, String schoolId) {}

    public record AuthResult(String token, AuthUser user) {}

    public record ProfileRequest(
            @NotBlank @Size(max = 20) String name,
            @NotBlank @Size(max = 10) String spicy,
            @Min(5) @Max(120) int budgetMin,
            @Min(5) @Max(120) int budgetMax,
            @NotBlank @Size(max = 20) String sleep,
            @NotBlank @Size(max = 20) String social,
            @Size(max = 20) List<String> dislikes) {}

    public record PreferenceRequest(@NotBlank @Size(max = 80) String schoolId) {}

    public record MeetupRequest(
            @NotBlank @Size(max = 20) String dish,
            @NotBlank @Size(max = 30) String canteen,
            @NotBlank @Size(max = 20) String when,
            @NotBlank @Size(max = 10) String time,
            @Min(1) @Max(3) int want,
            @Min(5) @Max(100) int budget,
            @Size(max = 10) List<String> tags,
            @Size(max = 80) String note) {}

    public record PostRequest(
            @NotBlank @Size(max = 120) String text,
            @NotBlank @Size(max = 30) String canteen,
            @NotBlank @Size(max = 30) String place,
            @Min(1) @Max(100) int price,
            @Size(max = 10) List<String> tags) {}

    public record CommentRequest(@NotBlank @Size(max = 80) String text) {}

    public record GreetRequest(@NotBlank @Size(max = 80) String message) {}

    public record ApplicationRequest(
            @NotBlank @Size(max = 30) String university,
            @NotBlank @Size(max = 20) String campus,
            @NotBlank @Size(max = 20) String city,
            @NotBlank @Size(max = 20) String contact,
            @NotBlank @Size(max = 20) String phone,
            @Size(max = 40) String email,
            @Size(max = 20) String canteens,
            @Size(max = 120) String note) {}

    public record AdminStatusRequest(@NotBlank String status) {}
}
