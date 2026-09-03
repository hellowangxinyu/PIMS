package com.pengyuan.pims.config;

import cn.dev33.satoken.stp.StpInterface;
import com.pengyuan.pims.entity.User;
import com.pengyuan.pims.repository.UserRepository;
import com.pengyuan.pims.service.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class StpInterfaceImpl implements StpInterface {

    private static final Logger log = LoggerFactory.getLogger(StpInterfaceImpl.class);
    private final UserRepository userRepo;
    private final RoleService roleService;

    public StpInterfaceImpl(UserRepository userRepo, RoleService roleService) {
        this.userRepo = userRepo;
        this.roleService = roleService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> perms = new ArrayList<>();
        try {
            long userId = Long.parseLong(loginId.toString());
            User user = userRepo.findById(userId).orElse(null);
            if (user != null && user.role != null && user.enabled) {
                // v5.69：加载后正反向展开（粗码→细码、细码→补粗码），存量角色零迁移可用
                perms = new ArrayList<>(com.pengyuan.pims.common.FineGrainedPermissions
                        .expandPermissions(roleService.getPermissionCodes(user.role)));
            }
        } catch (Exception e) {
            log.error("权限注入失败 loginId={}", loginId, e);
        }
        return perms;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> roles = new ArrayList<>();
        try {
            long userId = Long.parseLong(loginId.toString());
            User user = userRepo.findById(userId).orElse(null);
            if (user != null && user.role != null && user.enabled) {
                roles.add(user.role);
            }
        } catch (Exception e) {
            log.error("角色注入失败 loginId={}", loginId, e);
        }
        return roles;
    }
}
