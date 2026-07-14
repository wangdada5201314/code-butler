package com.agent.codebutler.handler;

import com.agent.codebutler.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.StringJoiner;
import java.util.concurrent.TimeoutException;

/**
 * 全局异常处理器
 * 统一捕获 Controller 层异常，返回标准 ApiResponse
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- 400 类 ----

    /** 参数校验失败（@RequestBody @Valid） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        StringJoiner errors = new StringJoiner("; ");
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errors.add(fe.getField() + ": " + fe.getDefaultMessage()));
        log.warn("请求参数校验失败: {}", errors);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "参数校验失败: " + errors));
    }

    /** 参数校验失败（@Validated on class） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        StringJoiner errors = new StringJoiner("; ");
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            errors.add(cv.getPropertyPath() + ": " + cv.getMessage());
        }
        log.warn("约束校验失败: {}", errors);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "参数校验失败: " + errors));
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("缺少必填参数: {}", ex.getParameterName());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "缺少必填参数: " + ex.getParameterName()));
    }

    /** 请求参数类型不匹配 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("参数类型不匹配: {} = {}", ex.getName(), ex.getValue());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "参数类型不匹配: " + ex.getName()));
    }

    /** 请求体解析失败 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "请求体格式错误"));
    }

    // ---- 400 业务类 ----

    /** 非法参数（如路径校验失败） */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("非法参数: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    /** 非法状态（如 GitHub Token 未配置、服务未就绪等业务前置条件不满足） */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("非法状态: {}", ex.getMessage());
        return ResponseEntity.unprocessableEntity()
                .body(ApiResponse.error(422, ex.getMessage()));
    }

    /** 业务异常 — 根据语义错误码映射 HTTP 状态 */
    @ExceptionHandler(com.agent.codebutler.exception.BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            com.agent.codebutler.exception.BusinessException ex) {
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        HttpStatus httpStatus = mapToHttpStatus(ex.getCode());
        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 将业务错误码映射到 HTTP 状态码
     * <p>
     * 401xx → 401 Unauthorized, 403xx → 403 Forbidden,
     * 404xx → 404 Not Found, 500xx → 500 Internal Server Error,
     * 其余 → 400 Bad Request
     */
    private HttpStatus mapToHttpStatus(int code) {
        if (code >= 40100 && code < 40200) return HttpStatus.UNAUTHORIZED;
        if (code >= 40300 && code < 40400) return HttpStatus.FORBIDDEN;
        if (code >= 40400 && code < 40500) return HttpStatus.NOT_FOUND;
        if (code >= 50000 && code < 60000) return HttpStatus.INTERNAL_SERVER_ERROR;
        return HttpStatus.BAD_REQUEST;
    }

    // ---- 408 类 ----

    /** 操作超时 */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleTimeout(TimeoutException ex) {
        log.error("操作超时", ex);
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                .body(ApiResponse.error(408, "操作超时，请稍后重试"));
    }

    // ---- 404 类 ----

    /** 静态资源不存在（如 favicon.ico），静默返回 404 */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return ResponseEntity.notFound().build();
    }

    // ---- 500 兜底 ----

    /** 未预期的异常（不对外暴露异常细节，防止信息泄漏） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("未预期异常: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误，请联系管理员"));
    }
}
