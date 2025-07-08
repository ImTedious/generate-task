package com.logmaster.clog;

import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.SpriteManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.OkHttpClient;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;

import lombok.extern.slf4j.Slf4j;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.logmaster.LogMasterConfig;
import com.logmaster.LogMasterPlugin;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskTier;
import com.logmaster.task.TaskService;

import net.runelite.api.VarbitComposition;
import net.runelite.api.events.ScriptPreFired;

import java.util.Set;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.inject.Inject;

@Slf4j
public class ClogItemsManager {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private LogMasterConfig config;

    @Inject
    private LogMasterPlugin plugin;

    @Inject
    private TaskService taskService;

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

    public void initialise() {
		// Collection log auto sync config
		clientThread.invoke(() -> {
			if (client.getIndexConfig() == null || client.getGameState().ordinal() < GameState.LOGIN_SCREEN.ordinal())
			{
				log.debug("Failed to get varbitComposition, state = {}", client.getGameState());
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
			log.debug("Manifest is not present so the collection log bitset index will not be updated");
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
				log.debug("Failed to get manifest: ", e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try
				{
					if (!response.isSuccessful())
					{
						log.debug("Failed to get manifest: {}", response.code());
						return;
					}
					InputStream in = response.body().byteStream();
					manifest = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), Manifest.class);
					populateCollectionLogItemIdToBitsetIndex();
				}
				catch (JsonParseException e)
				{
					log.debug("Failed to parse manifest: ", e);
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

	private int getVarbitValue(int varbitId)
	{
		VarbitComposition v = varbitCompositions.get(varbitId);
		if (v == null)
		{
			return -1;
		}

		int value = client.getVarpValue(v.getIndex());
		int lsb = v.getLeastSignificantBit();
		int msb = v.getMostSignificantBit();
		int mask = (1 << ((msb - lsb) + 1)) - 1;
		return (value >> lsb) & mask;
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
        }
    }

    public void clearCollectionLog() {
        clogItemsBitSet.clear();
    }

	public void sync() {
		if (clogItemsBitSet.isEmpty()) {
			return;
		}

		// Update completed tasks automatically
		for (TaskTier tier : TaskTier.values()) {
			for (Task task : taskService.getTaskList().getForTier(tier)) {
				if (task.getCheck() != null && task.getCheck().length > 0) {
					int count = 0;
					for (int itemId : task.getCheck()) {
						if (isCollectionLogItemUnlocked(itemId)) {
							count++;
						}
					}
					if (count >= task.getCount() && !plugin.isTaskCompleted(task.getId(), tier)) {
						// Check passed, task not yet completed, mark as completed
						plugin.completeTask(task.getId(), tier, false);
						log.debug("Task '{}' marked as completed for tier {}", task.getDescription(), tier.displayName);
					} else if (count < task.getCount() && plugin.isTaskCompleted(task.getId(), tier)) {
						// Check failed, task marked as completed, unmark completion
						plugin.completeTask(task.getId(), tier, false);
						log.debug("Task '{}' un-marked as this is not completed for tier {}", task.getDescription(), tier.displayName);
					}
				}
			}
		}
	}
}
