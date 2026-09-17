package com.homepage.controller;

import com.homepage.model.VisitorRecord;
import com.homepage.repository.VisitorRepository;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 访问统计控制器
 */
@RestController
@RequestMapping("/api/visitor")
public class VisitorController {

    private final VisitorRepository visitorRepo;

    public VisitorController(VisitorRepository visitorRepo) {
        this.visitorRepo = visitorRepo;
    }

    /**
     * 获取访问统计
     */
    @GetMapping("/count")
    public Map<String, Object> getStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalViews", visitorRepo.totalPageViews());
        result.put("uniqueVisitors", visitorRepo.uniqueVisitors());
        result.put("todayViews", visitorRepo.todayPageViews());
        result.put("todayVisitors", visitorRepo.todayUniqueVisitors());
        return result;
    }

    /**
     * 记录一次页面访问（前端每个会话调用一次）
     */
    @PostMapping("/ping")
    public Map<String, Object> recordVisit(@RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        String page = (String) body.getOrDefault("page", "/");
        String ip = getClientIp(request);

        visitorRepo.save(new VisitorRecord(ip, page));

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("totalViews", visitorRepo.totalPageViews());
        result.put("uniqueVisitors", visitorRepo.uniqueVisitors());
        result.put("todayViews", visitorRepo.todayPageViews());
        result.put("todayVisitors", visitorRepo.todayUniqueVisitors());
        return result;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
