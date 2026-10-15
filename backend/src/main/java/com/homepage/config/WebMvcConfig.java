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
 * Web MVC 配置：SPA 路由的前端路由 fallback。
 *
 * 前端路由分两条路兜底，缺一不可：
 * <ul>
 *   <li>根路径 {@code /} 由 {@link #addViewControllers} 显式转发到 index.html；</li>
 *   <li>其余前端路由（{@code /las-vegas}、{@code /gomoku} 等）由
 *       {@link #addResourceHandlers} 里的 {@link PathResourceResolver} 兜底：
 *       静态资源不存在时返回 index.html。</li>
 * </ul>
 *
 * 注意：{@code /api/**} 由 Controller 处理，Controller 映射优先于资源处理器，
 * 所以正常接口不会走到 fallback。真正拦截未知 {@code /api} 路径的是
 * {@link SecurityConfig} 里的 {@code .antMatchers("/api/**").authenticated()}：
 * 未带合法 JWT 时直接 403，不会返回 index.html。前端不要把「200 + HTML」
 * 当成接口响应，也不要用 404 判断接口是否存在（拿到的会是 403）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 根路径转发到 index.html。
     *
     * 这里只注册了 {@code /} 一条；其余前端路由见 {@link #addResourceHandlers}
     * 的 PathResourceResolver fallback，别以为加一条前端路由需要动这个类。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 首页
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    /**
     * 配置静态资源路径，并让缺失的资源回退到 index.html（SPA 前端路由 fallback）。
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
