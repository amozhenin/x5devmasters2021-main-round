package ru.dvorkin.x5.model;

import lombok.extern.slf4j.Slf4j;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.CheckoutLine;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.CurrentWorldResponse;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.Employee;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.HireEmployeeCommand;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class EmployeeManager {

    private final List<EmployeeInfo> employeeInfo;
    private final List<EmployeeInfo> goodTeam;
    private final List<EmployeeInfo> firedTeam;
    private final List<EmployeeInfo> toFireTeam;
    private int noEmployeeOnLineTicksCount;

    private EmployeeInfo workingOnLine1;
    private EmployeeInfo workingOnLine2;
    private EmployeeInfo restingOnLine1;
    private EmployeeInfo restingOnLine2;
    private EmployeeInfo nextOnLine1;
    private EmployeeInfo nextOnLine2;

    private static final int WORK_INTERVAL = 480;
    private static final int REST_INTERVAL = 960;
    private static final int CLOSE_INTERVAL = -5;
    private static final int BEST_TEAM_SIZE = 6;
    private static final int ONE_SHOT_TEAM_SIZE = 2;

    public EmployeeManager() {
        this.employeeInfo = new ArrayList<>();
        this.goodTeam = new ArrayList<>();
        this.firedTeam = new ArrayList<>();
        this.toFireTeam = new ArrayList<>();

        this.noEmployeeOnLineTicksCount = 0;

        workingOnLine1 = null;
        workingOnLine2 = null;
        restingOnLine1 = null;
        restingOnLine2 = null;
        nextOnLine1 = null;
        nextOnLine2 = null;
    }

    public HireEmployeeCommand.ExperienceEnum getUsedExperience() {
        return HireEmployeeCommand.ExperienceEnum.SENIOR;
    }

    private int getThreshold() {
        return 95;
    }

    public boolean isGoodTeamFilled() {
        return goodTeam.size() >= BEST_TEAM_SIZE;
    }

    public boolean isShotTeamFilled() {
        return goodTeam.size() >= ONE_SHOT_TEAM_SIZE;
    }

//    public Integer getMaxEfficiency() {
//        return getEfficiency(getUsedExperience());
//    }
//
//    private Integer getEfficiency(HireEmployeeCommand.ExperienceEnum experience) {
//        switch (experience) {
//            case JUNIOR:
//                return 2;
//            case MIDDLE:
//                return 4;
//            case SENIOR:
//                return 6;
//            default:
//                return 1;
//        }
//    }

    public void syncWithWorld(CurrentWorldResponse world) {
        Integer currentTick = world.getCurrentTick();
        List<Employee> employeeList = world.getEmployees();

        List<EmployeeInfo> newList = new ArrayList<>();

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
                newList.add(info.get());
            }
        }
        //decision making
        if (!newList.isEmpty()) {
            List<EmployeeInfo> staging = new ArrayList<>();
            for (EmployeeInfo info : newList) {
                if (info.getExperience() < getThreshold()) {
                    toFireTeam.add(info);
                    info.setFireTick(currentTick);
                } else {
                    staging.add(info);
                }
            }
            //no firing on good team (yet)
            log.info("staging started, size = " + staging.size());
            int needed = BEST_TEAM_SIZE - goodTeam.size();
            if (staging.size() > needed) {
                staging.sort((a, b) -> b.getExperience() - a.getExperience());
                while (staging.size() > needed) {
                    EmployeeInfo info = staging.remove(needed);
                    info.setFireTick(currentTick);
                    toFireTeam.add(info);
                }
            }
            log.info("staging ended, size = " + staging.size());
            //adding to the good team
            for (EmployeeInfo info : staging) {
                goodTeam.add(info);
                if (nextOnLine1 == null) {
                    nextOnLine1 = info;
                    info.setNextShotTick(currentTick);
                    info.setLineId(1);
                } else if (nextOnLine2 == null) {
                    nextOnLine2 = info;
                    info.setNextShotTick(currentTick);
                    info.setLineId(2);
                } else if (restingOnLine1 == null) {
                    restingOnLine1 = info;
                    info.setLineId(1);
                    info.setNextShotTick(currentTick + WORK_INTERVAL - 5);
                } else if (restingOnLine2 == null) {
                    restingOnLine2 = info;
                    info.setLineId(2);
                    info.setNextShotTick(currentTick + WORK_INTERVAL - 5);
                } else if (workingOnLine1 == null) {
                    workingOnLine1 = info;
                    info.setLineId(1); //kinda working
                    info.setNextShotTick(currentTick + REST_INTERVAL - 5);
                } else if (workingOnLine2 == null) {
                    workingOnLine2 = info;
                    info.setLineId(2); //kinda working
                    info.setNextShotTick(currentTick + REST_INTERVAL - 5);
                }
            }
        }
        //update fired employees
        Iterator<EmployeeInfo> iter = toFireTeam.iterator();
        while(iter.hasNext()) {
            EmployeeInfo info = iter.next();
            Optional<Employee> eOpt = employeeList.stream().filter(e -> e.getId().equals(info.getEmployeeId())).findFirst();
            if (eOpt.isEmpty()) {
                info.setStatus(EmployeeStatus.FIRED, currentTick);
                iter.remove();
                firedTeam.add(info);
            }
        }

        for (EmployeeInfo info : goodTeam) {
            Optional<CheckoutLine> lineOpt = findCheckOutLineByEmployeeId(world, info.getEmployeeId());
            switch (info.getStatus()) {
                case WORKING:
                    if (lineOpt.isEmpty()) {
                        info.setStatus(EmployeeStatus.REST, currentTick);
                        info.setNextShotTick(currentTick + REST_INTERVAL - 2);
                    }
                    if (info.getLineId() == 1) {
                        if (workingOnLine1 == info) {
                            workingOnLine1 = nextOnLine1;
                            nextOnLine1 = restingOnLine1;
                            restingOnLine1 = info;
                        } //else we already did this
                    } else {
                        if (workingOnLine2 == info) {
                            workingOnLine2 = nextOnLine2;
                            nextOnLine2 = restingOnLine2;
                            restingOnLine2 = info;
                        }
                    }
                    break;
                case REST:
                    if (lineOpt.isPresent()) {
                        info.setStatus(EmployeeStatus.WORKING, currentTick);
                        info.setNextShotTick(null);
                        info.setLineId(lineOpt.get().getId());
                        if (info.getLineId() == 1) {
                            if (nextOnLine1 == info) {
                                nextOnLine1 = restingOnLine1;
                                restingOnLine1 = workingOnLine1;
                                workingOnLine1 = info;
                            } //else we already did this
                        } else {
                            if (nextOnLine2 == info) {
                                nextOnLine2 = restingOnLine2;
                                restingOnLine2 = workingOnLine2;
                                workingOnLine2 = info;
                            } //else we already did this
                        }
                    } else if (currentTick - info.getStatusChangeTick() >= REST_INTERVAL) {
                        info.setStatus(EmployeeStatus.READY_TO_WORK, currentTick);
                    }
                    break;
                case READY_TO_WORK:
                    if (lineOpt.isPresent()) {
                        info.setStatus(EmployeeStatus.WORKING, currentTick);
                        info.setNextShotTick(null);
                        info.setLineId(lineOpt.get().getId());
                        if (info.getLineId() == 1) {
                            if (nextOnLine1 == info) {
                                nextOnLine1 = restingOnLine1;
                                restingOnLine1 = workingOnLine1;
                                workingOnLine1 = info;
                            } //else we already did this
                        } else {
                            if (nextOnLine2 == info) {
                                nextOnLine2 = restingOnLine2;
                                restingOnLine2 = workingOnLine2;
                                workingOnLine2 = info;
                            } //else we already did this
                        }
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
        for (EmployeeInfo info : goodTeam) {
            log.info(" employee #" + info.getEmployeeId() + ", worked = " + info.getWorkTicks() +
                    ", rested = " + info.getRestTicks() + ", ready = " + info.getReadyTicks() + ", experience = " +
                    info.getExperience() + ", status = " + info.getStatus() + ", setOn = " + info.getStatusChangeTick());
        }
        log.info("need to fire " + toFireTeam.size());
        log.info("fired " + firedTeam.size());
    }

    public void endGameStatusUpdate(Integer currentTick) {
        for (EmployeeInfo info : goodTeam) {
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

    public List<EmployeeInfo> getToFireTeam() {
        return toFireTeam;
    }

    public List<EmployeeInfo> getGoodTeam() {
        return goodTeam;
    }

    public int getHireBatch() {
        return 30;
    }
}
