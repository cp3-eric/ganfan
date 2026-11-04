package com.ganfan.backend.web;

import com.ganfan.backend.common.ApiResponse;
import com.ganfan.backend.dto.ApiDtos.ApplicationRequest;
import com.ganfan.backend.dto.ApiDtos.CommentRequest;
import com.ganfan.backend.dto.ApiDtos.GreetRequest;
import com.ganfan.backend.dto.ApiDtos.MeetupRequest;
import com.ganfan.backend.dto.ApiDtos.PostRequest;
import com.ganfan.backend.dto.ApiDtos.PreferenceRequest;
import com.ganfan.backend.dto.ApiDtos.ProfileRequest;
import com.ganfan.backend.service.CurrentUser;
import com.ganfan.backend.service.GanfanService;
import com.ganfan.backend.service.GanfanService.UserRow;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final GanfanService service;
    private final CurrentUser currentUser;
    private final JdbcTemplate jdbc;

    public ApiController(GanfanService service, CurrentUser currentUser, JdbcTemplate jdbc) {
        this.service = service;
        this.currentUser = currentUser;
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Integer db = jdbc.queryForObject("SELECT 1", Integer.class);
        return ApiResponse.ok(Map.of("service", "ganfan-backend", "status", "UP", "db", db == null ? 0 : db));
    }

    @GetMapping("/bootstrap")
    public ApiResponse<Map<String, Object>> bootstrap() {
        return ApiResponse.ok(service.bootstrap(user()));
    }

    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(@Valid @RequestBody ProfileRequest request) {
        return ApiResponse.ok(service.updateProfile(user(), request));
    }

    @PutMapping("/me/preferences")
    public ApiResponse<Map<String, Object>> preferences(@Valid @RequestBody PreferenceRequest request) {
        return ApiResponse.ok(service.selectSchool(user(), request));
    }

    @PostMapping("/favorites/stalls/{id}")
    public ApiResponse<Map<String, Object>> favoriteStall(@PathVariable String id) {
        return ApiResponse.ok(service.favoriteStall(user(), id, true));
    }

    @DeleteMapping("/favorites/stalls/{id}")
    public ApiResponse<Map<String, Object>> unfavoriteStall(@PathVariable String id) {
        return ApiResponse.ok(service.favoriteStall(user(), id, false));
    }

    @PostMapping("/favorites/posts/{id}")
    public ApiResponse<Map<String, Object>> collectPost(@PathVariable String id) {
        return ApiResponse.ok(service.collectPost(user(), id, true));
    }

    @DeleteMapping("/favorites/posts/{id}")
    public ApiResponse<Map<String, Object>> uncollectPost(@PathVariable String id) {
        return ApiResponse.ok(service.collectPost(user(), id, false));
    }

    @PostMapping("/posts/{id}/like")
    public ApiResponse<Map<String, Object>> likePost(@PathVariable String id) {
        return ApiResponse.ok(service.likePost(user(), id, true));
    }

    @DeleteMapping("/posts/{id}/like")
    public ApiResponse<Map<String, Object>> unlikePost(@PathVariable String id) {
        return ApiResponse.ok(service.likePost(user(), id, false));
    }

    @PostMapping("/meetups/{id}/join")
    public ApiResponse<Map<String, Object>> join(@PathVariable String id) {
        return ApiResponse.ok(service.joinMeetup(user(), id, true));
    }

    @DeleteMapping("/meetups/{id}/join")
    public ApiResponse<Map<String, Object>> leave(@PathVariable String id) {
        return ApiResponse.ok(service.joinMeetup(user(), id, false));
    }

    @PostMapping("/meetups/{id}/greetings")
    public ApiResponse<Map<String, Object>> greet(@PathVariable String id, @Valid @RequestBody GreetRequest request) {
        return ApiResponse.ok(service.greet(user(), id, request));
    }

    @PostMapping("/meetups")
    public ApiResponse<Map<String, Object>> createMeetup(@Valid @RequestBody MeetupRequest request) {
        return ApiResponse.ok(service.createMeetup(user(), request));
    }

    @PostMapping("/posts")
    public ApiResponse<Map<String, Object>> createPost(@Valid @RequestBody PostRequest request) {
        return ApiResponse.ok(service.createPost(user(), request));
    }

    @PostMapping("/posts/{id}/comments")
    public ApiResponse<Map<String, Object>> comment(@PathVariable String id, @Valid @RequestBody CommentRequest request) {
        return ApiResponse.ok(service.comment(user(), id, request));
    }

    @PostMapping("/school-applications")
    public ApiResponse<Map<String, Object>> application(@Valid @RequestBody ApplicationRequest request) {
        return ApiResponse.ok(service.applySchool(user(), request));
    }

    @PostMapping("/takeouts/{id}/orders")
    public ApiResponse<Map<String, Object>> order(@PathVariable String id) {
        return ApiResponse.ok(service.orderTakeout(user(), id));
    }

    @DeleteMapping("/me/demo-data")
    public ApiResponse<Map<String, Object>> resetDemo() {
        return ApiResponse.ok(service.resetDemo(user()));
    }

    private UserRow user() {
        return service.loadUser(currentUser.require().id());
    }
}
