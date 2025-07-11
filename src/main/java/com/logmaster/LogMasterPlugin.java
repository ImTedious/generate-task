package com.logmaster;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import com.logmaster.clog.ClogItemsManager;
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
import java.awt.event.MouseEvent;
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
public class LogMasterPlugin extends Plugin {
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

	@Inject
	public ClogItemsManager clogItemsManager;

	private Map<Integer, Integer> chatSpriteMap = new HashMap<>();

	private File playerFile;

	@Override
	protected void startUp() throws Exception
	{
		mouseManager.registerMouseWheelListener(interfaceManager);
		mouseManager.registerMouseListener(interfaceManager);
		interfaceManager.initialise();
		clogItemsManager.initialise();
		this.taskOverlay.setResizable(true);
		this.overlayManager.add(this.taskOverlay);
		this.taskService.getTaskList();
		// TODO when task save data can be stored and access externally; populate this with other people's data
//		this.clientThread.invoke(this::populateChatSpriteMap);
//		chatCommandManager.registerCommandAsync(TASK_CHAT_COMMAND, this::getTaskCommandData);
	}

	@Override
	protected void shutDown() throws Exception {
		mouseManager.unregisterMouseWheelListener(interfaceManager);
		mouseManager.unregisterMouseListener(interfaceManager);
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
				clogItemsManager.clearCollectionLog();
				break;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e) {
		if(e.getGroupId() == InterfaceID.COLLECTION) {
			interfaceManager.handleCollectionLogOpen();
			// Refresh the collection log after a short delay to ensure it is fully loaded
			new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					clientThread.invokeAtTickEnd(() -> {
						clogItemsManager.refreshCollectionLog();
					});
				}
			}, 600);
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
		String selectedTaskDescription = uniqueTasks.get(index).getDescription();
		Task selectedTask = uniqueTasks.stream()
			.filter(task -> task.getDescription().equals(selectedTaskDescription))
			.collect(Collectors.toList()).stream()
			.min(Comparator.comparingInt(Task::getCount))
			.orElse(uniqueTasks.get(index));

		TaskPointer newTaskPointer = new TaskPointer();
		newTaskPointer.setTask(selectedTask);
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
		completeTask(taskID, tier, true);
	}

	public void completeTask(int taskID, TaskTier tier, boolean playSound) {
		if (playSound) {
			this.client.playSoundEffect(SoundEffectID.UI_BOOP);
		}

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

	@Subscribe
	public void onScriptPreFired(ScriptPreFired preFired) {
		// This is fired when the collection log search is opened
		if (preFired.getScriptId() == 4100){
			clogItemsManager.updatePlayersCollectionLogItems(preFired);
		}
	}
}
