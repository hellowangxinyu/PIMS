package com.pengyuan.pims.common;

import java.util.List;
import java.util.Map;

/**
 * Excel 导入校验异常：携带全部错误行（row=行号，reason=原因），
 * 控制器捕获后以 400 + 错误清单返回，前端逐行展示（与期初导入同款交互）。
 */
public class ExcelImportException extends RuntimeException {

    private final List<Map<String, Object>> errors;

    public ExcelImportException(String message, List<Map<String, Object>> errors) {
        super(message);
        this.errors = errors;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }
}
