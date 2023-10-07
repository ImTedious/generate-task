package com.logmaster.task;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

@Slf4j
@Singleton
public class TaskListClient {

    private static final String TASK_LIST_URL = "raw.githubusercontent.com";
    private static final String TASK_LIST_PATH = "Alex-Banna/generate-task-tasks/main/tasks.json";

    @Inject
    private OkHttpClient okHttpClient;

    public void getTaskList(Callback callback) throws IOException {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host(TASK_LIST_URL)
                .addPathSegments(TASK_LIST_PATH)
                .build();

        getRequest(url, callback);
    }

    private void getRequest(HttpUrl url, Callback callback) {
        Request request = new Request.Builder().url(url).get().build();
        okHttpClient.newCall(request).enqueue(callback);
    }

    public JsonObject processResponse(Response response) throws IOException {
        if (!response.isSuccessful()) {
            return null;
        }

        ResponseBody resBody = response.body();
        if (resBody == null) {
            return null;
        }
        return new JsonParser().parse(resBody.string()).getAsJsonObject();
    }
}
