package ru.dvorkin.x5.model;

import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.HireEmployeeCommand;

public class EmployeeManager {

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
}
