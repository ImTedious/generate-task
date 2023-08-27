package com.logmaster;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;

@Slf4j
@Singleton
public class TaskListClient {

    private static final String TASK_LIST_BUCKET_URL = "collection-log-task-storage.s3.eu-west-2.amazonaws.com";
    private static final String TASK_LIST_KEY = "tasks.json";

    @Inject
    private OkHttpClient okHttpClient;

    public void getTaskList(Callback callback) throws IOException {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host(TASK_LIST_BUCKET_URL)
                .addPathSegment(TASK_LIST_KEY)
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
