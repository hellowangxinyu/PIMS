package com.pengyuan.pims.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.pengyuan.pims.common.Result;
import com.pengyuan.pims.entity.User;
import com.pengyuan.pims.repository.UserRepository;
import com.pengyuan.pims.service.RoleService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final com.pengyuan.pims.common.WriteQueue writeQueue;   // v8.10（A2 三批）

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final RoleService roleService;
    private final com.pengyuan.pims.config.MustChangePwdCache pwdCache;
    public AuthController(UserRepository userRepo, PasswordEncoder passwordEncoder, RoleService roleService,
                          com.pengyuan.pims.config.MustChangePwdCache pwdCache, com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.writeQueue = writeQueue;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.roleService = roleService;
        this.pwdCache = pwdCache;
    }

    /** v6.1 安全：登录失败计数（用户名 → [失败次数, 锁定截止, 最近失败时间戳]；内存级，重启清零）
     *  v6.1.1：时间戳必须 long——int 存 1.78e12 毫秒溢出为负，锁定判断永假
     *  v6.1.3：条目逐条 TTL（锁定过期释放/未锁定 30 分钟无失败重计）取代整体 clear——
     *  整体 clear 可被"刷 2001 个假用户名"触发，把真实账号的锁定一并清零 */
    private static final java.util.concurrent.ConcurrentHashMap<String, long[]> LOGIN_ATTEMPTS = new java.util.concurrent.ConcurrentHashMap<>();

    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        // v6.1.1：ConcurrentHashMap 不接受 null key，请求体缺用户名直接拒绝（原实现 NPE 500）
        if (username == null || username.isBlank() || password == null) {
            return Result.fail(401, "用户名或密码错误");
        }

        // v6.1.3：容量护栏改逐条过期清理，攻击者刷假用户名无法清掉真账号的锁定状态
        if (LOGIN_ATTEMPTS.size() > 2000) {
            long nowTs = System.currentTimeMillis();
            LOGIN_ATTEMPTS.values().removeIf(a ->
                    (a[1] > 0 && nowTs >= a[1]) || (a[1] == 0 && nowTs - a[2] > 30 * 60 * 1000L));
        }
        // v6.1.7（修 DoS 残留）：超限时改为「DB 真实用户放行、未知用户名拒绝」——
        // 原实现按 Map 是否含键判断，攻击者刷 1 万+假名后真实用户（登录成功即移除条目）也会被 429
        if (LOGIN_ATTEMPTS.size() > 10000 && !LOGIN_ATTEMPTS.containsKey(username)
                && userRepo.findByUsername(username).isEmpty()) {
            return Result.fail(429, "系统繁忙，请稍后重试");
        }

        // v6.1 安全：同一用户名 5 次失败锁 10 分钟（防爆破）
        long[] attempts = LOGIN_ATTEMPTS.computeIfAbsent(username, k -> new long[]{0, 0, 0});
        synchronized (attempts) {
            if (attempts[1] > 0 && System.currentTimeMillis() < attempts[1]) {
                long secs = (attempts[1] - System.currentTimeMillis()) / 1000 + 1;
                return Result.fail(429, "失败次数过多已锁定，请 " + secs + " 秒后重试");
            }
            if (attempts[1] > 0) { attempts[0] = 0; attempts[1] = 0; }   // 锁定已过期：条目释放重新计数
        }

        User user = userRepo.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.password)) {
            synchronized (attempts) {
                attempts[0]++;
                attempts[2] = System.currentTimeMillis();
                if (attempts[0] >= 5) {
                    attempts[1] = System.currentTimeMillis() + 10 * 60 * 1000L;
                    attempts[0] = 0;
                    return Result.fail(429, "连续失败 5 次，账号锁定 10 分钟");
                }
            }
            return Result.fail(401, "用户名或密码错误（连续失败 5 次将锁定）");
        }
        LOGIN_ATTEMPTS.remove(username);   // 登录成功即清条目

        if (!user.enabled) {
            return Result.fail(403, "账号已被禁用");
        }

        StpUtil.login(user.id);
        String token = StpUtil.getTokenValue();

        // v5.69.1：返回展开后权限（粗码→细码+细码→补粗码），前端菜单/按钮才能正确显隐
        return Result.ok(Map.of(
                "token", token,
                "userId", user.id,
                "username", user.username,
                "realName", user.realName,
                "role", user.role,
                "mustChangePwd", Boolean.TRUE.equals(user.mustChangePwd),
                "permissions", expandedPermissions(user.role)
        ));
    }

    @PostMapping("/logout")
    public Result<?> logout() {
        StpUtil.logout();
        return Result.ok();
    }

    @GetMapping("/info")
    public Result<?> info() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        return Result.ok(Map.of(
                "userId", user.id,
                "username", user.username,
                "realName", user.realName,
                "role", user.role,
                "permissions", expandedPermissions(user.role)
        ));
    }

    /** v6.1 安全：自助修改密码（强制改密/用户主动改；改完清除 mustChangePwd） */
    @PostMapping("/change-password")
    public Result<?> changePassword(@RequestBody Map<String, String> body) {
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (newPwd == null || newPwd.length() < 6) throw new IllegalArgumentException("新密码至少 6 位");
        long userId = StpUtil.getLoginIdAsLong();
        User user = userRepo.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!passwordEncoder.matches(oldPwd, user.password)) throw new IllegalArgumentException("原密码错误");
        user.password = passwordEncoder.encode(newPwd);
        user.mustChangePwd = false;
        writeQueue.executeTx(() -> userRepo.save(user));
        pwdCache.invalidate(userId);   // v6.1.3：拦截器缓存主动失效，改完立即可用
        return Result.ok("密码已修改");
    }

    /** v5.69.1 权限展开（登录/info 返回给前端，确保菜单显隐正确） */
    private List<String> expandedPermissions(String role) {
        return new ArrayList<>(com.pengyuan.pims.common.FineGrainedPermissions
                .expandPermissions(roleService.getPermissionCodes(role)));
    }
}
