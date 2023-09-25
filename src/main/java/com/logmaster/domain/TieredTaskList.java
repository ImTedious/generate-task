package com.logmaster.domain;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class TieredTaskList {

    private List<Task> easy;
    private List<Task> medium;
    private List<Task> hard;
    private List<Task> elite;
    private List<Task> master;

    public List<Task> getForTier(TaskTier tier) {
        if (tier == null) {
            return Collections.emptyList();
        }
        switch (tier) {
            case EASY: return easy;
            case MEDIUM: return medium;
            case HARD: return hard;
            case ELITE: return elite;
            case MASTER: return master;
            default: return Collections.emptyList();
        }
    }
}
