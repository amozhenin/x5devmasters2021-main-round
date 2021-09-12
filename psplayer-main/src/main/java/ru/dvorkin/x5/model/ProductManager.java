package ru.dvorkin.x5.model;

import lombok.extern.slf4j.Slf4j;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.CurrentWorldResponse;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.Customer;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.Product;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.ProductInBasket;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.RackCell;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ProductManager {

    private final Map<Integer, ProductInfo> usedProducts;

    public final static int ROCK_QUANTITY = 1000;

    public ProductManager() {
        usedProducts = new HashMap<>();
    }

    public List<Integer> getUsedProductIds() {
        //return List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        //return List.of(16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30);
        //return List.of(31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45);
        // 46
        return List.of(42, 29, 26, 17, 27, 13, 39, 21, 22, 24, 23, 32, 31, 41, 40);
    }

    public Integer getQuantityToBuy(int productId, int rackId, CurrentWorldResponse world) {
        ProductInfo info = usedProducts.get(productId);
        if (info.getSold() == 0 && world.getCurrentTick() == 0) {
            //TODO: do some updates
            switch (rackId) {
                case 1:
                case 2:
                case 3:
                    return 100;
                    //return 3700;
                case 4:
                case 5:
                case 6:
                    return 100;
                    //return 4500;
                case 7:
                case 8:
                case 9:
                    return 1000;
                    //return 5100;
                case 10:
                case 11:
                case 12:
                case 13:
                case 14:
                case 15:
                    return 10000;
                    //return 11400;
                default:
                    return 11111;
            }
        } else {
            double totalToBuy = ((double) info.getSold() * world.getTickCount()) / world.getCurrentTick();
            int total = (int)Math.round(totalToBuy);
            return (total - info.getTotalStock());
        }
    }

    public Double getSellPrice(int productId, double stockPrice) {
         return getPercent(productId) * stockPrice;
    }

    public Double getPercent(int productId) {
        Integer rackId = getRackForProductId(productId);
        switch (rackId) {
            case 1:
            case 2:
            case 3:
                return 1.42;
            case 4:
            case 5:
            case 6:
                return 1.41;
            case 7:
            case 8:
            case 9:
                return 1.36;
            case 10:
            case 11:
            case 12:
                return 1.207;
            case 13:
            case 14:
            case 15:
                return 1.232;
            default:
                return 1.2;

        }
    }

    public Integer getProductIdForRack(int rackId) {
        return getUsedProductIds().get(rackId - 1);
    }

    public Integer getRackForProductId(Integer productId) {
        return getUsedProductIds().indexOf(productId) + 1;
    }

    public void printProductStatistics() {
        log.info("*usedPercents*");
        StringBuilder sb = new StringBuilder();
        for (Integer productId: getUsedProductIds()) {
            sb.append("| id = ").append(productId).append(", % = ").append(getPercent(productId));
        }
        log.info(sb.toString());
        log.info("*usedProducts*");
        int totalSold = 0;
        double totalIneff = 0.0;
        double manageableIneff = 0.0;
        double rockCost = 0.0;
        for (Integer productId: getUsedProductIds()) {
            ProductInfo info = usedProducts.get(productId);
            log.info(info.toString());
            if (info.getInStock() < (isRockEnabled() ? ROCK_QUANTITY : 0)) {
                log.warn("Need quantity update on " + info.getProductId() + " " + info.getProductName());
            }
            totalSold += info.getSold();
            totalIneff += info.getTotalInefficiency();
            manageableIneff += info.getManageableInefficiency();
            rockCost += info.getStockPrice() * (isRockEnabled() ? ROCK_QUANTITY : 0);
        }
        log.info("totalSold = " + totalSold);
        log.info("totalIneff = " + totalIneff);
        log.info("manageableIneff = " + manageableIneff);
        log.info("rockCost = " + rockCost);
    }

    public Double getRockCost() {
        double totalPrice = 0.0;
        for (Integer productId: getUsedProductIds()) {
            totalPrice += usedProducts.get(productId).getStockPrice();
        }
        return totalPrice * (isRockEnabled() ? ROCK_QUANTITY : 0);
    }

    public ProductInfo getInfoForProduct(Product product) {
        return usedProducts.computeIfAbsent(product.getId(), productId ->
                new ProductInfo(productId, product.getName(), product.getStockPrice()));
    }

    public ProductInfo getUnsafeInfoForProductId(Integer productId) {
        ProductInfo info = usedProducts.get(productId);
        if (info == null) {
            log.error("!!!NO INFO for id " + productId);
            info = new ProductInfo(productId, "DUMB", 0.0);
            usedProducts.put(productId, info);
        }
        return info;
    }

    public void syncWithWorld(CurrentWorldResponse world) {
        for (Integer productId : getUsedProductIds()) {
            Product product = world.getStock().get(productId - 1);
            ProductInfo info = getInfoForProduct(product);
            info.setSellPrice(getSellPrice(productId, product.getStockPrice()));
            info.setInStock(product.getInStock());
            Integer inRack = world.getRackCells().stream().filter(rack -> productId.equals(rack.getProductId())).findFirst().map(RackCell::getProductQuantity).orElse(0);
            if (inRack == null) {
                inRack = 0;
            }
            info.setInRack(inRack);
            int inBasket = 0;
            for (Customer customer : world.getCustomers()) {
                Integer customerBasket = customer.getBasket().stream().filter(p -> productId.equals(p.getId())).findFirst().map(ProductInBasket::getProductCount).orElse(0);
                if (customerBasket == null) {
                    customerBasket = 0;
                }
                inBasket += customerBasket;
            }
            info.setInBasket(inBasket);
        }
    }

    public boolean isRockEnabled() {
        return false;
    }

}
