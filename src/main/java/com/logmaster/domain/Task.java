package com.logmaster.domain;

import javax.annotation.Nullable;

import lombok.Getter;

@Getter
public class Task {
    private int id;
    private String description;
    private Integer itemID;
    @Nullable
    private int[] check;
    @Nullable
    private Integer count;
}
