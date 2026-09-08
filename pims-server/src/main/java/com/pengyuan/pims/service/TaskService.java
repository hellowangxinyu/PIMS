package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.Task;
import com.pengyuan.pims.entity.TaskProgress;
import com.pengyuan.pims.entity.User;
import com.pengyuan.pims.repository.TaskProgressRepository;
import com.pengyuan.pims.repository.TaskRepository;
import com.pengyuan.pims.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 任务督办（v5.67）：领导安排工作 → 员工汇报进度。
 * 状态机：PENDING → IN_PROGRESS → COMPLETED；任意非终态可 CANCELLED；终态可 REOPEN→PENDING。
 * 权限规则：task:write 可全部操作；owner/collaborator 可 start/report/complete 自己的任务。
 */
@Service
public class TaskService {

    private final TaskRepository repo;
    private final TaskProgressRepository progressRepo;
    private final UserRepository userRepo;
    private final UserService userService;
    private final WriteQueue writeQueue;

    public TaskService(TaskRepository repo, TaskProgressRepository progressRepo,
                       UserRepository userRepo, UserService userService, WriteQueue writeQueue) {
        this.repo = repo;
        this.progressRepo = progressRepo;
        this.userRepo = userRepo;
        this.userService = userService;
        this.writeQueue = writeQueue;
    }

    /**
     * 任务列表（权限过滤版）：
     * - 有 task:write 的领导：看全部任务
     * - 普通员工：只看 自己被指派的（owner/collaborator）+ 自己创建的
     * 进度记录批量预取（禁 N+1）
     */
    public List<Task> list(String currentUsername, boolean isManager) {
        List<Task> all = repo.findAllByOrderByCreateTimeDescIdDesc();
        // 权限过滤：非管理者只看自己相关的
        if (!isManager) {
            all = all.stream().filter(t -> isRelated(t, currentUsername)).toList();
        }
        if (all.isEmpty()) return all;
        List<Long> ids = all.stream().map(t -> t.id).toList();
        Map<Long, List<TaskProgress>> byTask = new LinkedHashMap<>();
        for (TaskProgress p : progressRepo.findByTaskIdInOrderByTaskIdAscCreateTimeAscIdAsc(ids)) {
            byTask.computeIfAbsent(p.taskId, k -> new ArrayList<>()).add(p);
        }
        for (Task t : all) t.progressList = byTask.getOrDefault(t.id, List.of());
        return all;
    }

    /** 当前用户是否与任务相关（owner / collaborator / createdBy） */
    private boolean isRelated(Task t, String username) {
        if (username == null || username.isBlank()) return false;
        if (username.equals(t.owner)) return true;
        if (t.collaborators != null && List.of(t.collaborators.split(",")).contains(username)) return true;
        // createdBy 存的是 realName，不是 username——比较宽松匹配
        if (t.createdBy != null && t.createdBy.equals(displayName(username))) return true;
        return false;
    }

    /** 可选执行人列表（enabled 用户） */
    public List<Map<String, String>> users() {
        List<Map<String, String>> result = new ArrayList<>();
        for (User u : userRepo.findAll()) {
            if (!Boolean.TRUE.equals(u.enabled)) continue;
            Map<String, String> m = new LinkedHashMap<>();
            m.put("username", u.username);
            m.put("realName", u.realName != null ? u.realName : u.username);
            m.put("label", (u.realName != null ? u.realName : u.username) + "（" + u.username + "）");
            result.add(m);
        }
        return result;
    }

    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public Task create(Task t) {
        if (t.title == null || t.title.isBlank()) throw new IllegalArgumentException("任务标题不能为空");
        if (t.owner == null || t.owner.isBlank()) throw new IllegalArgumentException("主执行人不能为空");
        validateUser(t.owner);
        if (t.collaborators != null && !t.collaborators.isBlank()) {
            for (String c : t.collaborators.split(",")) validateUser(c.trim());
        }
        if (!"HIGH".equals(t.priority) && !"MEDIUM".equals(t.priority) && !"LOW".equals(t.priority)) t.priority = "MEDIUM";
        return writeQueue.executeTx(() -> {
            String day = LocalDate.now().toString().replace("-", "");
            Integer maxSeq = repo.findMaxSeq("TASK-" + day + "-%");
            t.docNo = String.format("TASK-%s-%04d", day, (maxSeq == null ? 0 : maxSeq) + 1);
            Task saved = repo.save(t);
            addProgress(saved.id, t.createdBy, "创建任务，指派给 " + displayName(t.owner), "CREATE");
            return saved;
        });
    }

    @Transactional
    public Task update(Long id, Task in) {
        Task t = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        if (!"PENDING".equals(t.status)) throw new IllegalArgumentException("只有待接收的任务可以编辑");
        if (in.title != null && !in.title.isBlank()) t.title = in.title;
        t.description = in.description;
        if (in.owner != null && !in.owner.isBlank()) { validateUser(in.owner); t.owner = in.owner; }
        if (in.collaborators != null) {
            if (!in.collaborators.isBlank()) for (String c : in.collaborators.split(",")) validateUser(c.trim());
            t.collaborators = in.collaborators;
        }
        if (in.priority != null) t.priority = in.priority;
        t.dueDate = in.dueDate;
        t.updateTime = LocalDateTime.now();
        return repo.save(t);
    }

    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public void delete(Long id) {
        Task t = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        if (!"PENDING".equals(t.status)) throw new IllegalArgumentException("只有待接收的任务可以删除");
        writeQueue.executeTx(() -> {
            progressRepo.deleteByTaskId(id);
            repo.deleteById(id);
        });
    }

    /** 开始执行：PENDING → IN_PROGRESS */
    @Transactional
    public Task start(Long id, String operator) {
        Task t = mustGet(id);
        if (!"PENDING".equals(t.status)) throw new IllegalArgumentException("任务不是待接收状态");
        checkCanOperate(t, operator);
        t.status = "IN_PROGRESS";
        t.updateTime = LocalDateTime.now();
        repo.save(t);
        addProgress(id, operator, "开始执行", "START");
        return t;
    }

    /** 汇报进度：IN_PROGRESS 状态下添加文字记录 */
    @Transactional
    public Task report(Long id, String content, String operator) {
        Task t = mustGet(id);
        if (!"IN_PROGRESS".equals(t.status)) throw new IllegalArgumentException("只有进行中的任务可以汇报进度");
        checkCanOperate(t, operator);
        if (content == null || content.isBlank()) throw new IllegalArgumentException("进度内容不能为空");
        addProgress(id, operator, content.trim(), "UPDATE");
        t.updateTime = LocalDateTime.now();
        return repo.save(t);
    }

    /** 完成：IN_PROGRESS → COMPLETED（v5.67.2 只有发布人/领导可以确认完成，执行人不能自己点完成） */
    @Transactional
    public Task complete(Long id, String note, String operator) {
        Task t = mustGet(id);
        if (!"IN_PROGRESS".equals(t.status)) throw new IllegalArgumentException("任务不是进行中状态");
        // 完成是领导的确认动作：必须有 task:write 权限
        if (!com.pengyuan.pims.common.FieldFilter.hasPerm("task:write")) {
            throw new IllegalArgumentException("任务完成需要发布人或领导确认（执行人请先汇报进度说明已完成，等领导确认）");
        }
        t.status = "COMPLETED";
        t.completedAt = LocalDateTime.now();
        t.completedNote = note != null && !note.isBlank() ? note.trim() : null;
        t.updateTime = LocalDateTime.now();
        repo.save(t);
        addProgress(id, operator, "任务完成" + (t.completedNote != null ? "：" + t.completedNote : ""), "COMPLETE");
        return t;
    }

    /** 取消：PENDING/IN_PROGRESS → CANCELLED（需 task:write） */
    @Transactional
    public Task cancel(Long id, String reason, String operator) {
        Task t = mustGet(id);
        if ("COMPLETED".equals(t.status) || "CANCELLED".equals(t.status)) throw new IllegalArgumentException("任务已是终态");
        t.status = "CANCELLED";
        t.completedNote = reason != null && !reason.isBlank() ? "取消原因：" + reason.trim() : null;
        t.updateTime = LocalDateTime.now();
        repo.save(t);
        addProgress(id, operator, "任务取消" + (reason != null && !reason.isBlank() ? "：" + reason : ""), "CANCEL");
        return t;
    }

    /** 重开：COMPLETED/CANCELLED → PENDING */
    @Transactional
    public Task reopen(Long id, String operator) {
        Task t = mustGet(id);
        if (!"COMPLETED".equals(t.status) && !"CANCELLED".equals(t.status)) throw new IllegalArgumentException("任务不是终态");
        t.status = "PENDING";
        t.completedAt = null;
        t.completedNote = null;
        t.updateTime = LocalDateTime.now();
        repo.save(t);
        addProgress(id, operator, "任务重开", "REOPEN");
        return t;
    }

    /** 当前用户的待办任务数（Dashboard 用） */
    public Map<String, Integer> myTaskCount(String username) {
        int open = 0, overdue = 0;
        String today = LocalDate.now().toString();
        for (Task t : repo.findAll()) {
            if ("COMPLETED".equals(t.status) || "CANCELLED".equals(t.status)) continue;
            boolean mine = username.equals(t.owner)
                    || (t.collaborators != null && List.of(t.collaborators.split(",")).contains(username));
            if (!mine) continue;
            open++;
            if (t.dueDate != null && t.dueDate.toString().compareTo(today) < 0) overdue++;
        }
        return Map.of("myOpenTasks", open, "overdueTasks", overdue);
    }

    // ===== 内部 =====

    private Task mustGet(Long id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("任务不存在"));
    }

    /** 操作权限：owner / collaborator / 有 task:write 的领导 */
    private void checkCanOperate(Task t, String operator) {
        boolean isOwner = operator.equals(t.owner);
        boolean isCollab = t.collaborators != null && List.of(t.collaborators.split(",")).contains(operator);
        if (!isOwner && !isCollab && !com.pengyuan.pims.common.FieldFilter.hasPerm("task:write")) {
            throw new IllegalArgumentException("只有任务执行人或领导可以操作此任务");
        }
    }

    private void addProgress(Long taskId, String reporter, String content, String actionType) {
        TaskProgress p = new TaskProgress();
        p.taskId = taskId;
        p.reporter = reporter;
        p.content = content;
        p.actionType = actionType;
        p.createTime = LocalDateTime.now();
        progressRepo.save(p);
    }

    private void validateUser(String username) {
        userRepo.findByUsername(username)
                .filter(u -> Boolean.TRUE.equals(u.enabled))
                .orElseThrow(() -> new IllegalArgumentException("用户 " + username + " 不存在或已禁用"));
    }

    private String displayName(String username) {
        return userRepo.findByUsername(username)
                .map(u -> u.realName != null ? u.realName : u.username)
                .orElse(username);
    }
}
