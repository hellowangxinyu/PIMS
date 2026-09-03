package com.pengyuan.pims.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.pengyuan.pims.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<?> handleNotLogin(NotLoginException e) {
        return Result.fail(401, "请先登录");
    }

    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleNotPermission(NotPermissionException e) {
        return Result.fail(403, "无权限访问");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleIllegalArgument(IllegalArgumentException e) {
        return Result.fail(400, e.getMessage());
    }

    /** v6.1.7：业务拒绝已改用 IllegalArgument 透出；IllegalState 剩框架内部状态异常——
     *  通用 400 文案不透 message（连接池/驱动等内部消息不外泄），细节仅服务端日志 */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleIllegalState(IllegalStateException e) {
        log.warn("IllegalState: {}", e.getMessage());
        return Result.fail(400, "当前状态不支持该操作");
    }

    /** 请求体格式错误（JSON 解析失败/类型不匹配）返回 400 而非 500 */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleNotReadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
        return Result.fail(400, "请求体格式错误，请检查发送的数据");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return Result.fail(400, msg);
    }

    /** 静态资源缺失（如前端更新后旧 chunk 文件已被清理）返回 404 而非 500，配合前端 router.onError 自动刷新 */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return Result.fail(404, "资源不存在: " + e.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统异常，请稍后重试或联系管理员");   // v6.1.4：不回传 e.getMessage()（防内部信息泄露）
    }
}
