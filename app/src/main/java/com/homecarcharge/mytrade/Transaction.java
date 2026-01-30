package com.homecarcharge.mytrade;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Transaction implements Serializable {
    private Date date;
    private double amount;
    private boolean isProfit;
    private String stockName;
    private String reason;
    private double roi;
    private double capitalUsed;
    private List<TradeEntry> tradeEntries = new ArrayList<>();
    private List<String> imagePaths = new ArrayList<>();

    public Transaction(Date date, double amount, boolean isProfit) {
        this.date = date;
        this.amount = amount;
        this.isProfit = isProfit;
    }

    // New constructor with all fields
    public Transaction(Date date, double amount, boolean isProfit, String stockName,
                       String reason, double roi, double capitalUsed,
                       List<TradeEntry> tradeEntries, List<String> imagePaths) {
        this.date = date;
        this.amount = amount;
        this.isProfit = isProfit;
        this.stockName = stockName;
        this.reason = reason;
        this.roi = roi;
        this.capitalUsed = capitalUsed;
        this.tradeEntries = tradeEntries != null ? tradeEntries : new ArrayList<>();
        this.imagePaths = imagePaths != null ? imagePaths : new ArrayList<>();
    }

    // Getters and Setters
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

    public String getStockName() {
        return stockName;
    }

    public void setStockName(String stockName) {
        this.stockName = stockName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public double getRoi() {
        return roi;
    }

    public void setRoi(double roi) {
        this.roi = roi;
    }

    public double getCapitalUsed() {
        return capitalUsed;
    }

    public void setCapitalUsed(double capitalUsed) {
        this.capitalUsed = capitalUsed;
    }

    public List<TradeEntry> getTradeEntries() {
        return tradeEntries;
    }

    public void setTradeEntries(List<TradeEntry> tradeEntries) {
        this.tradeEntries = tradeEntries;
    }

    public List<String> getImagePaths() {
        return imagePaths;
    }

    public void setImagePaths(List<String> imagePaths) {
        this.imagePaths = imagePaths;
    }

    public void addTradeEntry(TradeEntry entry) {
        this.tradeEntries.add(entry);
    }

    public void addImagePath(String imagePath) {
        if (this.imagePaths.size() < 3) {
            this.imagePaths.add(imagePath);
        }
    }
}