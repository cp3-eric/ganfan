package com.ganfan.backend.security;

public record AuthPrincipal(Long id, String email, String role) {}
