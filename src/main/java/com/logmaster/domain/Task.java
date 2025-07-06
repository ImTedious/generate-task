package com.logmaster.domain;

import lombok.Getter;

@Getter
public class Task {
    private int id;
    private String description;
    private Integer itemID;
    private int[] check;
    private Integer count;
}
