package com.pengyuan.pims.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA fallback：非 API 请求全部转发到 index.html
 */
@Controller
public class SpaController {

    @RequestMapping("/{path:[^\\.]*}")
    public String forward(@PathVariable String path) {
        // API 请求由 RestController 处理，这里拦截的是非静态资源的页面路由
        if ("api".equals(path)) return "forward:/";
        return "forward:/index.html";
    }
}
