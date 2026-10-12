package com.homepage.lasvegas.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 一局拉斯维加斯的战绩汇总（战绩表用）。
 */
public class LasVegasMatchSummary {

    private Long matchId;
    private String roomCode;
    private int playerCount;
    private int rounds;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    /** 当前查询者在本局的名次（1 起） */
    private int myRank;
    private int myMoney;
    private int myBanknotes;
    private boolean iWon;

    private final List<LasVegasPlayerResult> standings = new ArrayList<>();

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public int getPlayerCount() { return playerCount; }
    public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }

    public int getRounds() { return rounds; }
    public void setRounds(int rounds) { this.rounds = rounds; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public int getMyRank() { return myRank; }
    public void setMyRank(int myRank) { this.myRank = myRank; }

    public int getMyMoney() { return myMoney; }
    public void setMyMoney(int myMoney) { this.myMoney = myMoney; }

    public int getMyBanknotes() { return myBanknotes; }
    public void setMyBanknotes(int myBanknotes) { this.myBanknotes = myBanknotes; }

    /**
     * Jackson 对 isIWon() 推断出的名字是 "iwon"（连续大写被整体小写化），
     * 与前端使用的 iWon 不一致，因此显式固定为 iWon。
     */
    @com.fasterxml.jackson.annotation.JsonProperty("iWon")
    public boolean isIWon() { return iWon; }
    public void setIWon(boolean iWon) { this.iWon = iWon; }

    public List<LasVegasPlayerResult> getStandings() { return standings; }
}
