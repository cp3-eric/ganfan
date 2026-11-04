package com.ganfan.backend.web;

import com.ganfan.backend.common.ApiResponse;
import com.ganfan.backend.dto.ApiDtos.AuthResult;
import com.ganfan.backend.dto.ApiDtos.LoginRequest;
import com.ganfan.backend.dto.ApiDtos.RegisterRequest;
import com.ganfan.backend.service.CurrentUser;
import com.ganfan.backend.service.GanfanService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final GanfanService service;
    private final CurrentUser currentUser;

    public AuthController(GanfanService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResult> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(service.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(service.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        var principal = currentUser.require();
        return ApiResponse.ok(service.me(service.loadUser(principal.id())));
    }
}
