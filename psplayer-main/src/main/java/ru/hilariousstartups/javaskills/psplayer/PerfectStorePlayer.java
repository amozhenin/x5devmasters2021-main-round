package ru.hilariousstartups.javaskills.psplayer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import ru.dvorkin.x5.model.EmployeeInfo;
import ru.dvorkin.x5.model.EmployeeManager;
import ru.dvorkin.x5.model.ProductManager;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.ApiClient;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.ApiException;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.api.PerfectStoreEndpointApi;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

        log.info("Игрок готов. Подключаемся к серверу..");
        CurrentWorldResponse currentWorldResponse = awaitServer(psApiClient);
//        printWorldStartData(currentWorldResponse);
        log.info("Подключение к серверу успешно. Начинаем игру");
        try {
            int cnt = 0;
            do {
                cnt += 1;
//                if (cnt % 120 == 0) {
//                    log.info("Пройден " + cnt + " тик");
//                }

                if (currentWorldResponse == null) {
                    currentWorldResponse = psApiClient.loadWorld();
                }
                final Integer currentTick = currentWorldResponse.getCurrentTick();
                employeeManager.syncWithWorld(currentWorldResponse);

                CurrentTickRequest request = new CurrentTickRequest();

                List<HireEmployeeCommand> hireEmployeeCommands = new ArrayList<>();
                List<SetOnCheckoutLineCommand> setOnCheckoutLineCommands = new ArrayList<>();

                // Смотрим на каких кассах нет кассира (либо не был назначен, либо ушел с кассы отдыхать), нанимаем новых кассиров и ставим на эти кассы.
                // Нанимаем самых опытных!
                currentWorldResponse.getCheckoutLines().stream().filter(line -> line.getEmployeeId() == null).forEach(line -> {
                    EmployeeInfo info = employeeManager.findReadyEmployeeForLine(line.getId());
                    if (info != null) {
                        SetOnCheckoutLineCommand command = new SetOnCheckoutLineCommand();
                        command.setCheckoutLineId(line.getId());
                        command.setEmployeeId(info.getEmployeeId());
                        setOnCheckoutLineCommands.add(command);
                    } else if (!employeeManager.aboutToHaveReadyEmployee(currentTick)) {
                        HireEmployeeCommand hireEmployeeCommand = new HireEmployeeCommand();
                        hireEmployeeCommand.setCheckoutLineId(line.getId());
                        hireEmployeeCommand.setExperience(employeeManager.getUsedExperience());
                        hireEmployeeCommands.add(hireEmployeeCommand);
                    } //else do nothing
                });
                request.setHireEmployeeCommands(hireEmployeeCommands);
                request.setOnCheckoutLineCommands(setOnCheckoutLineCommands);

                // готовимся закупать товар на склад и выставлять его на полки
                ArrayList<BuyStockCommand> buyStockCommands = new ArrayList<>();
                request.setBuyStockCommands(buyStockCommands);

                ArrayList<PutOnRackCellCommand> putOnRackCellCommands = new ArrayList<>();
                request.setPutOnRackCellCommands(putOnRackCellCommands);

                List<Product> stock = currentWorldResponse.getStock();
                List<RackCell> rackCells = currentWorldResponse.getRackCells();

                for (Integer productId : productManager.getUsedProductIds()) {
                    Integer quantity = productManager.getQuantityToBuy(productId);
                    if ((stock.get(productId - 1).getInStock() == 0) &&
                            ((currentWorldResponse.getTickCount() - currentTick) > (quantity / employeeManager.getMaxEfficiency()))) {
                        BuyStockCommand command = new BuyStockCommand();
                        command.setProductId(productId);
                        command.setQuantity(quantity);
                        buyStockCommands.add(command);
                    }
                }
                for (RackCell rack : currentWorldResponse.getRackCells()) {
                    if (rack.getProductId() == null || rack.getProductQuantity() < rack.getCapacity()) {
                        Integer quantity = rack.getProductQuantity() == null ? 0 : rack.getProductQuantity();
                        Product product = stock.get(productManager.getProductIdForRack(rack.getId()) - 1);
                        Integer quantityToAdd = Math.min(rack.getCapacity() - quantity, product.getInStock());
                        PutOnRackCellCommand command = new PutOnRackCellCommand();
                        command.setProductId(product.getId());
                        command.setRackCellId(rack.getId());
                        command.setProductQuantity(quantityToAdd);
                        command.setSellPrice(productManager.getSellPrice(product.getId(), product.getStockPrice()));
                        putOnRackCellCommands.add(command);
                    }
                }

                // Обходим торговый зал и смотрим какие полки пустые. Выставляем на них товар.
//                currentWorldResponse.getRackCells().stream().filter(rack -> rack.getProductId() == null || rack.getProductQuantity().equals(0)).forEach(rack -> {
//                    Product producttoPutOnRack = null;
//                    if (rack.getProductId() == null) {
//                        List<Integer> productsOnRack = rackCells.stream().filter(r -> r.getProductId() != null).map(RackCell::getProductId).collect(Collectors.toList());
//                        productsOnRack.addAll(putOnRackCellCommands.stream().map(c -> c.getProductId()).collect(Collectors.toList()));
//                        producttoPutOnRack = stock.stream().filter(product -> !productsOnRack.contains(product.getId())).findFirst().orElse(null);
//                    }
//                    else {
//                        producttoPutOnRack = stock.stream().filter(product -> product.getId().equals(rack.getProductId())).findFirst().orElse(null);
//                    }
//
//                    Integer productQuantity = rack.getProductQuantity();
//                    if (productQuantity == null) {
//                        productQuantity = 0;
//                    }
//
//                    // Вначале закупим товар на склад. Каждый ход закупать товар накладно, но ведь это тестовый игрок.
//                    Integer orderQuantity = rack.getCapacity() - productQuantity;
//                    if (producttoPutOnRack.getInStock() < orderQuantity) {
//                        BuyStockCommand command = new BuyStockCommand();
//                        command.setProductId(producttoPutOnRack.getId());
//                        command.setQuantity(10000);
//                        buyStockCommands.add(command);
//                    }
//
//                    // Далее разложим на полки. И сформируем цену. Накинем 10 рублей к оптовой цене
//                    PutOnRackCellCommand command = new PutOnRackCellCommand();
//                    command.setProductId(producttoPutOnRack.getId());
//                    command.setRackCellId(rack.getId());
//                    command.setProductQuantity(orderQuantity);
//                    if (producttoPutOnRack.getSellPrice() == null) {
//                        command.setSellPrice(producttoPutOnRack.getStockPrice() * 1.2);
//                    }
//                    putOnRackCellCommands.add(command);
//
//                });

                currentWorldResponse = psApiClient.tick(request);
                if (currentWorldResponse.isGameOver()) {
                    employeeManager.endGameStatusUpdate(currentWorldResponse.getCurrentTick());
                    employeeManager.printEmployeeStatusStatistic();
                    printWorldEndData(currentWorldResponse);
                }
            }
            while (!currentWorldResponse.isGameOver());

            // Если пришел Game Over, значит все время игры закончилось. Пора считать прибыль
            log.info("Я заработал " + (currentWorldResponse.getIncome() - currentWorldResponse.getSalaryCosts() - currentWorldResponse.getStockCosts()) + "руб.");

        } catch (ApiException e) {
            log.error(e.getMessage(), e);
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

//    private void printWorldStartData(CurrentWorldResponse world) {
//        log.info("currentTick = " + world.getCurrentTick() + ", tickCount = " + world.getTickCount());
//        printCheckoutLinesInfo(world);
//        printEmployeesInfo(world);
//        printOffersInfo(world);
//        printCustomersInfo(world);
//        printRackCellInfo(world);
//        printProductInfo(world);
//    }

    private void printWorldEndData(CurrentWorldResponse world) {
        log.info("currentTick = " + world.getCurrentTick() + ", tickCount = " + world.getTickCount());
//        printCheckoutLinesInfo(world);
//        printEmployeesInfo(world);
//        printOffersInfo(world);
        printCustomersInfo(world);
//        printRackCellInfo(world);
        printProductInfo(world);
    }


    private void printProductInfo(CurrentWorldResponse world) {
        log.info("products = " + world.getStock().size());
        for (Product product : world.getStock()) {
            log.info("id = " + product.getId() + ", name = " + product.getName() + ", inStock = " + product.getInStock() +
                    ", stockPrice = " + product.getStockPrice());
        }
    }

    private void printRackCellInfo(CurrentWorldResponse world) {
        log.info("rackCells = " + world.getRackCells().size());
        for (RackCell rackCell : world.getRackCells()) {
            log.info("id = " + rackCell.getId() + ", visibility = " + rackCell.getVisibility() + ", capacity = " +
                    rackCell.getCapacity() + ", productId = " +
                    (rackCell.getProductId() == null ? "null" : rackCell.getProductId().toString()) +
                    ", productName = " + (rackCell.getProductName() == null ? "null" : rackCell.getProductName()) +
                    ", productQuantity = " + (rackCell.getProductQuantity() == null ? "null" : rackCell.getProductQuantity().toString()));
        }
    }

    private void printOffersInfo(CurrentWorldResponse world) {
        log.info("offers = " + world.getRecruitmentAgency().size());
        for (EmployeeRecruitmentOffer offer : world.getRecruitmentAgency()) {
            log.info("type = " + offer.getEmployeeType() + ", exp = " + offer.getExperience() + ", salary = " + offer.getSalary());
        }
    }

    private void printCustomersInfo(CurrentWorldResponse world) {
        log.info("customers = " + world.getCustomers().size());
        for (Customer customer : world.getCustomers()) {
            log.info("id = " + customer.getId() + ", mode = " + customer.getMode() + ", goods = " + customer.getBasket().size());
        }
    }

    private void printEmployeesInfo(CurrentWorldResponse world) {
        log.info("employees = " + world.getEmployees().size());
        for (Employee employee : world.getEmployees()) {
            log.info("id = " + employee.getId() + ", firstName = " + employee.getFirstName() + ", lastName = " +
                    employee.getLastName() + ", experience = " + employee.getExperience() + ", salary = " + employee.getSalary());
        }
    }

    private void printCheckoutLinesInfo(CurrentWorldResponse world) {
        log.info("checkoutLines = " + world.getCheckoutLines().size());
        for (CheckoutLine checkoutLine : world.getCheckoutLines()) {
            log.info("id = " + checkoutLine.getId() + ", employeeId = " +
                    (checkoutLine.getEmployeeId() == null ? "null": checkoutLine.getEmployeeId().toString())
                    + ", customerId = " + (checkoutLine.getCustomerId() == null ? "null" : checkoutLine.getCustomerId().toString()));
        }
    }

//    private void syncWorld(CurrentWorldResponse currentWorldResponse, )

}
