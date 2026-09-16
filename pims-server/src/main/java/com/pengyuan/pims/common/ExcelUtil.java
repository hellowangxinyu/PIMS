package com.pengyuan.pims.common;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Excel 导出工具 v5.98：POI SXSSF 流式写入（内存滑动窗口 200 行，百万行不 OOM）。
 * 原 hutool ExcelWriter 是 XSSF 全内存模式，10 万行级导出即数百 MB 堆占用。
 * 保留 export(...) 兼容签名（54 处调用无需改）；新增 stream(...) 流式分批重载供超大数据量导出用。
 */
public class ExcelUtil {

    private static final int WINDOW_ROWS = 200;

    /** v9.6 导出通用：单据状态码→中文（未知值原样返回） */
    public static String statusCn(String s) {
        if (s == null) return "";
        return switch (s) {
            case "DRAFT" -> "草稿";
            case "CONFIRMED" -> "已确认";
            case "APPROVED" -> "已审核";
            case "CLOSED" -> "已关闭";
            case "DONE" -> "已完成";
            case "CANCELLED" -> "已作废";
            case "PENDING" -> "待处理";
            case "SHIPPED" -> "已发货";
            case "COMPLETED" -> "已完结";
            default -> s;
        };
    }

    public static void export(HttpServletResponse response, String fileName, String sheetName,
                              String[] headers, List<Object[]> rows) throws IOException {
        write(response, fileName, sheetName, headers, (wb, sheet, headStyle) -> {
            int rowIdx = 1;
            for (Object[] row : rows) {
                writeRow(sheet, rowIdx++, row);
            }
        });
    }

    /**
     * 流式分批导出：loader(start) 返回从 start 起的一批（每批 5000 行），空列表即结束。
     * 内存占用 = 窗口 + 单批，与总量无关；每批后 flush 到磁盘临时文件。
     */
    public static void stream(HttpServletResponse response, String fileName, String sheetName,
                              String[] headers, IntFunction<List<Object[]>> loader) throws IOException {
        write(response, fileName, sheetName, headers, (wb, sheet, headStyle) -> {
            int rowIdx = 1;
            for (int start = 0; ; start += 5000) {
                List<Object[]> batch = loader.apply(start);
                if (batch == null || batch.isEmpty()) break;
                for (Object[] row : batch) {
                    writeRow(sheet, rowIdx++, row);
                }
                sheet.flushRows();
            }
        });
    }

    private interface RowWriter {
        void write(SXSSFWorkbook wb, SXSSFSheet sheet, CellStyle headStyle) throws IOException;
    }

    private static void write(HttpServletResponse response, String fileName, String sheetName,
                              String[] headers, RowWriter bodyWriter) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded + ".xlsx");
        try (SXSSFWorkbook wb = new SXSSFWorkbook(WINDOW_ROWS)) {
            SXSSFSheet sheet = wb.createSheet(sheetName);
            CellStyle headStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headStyle.setFont(bold);
            Row head = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                Cell cell = head.createCell(c, CellType.STRING);
                cell.setCellValue(headers[c]);
                cell.setCellStyle(headStyle);
            }
            bodyWriter.write(wb, sheet, headStyle);
            wb.write(response.getOutputStream());
        }
    }

    private static void writeRow(SXSSFSheet sheet, int rowIdx, Object[] row) {
        Row r = sheet.createRow(rowIdx);
        for (int c = 0; c < row.length; c++) {
            Object v = row[c];
            Cell cell = r.createCell(c);
            if (v == null) { cell.setBlank(); continue; }
            if (v instanceof Number n) cell.setCellValue(n.doubleValue());
            else if (v instanceof Boolean b) cell.setCellValue(b);
            else cell.setCellValue(v.toString());
        }
    }
}
