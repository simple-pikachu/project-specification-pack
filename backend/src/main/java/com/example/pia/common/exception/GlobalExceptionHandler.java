package com.example.pia.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 *
 * <p>统一将各类异常转换为标准 JSON 错误响应，避免每个 Controller 都写 try-catch。
 * {@code @RestControllerAdvice} 作用于所有 {@code @RestController}。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：可预期的错误（如项目不存在），返回 4xx */
    @ExceptionHandler(PiaException.class)
    public ResponseEntity<ErrorResponse> handlePiaException(PiaException ex) {
        log.warn("业务异常 [{}]: {}", ex.getErrorCode(), ex.getMessage());
        HttpStatus status = switch (ex.getErrorCode()) {
            case "PROJECT_NOT_FOUND", "RUN_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "PATH_TRAVERSAL" -> HttpStatus.FORBIDDEN;
            case "INDEX_NOT_READY" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    /** 参数校验失败：@Valid 注解触发，返回 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
            errors.put(field, err.getDefaultMessage());
        });
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_FAILED", "请求参数校验失败", errors));
    }

    /** 兜底：未预期的系统异常，返回 500，隐藏内部细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.internalServerError()
            .body(new ErrorResponse("INTERNAL_ERROR", "服务器内部错误，请联系管理员"));
    }

    /** 标准错误响应体 */
    public record ErrorResponse(
        String code,
        String message,
        Object details,
        LocalDateTime timestamp
    ) {
        ErrorResponse(String code, String message) {
            this(code, message, null, LocalDateTime.now());
        }

        ErrorResponse(String code, String message, Object details) {
            this(code, message, details, LocalDateTime.now());
        }
    }
}
