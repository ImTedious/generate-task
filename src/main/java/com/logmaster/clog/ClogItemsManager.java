package com.logmaster.clog;

import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.OkHttpClient;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.StructComposition;

import lombok.extern.slf4j.Slf4j;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.logmaster.LogMasterPlugin;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskTier;
import com.logmaster.task.TaskService;
import com.logmaster.ui.InterfaceManager;

import net.runelite.api.VarbitComposition;
import net.runelite.api.events.ScriptPreFired;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;

@Slf4j
public class ClogItemsManager {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private LogMasterPlugin plugin;

    @Inject
    private TaskService taskService;

    @Inject
    private InterfaceManager interfaceManager;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    private final HashSet<Integer> collectionLogItemIdsFromCache = new HashSet<>();
    private static final HashMap<Integer, Integer> collectionLogItemIdToBitsetIndex = new HashMap<>();
    private Manifest manifest;
    private static final int VARBITS_ARCHIVE_ID = 14;
    private Map<Integer, VarbitComposition> varbitCompositions = new HashMap<>();
    private static final String MANIFEST_URL = "https://sync.runescape.wiki/runelite/manifest";
    private static final BitSet clogItemsBitSet = new BitSet();
    private final Object syncButtonLock = new Object();
    private java.util.Timer syncButtonTimer = new java.util.Timer("SyncButtonTimer", true);
    private java.util.TimerTask syncButtonTask = null;
    private boolean userInitiatedSync = false;

    public void initialise() {
        // Collection log auto sync config
        clientThread.invoke(() -> {
            if (client.getIndexConfig() == null || client.getGameState().ordinal() < GameState.LOGIN_SCREEN.ordinal())
            {
                log.warn("Failed to get varbitComposition, state = {}", client.getGameState());
                return false;
            }
            collectionLogItemIdsFromCache.addAll(parseCacheForClog());
            populateCollectionLogItemIdToBitsetIndex();
            final int[] varbitIds = client.getIndexConfig().getFileIds(VARBITS_ARCHIVE_ID);
            for (int id : varbitIds)
            {
                varbitCompositions.put(id, client.getVarbit(id));
            }
            return true;
        });
        checkManifest();
    }

    private HashSet<Integer> parseCacheForClog()
    {
        HashSet<Integer> itemIds = new HashSet<>();
        // 2102 - Struct that contains the highest level tabs in the collection log (Bosses, Raids, etc)
        // https://chisel.weirdgloop.org/structs/index.html?type=enums&id=2102
        int[] topLevelTabStructIds = client.getEnum(2102).getIntVals();
        for (int topLevelTabStructIndex : topLevelTabStructIds)
        {
            // The collection log top level tab structs contain a param that points to the enum
            // that contains the pointers to sub tabs.
            // ex: https://chisel.weirdgloop.org/structs/index.html?type=structs&id=471
            StructComposition topLevelTabStruct = client.getStructComposition(topLevelTabStructIndex);

            // Param 683 contains the pointer to the enum that contains the subtabs ids
            // ex: https://chisel.weirdgloop.org/structs/index.html?type=enums&id=2103
            int[] subtabStructIndices = client.getEnum(topLevelTabStruct.getIntValue(683)).getIntVals();
            for (int subtabStructIndex : subtabStructIndices) {

                // The subtab structs are for subtabs in the collection log (Commander Zilyana, Chambers of Xeric, etc.)
                // and contain a pointer to the enum that contains all the item ids for that tab.
                // ex subtab struct: https://chisel.weirdgloop.org/structs/index.html?type=structs&id=476
                // ex subtab enum: https://chisel.weirdgloop.org/structs/index.html?type=enums&id=2109
                StructComposition subtabStruct = client.getStructComposition(subtabStructIndex);
                int[] clogItems = client.getEnum(subtabStruct.getIntValue(690)).getIntVals();
                for (int clogItemId : clogItems) itemIds.add(clogItemId);
            }
        }

        // Some items with data saved on them have replacements to fix a duping issue (satchels, flamtaer bag)
        // Enum 3721 contains a mapping of the item ids to replace -> ids to replace them with
        EnumComposition replacements = client.getEnum(3721);
        for (int badItemId : replacements.getKeys())
            itemIds.remove(badItemId);
        for (int goodItemId : replacements.getIntVals())
            itemIds.add(goodItemId);

        return itemIds;
    }

    private void populateCollectionLogItemIdToBitsetIndex()
    {
        if (manifest == null)
        {
            log.warn("Manifest is not present so the collection log bitset index will not be updated");
            return;
        }
        clientThread.invoke(() -> {
            // Add missing keys in order to the map. Order is extremely important here so
            // we get a stable map given the same cache data.
            List<Integer> itemIdsMissingFromManifest = collectionLogItemIdsFromCache
                    .stream()
                    .filter((t) -> !manifest.collections.contains(t))
                    .sorted()
                    .collect(Collectors.toList());

            int currentIndex = 0;
            collectionLogItemIdToBitsetIndex.clear();
            for (Integer itemId : manifest.collections)
                collectionLogItemIdToBitsetIndex.put(itemId, currentIndex++);
            for (Integer missingItemId : itemIdsMissingFromManifest) {
                collectionLogItemIdToBitsetIndex.put(missingItemId, currentIndex++);
            }
        });
    }

    private void checkManifest()
    {
        Request request = new Request.Builder()
                .url(MANIFEST_URL)
                .build();
        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("Failed to get manifest: ", e);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try
                {
                    if (!response.isSuccessful())
                    {
                        log.warn("Failed to get manifest: {}", response.code());
                        return;
                    }
                    okhttp3.ResponseBody body = response.body();
                    if (body == null)
                    {
                        log.warn("Failed to get manifest: response body is null");
                        return;
                    }
                    InputStream in = body.byteStream();
                    manifest = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Manifest.class);
                    populateCollectionLogItemIdToBitsetIndex();
                }
                catch (JsonParseException e)
                {
                    log.warn("Failed to parse manifest: ", e);
                }
                finally
                {
                    response.close();
                }
            }
        });
    }

    public boolean isCollectionLogItemUnlocked(int itemId) {
        // Some items have bad IDs, check these ones for a replacement
        EnumComposition replacements = client.getEnum(3721);
        int replacementItemId = replacements.getIntValue(itemId);
        itemId = replacementItemId >= 0 ? replacementItemId : itemId;
        int index = lookupCollectionLogItemIndex(itemId);
        if (index == -1) {
            return false;
        }

        // Check if the bit is set in our bitset
        boolean isUnlocked = clogItemsBitSet.get(index);
        return isUnlocked;
    }

    private int lookupCollectionLogItemIndex(int itemId) {
        // The map has not loaded yet, or failed to load.
        if (collectionLogItemIdToBitsetIndex.isEmpty()) {
            return -1;
        }
        Integer result = collectionLogItemIdToBitsetIndex.get(itemId);
        if (result == null) {
            return -1;
        }
        return result;
    }

    private void scheduleSync() {
        synchronized (syncButtonLock) {
            if (syncButtonTask != null) {
                syncButtonTask.cancel();
            }
            syncButtonTask = new java.util.TimerTask() {
                @Override
                public void run() {
                    clientThread.invokeLater(() -> syncClogWithProgress());
                }
            };
            syncButtonTimer.schedule(syncButtonTask, 3000);
        }
    }

    public void updatePlayersCollectionLogItems(ScriptPreFired preFired) {
        if (collectionLogItemIdToBitsetIndex.isEmpty())
        {
            return;
        }
        Object[] args = preFired.getScriptEvent().getArguments();
        if (args == null || args.length < 2) {
            return;
        }
        int itemId = (int) args[1];
        int idx = lookupCollectionLogItemIndex(itemId);
        // We should never return -1 under normal circumstances
        if (idx != -1) {
            clogItemsBitSet.set(idx);
            // Only schedule sync if user has initiated a sync
            if (userInitiatedSync) {
                disableButton("Loading collection log items...");
                scheduleSync();
            }
        }
    }

    public void clearCollectionLog() {
        clogItemsBitSet.clear();
        // Reset sync flag when clearing collection log
        userInitiatedSync = false;
    }

    public void enableButton() {
        if (interfaceManager.taskDashboard != null) {
            interfaceManager.taskDashboard.enableSyncButton();
        }
    }

    public void disableButton(String reason) {
        if (interfaceManager.taskDashboard != null) {
            interfaceManager.taskDashboard.disableSyncButton(reason);
        }
    }

    public void sync() {
        userInitiatedSync = true;
        disableButton("Loading collection log items...");
        refreshCollectionLog();
    }

    public void refreshCollectionLog() {
        clientThread.invokeLater(() -> {
            client.menuAction(-1, net.runelite.api.gameval.InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
            client.runScript(2240);
        });
    }

    public void syncClogWithProgress() {
        if (clogItemsBitSet.isEmpty()) {
            return;
        }

        disableButton("Updating progress...");

        // Update completed tasks automatically
        for (TaskTier tier : TaskTier.values()) {
            for (Task task : taskService.getTaskList().getForTier(tier)) {
                int[] check = task.getCheck();
                Integer taskCount = task.getCount();
                if (check == null || taskCount == null) {
                    continue;
                }
                if (check.length > 0) {
                    int count = 0;
                    for (int itemId : check) {
                        if (isCollectionLogItemUnlocked(itemId)) {
                            count++;
                        }
                    }
                    if (count >= taskCount && !plugin.isTaskCompleted(task.getId(), tier)) {
                        // Check passed, task not yet completed, mark as completed
                        plugin.completeTask(task.getId(), tier, false);
                        log.debug("Task '{}' marked as completed for tier {}", task.getDescription(), tier.displayName);
                    } else if (count < taskCount && plugin.isTaskCompleted(task.getId(), tier)) {
                        // Check failed, task marked as completed, unmark completion
                        plugin.completeTask(task.getId(), tier, false);
                        log.debug("Task '{}' un-marked as this is not completed for tier {}", task.getDescription(), tier.displayName);
                    }
                }
            }
        }
        
        // Reset the flag after sync is complete
        userInitiatedSync = false;
        enableButton();
    }
}
