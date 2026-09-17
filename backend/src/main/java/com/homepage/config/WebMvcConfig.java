package com.homepage.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Web MVC 配置
 * 配置 SPA 路由：所有非 /api/** 的请求都转发到 index.html
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 将 Vue Router 的所有路径转发到 index.html（SPA 模式）
     * /api/** 的请求不受影响，由 Controller 处理
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 首页
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    /**
     * 配置静态资源路径
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源从 classpath:/static/ 加载（Vue 构建产物）
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        // 如果请求的资源存在且可读，返回它
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }
                        // 否则返回 index.html（SPA 路由 fallback）
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
