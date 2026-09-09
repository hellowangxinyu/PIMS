package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.User;
import com.pengyuan.pims.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final com.pengyuan.pims.common.WriteQueue writeQueue;   // v8.8（A2）：写路径收口
    private final UserRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final com.pengyuan.pims.config.MustChangePwdCache pwdCache;
    private final com.pengyuan.pims.repository.RoleRepository roleRepo;

    /** 系统预置角色 */
    public static final String[] ROLES = {
        "GM", "PM", "BUYER", "TECH_LEAD", "TECHNICIAN",
        "OUTSOURCE", "STOREKEEPER", "SALES", "FINANCE", "QC"
    };

    /** 角色中文名映射 */
    public static final java.util.Map<String, String> ROLE_NAMES = java.util.Map.ofEntries(
        java.util.Map.entry("GM", "总经理"),
        java.util.Map.entry("PM", "采购经理"),
        java.util.Map.entry("BUYER", "采购员"),
        java.util.Map.entry("TECH_LEAD", "技术负责人"),
        java.util.Map.entry("TECHNICIAN", "技术员"),
        java.util.Map.entry("OUTSOURCE", "委外对接人"),
        java.util.Map.entry("STOREKEEPER", "仓管员"),
        java.util.Map.entry("SALES", "销售对接人"),
        java.util.Map.entry("FINANCE", "财务"),
        java.util.Map.entry("QC", "质检员")
    );

    /** 角色→模块权限码映射（供 StpInterfaceImpl 和前端使用） */
    public static final java.util.Map<String, java.util.List<String>> ROLE_PERMISSIONS = buildPermissions();

    private static java.util.Map<String, java.util.List<String>> buildPermissions() {
        var map = new java.util.LinkedHashMap<String, java.util.List<String>>();

        // GM: 全部权限
        map.put("GM", java.util.List.of(
            "supplier:read","supplier:write","supplier:delete",
            "customer:read","customer:write","customer:delete",
            "material:read","material:write","material:delete",
            "warehouse:read","warehouse:write","warehouse:delete",
            "inventory:read","inventory:write",
            "purchase:read","purchase:write","purchase:audit",
            "sales:read","sales:write",
            "outsource:read","outsource:write",
            "finance:read","finance:write",
            "user:read","user:write","user:delete"
        ));

        // PM: 供应商/物料/采购 + 应付查看
        map.put("PM", java.util.List.of(
            "supplier:read","supplier:write",
            "customer:read",
            "material:read","material:write",
            "warehouse:read",
            "inventory:read",
            "purchase:read","purchase:write",
            "sales:read",
            "outsource:read",
            "finance:read"
        ));

        // BUYER: 供应商/物料/采购
        map.put("BUYER", java.util.List.of(
            "supplier:read","supplier:write",
            "customer:read",
            "material:read","material:write",
            "warehouse:read",
            "inventory:read",
            "purchase:read","purchase:write",
            "sales:read",
            "outsource:read"
        ));

        // TECH_LEAD: 物料 + 配方（预留）
        map.put("TECH_LEAD", java.util.List.of(
            "material:read","material:write",
            "warehouse:read",
            "inventory:read",
            "supplier:read","customer:read",
            "purchase:read","sales:read","outsource:read"
        ));

        // TECHNICIAN: 物料只读
        map.put("TECHNICIAN", java.util.List.of(
            "material:read",
            "warehouse:read",
            "inventory:read",
            "supplier:read","customer:read"
        ));

        // OUTSOURCE: 委外 + 库存查看
        map.put("OUTSOURCE", java.util.List.of(
            "outsource:read","outsource:write",
            "inventory:read",
            "warehouse:read",
            "material:read",
            "supplier:read","customer:read",
            "purchase:read","sales:read"
        ));

        // STOREKEEPER: 库存出入库 + 委外收发 + 仓库管理
        map.put("STOREKEEPER", java.util.List.of(
            "warehouse:read","warehouse:write",
            "inventory:read","inventory:write",
            "outsource:read","outsource:write",
            "material:read",
            "supplier:read","customer:read",
            "purchase:read","sales:read"
        ));

        // SALES: 客户/销售 + 应收查看
        map.put("SALES", java.util.List.of(
            "customer:read","customer:write",
            "sales:read","sales:write",
            "finance:read",
            "material:read","warehouse:read",
            "inventory:read",
            "supplier:read","purchase:read","outsource:read"
        ));

        // FINANCE: 应收应付
        map.put("FINANCE", java.util.List.of(
            "finance:read","finance:write",
            "supplier:read","customer:read",
            "material:read","warehouse:read",
            "inventory:read",
            "purchase:read","sales:read","outsource:read"
        ));

        // QC: 只读基础数据
        map.put("QC", java.util.List.of(
            "supplier:read","customer:read",
            "material:read","warehouse:read",
            "inventory:read",
            "purchase:read","sales:read","outsource:read","finance:read"
        ));

        return java.util.Collections.unmodifiableMap(map);
    }

    public UserService(UserRepository repo, PasswordEncoder passwordEncoder,
                        com.pengyuan.pims.config.MustChangePwdCache pwdCache,
                        com.pengyuan.pims.repository.RoleRepository roleRepo, com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.writeQueue = writeQueue;
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
        this.pwdCache = pwdCache;
        this.roleRepo = roleRepo;
    }

    public List<User> listAll() { return repo.findAll(); }

    public Optional<User> getById(Long id) { return repo.findById(id); }

    /** 按用户名查用户（权限注入用） */
    public User findByUsername(String username) {
        return repo.findByUsername(username).orElse(null);
    }

    /**
     * v5.2：当前登录操作人显示名——优先真实姓名（realName），未设置时回退登录账号（username）。
     * 制单员/检验员/审核人等字段统一写入该值（原为登录用户 ID 数字，不可读）
     */
    public String currentOperatorName() {
        try {
            Object loginId = cn.dev33.satoken.stp.StpUtil.getLoginIdDefaultNull();
            if (loginId == null) return "";
            User u = repo.findById(Long.parseLong(loginId.toString())).orElse(null);
            if (u == null) return loginId.toString();
            return (u.realName != null && !u.realName.isBlank()) ? u.realName : u.username;
        } catch (Exception e) {
            return "";
        }
    }

    /** 当前登录用户的 username（v5.67 任务督办 myTaskCount 用） */
    public String currentUsername() {
        try {
            Object loginId = cn.dev33.satoken.stp.StpUtil.getLoginIdDefaultNull();
            if (loginId == null) return "";
            User u = repo.findById(Long.parseLong(loginId.toString())).orElse(null);
            return u != null ? u.username : "";
        } catch (Exception e) {
            return "";
        }
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public User create(User u) {
        return writeQueue.executeTx(() -> {
            // v5.70 P2 防呆：密码至少 6 位
            if (u.password != null && u.password.length() < 6) {
                throw new IllegalArgumentException("密码至少 6 位");
            }
            if (repo.findByUsername(u.username).isPresent()) {
                throw new IllegalArgumentException("用户名已存在");
            }
            u.createTime = LocalDateTime.now();
            u.password = passwordEncoder.encode(u.password);
            u.mustChangePwd = true;   // v6.1.2：新建用户使用管理员设置的初始密码，首登强制改密
            return repo.save(u);
    
        });
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public User update(Long id, User u) {
        return writeQueue.executeTx(() -> {
            User exist = repo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            // v6.1.7：角色白名单改读角色表（含自定义角色）——原硬编码 10 个预置角色，自定义角色用户会被误拒
            if (u.role != null && !roleRepo.existsByCode(u.role)) {
                throw new IllegalArgumentException("非法角色: " + u.role);
            }
            exist.realName = u.realName;
            exist.phone = u.phone;
            exist.role = u.role;
            exist.enabled = u.enabled;
            exist.updateTime = LocalDateTime.now();
            // 不更新 username 和 password
            return repo.save(exist);
    
        });
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public void resetPassword(Long id, String newPassword) {
        writeQueue.executeTx(() -> {
            // v6.1.4：补 null/长度校验（原可置 1 位弱密码）
            if (newPassword == null || newPassword.length() < 6) {
                throw new IllegalArgumentException("新密码至少 6 位");
            }
            User u = repo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            u.password = passwordEncoder.encode(newPassword);
            u.mustChangePwd = true;   // v6.1.1：管理员重置后强制下次登录改密
            u.updateTime = LocalDateTime.now();
            repo.save(u);
            pwdCache.invalidate(id);   // v6.1.5：拦截器缓存主动失效（否则标记最长延迟 60 秒生效）
    
        });
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public void delete(Long id) {
        writeQueue.executeTx(() -> {
            User u = repo.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            u.enabled = false;
            repo.save(u);
    
        });
    }
}
