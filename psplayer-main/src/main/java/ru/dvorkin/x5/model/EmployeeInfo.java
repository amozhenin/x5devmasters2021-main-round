package ru.dvorkin.x5.model;

import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.Employee;

public class EmployeeInfo {

    private final Integer employeeId;

    private EmployeeStatus status;

    private Integer statusChangeTick;

    private Integer lineId;

    private final Integer experience;

    private final Integer salary;

    private final String firstName;

    private final String lastName;

    private int workTicks, restTicks, readyTicks;

    //planned fire tick
    private Integer fireTick;

    private Integer nextShotTick;

    public EmployeeInfo(Employee employee, EmployeeStatus status, Integer statusChangeTick, Integer lineId) {
        this.employeeId = employee.getId();
        this.status = status;
        this.statusChangeTick = statusChangeTick;
        this.lineId = lineId;
        workTicks = 0;
        restTicks = 0;
        readyTicks = 0;
        this.experience = employee.getExperience();
        this.salary = employee.getSalary();
        this.firstName = employee.getFirstName();
        this.lastName = employee.getLastName();
        this.fireTick = null;
        this.nextShotTick = null;
    }

    public Integer getEmployeeId() {
        return this.employeeId;
    }

    public Integer getLineId() {
        return this.lineId;
    }

    public EmployeeStatus getStatus() {
        return this.status;
    }

    public void setStatus(EmployeeStatus status, Integer statusChangeTick) {
        updateStats(statusChangeTick);
        if (status != EmployeeStatus.GAME_OVER) {
            this.status = status;
            this.statusChangeTick = statusChangeTick;
        }
    }

    private void updateStats(Integer statusChangeTick) {
        int delta = statusChangeTick - this.statusChangeTick;
        switch (this.status) {
            case REST:
                restTicks += delta;
                break;
            case WORKING:
                workTicks += delta;
                break;
            case READY_TO_WORK:
                readyTicks += delta;
                break;
        }
    }

    public Integer getStatusChangeTick() {
        return this.statusChangeTick;
    }

    public int getReadyTicks() {
        return this.readyTicks;
    }

    public int getRestTicks() {
        return this.restTicks;
    }

    public int getWorkTicks() {
        return this.workTicks;
    }

    public void setLineId(Integer lineId) {
        this.lineId = lineId;
    }

    public Integer getSalary() {
        return salary;
    }

    public Integer getExperience() {
        return experience;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Integer getFireTick() {
        return fireTick;
    }

    public void setFireTick(Integer fireTick) {
        this.fireTick = fireTick;
    }

    public Integer getNextShotTick() {
        return nextShotTick;
    }

    public void setNextShotTick(Integer nextShotTick) {
        this.nextShotTick = nextShotTick;
    }
}
