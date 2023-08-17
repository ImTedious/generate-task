package com.logmaster.domain;

public enum TaskTier {

    NONE("None"),
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
    ELITE("Elite"),
    MASTER("Master");

    public final String displayName;

    TaskTier(String displayName) {
        this.displayName = displayName;
    }
}
