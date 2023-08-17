package com.logmaster.domain;

import com.logmaster.domain.Task;
import com.logmaster.domain.TaskTier;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class SaveData {
    // We have to leave this here in case someone was running the old version of the plugin
    @Getter
    private HashMap<Integer, Integer> completedTasks = new HashMap<>();

    public Task currentTask;

    // New save data!
    @Getter
    private Map<TaskTier, Set<Integer>> progress = new HashMap<>();

    @Getter
    @Setter
    private TaskPointer activeTaskPointer;
    @Getter
    @Setter
    private TaskTier selectedTier;
}
