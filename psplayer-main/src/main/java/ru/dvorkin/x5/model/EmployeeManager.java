package ru.dvorkin.x5.model;

import lombok.extern.slf4j.Slf4j;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.CheckoutLine;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.CurrentWorldResponse;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.Employee;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.HireEmployeeCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class EmployeeManager {

    private final List<EmployeeInfo> employeeInfo;
    private int noEmployeeOnLineTicksCount;

    public static final int WORK_INTERVAL = 480;
    public static final int REST_INTERVAL = 960;
    private static final int CLOSE_INTERVAL = -5;

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
                            .map(checkoutLine -> new EmployeeInfo(employee, EmployeeStatus.WORKING, currentTick, checkoutLine.getId()))
                            .or(() -> Optional.of(new EmployeeInfo(employee, EmployeeStatus.READY_TO_WORK, currentTick, null)));
                    employeeInfo.add(info.get());
                } else {
                    EmployeeInfo i = info.get();
                    //check salary and experience does not change
                    if (!employee.getExperience().equals(i.getExperience())) {
                        log.warn("EXPERIENCE CHANGES " + i.getExperience() + "->" + employee.getExperience());
                        i.setExperience(employee.getExperience());
                    }
                    if (!employee.getSalary().equals(i.getSalary())) {
                        log.warn("SALARY CHANGES " + i.getSalary() + "->" + employee.getSalary());
                        i.setSalary(employee.getSalary());
                    }
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
                        info.setLineId(lineOpt.get().getId());
                    } else if (currentTick - info.getStatusChangeTick() >= REST_INTERVAL) {
                        info.setStatus(EmployeeStatus.READY_TO_WORK, currentTick);
                    }
                    break;
                case READY_TO_WORK:
                    if (lineOpt.isPresent()) {
                        info.setStatus(EmployeeStatus.WORKING, currentTick);
                        info.setLineId(lineOpt.get().getId());
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
                    ", rested = " + info.getRestTicks() + ", ready = " + info.getReadyTicks() + ", experience = " +
                    info.getExperience());
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

//    public EmployeeInfo findReadyEmployeeForLine(Integer id) {
//        List<EmployeeInfo> readyList = employeeInfo
//                .stream()
//                .filter(info -> info.getStatus() == EmployeeStatus.READY_TO_WORK)
//                .collect(Collectors.toList());
//        if (readyList.isEmpty()) {
//            return null;
//        }
//        if (readyList.size() == 1) {
//            return readyList.get(0);
//        }
//        Optional<EmployeeInfo> infoOpt = readyList.stream().filter(info -> id.equals(info.getLineId())).findFirst();
//        return infoOpt.orElseGet(() -> readyList.get(0));
//    }

    public List<EmployeeInfo> findReadyEmployees() {
        List<EmployeeInfo> readyList = employeeInfo
                .stream()
                .filter(info -> info.getStatus() == EmployeeStatus.READY_TO_WORK)
                .collect(Collectors.toList());
        return readyList;
//        if (readyList.isEmpty()) {
//            return null;
//        }
//        if (readyList.size() == 1) {
//            return readyList.get(0);
//        }
//        Optional<EmployeeInfo> infoOpt = readyList.stream().filter(info -> id.equals(info.getLineId())).findFirst();
//        return infoOpt.orElseGet(() -> readyList.get(0));
    }


//    public boolean aboutToHaveReadyEmployee(Integer currentTick) {
//        return employeeInfo.stream().anyMatch(info -> (info.getStatus() == EmployeeStatus.REST) &&
//                (currentTick - info.getStatusChangeTick() - REST_INTERVAL >= CLOSE_INTERVAL));
//    }
//
//    public boolean aboutToLeave(Integer currentTick, Integer lineId) {
//        return employeeInfo.stream().anyMatch(info -> (info.getStatus() == EmployeeStatus.WORKING) &&
//                (info.getLineId().equals(lineId)) && (currentTick - info.getStatusChangeTick() - WORK_INTERVAL + 1 >= 0));
//    }
}
