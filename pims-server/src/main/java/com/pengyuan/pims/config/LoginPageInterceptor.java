package com.pengyuan.pims.config;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 页面路由登录校验：未登录访问页面（非 API/静态资源）直接 302 跳转登录页。
 * 与 API 层的 Sa-Token 校验（401 + 前端跳转）互补：
 * 防止直接输入 URL（或 localStorage 残留失效 token）绕过前端守卫进入页面。
 */
@Component
public class LoginPageInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!StpUtil.isLogin()) {
            response.sendRedirect("/login");
            return false;
        }
        return true;
    }
}
