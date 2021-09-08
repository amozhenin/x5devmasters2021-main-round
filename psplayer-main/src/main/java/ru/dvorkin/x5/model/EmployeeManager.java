package ru.dvorkin.x5.model;

import lombok.extern.slf4j.Slf4j;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.CheckoutLine;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.CurrentWorldResponse;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.Employee;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.HireEmployeeCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class EmployeeManager {

    private final List<EmployeeInfo> employeeInfo;
    private int noEmployeeOnLineTicksCount;

    private static final int WORK_INTERVAL = 480;
    private static final int REST_INTERVAL = 960;

    public EmployeeManager() {
        this.employeeInfo = new ArrayList<>();
        this.noEmployeeOnLineTicksCount = 0;
    }

    public HireEmployeeCommand.ExperienceEnum getUsedExperience() {
        return HireEmployeeCommand.ExperienceEnum.SENIOR;
    }

    public Integer getMaxEfficiency() {
        return getEfficiency(getUsedExperience());
    }

    private Integer getEfficiency(HireEmployeeCommand.ExperienceEnum experience) {
        switch (experience) {
            case JUNIOR:
                return 2;
            case MIDDLE:
                return 4;
            case SENIOR:
                return 6;
            default:
                return 1;
        }
    }

    public void syncWithWorld(CurrentWorldResponse world) {
        Integer currentTick = world.getCurrentTick();
        List<Employee> employeeList = world.getEmployees();
        if (employeeList.size() > employeeInfo.size()) {
            //add new employees
            for (Employee employee : employeeList) {
                Optional<EmployeeInfo> info =
                        employeeInfo
                                .stream()
                                .filter(data -> data.getEmployeeId().equals(employee.getId()))
                                .findFirst();
                if (info.isEmpty()) {
                    Optional<CheckoutLine> lineOpt = findCheckOutLineByEmployeeId(world, employee.getId());
                    info = lineOpt
                            .map(checkoutLine -> new EmployeeInfo(employee.getId(), EmployeeStatus.WORKING, currentTick, checkoutLine.getId()))
                            .or(() -> Optional.of(new EmployeeInfo(employee.getId(), EmployeeStatus.READY_TO_WORK, currentTick, null)));
                    employeeInfo.add(info.get());
                }
            }
        }
        for (EmployeeInfo info : employeeInfo) {
            Optional<CheckoutLine> lineOpt = findCheckOutLineByEmployeeId(world, info.getEmployeeId());
            switch(info.getStatus()) {
                case WORKING:
                    if (lineOpt.isEmpty()) {
                        info.setStatus(EmployeeStatus.REST, currentTick);
                    }
                    break;
                case REST:
                    if (lineOpt.isPresent()) {
                        info.setStatus(EmployeeStatus.WORKING, currentTick);
                    } else if (currentTick - info.getStatusChangeTick() >= REST_INTERVAL) {
                        info.setStatus(EmployeeStatus.READY_TO_WORK, currentTick);
                    }
                    break;
                case READY_TO_WORK:
                    if (lineOpt.isPresent()) {
                        info.setStatus(EmployeeStatus.WORKING, currentTick);
                    }
                    break;
            }
        }
        for (CheckoutLine line : world.getCheckoutLines()) {
            if (line.getEmployeeId() == null) {
                noEmployeeOnLineTicksCount++;
            }
        }
    }

    public int getNoEmployeeOnLineTicksCount() {
        return noEmployeeOnLineTicksCount;
    }

    public void printEmployeeStatusStatistic() {
        log.info(" # of ticks with lines without employees = " + getNoEmployeeOnLineTicksCount());
        for (EmployeeInfo info : employeeInfo) {
            log.info(" employee #" + info.getEmployeeId() + ", worked = " + info.getWorkTicks() +
                    ", rested = " + info.getRestTicks() + ", ready = " + info.getReadyTicks());
        }
    }

    public void endGameStatusUpdate(Integer currentTick) {
        for (EmployeeInfo info : employeeInfo) {
            info.setStatus(EmployeeStatus.GAME_OVER, currentTick);
        }
    }
    private Optional<CheckoutLine> findCheckOutLineByEmployeeId(CurrentWorldResponse world, Integer employeeId) {
        Optional<CheckoutLine> lineOpt =
                world.getCheckoutLines()
                        .stream()
                        .filter(line -> employeeId.equals(line.getEmployeeId()))
                        .findFirst();
        return lineOpt;
    }
}
