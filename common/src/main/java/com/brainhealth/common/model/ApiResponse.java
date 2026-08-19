package com.brainhealth.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Standard API response wrapper.
 * All REST endpoints return this envelope.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;
    private String traceId;
    private long timestamp;

    public ApiResponse() {}

    public ApiResponse(int code, String message, T data, String traceId, long timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.timestamp = timestamp;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    // ---- Success factories ----

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data, null, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(200, message, data, null, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "created", data, null, System.currentTimeMillis());
    }

    // ---- Error factories ----

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, null, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> error(int code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId, System.currentTimeMillis());
    }

    // ---- Convenience ----

    public static <T> ApiResponse<T> badRequest(String message) {
        return error(400, message);
    }

    public static <T> ApiResponse<T> unauthorized(String message) {
        return error(401, message != null ? message : "Unauthorized");
    }

    public static <T> ApiResponse<T> forbidden(String message) {
        return error(403, message != null ? message : "Forbidden");
    }

    public static <T> ApiResponse<T> notFound(String message) {
        return error(404, message != null ? message : "Not found");
    }

    public static <T> ApiResponse<T> internalError(String message) {
        return error(500, message != null ? message : "Internal server error");
    }
}
