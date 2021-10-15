package ru.hilariousstartups.javaskills.psplayer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import ru.dvorkin.x5.model.EmployeeInfo;
import ru.dvorkin.x5.model.EmployeeManager;
import ru.dvorkin.x5.model.ProductInfo;
import ru.dvorkin.x5.model.ProductManager;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.ApiClient;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.ApiException;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.api.PerfectStoreEndpointApi;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.*;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class PerfectStorePlayer implements ApplicationListener<ApplicationReadyEvent> {

    private String serverUrl;
    private ProductManager productManager;
    private EmployeeManager employeeManager;

    public PerfectStorePlayer(@Value("${rs.endpoint:http://localhost:9080}") String serverUrl) {
        this.serverUrl = serverUrl;
        this.productManager = new ProductManager();
        this.employeeManager = new EmployeeManager();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(serverUrl);

        PerfectStoreEndpointApi psApiClient = new PerfectStoreEndpointApi(apiClient);

        CurrentWorldResponse currentWorldResponse = awaitServer(psApiClient);
        try {
            boolean stopSpamBuys = false;
            do {
                if (currentWorldResponse == null) {
                    currentWorldResponse = psApiClient.loadWorld();
                }
                final int currentTick = currentWorldResponse.getCurrentTick();
                employeeManager.syncWithWorld(currentWorldResponse);
                productManager.syncWithWorld(currentWorldResponse);
                CurrentTickRequest request = new CurrentTickRequest();

                List<HireEmployeeCommand> hireEmployeeCommands = new ArrayList<>();
                List<SetOnCheckoutLineCommand> setOnCheckoutLineCommands = new ArrayList<>();

                List<FireEmployeeCommand> fireEmployeeCommands = new ArrayList<>();

                if (!employeeManager.isGoodTeamFilled()) {
                    for (int i = 0; i < employeeManager.getHireBatch(); i++) {
                        HireEmployeeCommand hireEmployeeCommand = new HireEmployeeCommand();
                        hireEmployeeCommand.setExperience(employeeManager.getUsedExperience());
                        hireEmployeeCommands.add(hireEmployeeCommand);
                    }
                }

                for (EmployeeInfo info : employeeManager.getToFireTeam()) {
                    if (info.getFireTick() == null) {
                        log.warn("fire tick is null, id = " + info.getEmployeeId() + ", exp = " + info.getExperience());
                    } else if (info.getFireTick() <= currentTick) {
                        FireEmployeeCommand command = new FireEmployeeCommand();
                        command.setEmployeeId(info.getEmployeeId());
                        fireEmployeeCommands.add(command);
                    }
                }

                for (EmployeeInfo info : employeeManager.getGoodTeam()) {
                    if (info.getNextShotTick() != null && info.getNextShotTick() <= currentTick) {
                        SetOnCheckoutLineCommand command = new SetOnCheckoutLineCommand();
                        command.setCheckoutLineId(info.getLineId());
                        command.setEmployeeId(info.getEmployeeId());
                        setOnCheckoutLineCommands.add(command);
                    }
                }

                // Смотрим на каких кассах нет кассира (либо не был назначен, либо ушел с кассы отдыхать), нанимаем новых кассиров и ставим на эти кассы.
                // Нанимаем самых опытных!
//                currentWorldResponse.getCheckoutLines().stream().filter(line -> line.getEmployeeId() == null).forEach(line -> {
//                    EmployeeInfo info = employeeManager.findReadyEmployeeForLine(line.getId());
//                    if (info != null) {
//                        SetOnCheckoutLineCommand command = new SetOnCheckoutLineCommand();
//                        command.setCheckoutLineId(line.getId());
//                        command.setEmployeeId(info.getEmployeeId());
//                        setOnCheckoutLineCommands.add(command);
//                    } else if (!employeeManager.aboutToHaveReadyEmployee(currentTick)) {
//                        HireEmployeeCommand hireEmployeeCommand = new HireEmployeeCommand();
//                        hireEmployeeCommand.setCheckoutLineId(line.getId());
//                        hireEmployeeCommand.setExperience(employeeManager.getUsedExperience());
//                        hireEmployeeCommands.add(hireEmployeeCommand);
//                    } //else do nothing
//                });

                request.setHireEmployeeCommands(hireEmployeeCommands);
                request.setOnCheckoutLineCommands(setOnCheckoutLineCommands);

                request.setFireEmployeeCommands(fireEmployeeCommands);

                 // готовимся закупать товар на склад и выставлять его на полки
                ArrayList<BuyStockCommand> buyStockCommands = new ArrayList<>();
                request.setBuyStockCommands(buyStockCommands);

                ArrayList<PutOnRackCellCommand> putOnRackCellCommands = new ArrayList<>();
                request.setPutOnRackCellCommands(putOnRackCellCommands);

                List<Product> stock = currentWorldResponse.getStock();
                List<RackCell> rackCells = currentWorldResponse.getRackCells();

                boolean doBatchBuy = false;
                for (Integer productId : productManager.getUsedProductIds()) {
                    Integer rackId = productManager.getRackForProductId(productId);
                    Integer quantity = productManager.getQuantityToBuy(productId, rackId, currentWorldResponse);
                    Product product = stock.get(productId - 1);
                    if (product.getInStock() == 0 && quantity > 0) {
                        doBatchBuy = true;
                    }
                }

                double buyProfit = 0.0;
                if (doBatchBuy) {
                    for (Integer productId : productManager.getUsedProductIds()) {
                        Integer rackId = productManager.getRackForProductId(productId);
                        Integer quantity = productManager.getQuantityToBuy(productId, rackId, currentWorldResponse);
                        ProductInfo info = productManager.getUnsafeInfoForProductId(productId);
                        if (quantity > 0) {
                            BuyStockCommand command = new BuyStockCommand();
                            command.setProductId(productId);
                            command.setQuantity(quantity);
                            buyProfit += (info.getSellPrice() - info.getStockPrice()) * quantity;
                            buyStockCommands.add(command);
                        }
                    }
                    if (buyProfit < 5000.0) {
                        if (!stopSpamBuys) {
                            log.info("buy rejection, tick = " + currentTick + ", buyProfit = " + buyProfit);
                            stopSpamBuys = true;
                        }
                        buyStockCommands.clear();
                    }
                }

                if (buyStockCommands.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("buy, tick = ").append(currentTick).append(", count = ").append(buyStockCommands.size());
                    for (BuyStockCommand command: buyStockCommands) {
                        sb.append(" | productId = ").append(command.getProductId()).append(", quantity = ").append(command.getQuantity());
                        ProductInfo info = productManager.getUnsafeInfoForProductId(command.getProductId());
                        info.addStock(command.getQuantity());
                    }
                    sb.append(" || buyProfit = ").append(buyProfit);
                    log.info(sb.toString());
                }

                for (Integer productId : productManager.getUsedProductIds()) {
                    ProductInfo info = productManager.getUnsafeInfoForProductId(productId);
                    if (info.getInStock() == 0 && info.getInRack() == 0 && currentTick > 1) {
                        if (!info.isStopSpam()) {
                            log.info(" all is out, id = " + info.getProductId() + ", rack = " +
                                    productManager.getRackForProductId(productId) +", tick = " + currentTick + ", sold = " + info.getSold());
                            info.stopSpam();
                        }
                    }
                }

                //adding rock
                if (currentTick == currentWorldResponse.getTickCount() - 5 && productManager.isRockEnabled()) {
                    for (Integer productId : productManager.getUsedProductIds()) {
                        BuyStockCommand command = new BuyStockCommand();
                        command.setProductId(productId);
                        command.setQuantity(ProductManager.ROCK_QUANTITY);
                        buyStockCommands.add(command);
                        productManager.getUnsafeInfoForProductId(productId).addStock(ProductManager.ROCK_QUANTITY);
                    }
                }


                for (RackCell rack : rackCells) {
                    if (rack.getProductId() == null || rack.getProductQuantity() < rack.getCapacity()) {
                        Integer quantity = rack.getProductQuantity() == null ? 0 : rack.getProductQuantity();
                        Product product = stock.get(productManager.getProductIdForRack(rack.getId()) - 1);
                        Integer quantityToAdd = Math.min(rack.getCapacity() - quantity, product.getInStock());
                        if (quantityToAdd > 0) {
                            PutOnRackCellCommand command = new PutOnRackCellCommand();
                            command.setProductId(product.getId());
                            command.setRackCellId(rack.getId());
                            command.setProductQuantity(quantityToAdd);
                            command.setSellPrice(productManager.getSellPrice(product.getId(), product.getStockPrice()));
                            putOnRackCellCommands.add(command);
                        }
                    }
                }

                currentWorldResponse = psApiClient.tick(request);
                if (currentWorldResponse.isGameOver()) {
                    employeeManager.endGameStatusUpdate(currentWorldResponse.getCurrentTick());
                    productManager.syncWithWorld(currentWorldResponse);
                    employeeManager.printEmployeeStatusStatistic();
                    productManager.printProductStatistics();
                    printWorldEndData(currentWorldResponse);
                }
            }
            while (!currentWorldResponse.isGameOver());

            // Если пришел Game Over, значит все время игры закончилось. Пора считать прибыль
            log.info("Real score = " + (currentWorldResponse.getIncome() - currentWorldResponse.getSalaryCosts() - currentWorldResponse.getStockCosts() + productManager.getRockCost()));
            log.info("Я заработал " + (currentWorldResponse.getIncome() - currentWorldResponse.getSalaryCosts() - currentWorldResponse.getStockCosts()) + " руб.");

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            try {
                while (!currentWorldResponse.isGameOver()) {
                    CurrentTickRequest request = new CurrentTickRequest();
                    currentWorldResponse = psApiClient.tick(request);
                }
            } catch (Exception e1) {
                log.error(e.getMessage(), e);
            }
        }

    }

    private CurrentWorldResponse awaitServer(PerfectStoreEndpointApi psApiClient) {
        int awaitTimes = 60;
        int cnt = 0;
        CurrentWorldResponse response = null;
        boolean serverReady = false;
        do {
            try {
                cnt += 1;
                response = psApiClient.loadWorld();
                serverReady = true;
            } catch (ApiException e) {
                try {
                    Thread.currentThread().sleep(1000L);
                } catch (InterruptedException interruptedException) {
                    e.printStackTrace();
                }
            }
        } while (!serverReady && cnt < awaitTimes);
        return response;
    }

    private void printWorldStartData(CurrentWorldResponse world) {
        log.info("currentTick = " + world.getCurrentTick() + ", tickCount = " + world.getTickCount());
//        printCheckoutLinesInfo(world);
//        printOffersInfo(world);
        printCustomersInfo(world);
//        printRackCellInfo(world);
//        printProductInfo(world);
    }

    private void printWorldEndData(CurrentWorldResponse world) {
        printCustomersInfo(world);
    }


    private void printProductInfo(CurrentWorldResponse world) {
        log.info("products = " + world.getStock().size());
        for (Product product : world.getStock()) {
            log.info("id = " + product.getId() + ", name = " + product.getName() + ", stockPrice = " + product.getStockPrice());
        }
    }

    private void printRackCellInfo(CurrentWorldResponse world) {
        log.info("rackCells = " + world.getRackCells().size());
        for (RackCell rackCell : world.getRackCells()) {
            log.info("id = " + rackCell.getId() + ", visibility = " + rackCell.getVisibility() + ", capacity = " +
                    rackCell.getCapacity()
//                    + ", productId = " +
//                    (rackCell.getProductId() == null ? "null" : rackCell.getProductId().toString()) +
//                    ", productName = " + (rackCell.getProductName() == null ? "null" : rackCell.getProductName()) +
//                    ", productQuantity = " + (rackCell.getProductQuantity() == null ? "null" : rackCell.getProductQuantity().toString())
        );
        }
    }

    private void printOffersInfo(CurrentWorldResponse world) {
        log.info("offers = " + world.getRecruitmentAgency().size());
        for (EmployeeRecruitmentOffer offer : world.getRecruitmentAgency()) {
            log.info("type = " + offer.getEmployeeType() + ", exp = " + offer.getExperience() + ", salary = " + offer.getSalary());
        }
    }

    private void printCustomersInfo(CurrentWorldResponse world) {
        log.info("tick = " + world.getCurrentTick() + ", customers = " + world.getCustomers().size());
        int in_hall = 0;
        int wait_checkout = 0;
        int at_checkout = 0;
        int in_hall_size = 0;
        int wait_checkout_size = 0;
        int at_checkout_size = 0;
        Integer minId = null;
        for (Customer customer : world.getCustomers()) {
            if (minId == null) {
                minId = customer.getId();
            } else {
                if (customer.getId() < minId) {
                    minId = customer.getId();
                }
            }
            switch (customer.getMode()) {
                case IN_HALL:
                    in_hall++;
                    in_hall_size += customer.getBasket().size();
                    break;
                case AT_CHECKOUT:
                    at_checkout++;
                    at_checkout_size += customer.getBasket().size();
                    break;
                case WAIT_CHECKOUT:
                    wait_checkout++;
                    wait_checkout_size += customer.getBasket().size();
                    break;
            }
        }
        log.info("minId = " + minId);
        log.info("IN HALL =" + in_hall + ", total basket size = " + in_hall_size);
        log.info("AT CHECKOUT =" + at_checkout + ", total basket size = " + at_checkout_size);
        log.info("WAIT_CHECKOUT =" + wait_checkout + ", total basket size = " + wait_checkout_size);
    }

    private void printCheckoutLinesInfo(CurrentWorldResponse world) {
        log.info("checkoutLines = " + world.getCheckoutLines().size());
    }
}
