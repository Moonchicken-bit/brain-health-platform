package com.brainhealth.common.exception;

import com.brainhealth.common.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Global exception handler. Returns ApiResponse for all errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = Logger.getLogger(GlobalExceptionHandler.class.getName());

    private String traceId(HttpServletRequest request) {
        return request.getHeader("X-Trace-Id") != null
                ? request.getHeader("X-Trace-Id")
                : UUID.randomUUID().toString().substring(0, 8);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e, HttpServletRequest request) {
        log.warning("[" + traceId(request) + "] Business error (" + e.getCode() + "): " + e.getMessage());
        int httpStatus = mapToHttpStatus(e.getCode());
        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.error(e.getCode(), e.getMessage(), traceId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warning("[" + traceId(request) + "] Validation error: " + details);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.getCode(), details, traceId(request)));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException e, HttpServletRequest request) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warning("[" + traceId(request) + "] Bind error: " + details);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.getCode(), details, traceId(request)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        log.warning("[" + traceId(request) + "] Constraint violation: " + e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED.getCode(), e.getMessage(), traceId(request)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(),
                        "缺少必要参数: " + e.getParameterName(), traceId(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        log.warning("[" + traceId(request) + "] Illegal argument: " + e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage(), traceId(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        log.warning("[" + traceId(request) + "] Access denied: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.FORBIDDEN.getCode(),
                        ErrorCode.FORBIDDEN.getMessage(), traceId(request)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(
            ResponseStatusException e, HttpServletRequest request) {
        int status = e.getStatusCode().value();
        int code = status == 401 ? ErrorCode.UNAUTHORIZED.getCode()
                : status == 403 ? ErrorCode.FORBIDDEN.getCode()
                : ErrorCode.BAD_REQUEST.getCode();
        String message = e.getReason() != null ? e.getReason() : e.getMessage();
        log.warning("[" + traceId(request) + "] HTTP " + status + ": " + message);
        return ResponseEntity.status(status)
                .body(ApiResponse.error(code, message, traceId(request)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileTooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        log.warning("[" + traceId(request) + "] File too large");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE.getCode(),
                        ErrorCode.FILE_TOO_LARGE.getMessage(), traceId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e, HttpServletRequest request) {
        log.severe("[" + traceId(request) + "] Unexpected error: " + e.getMessage());
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getMessage(), traceId(request)));
    }

    private int mapToHttpStatus(int errorCode) {
        if (errorCode >= 20001 && errorCode <= 20099) {
            return errorCode == 20001 ? 401 : 403;
        }
        return switch (errorCode / 1000) {
            case 30 -> 404;
            case 40 -> 404;
            case 50 -> 404;
            case 60 -> 404;
            case 70 -> 404;
            default -> 400;
        };
    }
}
