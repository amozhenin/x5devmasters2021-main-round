package ru.dvorkin.x5.model;

public class EmployeeInfo {

    private final Integer employeeId;

    private EmployeeStatus status;

    private Integer statusChangeTick;

    private Integer lineId;

    private int workTicks, restTicks, readyTicks;

    public EmployeeInfo(Integer employeeId, EmployeeStatus status, Integer statusChangeTick, Integer lineId) {
        this.employeeId = employeeId;
        this.status = status;
        this.statusChangeTick = statusChangeTick;
        this.lineId = lineId;
        workTicks = 0;
        restTicks = 0;
        readyTicks = 0;
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
        this.status = status;
        this.statusChangeTick = statusChangeTick;
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
}
