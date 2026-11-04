package com.ganfan.backend.common;

import java.util.Map;

public class ApiException extends RuntimeException {
    private final int status;
    private final int code;
    private final Map<String, String> errors;

    public ApiException(int status, int code, String message) {
        this(status, code, message, null);
    }

    public ApiException(int status, int code, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors;
    }

    public int getStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, 40001, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, 40101, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, 40301, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, 40401, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, 40901, message);
    }
}
