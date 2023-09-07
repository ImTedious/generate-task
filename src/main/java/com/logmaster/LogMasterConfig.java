package com.logmaster;

import com.logmaster.domain.TaskTier;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.camera.ControlFunction;

@ConfigGroup("log-master")
public interface LogMasterConfig extends Config
{

    @Range(
            min = 1000,
            max = 10000
    )
    @ConfigItem(
            keyName = "rollTime",
            name = "Roll Time",
            description = "How long new tasks will take to roll",
            position = 1
    )
    default int rollTime()
    {
        return 5000;
    }

    @ConfigItem(
            keyName = "rollPastCompleted",
            name = "Roll past completed",
            description = "When rolling tasks, include those you've already completed in the roll animation. Helpful when you're getting to the end of a tier!",
            position = 2
    )
    default boolean rollPastCompleted()
    {
        return false;
    }

    @ConfigItem(
            keyName = "hideBelow",
            name = "Hide Tasks Below",
            description = "Disabled the showing up/assigning of tasks at or below the specified tier",
            position = 3
    )
    default TaskTier hideBelow()
    {
        return TaskTier.EASY;
    }

    @ConfigItem(
            keyName = "loadRemoteTaskList",
            name = "Load remote task list",
            description = "Load the latest version of the tasklist, this will be updated more frequently than the default list bundled with the plugin",
            position = 4
    )
    default boolean loadRemoteTaskList()
    {
        return true;
    }
}
