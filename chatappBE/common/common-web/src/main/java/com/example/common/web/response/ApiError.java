package com.example.common.web.response;

public class ApiError {

    private final String code;
    private final String message;
    private final Object details;

    public ApiError(String code, String message, Object details) {
        this.code = code;
        this.message = message;
        this.details = details;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Object getDetails() { return details; }
}
