package com.homepage.lasvegas.model;

/**
 * 战绩表中某位玩家在一局里的成绩。
 *
 * 一个 {@code lasvegas_player} 行对应一个本对象：真人玩家有 userId，
 * AI 与游客写库时 user_id 落 NULL，因此 {@link #getUserId()} 可能为 null——
 * 判断「是不是我」之前必须先判空。
 */
public class LasVegasPlayerResult {

    /** 登录用户 id；游客与 AI 为 null（数据库里 user_id 是 NULL） */
    private Long userId;

    /** 展示用名字；AI 为「电脑·稳健」这类名字 */
    private String username;

    /** 座位号 0..n-1，与名次无关（名次见 LasVegasMatchSummary.myRank） */
    private int seat;

    /** 本局最终金额 */
    private int money;

    /** 本局最终钞票张数（金额相同时用来比名次） */
    private int banknotes;

    /** 是否胜者；并列胜利时每位胜者都是 true */
    private boolean winner;

    public LasVegasPlayerResult() {
    }

    public LasVegasPlayerResult(Long userId, String username, int seat,
                                int money, int banknotes, boolean winner) {
        this.userId = userId;
        this.username = username;
        this.seat = seat;
        this.money = money;
        this.banknotes = banknotes;
        this.winner = winner;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public int getSeat() { return seat; }
    public void setSeat(int seat) { this.seat = seat; }

    public int getMoney() { return money; }
    public void setMoney(int money) { this.money = money; }

    public int getBanknotes() { return banknotes; }
    public void setBanknotes(int banknotes) { this.banknotes = banknotes; }

    public boolean isWinner() { return winner; }
    public void setWinner(boolean winner) { this.winner = winner; }
}
