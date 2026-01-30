package com.homecarcharge.mytrade;

import java.io.Serializable;
import java.util.Date;

public class Transaction implements Serializable {
    private Date date;
    private double amount;
    private boolean isProfit;

    public Transaction(Date date, double amount, boolean isProfit) {
        this.date = date;
        this.amount = amount;
        this.isProfit = isProfit;
    }

    public Date getDate() {
        return date;
    }

    public double getAmount() {
        return amount;
    }

    public boolean isProfit() {
        return isProfit;
    }

    public void setProfit(boolean profit) {
        isProfit = profit;
    }
}