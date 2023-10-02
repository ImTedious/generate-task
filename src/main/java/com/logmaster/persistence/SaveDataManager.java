package com.logmaster.persistence;

import com.google.gson.reflect.TypeToken;
import com.logmaster.domain.SaveData;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskPointer;
import com.logmaster.domain.TaskTier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.slayer.SlayerConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.HashSet;
import java.util.Scanner;

import static com.logmaster.LogMasterConfig.CONFIG_GROUP;
import static com.logmaster.LogMasterConfig.SAVE_DATA_KEY;
import static net.runelite.http.api.RuneLiteAPI.GSON;

@Singleton
@Slf4j
public class SaveDataManager {

    private static final String DATA_FOLDER_NAME = "generate-task";

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    private SaveData saveData;

    public SaveData getSaveData() {
        if (saveData == null) {
            // First check if there's a save associated with the RL profile
            SaveData rlProfileSave = loadRLProfileSaveData();
            SaveData localSave = loadLocalPlayerSaveData();
            if (rlProfileSave != null) {
                saveData = rlProfileSave;
            } else if (localSave != null) {
                saveData = localSave;
            } else {
                saveData = initialiseSaveData();
            }
        }
        return saveData;
    }

    public void save() {
        String json = GSON.toJson(saveData);
        configManager.setRSProfileConfiguration(CONFIG_GROUP, SAVE_DATA_KEY, json);
    }

    public Task currentTask() {
        if (getSaveData().getActiveTaskPointer() == null) {
            return null;
        }
        return getSaveData().getActiveTaskPointer().getTask();
    }

    private SaveData loadRLProfileSaveData() {
        String saveDataJson = configManager.getRSProfileConfiguration(CONFIG_GROUP, SAVE_DATA_KEY, String.class);
        if (saveDataJson == null) {
            return null;
        }
        try {
            return GSON.fromJson(saveDataJson, new TypeToken<SaveData>() {}.getType());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private SaveData loadLocalPlayerSaveData() {
        File playerFolder = new File(RuneLite.RUNELITE_DIR, DATA_FOLDER_NAME);
        if (!playerFolder.exists()) {
            return null;
        }
        File playerFile = new File(playerFolder, client.getAccountHash() + ".txt");
        if (!playerFile.exists()) {
            return null;
        }
        try {
            String json = new Scanner(playerFile).useDelimiter("\\Z").next();
            SaveData loaded = GSON.fromJson(json, new TypeToken<SaveData>() {}.getType());
            for (TaskTier loopTier : TaskTier.values()) {
                if (!loaded.getProgress().containsKey(loopTier)) {
                    loaded.getProgress().put(loopTier, new HashSet<>());
                }
            }
            // Can get rid of this eventually
            if (!loaded.getCompletedTasks().isEmpty()) {
                loaded.getProgress().get(TaskTier.MASTER).addAll(loaded.getCompletedTasks().keySet());
            }
            if (loaded.currentTask != null) {
                TaskPointer taskPointer = new TaskPointer();
                taskPointer.setTask(loaded.currentTask);
                taskPointer.setTaskTier(TaskTier.MASTER);
                loaded.setActiveTaskPointer(taskPointer);
                loaded.currentTask = null;
            }
            return loaded;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private SaveData initialiseSaveData() {
        SaveData created = new SaveData();
        for (TaskTier loopTier : TaskTier.values()) {
            if (!created.getProgress().containsKey(loopTier)) {
                created.getProgress().put(loopTier, new HashSet<>());
            }
        }
        return created;
    }
}
