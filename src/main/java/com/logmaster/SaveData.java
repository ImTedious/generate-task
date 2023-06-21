package com.logmaster;

import lombok.Getter;

import java.util.HashMap;


public class SaveData {
    @Getter
    private HashMap<Integer, Integer> completedTasks = new HashMap<>();

    public Task currentTask;
}
