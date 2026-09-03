package com.pengyuan.pims.common;

import java.util.List;
import java.util.Map;

/**
 * 期初导入校验失败异常：携带全部错误行（行号+原因），由控制器转成 400 + 错误清单响应。
 */
public class OpeningImportException extends RuntimeException {

    private final List<Map<String, Object>> errors;

    public OpeningImportException(String message, List<Map<String, Object>> errors) {
        super(message);
        this.errors = errors;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }
}
