package ru.dvorkin.x5.model;

import java.util.List;

public class ProductManager {

    public List<Integer> getUsedProductIds() {
        return List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    }

    public Integer getQuantityToBuy(int productId) {
        switch (productId) {
            case 1:
            case 2:
            case 3:
                return 3800;
            case 4:
            case 5:
            case 6:
                return 4600;
            case 7:
            case 8:
            case 9:
                return 5100;
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
                return 11000;
            default:
                return 11111;
        }
    }

    public Double getSellPrice(int productId, double stockPrice) {
        return 1.2 * stockPrice;
    }

    public Integer getProductIdForRack(int rackId) {
        return getUsedProductIds().get(rackId - 1);
    }
}
