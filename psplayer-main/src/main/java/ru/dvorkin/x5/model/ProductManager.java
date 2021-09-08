package ru.dvorkin.x5.model;

import java.util.List;

public class ProductManager {

    public List<Integer> getUsedProductIds() {
        return List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    }

    public Integer getQuantityToBuy(int productId) {
        return 15000;
    }

    public Double getSellPrice(int productId, double stockPrice) {
        return 1.2 * stockPrice;
    }

    public Integer getProductIdForRack(int rackId) {
        return getUsedProductIds().get(rackId - 1);
    }
}
