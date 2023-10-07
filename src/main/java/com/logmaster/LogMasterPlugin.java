package com.logmaster;

import com.google.inject.Provides;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskPointer;
import com.logmaster.domain.TaskTier;
import com.logmaster.persistence.SaveDataManager;
import com.logmaster.task.TaskService;
import com.logmaster.ui.InterfaceManager;
import com.logmaster.ui.component.TaskOverlay;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.SoundEffectID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "Collection Log Master"
)
public class LogMasterPlugin extends Plugin implements MouseWheelListener {
	private static final String TASK_CHAT_COMMAND = "!tasker";


	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private LogMasterConfig config;


	@Inject
	private SpriteManager spriteManager;

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
	private ItemManager itemManager;

	private Map<Integer, Integer> chatSpriteMap = new HashMap<>();

	private File playerFile;

	@Override
	protected void startUp() throws Exception
	{
		mouseManager.registerMouseWheelListener(this);
		interfaceManager.initialise();
		this.taskOverlay.setResizable(true);
		this.overlayManager.add(this.taskOverlay);
		// TODO when task save data can be stored and access externally; populate this with other people's data
//		this.clientThread.invoke(this::populateChatSpriteMap);
//		chatCommandManager.registerCommandAsync(TASK_CHAT_COMMAND, this::getTaskCommandData);
	}

	@Override
	protected void shutDown() throws Exception {
		mouseManager.unregisterMouseWheelListener(this);
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
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e) {
		if(e.getGroupId() == WidgetInfo.COLLECTION_LOG.getGroupId()) {
			interfaceManager.handleCollectionLogOpen();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e) {
		if(e.getGroupId() == WidgetInfo.COLLECTION_LOG.getGroupId()) {
			interfaceManager.handleCollectionLogClose();
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
		interfaceManager.rollTask(this.saveDataManager.getSaveData().getActiveTaskPointer().getTask().getDescription(), this.saveDataManager.getSaveData().getActiveTaskPointer().getTask().getItemID(), config.rollPastCompleted() ? taskService.getForTier(getCurrentTier()) : uniqueTasks);
		log.debug("Task generated: "+this.saveDataManager.getSaveData().getActiveTaskPointer().getTask().getDescription());

		this.saveDataManager.save();
	}

	public void completeTask() {
		completeTask(saveDataManager.getSaveData().getActiveTaskPointer().getTask().getId(), saveDataManager.getSaveData().getActiveTaskPointer().getTaskTier());
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
		interfaceManager.completeTask();

		this.saveDataManager.save();
	}

	public void nullCurrentTask() {
		this.saveDataManager.getSaveData().setActiveTaskPointer(null);
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
	}

	public TaskTier getCurrentTier() {
		if (this.saveDataManager.getSaveData().getProgress().get(TaskTier.EASY).size() < taskService.getTaskList().getEasy().size() &&
				!Arrays.asList(TaskTier.MEDIUM, TaskTier.HARD, TaskTier.ELITE, TaskTier.MASTER).contains(config.hideBelow())) {
			return TaskTier.EASY;
		} else if (this.saveDataManager.getSaveData().getProgress().get(TaskTier.MEDIUM).size() < taskService.getTaskList().getMedium().size() &&
				!Arrays.asList(TaskTier.HARD, TaskTier.ELITE, TaskTier.MASTER).contains(config.hideBelow())) {
			return TaskTier.MEDIUM;
		} else if (this.saveDataManager.getSaveData().getProgress().get(TaskTier.HARD).size() < taskService.getTaskList().getHard().size() &&
				!Arrays.asList(TaskTier.ELITE, TaskTier.MASTER).contains(config.hideBelow())) {
			return TaskTier.HARD;
		} else if (this.saveDataManager.getSaveData().getProgress().get(TaskTier.ELITE).size() < taskService.getTaskList().getElite().size() &&
				TaskTier.MASTER != config.hideBelow()) {
			return TaskTier.ELITE;
		} else {
			return TaskTier.MASTER;
		}
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
}
