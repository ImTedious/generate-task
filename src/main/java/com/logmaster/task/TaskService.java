package com.logmaster.task;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.logmaster.LogMasterConfig;
import com.logmaster.domain.SaveData;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskTier;
import com.logmaster.domain.TieredTaskList;
import com.logmaster.persistence.SaveDataManager;
import com.logmaster.util.FileUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@Slf4j
public class TaskService {

    private static final String DEF_FILE_TASKS = "default-tasks.json";

    @Inject
    private Gson gson;

    @Inject
    private LogMasterConfig config;

    @Inject
    private TaskListClient taskListClient;

    @Inject
    private ClientThread clientThread;

    @Inject
    private SaveDataManager saveDataManager;

    private TieredTaskList localList;

    public TieredTaskList getTaskList() {
        loadTaskList();
        return localList;
    }

    public List<Task> getForTier(TaskTier tier) {
        return getTaskList().getForTier(tier);
    }

    public Map<TaskTier, Integer> completionPercentages(SaveData saveData) {
        Map<TaskTier, Integer> completionPercentages = new HashMap<>();
        for (TaskTier tier : TaskTier.values()) {
            completionPercentages.put(tier, (int) Math.round(((double) saveData.getProgress().get(tier).size() / (double) getTaskList().getForTier(tier).size()) * 100));
        }
        return completionPercentages;
    }

    private void loadTaskList() {
        this.localList = FileUtils.loadDefinitionResource(TieredTaskList.class, DEF_FILE_TASKS, gson);
        if (config.loadRemoteTaskList()) {
            loadRemoteTaskList();
        }
    }


    private void loadRemoteTaskList() {
        log.info("Loading remote task list");
        // Load the remote task list
        try {
            taskListClient.getTaskList(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    log.error("Unable to load remote task list, will defer to the default task list", e);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    JsonObject tasksJson = taskListClient.processResponse(response);
                    response.close();
                    if (tasksJson == null) {
                        log.error("Loaded null remote task list, will defer to the default task list");
                        return;
                    }

                    TieredTaskList tieredTaskList = gson.fromJson(tasksJson, TieredTaskList.class);
                    clientThread.invoke(() -> replaceTaskList(tieredTaskList));
                }
            });
        } catch (IOException e) {
            log.error("Unable to load remote task list, will defer to the default task list");
        }
    }

    private void replaceTaskList(TieredTaskList taskList) {
        this.localList = taskList;
    }
}
