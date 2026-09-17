package com.homepage.model;

import java.time.LocalDateTime;

/**
 * 贪吃蛇分数记录
 */
public class Score {

    private Long id;
    private String playerName;
    private int score;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationSeconds;
    private LocalDateTime createdAt;

    public Score() {}

    public Score(String playerName, int score) {
        this.playerName = playerName;
        this.score = score;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
