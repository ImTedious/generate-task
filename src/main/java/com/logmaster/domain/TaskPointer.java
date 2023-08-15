package com.logmaster.domain;

import com.logmaster.domain.Task;
import com.logmaster.domain.TaskTier;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TaskPointer {

    private TaskTier taskTier;
    private Task task;
}
