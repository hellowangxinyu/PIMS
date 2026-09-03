package com.pengyuan.pims.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginPageInterceptor loginPageInterceptor;
    private final com.pengyuan.pims.repository.UserRepository userRepo;
    private final MustChangePwdCache pwdCache;

    public WebMvcConfig(LoginPageInterceptor loginPageInterceptor,
                        com.pengyuan.pims.repository.UserRepository userRepo,
                        MustChangePwdCache pwdCache) {
        this.loginPageInterceptor = loginPageInterceptor;
        this.userRepo = userRepo;
        this.pwdCache = pwdCache;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Sa-Token 路由拦截：API 未登录返回 401（前端统一跳登录页）
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // 登录校验：除登录接口外全部拦截
                    SaRouter.match("/api/**")
                            .notMatch("/api/auth/login")
                            .check(r -> StpUtil.checkLogin());
                    // v6.1.2：强制改密后端兜底——mustChangePwd=true 的账号只放行 auth 域（改密/登出/信息），
                    // 前端弹窗被绕过（手改 localStorage、直接调 API）也无法使用系统
                    // （走 IllegalArgumentException：NotPermissionException 会被全局处理器统一改写为"无权限访问"）
                    SaRouter.match("/api/**")
                            .notMatch("/api/auth/**")
                            .check(r -> {
                                long uid = StpUtil.getLoginIdAsLong();
                                // v6.1.3：60 秒 TTL 缓存（改密/重置时主动失效），消除每请求查库
                                if (pwdCache.flagged(uid, () -> userRepo.findById(uid)
                                        .map(u -> Boolean.TRUE.equals(u.mustChangePwd)).orElse(false))) {
                                    throw new IllegalArgumentException("请先修改初始密码后再使用系统");
                                }
                            });
                }))
                .addPathPatterns("/api/**");

        // 页面路由登录校验：未登录直接 302 跳转登录页（防止直接输入 URL 绕过登录）
        // /index.html 放行：/login 等页面由 SpaController forward 到 index.html，内部转发同样经过拦截器链
        registry.addInterceptor(loginPageInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/api/**", "/login", "/index.html", "/assets/**", "/favicon.svg");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 带 hash 的静态资源（JS/CSS）——长期缓存
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic());
        // index.html 等入口文件——no-store 彻底禁止缓存（noCache 仍可能被浏览器启发式缓存拿到旧引用）
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore());
    }
}
