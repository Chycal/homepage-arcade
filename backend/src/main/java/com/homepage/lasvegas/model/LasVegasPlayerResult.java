package com.homepage.lasvegas.model;

/**
 * 战绩表中某位玩家在一局里的成绩。
 */
public class LasVegasPlayerResult {

    private Long userId;
    private String username;
    private int seat;
    private int money;
    private int banknotes;
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
