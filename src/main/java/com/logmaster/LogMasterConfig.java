package com.logmaster;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

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
}
