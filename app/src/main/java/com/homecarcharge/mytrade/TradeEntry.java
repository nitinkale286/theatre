package com.homecarcharge.mytrade;

import java.io.Serializable;

public class TradeEntry implements Serializable {
    private double entryPrice;
    private double exitPrice;

    public TradeEntry(double entryPrice, double exitPrice) {
        this.entryPrice = entryPrice;
        this.exitPrice = exitPrice;
    }

    public double getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(double entryPrice) {
        this.entryPrice = entryPrice;
    }

    public double getExitPrice() {
        return exitPrice;
    }

    public void setExitPrice(double exitPrice) {
        this.exitPrice = exitPrice;
    }
}