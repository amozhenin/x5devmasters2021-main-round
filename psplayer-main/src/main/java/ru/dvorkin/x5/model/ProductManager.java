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

    public ProductManager() {
        usedProducts = new HashMap<>();
    }

    public List<Integer> getUsedProductIds() {
        return List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    }

    public Integer getQuantityToBuy(int productId, int rackId) {
        switch (rackId) {
            case 1:
            case 2:
            case 3:
                return 3700;
            case 4:
            case 5:
            case 6:
                return 4500;
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
                return 11400;
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

    public Integer getRackForProductId(Integer productId) {
        return getUsedProductIds().indexOf(productId) + 1;
    }

    public void printProductStatistics() {
        log.info("***usedProducts***");
        int totalSold = 0;
        for (Integer productId: getUsedProductIds()) {
            ProductInfo info = usedProducts.get(productId);
            log.info(info.toString());
            totalSold += info.getSold();
        }
        log.info("totalSold = " + totalSold);
    }

    public ProductInfo getInfoForProduct(Product product) {
        return usedProducts.computeIfAbsent(product.getId(), productId ->
                new ProductInfo(productId, product.getName(), product.getStockPrice()));
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
}
