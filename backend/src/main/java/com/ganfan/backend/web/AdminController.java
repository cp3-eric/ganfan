package com.ganfan.backend.web;

import com.ganfan.backend.common.ApiResponse;
import com.ganfan.backend.dto.ApiDtos.AdminStatusRequest;
import com.ganfan.backend.service.CurrentUser;
import com.ganfan.backend.service.GanfanService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final GanfanService service;
    private final CurrentUser currentUser;

    public AdminController(GanfanService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/applications")
    public ApiResponse<List<Map<String, Object>>> applications() {
        currentUser.requireAdmin();
        return ApiResponse.ok(service.adminApplications());
    }

    @PatchMapping("/applications/{id}")
    public ApiResponse<Map<String, Object>> updateApplication(
            @PathVariable String id,
            @Valid @RequestBody AdminStatusRequest request) {
        currentUser.requireAdmin();
        return ApiResponse.ok(service.updateApplication(currentUser.require().id(), id, request.status()));
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users() {
        currentUser.requireAdmin();
        return ApiResponse.ok(service.adminUsers());
    }
}
