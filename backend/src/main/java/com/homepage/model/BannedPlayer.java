package com.homepage.model;

import java.time.LocalDateTime;

/**
 * 被封禁玩家记录
 */
public class BannedPlayer {

    private Long id;
    private String playerName;
    private LocalDateTime bannedAt;
    private String bannedBy;

    public BannedPlayer() {}

    public BannedPlayer(String playerName, String bannedBy) {
        this.playerName = playerName;
        this.bannedBy = bannedBy;
        this.bannedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public LocalDateTime getBannedAt() { return bannedAt; }
    public void setBannedAt(LocalDateTime bannedAt) { this.bannedAt = bannedAt; }

    public String getBannedBy() { return bannedBy; }
    public void setBannedBy(String bannedBy) { this.bannedBy = bannedBy; }
}
