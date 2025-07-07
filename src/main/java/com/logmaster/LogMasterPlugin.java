package com.logmaster;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskPointer;
import com.logmaster.domain.TaskTier;
import com.logmaster.domain.TieredTaskList;
import com.logmaster.persistence.SaveDataManager;
import com.logmaster.task.TaskService;
import com.logmaster.ui.InterfaceManager;
import com.logmaster.ui.component.TaskOverlay;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.SoundEffectID;
import net.runelite.api.StructComposition;
import net.runelite.api.VarbitComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.LinkBrowser;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "Collection Log Master"
)
public class LogMasterPlugin extends Plugin implements MouseWheelListener {
	private static final String TASK_CHAT_COMMAND = "!tasker";

	private static final int COLLECTION_LOG_SETUP_SCRIPT_ID = 7797;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private LogMasterConfig config;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private SpriteManager spriteManager;
	
	@Inject
	private Gson gson;

	@Inject
	private MouseManager mouseManager;

	@Inject
	protected TaskOverlay taskOverlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ChatCommandManager chatCommandManager;

	@Inject
	private TaskService taskService;

	@Inject
	private SaveDataManager saveDataManager;

	@Inject
	private InterfaceManager interfaceManager;

	@Inject
	public ItemManager itemManager;

	private Map<Integer, Integer> chatSpriteMap = new HashMap<>();

	private File playerFile;

	@Override
	protected void startUp() throws Exception
	{
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

		// Task overlay
		mouseManager.registerMouseWheelListener(this);
		interfaceManager.initialise();
		this.taskOverlay.setResizable(true);
		this.overlayManager.add(this.taskOverlay);
		this.taskService.getTaskList();
		// TODO when task save data can be stored and access externally; populate this with other people's data
//		this.clientThread.invoke(this::populateChatSpriteMap);
//		chatCommandManager.registerCommandAsync(TASK_CHAT_COMMAND, this::getTaskCommandData);
	}

	@Override
	protected void shutDown() throws Exception {
		mouseManager.unregisterMouseWheelListener(this);
		this.overlayManager.remove(this.taskOverlay);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals("log-master")) {
			return;
		}
		interfaceManager.updateAfterConfigChange();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
			saveDataManager.getSaveData();
		} else if(gameStateChanged.getGameState().equals(GameState.LOGIN_SCREEN)) {
			saveDataManager.save();
		}
		
		switch (gameStateChanged.getGameState())
		{
			// When hopping, we need to clear any state related to the player
			case HOPPING:
			case LOGGING_IN:
			case CONNECTION_LOST:
				clogItemsBitSet.clear();
				clogItemsCount = null;
				break;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e) {
		if(e.getGroupId() == InterfaceID.COLLECTION) {
			interfaceManager.handleCollectionLogOpen();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e) {
		if(e.getGroupId() == InterfaceID.COLLECTION) {
			interfaceManager.handleCollectionLogClose();
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired scriptPostFired) {
		if (scriptPostFired.getScriptId() == COLLECTION_LOG_SETUP_SCRIPT_ID) {
			interfaceManager.handleCollectionLogScriptRan();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		interfaceManager.updateTaskListBounds();
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event) {
		interfaceManager.handleMouseWheel(event);
		return event;
	}

	public void generateTask() {
		if(this.saveDataManager.getSaveData().currentTask != null || taskService.getTaskList() == null) {
			interfaceManager.disableGenerateTaskButton();
			return;
		}

		this.client.playSoundEffect(SoundEffectID.UI_BOOP);
		List<Task> uniqueTasks = findAvailableTasks();

		if(uniqueTasks.size() <= 0) {
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "No more tasks left. Looks like you win?", "");
			playFailSound();

			return;
		}

		int index = (int) Math.floor(Math.random()*uniqueTasks.size());


		TaskPointer newTaskPointer = new TaskPointer();
		newTaskPointer.setTask(uniqueTasks.get(index));
		newTaskPointer.setTaskTier(getCurrentTier());
		this.saveDataManager.getSaveData().setActiveTaskPointer(newTaskPointer);
		this.saveDataManager.save();
		interfaceManager.rollTask(this.saveDataManager.getSaveData().getActiveTaskPointer().getTask().getDescription(), this.saveDataManager.getSaveData().getActiveTaskPointer().getTask().getItemID(), config.rollPastCompleted() ? taskService.getForTier(getCurrentTier()) : uniqueTasks);
		log.debug("Task generated: "+this.saveDataManager.getSaveData().getActiveTaskPointer().getTask().getDescription());

		this.saveDataManager.save();
	}

	public void completeTask() {
		completeTask(saveDataManager.getSaveData().getActiveTaskPointer().getTask().getId(), saveDataManager.getSaveData().getActiveTaskPointer().getTaskTier());
	}

	public boolean isTaskCompleted(int taskID, TaskTier tier) {
		return saveDataManager.getSaveData().getProgress().get(tier).contains(taskID);
	}

	public void completeTask(int taskID, TaskTier tier) {
		this.client.playSoundEffect(SoundEffectID.UI_BOOP);

		if (saveDataManager.getSaveData().getProgress().get(tier).contains(taskID)) {
			saveDataManager.getSaveData().getProgress().get(tier).remove(taskID);
		} else {
			addCompletedTask(taskID, tier);
			if (saveDataManager.getSaveData().getActiveTaskPointer() != null && taskID == saveDataManager.getSaveData().getActiveTaskPointer().getTask().getId()) {
				nullCurrentTask();
			}
		}
		this.saveDataManager.save();
		interfaceManager.completeTask();
	}

	public void nullCurrentTask() {
		this.saveDataManager.getSaveData().setActiveTaskPointer(null);
		this.saveDataManager.save();
		interfaceManager.clearCurrentTask();
	}

	public static int getCenterX(Widget window, int width) {
		return (window.getWidth() / 2) - (width / 2);
	}

	public static int getCenterY(Widget window, int height) {
		return (window.getHeight() / 2) - (height / 2);
	}

	public void addCompletedTask(int taskID, TaskTier tier) {
		this.saveDataManager.getSaveData().getProgress().get(tier).add(taskID);
		this.saveDataManager.save();
	}

	public TaskTier getCurrentTier() {
		TaskTier[] allTiers = TaskTier.values();
		int firstVisibleTier = 0;
		for (int i = 0; i < allTiers.length; i++) {
			if (config.hideBelow() == allTiers[i]) {
				firstVisibleTier = i;
			}
		}

		Map<TaskTier, Integer> tierPercentages = taskService.completionPercentages(saveDataManager.getSaveData());
		for (int i = firstVisibleTier; i < allTiers.length; i++) {
			TaskTier tier = allTiers[i];
			if (tierPercentages.get(tier) < 100) {
				return tier;
			}
		}


		return TaskTier.MASTER;
	}

	public TaskTier getSelectedTier() {
		return this.saveDataManager.getSaveData().getSelectedTier();
	}

	public List<Task> findAvailableTasks() {
		return taskService.getTaskList().getForTier(getCurrentTier()).stream().filter(t -> !this.saveDataManager.getSaveData().getProgress().get(getCurrentTier()).contains(t.getId())).collect(Collectors.toList());
	}

	public void playFailSound() {
		client.playSoundEffect(2277);
	}

	@Provides
	LogMasterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LogMasterConfig.class);
	}

	public void visitFaq() {
		LinkBrowser.browse("https://docs.google.com/document/d/e/2PACX-1vTHfXHzMQFbt_iYAP-O88uRhhz3wigh1KMiiuomU7ftli-rL_c3bRqfGYmUliE1EHcIr3LfMx2UTf2U/pub");
	}

	/*
	 * Collection log auto sync
	 * Modified from wiki-sync plugin to get base functionality
	 */
	
	private final HashSet<Integer> collectionLogItemIdsFromCache = new HashSet<>();
	private static final HashMap<Integer, Integer> collectionLogItemIdToBitsetIndex = new HashMap<>();
	private Manifest manifest;
	private static final int VARBITS_ARCHIVE_ID = 14;
	private Map<Integer, VarbitComposition> varbitCompositions = new HashMap<>();
	private static final String MANIFEST_URL = "https://sync.runescape.wiki/runelite/manifest";
	private static final BitSet clogItemsBitSet = new BitSet();
	private static Integer clogItemsCount = null;

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

	@Subscribe
	public void onScriptPreFired(ScriptPreFired preFired) {
		// This is fired when the collection log search is opened
		if (preFired.getScriptId() == 4100){
			if (collectionLogItemIdToBitsetIndex.isEmpty())
			{
				return;
			}
			clogItemsCount = collectionLogItemIdsFromCache.size();
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
	private PlayerData getPlayerData()
	{
		PlayerData out = new PlayerData();
		for (int varbitId : manifest.varbits)
		{
			out.varb.put(varbitId, getVarbitValue(varbitId));
		}
		for (int varpId : manifest.varps)
		{
			out.varp.put(varpId, client.getVarpValue(varpId));
		}
		for(Skill s : Skill.values())
		{
			out.level.put(s.getName(), client.getRealSkillLevel(s));
		}
		out.collectionLogSlots = Base64.getEncoder().encodeToString(clogItemsBitSet.toByteArray());
		out.collectionLogItemCount = clogItemsCount;
		return out;
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
					if (count >= task.getCount() && !isTaskCompleted(task.getId(), tier)) {
						// Check passed, task not yet completed, mark as completed
						completeTask(task.getId(), tier);
						log.debug("Task '{}' marked as completed for tier {}", task.getDescription(), tier.displayName);
					} else if (count < task.getCount() && isTaskCompleted(task.getId(), tier)) {
						// Check failed, task marked as completed, unmark completion
						completeTask(task.getId(), tier);
						log.debug("Task '{}' un-marked as this is not completed for tier {}", task.getDescription(), tier.displayName);
					}
				}
			}
		}
	}
}
