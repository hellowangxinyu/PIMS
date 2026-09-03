package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.RdProgress;
import com.pengyuan.pims.entity.WeeklyTopic;
import com.pengyuan.pims.repository.RdProgressRepository;
import com.pengyuan.pims.repository.WeeklyTopicRepository;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 周度会议（v5.44）：每周议题 + 研发进度。
 * 导入三级支持（v5.44.1）：单表常规导入（按表头名定位列，去重：责任人/提出人+内容 一致跳过）；
 * 原双 sheet 存量导入（幂等=表非空跳过）；导出格式与导入表头一致（导出→修改→导回闭环）。
 */
@Service
public class WeeklyMeetingService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyMeetingService.class);

    private static final String[] TOPIC_HEADERS = {"责任人", "分类", "待办事项", "结果", "计划完成时间", "已结案时间"};
    private static final String[] RD_HEADERS = {"提出时间", "提出人", "分类", "内容", "结果", "下次跟进时间", "已结案时间", "进度"};

    private final WeeklyTopicRepository topicRepo;
    private final RdProgressRepository rdRepo;
    private final WriteQueue writeQueue;

    public WeeklyMeetingService(WeeklyTopicRepository topicRepo, RdProgressRepository rdRepo, WriteQueue writeQueue) {
        this.topicRepo = topicRepo;
        this.rdRepo = rdRepo;
        this.writeQueue = writeQueue;
    }

    // ===== 每周议题 =====

    public List<WeeklyTopic> listTopics() { return topicRepo.findAllByOrderByPlanDateDescIdDesc(); }

    @Transactional
    public WeeklyTopic createTopic(WeeklyTopic t, String operator) {
        if (t.owner == null || t.owner.isBlank()) throw new IllegalArgumentException("责任人不能为空");
        if (t.category == null || t.category.isBlank()) throw new IllegalArgumentException("分类不能为空");
        if (t.content == null || t.content.isBlank()) throw new IllegalArgumentException("待办事项不能为空");
        t.createdBy = operator;
        return writeQueue.execute(() -> topicRepo.save(t));
    }

    @Transactional
    public WeeklyTopic updateTopic(Long id, WeeklyTopic in) {
        WeeklyTopic t = topicRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        if (in.owner == null || in.owner.isBlank()) throw new IllegalArgumentException("责任人不能为空");
        if (in.content == null || in.content.isBlank()) throw new IllegalArgumentException("待办事项不能为空");
        t.owner = in.owner;
        t.category = in.category;
        t.content = in.content;
        t.result = in.result;
        t.planDate = in.planDate;
        t.closedDate = in.closedDate;
        t.updateTime = LocalDateTime.now();
        return topicRepo.save(t);
    }

    /** 结案（一键填今天）；close=false 反结案（清空结案时间） */
    @Transactional
    public WeeklyTopic closeTopic(Long id, boolean close) {
        WeeklyTopic t = topicRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("议题不存在"));
        t.closedDate = close ? LocalDate.now() : null;
        t.updateTime = LocalDateTime.now();
        return topicRepo.save(t);
    }

    @Transactional
    public void deleteTopic(Long id) { topicRepo.deleteById(id); }

    // ===== 研发进度 =====

    public List<RdProgress> listRd() { return rdRepo.findAllByOrderByRaiseDateDescIdDesc(); }

    @Transactional
    public RdProgress createRd(RdProgress r, String operator) {
        if (r.owner == null || r.owner.isBlank()) throw new IllegalArgumentException("提出人不能为空");
        if (r.content == null || r.content.isBlank()) throw new IllegalArgumentException("内容不能为空");
        if (r.raiseDate == null) r.raiseDate = LocalDate.now();
        r.createdBy = operator;
        return writeQueue.execute(() -> rdRepo.save(r));
    }

    @Transactional
    public RdProgress updateRd(Long id, RdProgress in) {
        RdProgress r = rdRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("研发事项不存在"));
        if (in.owner == null || in.owner.isBlank()) throw new IllegalArgumentException("提出人不能为空");
        if (in.content == null || in.content.isBlank()) throw new IllegalArgumentException("内容不能为空");
        r.raiseDate = in.raiseDate;
        r.owner = in.owner;
        r.category = in.category;
        r.content = in.content;
        r.result = in.result;
        r.nextDate = in.nextDate;
        r.closedDate = in.closedDate;
        r.progress = in.progress;
        r.updateTime = LocalDateTime.now();
        return rdRepo.save(r);
    }

    @Transactional
    public RdProgress closeRd(Long id, boolean close) {
        RdProgress r = rdRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("研发事项不存在"));
        r.closedDate = close ? LocalDate.now() : null;
        r.updateTime = LocalDateTime.now();
        return rdRepo.save(r);
    }

    @Transactional
    public void deleteRd(Long id) { rdRepo.deleteById(id); }

    // ===== Excel 导入 =====

    /** v5.44.1 单表导入（每周议题）：取首个 sheet，按表头名定位列；去重=责任人+待办事项一致跳过 */
    @Transactional
    public String importTopicsFile(MultipartFile file, String operator) {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            int[] col = locateColumns(sheet, TOPIC_HEADERS);
            Set<String> exist = new HashSet<>();
            for (WeeklyTopic t : topicRepo.findAll()) exist.add(t.owner + "|" + t.content);
            int added = 0, skipped = 0;
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                String content = strSafe(row, col[2]);
                if (content.isBlank()) continue;
                String owner = strSafe(row, col[0]).isBlank() ? "未指定" : strSafe(row, col[0]);
                String key = owner + "|" + content;
                if (!exist.add(key)) { skipped++; continue; }
                WeeklyTopic t = new WeeklyTopic();
                t.owner = owner;
                t.category = strSafe(row, col[1]).isBlank() ? "生产" : strSafe(row, col[1]);
                t.content = content;
                t.result = strSafe(row, col[3]);
                t.planDate = col[4] < 0 ? null : date(row, col[4]);
                t.closedDate = col[5] < 0 ? null : date(row, col[5]);
                t.createdBy = operator;
                topicRepo.save(t);
                added++;
            }
            String msg = String.format("每周议题导入完成：新增 %d 条，跳过已存在 %d 条", added, skipped);
            log.info("周度会议导入: {}", msg);
            return msg;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel 解析失败: " + e.getMessage());
        }
    }

    /** v5.44.1 单表导入（研发进度）：去重=提出人+内容一致跳过 */
    @Transactional
    public String importRdFile(MultipartFile file, String operator) {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            int[] col = locateColumns(sheet, RD_HEADERS);
            Set<String> exist = new HashSet<>();
            for (RdProgress r : rdRepo.findAll()) exist.add(r.owner + "|" + r.content);
            int added = 0, skipped = 0;
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                String content = strSafe(row, col[3]);
                if (content.isBlank()) continue;
                String owner = strSafe(row, col[1]).isBlank() ? "未指定" : strSafe(row, col[1]);
                String key = owner + "|" + content;
                if (!exist.add(key)) { skipped++; continue; }
                RdProgress r = new RdProgress();
                r.raiseDate = col[0] < 0 ? null : date(row, col[0]);
                r.owner = owner;
                r.category = strSafe(row, col[2]).isBlank() ? "配方" : strSafe(row, col[2]);
                r.content = content;
                r.result = strSafe(row, col[4]);
                r.nextDate = col[5] < 0 ? null : date(row, col[5]);
                r.closedDate = col[6] < 0 ? null : date(row, col[6]);
                r.progress = strSafe(row, col[7]);
                r.createdBy = operator;
                rdRepo.save(r);
                added++;
            }
            String msg = String.format("研发进度导入完成：新增 %d 条，跳过已存在 %d 条", added, skipped);
            log.info("周度会议导入: {}", msg);
            return msg;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel 解析失败: " + e.getMessage());
        }
    }

    /**
     * 原双 sheet 存量导入（管委会周会 Excel，sheet 名定位）；幂等=对应表非空时跳过该 sheet。
     */
    @Transactional
    public String importExcel(MultipartFile file, String operator) {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            int topics = 0, rds = 0;
            if (topicRepo.count() == 0) {
                Sheet sheet = wb.getSheet("每周议题");
                if (sheet != null) {
                    int[] col = locateColumns(sheet, TOPIC_HEADERS);
                    for (Row row : sheet) {
                        if (row.getRowNum() == 0) continue;
                        String content = strSafe(row, col[2]);
                        if (content.isBlank()) continue;
                        WeeklyTopic t = new WeeklyTopic();
                        t.owner = strSafe(row, col[0]).isBlank() ? "未指定" : strSafe(row, col[0]);
                        t.category = strSafe(row, col[1]).isBlank() ? "生产" : strSafe(row, col[1]);
                        t.content = content;
                        t.result = strSafe(row, col[3]);
                        t.planDate = col[4] < 0 ? null : date(row, col[4]);
                        t.closedDate = col[5] < 0 ? null : date(row, col[5]);
                        t.createdBy = operator;
                        topicRepo.save(t);
                        topics++;
                    }
                }
            }
            if (rdRepo.count() == 0) {
                Sheet sheet = wb.getSheet("研发进度");
                if (sheet != null) {
                    int[] col = locateColumns(sheet, RD_HEADERS);
                    for (Row row : sheet) {
                        if (row.getRowNum() == 0) continue;
                        String content = strSafe(row, col[3]);
                        if (content.isBlank()) continue;
                        RdProgress r = new RdProgress();
                        r.raiseDate = col[0] < 0 ? null : date(row, col[0]);
                        r.owner = strSafe(row, col[1]).isBlank() ? "未指定" : strSafe(row, col[1]);
                        r.category = strSafe(row, col[2]).isBlank() ? "配方" : strSafe(row, col[2]);
                        r.content = content;
                        r.result = strSafe(row, col[4]);
                        r.nextDate = col[5] < 0 ? null : date(row, col[5]);
                        r.closedDate = col[6] < 0 ? null : date(row, col[6]);
                        r.progress = strSafe(row, col[7]);
                        r.createdBy = operator;
                        rdRepo.save(r);
                        rds++;
                    }
                }
            }
            String msg = String.format("导入完成：每周议题 %d 条、研发进度 %d 条", topics, rds);
            log.info("周度会议 Excel 导入: {}", msg);
            return msg;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel 解析失败: " + e.getMessage());
        }
    }

    /** 表头名定位列（未识别的列为 -1 按空处理）；全部未识别时报错 */
    private static int[] locateColumns(Sheet sheet, String[] headers) {
        Row head = sheet.getRow(0);
        if (head == null) throw new IllegalArgumentException("Excel 首行为空，无法识别表头");
        int[] col = new int[headers.length];
        Arrays.fill(col, -1);
        for (Cell c : head) {
            String v;
            try { v = c.getStringCellValue() == null ? "" : c.getStringCellValue().trim(); } catch (Exception e) { v = ""; }
            for (int i = 0; i < headers.length; i++) {
                if (v.equals(headers[i])) col[i] = c.getColumnIndex();
            }
        }
        boolean any = false;
        for (int c : col) if (c >= 0) any = true;
        if (!any) throw new IllegalArgumentException("无法识别表头（首行需包含: " + String.join("/", headers) + "）");
        return col;
    }

    private static String strSafe(Row row, int idx) { return idx < 0 ? "" : str(row, idx); }

    private static String str(Row row, int idx) {
        try {
            Cell c = row.getCell(idx);
            if (c == null) return "";
            if (c.getCellType() == CellType.NUMERIC) {
                double d = c.getNumericCellValue();
                if (d == Math.floor(d)) return String.valueOf((long) d);
                return String.valueOf(d);
            }
            return c.getStringCellValue() == null ? "" : c.getStringCellValue().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static LocalDate date(Row row, int idx) {
        try {
            Cell c = row.getCell(idx);
            if (c == null) return null;
            if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate();
            }
            String s;
            try { s = c.getStringCellValue(); } catch (Exception e) { return null; }
            if (s == null || s.isBlank()) return null;
            return LocalDate.parse(s.trim().substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }
}
