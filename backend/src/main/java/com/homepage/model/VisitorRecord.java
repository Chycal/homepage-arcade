package com.homepage.model;

import java.time.LocalDateTime;

/**
 * 访问记录
 */
public class VisitorRecord {

    private Long id;
    private String ip;
    private String page;
    private LocalDateTime createdAt;

    public VisitorRecord() {}

    public VisitorRecord(String ip, String page) {
        this.ip = ip;
        this.page = page;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getPage() { return page; }
    public void setPage(String page) { this.page = page; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
