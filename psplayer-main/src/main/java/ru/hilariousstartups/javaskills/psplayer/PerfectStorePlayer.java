package ru.hilariousstartups.javaskills.psplayer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
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

    public PerfectStorePlayer(@Value("${rs.endpoint:http://localhost:9080}") String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(serverUrl);

        PerfectStoreEndpointApi psApiClient = new PerfectStoreEndpointApi(apiClient);

        log.info("Игрок готов. Подключаемся к серверу..");
        CurrentWorldResponse currentWorldResponse = awaitServer(psApiClient);
        printWorldData(currentWorldResponse);
        log.info("Подключение к серверу успешно. Начинаем игру");
        try {
            int cnt = 0;
            do {
                cnt += 1;
                if (cnt % 120 == 0) {
                    log.info("Пройден " + cnt + " тик");
                }

                if (currentWorldResponse == null) {
                    currentWorldResponse = psApiClient.loadWorld();
                }

                CurrentTickRequest request = new CurrentTickRequest();

                List<HireEmployeeCommand> hireEmployeeCommands = new ArrayList<>();
                // Смотрим на каких кассах нет кассира (либо не был назначен, либо ушел с кассы отдыхать), нанимаем новых кассиров и ставим на эти кассы.
                // Нанимаем самых опытных!
                currentWorldResponse.getCheckoutLines().stream().filter(line -> line.getEmployeeId() == null).forEach(line -> {
                    HireEmployeeCommand hireEmployeeCommand = new HireEmployeeCommand();
                    hireEmployeeCommand.setCheckoutLineId(line.getId());
                    hireEmployeeCommand.setExperience(HireEmployeeCommand.ExperienceEnum.JUNIOR);
                    hireEmployeeCommands.add(hireEmployeeCommand);
                });
                request.setHireEmployeeCommands(hireEmployeeCommands);

                // готовимся закупать товар на склад и выставлять его на полки
                ArrayList<BuyStockCommand> buyStockCommands = new ArrayList<>();
                request.setBuyStockCommands(buyStockCommands);

                ArrayList<PutOnRackCellCommand> putOnRackCellCommands = new ArrayList<>();
                request.setPutOnRackCellCommands(putOnRackCellCommands);

                List<Product> stock = currentWorldResponse.getStock();
                List<RackCell> rackCells = currentWorldResponse.getRackCells();

                // Обходим торговый зал и смотрим какие полки пустые. Выставляем на них товар.
                currentWorldResponse.getRackCells().stream().filter(rack -> rack.getProductId() == null || rack.getProductQuantity().equals(0)).forEach(rack -> {
                    Product producttoPutOnRack = null;
                    if (rack.getProductId() == null) {
                        List<Integer> productsOnRack = rackCells.stream().filter(r -> r.getProductId() != null).map(RackCell::getProductId).collect(Collectors.toList());
                        productsOnRack.addAll(putOnRackCellCommands.stream().map(c -> c.getProductId()).collect(Collectors.toList()));
                        producttoPutOnRack = stock.stream().filter(product -> !productsOnRack.contains(product.getId())).findFirst().orElse(null);
                    }
                    else {
                        producttoPutOnRack = stock.stream().filter(product -> product.getId().equals(rack.getProductId())).findFirst().orElse(null);
                    }

                    Integer productQuantity = rack.getProductQuantity();
                    if (productQuantity == null) {
                        productQuantity = 0;
                    }

                    // Вначале закупим товар на склад. Каждый ход закупать товар накладно, но ведь это тестовый игрок.
                    Integer orderQuantity = rack.getCapacity() - productQuantity;
                    if (producttoPutOnRack.getInStock() < orderQuantity) {
                        BuyStockCommand command = new BuyStockCommand();
                        command.setProductId(producttoPutOnRack.getId());
                        command.setQuantity(100);
                        buyStockCommands.add(command);
                    }

                    // Далее разложим на полки. И сформируем цену. Накинем 10 рублей к оптовой цене
                    PutOnRackCellCommand command = new PutOnRackCellCommand();
                    command.setProductId(producttoPutOnRack.getId());
                    command.setRackCellId(rack.getId());
                    command.setProductQuantity(orderQuantity);
                    if (producttoPutOnRack.getSellPrice() == null) {
                        command.setSellPrice(producttoPutOnRack.getStockPrice() + 10);
                    }
                    putOnRackCellCommands.add(command);

                });

                currentWorldResponse = psApiClient.tick(request);

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

    private void printWorldData(CurrentWorldResponse world) {
        log.info("currentTick = " + world.getCurrentTick() + ", tickCount = " + world.getTickCount());
        log.info("checkoutLines = " + world.getCheckoutLines().size());
        for (CheckoutLine checkoutLine : world.getCheckoutLines()) {
            log.info("id = " + checkoutLine.getId() + ", employeeId = " +
                    (checkoutLine.getEmployeeId() == null ? "null": checkoutLine.getEmployeeId().toString())
                    + ", customerId = " + (checkoutLine.getCustomerId() == null ? "null" : checkoutLine.getCustomerId().toString()));
        }
        log.info("employees = " + world.getEmployees().size());
        for (Employee employee : world.getEmployees()) {
            log.info("id = " + employee.getId() + ", firstName = " + employee.getFirstName() + ", lastName = " +
                    employee.getLastName() + ", experience = " + employee.getExperience() + ", salary = " + employee.getSalary());
        }
        log.info("offers = " + world.getRecruitmentAgency().size());
        log.info("customers = " + world.getCustomers().size());
        log.info("rackCells = " + world.getRackCells().size());
        for (RackCell rackCell : world.getRackCells()) {
            log.info("id = " + rackCell.getId() + ", visibility = " + rackCell.getVisibility() + ", capacity = " +
                    rackCell.getCapacity() + ", productId = " +
                    (rackCell.getProductId() == null ? "null" : rackCell.getProductId().toString()) +
                    ", productName = " + (rackCell.getProductName() == null ? "null" : rackCell.getProductName()) +
                    ", productQuantity = " + (rackCell.getProductQuantity() == null ? "null" : rackCell.getProductQuantity().toString()));
        }
        log.info("products = " + world.getStock().size());
        for (Product product : world.getStock()) {
            log.info("id = " + product.getId() + ", name = " + product.getName() + ", inStock = " + product.getInStock() +
                    ", stockPrice = " + product.getStockPrice() + ", sellPrice = " + (product.getSellPrice() == null ? "null" : product.getSellPrice().toString());
        }
    }


}
