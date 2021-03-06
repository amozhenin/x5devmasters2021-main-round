package ru.dvorkin.x5.model;

public class ProductInfo {

    private final Integer productId;

    private final String productName;

    private final double stockPrice;

    private double sellPrice;

    private int totalStock;

    private int inStock;

    private int inRack;

    private int inBasket;

    private boolean stopSpam;

    public ProductInfo(Integer productId, String productName, double stockPrice) {
        this.productId = productId;
        this.productName = productName;
        this.stockPrice = stockPrice;
        this.totalStock = 0;
        this.inStock = 0;
        this.inRack = 0;
        this.inBasket = 0;
        this.sellPrice = stockPrice;
        stopSpam = false;
    }

    public Integer getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public double getStockPrice() {
        return stockPrice;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(double sellPrice) {
        this.sellPrice = sellPrice;
    }

    public int getTotalStock() {
        return totalStock;
    }

    public void addStock(int quantity) {
        this.totalStock += quantity;
    }

    public int getInStock() {
        return inStock;
    }

    public void setInStock(int inStock) {
        this.inStock = inStock;
    }

    public int getInRack() {
        return inRack;
    }

    public void setInRack(int inRack) {
        this.inRack = inRack;
    }

    public int getInBasket() {
        return inBasket;
    }

    public void setInBasket(int inBasket) {
        this.inBasket = inBasket;
    }

    public boolean isStopSpam() {
        return stopSpam;
    }

    public void stopSpam() {
        this.stopSpam = true;
    }

    public int getSold() {
        return getTotalStock() - getInStock() - getInRack() - getInBasket();
    }

    public double getIncome() {
        return getSold() * getSellPrice();
    }

    public double getStockCost() {
        return getTotalStock() * getStockPrice();
    }

    public double getProfit() {
        return getIncome() - getStockCost();
    }

    public double getSoldProfit() {
        return getSold() * (getSellPrice() - getStockPrice());
    }

    public double getTotalInefficiency() {
        return (getInStock() + getInRack() + getInBasket()) * getStockPrice();
    }

    public double getManageableInefficiency() {
        return (getInStock()) * getStockPrice();
    }

    @Override
    public String toString() {
        return "id = " + getProductId() + ", name = " + getProductName() + ", stockPrice = " +
                getStockPrice() + ", sellPrice = " + getSellPrice() + ", totalStock = " +
                getTotalStock() + ", inStock = " + getInStock() + ", inRack = " + getInRack() +
                ", inBasket = " + getInBasket() + ", sold = " + getSold();
    }
}
