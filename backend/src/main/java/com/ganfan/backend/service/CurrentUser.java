package com.ganfan.backend.service;

import com.ganfan.backend.common.ApiException;
import com.ganfan.backend.security.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public AuthPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw ApiException.unauthorized("请先登录");
        }
        return principal;
    }

    public AuthPrincipal requireAdmin() {
        AuthPrincipal principal = require();
        if (!"ADMIN".equals(principal.role())) {
            throw ApiException.forbidden("仅管理员可执行此操作");
        }
        return principal;
    }
}
