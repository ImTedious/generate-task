package com.logmaster;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import com.logmaster.domain.SaveData;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskPointer;
import com.logmaster.domain.TaskTier;
import com.logmaster.domain.TieredTaskList;
import com.logmaster.ui.UIButton;
import com.logmaster.ui.UICheckBox;
import com.logmaster.ui.UIComponent;
import com.logmaster.ui.UIGraphic;
import lombok.Getter;
import lombok.NonNull;
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
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import javax.inject.Inject;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

import static net.runelite.http.api.RuneLiteAPI.GSON;

@Slf4j
@PluginDescriptor(
	name = "Collection Log Master"
)
public class LogMasterPlugin extends Plugin implements MouseWheelListener
{
	public static final String DEF_FILE_SPRITES = "SpriteDef.json";
	public static final String DEF_FILE_TASKS = "default-tasks.json";
	public static final int TASK_BACKGROUND_SPRITE_ID = -20006;
	public static final int TASK_LIST_BACKGROUND_SPRITE_ID = -20012;
	public static final int TASK_COMPLETE_BACKGROUND_SPRITE_ID = -20013;
	public static final int TASK_CURRENT_BACKGROUND_SPRITE_ID = -20016;

	private static final String DATA_FOLDER_NAME = "generate-task";
	public static final int COLLECTION_LOG_WINDOW_WIDTH = 500;
	public static final int COLLECTION_LOG_WINDOW_HEIGHT = 314;
	public static final int COLLECTION_LOG_CONTENT_WIDGET_ID = 40697858;

	private static final int DASHBOARD_TAB_SPRITE_ID = -20007;
	private static final int DASHBOARD_TAB_HOVER_SPRITE_ID = -20008;
	private static final int TASKLIST_TAB_SPRITE_ID = -20009;
	private static final int TASKLIST_TAB_HOVER_SPRITE_ID = -20010;
	private static final int DIVIDER_SPRITE_ID = -20011;

	public static final int TASKLIST_EASY_TAB_SPRITE_ID = -20017;
	public static final int TASKLIST_EASY_TAB_HOVER_SPRITE_ID = -20018;
	public static final int TASKLIST_MEDIUM_TAB_SPRITE_ID = -20019;
	public static final int TASKLIST_MEDIUM_TAB_HOVER_SPRITE_ID = -20020;
	public static final int TASKLIST_HARD_TAB_SPRITE_ID = -20021;
	public static final int TASKLIST_HARD_TAB_HOVER_SPRITE_ID = -20022;
	public static final int TASKLIST_ELITE_TAB_SPRITE_ID = -20023;
	public static final int TASKLIST_ELITE_TAB_HOVER_SPRITE_ID = -20024;
	public static final int TASKLIST_MASTER_TAB_SPRITE_ID = -20025;
	public static final int TASKLIST_MASTER_TAB_HOVER_SPRITE_ID = -20026;


	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private LogMasterConfig config;

	@Inject
	TaskListClient taskListClient;

	@Inject
	private Gson gson;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private MouseManager mouseManager;

	private SpriteDefinition[] spriteDefinitions;
	private TieredTaskList tasks;

	@Getter
	private SaveData saveData;


	private TaskDashboard taskDashboard;
	private TaskList taskList;
	private UICheckBox taskDashboardCheckbox;

	private List<UIButton> tabs;
	private UIButton taskListTab;
	private UIButton taskDashboardTab;

	private int activeTab = 0;

	private File playerFile;

	@Override
	protected void startUp() throws Exception
	{
		this.spriteDefinitions = loadDefinitionResource(SpriteDefinition[].class, DEF_FILE_SPRITES, gson);
		// Load the default task list so we definitely have something working
		this.tasks = loadDefinitionResource(TieredTaskList.class, DEF_FILE_TASKS, gson);
		if (config.loadRemoteTaskList()) {
			loadRemoteTaskList();
		}
		this.spriteManager.addSpriteOverrides(spriteDefinitions);
		mouseManager.registerMouseWheelListener(this);
	}

	protected void loadRemoteTaskList() {
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

	protected void replaceTaskList(TieredTaskList taskList) {
		this.tasks = taskList;
	}

	@Override
	protected void shutDown() throws Exception
	{
		mouseManager.unregisterMouseWheelListener(this);
	}

	/**
	 * Loads a definition resource from a JSON file
	 *
	 * @param classType the class into which the data contained in the JSON file will be read into
	 * @param resource  the name of the resource (file name)
	 * @param gson      a reference to the GSON object
	 * @param <T>       the class type
	 * @return the data read from the JSON definition file
	 */
	private <T> T loadDefinitionResource(Class<T> classType, String resource, Gson gson) {
		// Load the resource as a stream and wrap it in a reader
		InputStream resourceStream = classType.getResourceAsStream(resource);
		assert resourceStream != null;
		InputStreamReader definitionReader = new InputStreamReader(resourceStream);

		// Load the objects from the JSON file
		return gson.fromJson(definitionReader, classType);
	}

	/**
	 * Sets up the playerFile variable, and makes the player file if needed.
	 */
	private void setupPlayerFile() {
		saveData = new SaveData();

		for (TaskTier loopTier : TaskTier.values()) {
			if (!saveData.getProgress().containsKey(loopTier)) {
				saveData.getProgress().put(loopTier, new HashSet<>());
			}
		}
		File playerFolder = new File(RuneLite.RUNELITE_DIR, DATA_FOLDER_NAME);
		if (!playerFolder.exists()) {
			playerFolder.mkdirs();
		}
		playerFile = new File(playerFolder, client.getAccountHash() + ".txt");
		if (!playerFile.exists()) {
			try {
				playerFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			loadPlayerData();
		}
	}

	private void loadPlayerData() {
		try {
			String json = new Scanner(playerFile).useDelimiter("\\Z").next();
			saveData = GSON.fromJson(json, new TypeToken<SaveData>() {}.getType());
			for (TaskTier loopTier : TaskTier.values()) {
				if (!saveData.getProgress().containsKey(loopTier)) {
					saveData.getProgress().put(loopTier, new HashSet<>());
				}
			}
			// Can get rid of this eventually
			if (!this.saveData.getCompletedTasks().isEmpty()) {
				this.saveData.getProgress().get(TaskTier.MASTER).addAll(this.saveData.getCompletedTasks().keySet());
			}
			if (saveData.currentTask != null) {
				TaskPointer taskPointer = new TaskPointer();
				taskPointer.setTask(saveData.currentTask);
				taskPointer.setTaskTier(TaskTier.MASTER);
				saveData.setActiveTaskPointer(taskPointer);
				saveData.currentTask = null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void savePlayerData() {
		try {
			PrintWriter w = new PrintWriter(playerFile);
			String json = GSON.toJson(saveData);
			w.println(json);
			w.close();
			log.debug("Saving player data");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals("log-master")) {
			return;
		}
		hideTabs();
		updateTabs();
		if (this.config.hideBelow() == TaskTier.MASTER && this.saveData.getSelectedTier() == TaskTier.MASTER && !this.taskDashboard.isVisible()) {
			this.taskListTab.setSprites(TASKLIST_TAB_HOVER_SPRITE_ID);
		}
		if (this.taskDashboardCheckbox.isEnabled()) {
			showTabs();
			if (this.saveData.getSelectedTier() != null && Arrays.asList(TaskTier.values()).indexOf(this.saveData.getSelectedTier()) < Arrays.asList(TaskTier.values()).indexOf(this.config.hideBelow())) {
				activateTaskDashboard();
			}
			this.taskDashboard.updatePercentages();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			if(saveData == null) {
				setupPlayerFile();
			}
		} else if(gameStateChanged.getGameState().equals(GameState.LOGIN_SCREEN)) {
			if(saveData != null) {
				savePlayerData();
			}

			saveData = null;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e) {
		if(e.getGroupId() == WidgetInfo.COLLECTION_LOG.getGroupId()) {
			if (saveData == null) {
				setupPlayerFile();
			}
			Widget window = client.getWidget(40697857);

			Widget dashboardTabWidget = window.createChild(-1, WidgetType.GRAPHIC);
			taskDashboardTab = new UIButton(dashboardTabWidget);
			taskDashboardTab.setSprites(DASHBOARD_TAB_SPRITE_ID, DASHBOARD_TAB_HOVER_SPRITE_ID);
			taskDashboardTab.setSize(95, 21);
			taskDashboardTab.setPosition(10, 36);
			taskDashboardTab.addAction("View <col=ff9040>Dashboard</col>", this::activateTaskDashboard);
			taskDashboardTab.setVisibility(false);

			tabs = new ArrayList<>();

			int currentTabX = 110;

			for (int i = 0; i < 5; i++) {
				Widget tabWiget = window.createChild(-1, WidgetType.GRAPHIC);
				UIButton tab = new UIButton(tabWiget);
				tab.setSize(66, 21);
				tab.setPosition(currentTabX, 36);
				tab.setVisibility(false);
				currentTabX += 71;
				tabs.add(tab);
			}

			Widget tabWiget = window.createChild(-1, WidgetType.GRAPHIC);
			taskListTab = new UIButton(tabWiget);
			taskListTab.setSize(95, 21);
			taskListTab.setPosition(110, 36);
			taskListTab.setSprites(TASKLIST_TAB_SPRITE_ID, TASKLIST_TAB_HOVER_SPRITE_ID);
			taskListTab.setVisibility(false);
			taskListTab.addAction("View <col=ff9040>Task List</col>", () -> {
				taskDashboardTab.setSprites(DASHBOARD_TAB_SPRITE_ID, DASHBOARD_TAB_HOVER_SPRITE_ID);
				if (this.saveData.getSelectedTier() != TaskTier.MASTER) {
					this.taskList.goToTop();
					this.saveData.setSelectedTier(TaskTier.MASTER);
				}
				updateTabs();
				taskListTab.setSprites(TASKLIST_TAB_HOVER_SPRITE_ID);
				this.taskDashboard.setVisibility(false);
				this.taskList.refreshTasks(0);
				this.taskList.setVisibility(true);
			});

			Widget dividerWidget = window.createChild(-1, WidgetType.GRAPHIC);
			UIGraphic divider = new UIGraphic(dividerWidget);
			divider.setSprite(DIVIDER_SPRITE_ID);
			divider.setSize(480, 1);
			divider.setPosition(10, 56);

			createTaskDashboard(window);
			createTaskList(window);
			createTaskCheckbox();
			updateTabs();

			this.taskDashboardCheckbox.setEnabled(false);
			this.taskDashboard.setVisibility(false);
		}
	}

	private void updateTabs() {
		int tabIndex = 0;
		for (TaskTier tier : TaskTier.values()) {
			if (tabIndex > 0 || tier == config.hideBelow()) {
				if (this.saveData.getSelectedTier() == tier && !this.taskDashboard.isVisible()) {
					tabs.get(tabIndex).setSprites(tier.tabSpriteHoverId);
				} else {
					tabs.get(tabIndex).setSprites(tier.tabSpriteId, tier.tabSpriteHoverId);
				}
				int finalTabIndex = tabIndex;
				tabs.get(tabIndex).clearActions();
				tabs.get(tabIndex).setSize(66, 21);
				tabs.get(tabIndex).addAction(String.format("View <col=ff9040>%s Task List</col>", tier.displayName), () -> {
					taskDashboardTab.setSprites(DASHBOARD_TAB_SPRITE_ID, DASHBOARD_TAB_HOVER_SPRITE_ID);
					if (this.saveData.getSelectedTier() != tier) {
						this.taskList.goToTop();
						this.saveData.setSelectedTier(tier);
					}
					updateTabs();
					tabs.get(finalTabIndex).setSprites(tier.tabSpriteHoverId);
					this.taskDashboard.setVisibility(false);
					this.taskList.refreshTasks(0);
					this.taskList.setVisibility(true);
				});
				tabIndex++;
			}
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e) {
		if(e.getGroupId() == WidgetInfo.COLLECTION_LOG.getGroupId()) {
			this.taskDashboard.setVisibility(false);
			this.taskList.setVisibility(false);
			hideTabs();
			this.taskDashboardCheckbox.setEnabled(false);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (this.taskList != null)
			taskList.updateBounds();
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
	{
		if(this.taskList != null) {
			taskList.handleWheel(event);
		}

		return event;
	}

	private void createTaskCheckbox() {
		Widget window = client.getWidget(40697857);
		// Create the graphic widget for the checkbox
		Widget toggleWidget = window.createChild(-1, WidgetType.GRAPHIC);
		Widget labelWidget = window.createChild(-1, WidgetType.TEXT);

		// Wrap in checkbox, set size, position, etc.
		taskDashboardCheckbox = new UICheckBox(toggleWidget, labelWidget);
		taskDashboardCheckbox.setPosition(360, 10);
		taskDashboardCheckbox.setName("Task Dashboard");
		taskDashboardCheckbox.setEnabled(false);
		taskDashboardCheckbox.setText("Task Dashboard");
		labelWidget.setPos(375, 10);
		taskDashboardCheckbox.setToggleListener(this::toggleTaskDashboard);
	}

	private void createTaskDashboard(Widget window) {
		this.taskDashboard = new TaskDashboard(this, config, window);
		this.taskDashboard.setVisibility(false);
	}

	private void createTaskList(Widget window) {
		this.taskList = new TaskList(window, this.tasks, this, clientThread);
		this.taskList.setVisibility(false);
	}

	private void toggleTaskDashboard(UIComponent src) {
		if(this.taskDashboard == null) return;

		if(saveData.getActiveTaskPointer() != null) {
			this.taskDashboard.setTask(this.saveData.getActiveTaskPointer().getTask().getDescription(), this.saveData.getActiveTaskPointer().getTask().getItemID(), null);
			this.taskDashboard.disableGenerateTask();
		} else {
			nullCurrentTask();
		}

		client.getWidget(COLLECTION_LOG_CONTENT_WIDGET_ID).setHidden(this.taskDashboardCheckbox.isEnabled());
		client.getWidget(40697936).setHidden(this.taskDashboardCheckbox.isEnabled());

		if (this.taskDashboardCheckbox.isEnabled()) {
			activateTaskDashboard();
		} else {
			this.taskDashboard.setVisibility(false);
			this.taskList.setVisibility(false);

			hideTabs();
		}

		// *Boop*
		this.client.playSoundEffect(SoundEffectID.UI_BOOP);
	}

	public void generateTask() {
		if(this.saveData.currentTask != null || this.tasks == null) {
			this.taskDashboard.disableGenerateTask();
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
		this.saveData.setActiveTaskPointer(newTaskPointer);
		this.taskDashboard.setTask(this.saveData.getActiveTaskPointer().getTask().getDescription(), this.saveData.getActiveTaskPointer().getTask().getItemID(), config.rollPastCompleted() ? this.tasks.getForTier(getCurrentTier()) : uniqueTasks);
		log.debug("Task generated: "+this.saveData.getActiveTaskPointer().getTask().getDescription());

		this.taskDashboard.disableGenerateTask(false);
		taskList.refreshTasks(0);

		this.taskDashboard.updatePercentages();
		savePlayerData();
	}

	public void completeTask() {
		completeTask(saveData.getActiveTaskPointer().getTask().getId(), saveData.getActiveTaskPointer().getTaskTier());
	}

	public void completeTask(int taskID, TaskTier tier) {
		this.client.playSoundEffect(SoundEffectID.UI_BOOP);

		if (saveData.getProgress().get(tier).contains(taskID)) {
			saveData.getProgress().get(tier).remove(taskID);
		} else {
			addCompletedTask(taskID, tier);
			if (saveData.getActiveTaskPointer() != null && taskID == saveData.getActiveTaskPointer().getTask().getId()) {
				nullCurrentTask();
			}
		}
		this.taskDashboard.updatePercentages();

		taskList.refreshTasks(0);

		savePlayerData();
	}

	private void nullCurrentTask() {
		this.saveData.setActiveTaskPointer(null);
		this.taskDashboard.setTask("No task.", -1, null);
		this.taskDashboard.enableGenerateTask();
	}

	public static int getCenterX(Widget window, int width) {
		return (window.getWidth() / 2) - (width / 2);
	}

	public static int getCenterY(Widget window, int height) {
		return (window.getHeight() / 2) - (height / 2);
	}

	public void addCompletedTask(int taskID, TaskTier tier) {
		this.saveData.getProgress().get(tier).add(taskID);
	}

	public TaskTier getCurrentTier() {
		if (this.saveData.getProgress().get(TaskTier.EASY).size() < this.tasks.getEasy().size() &&
				!Arrays.asList(TaskTier.MEDIUM, TaskTier.HARD, TaskTier.ELITE, TaskTier.MASTER).contains(config.hideBelow())) {
			return TaskTier.EASY;
		} else if (this.saveData.getProgress().get(TaskTier.MEDIUM).size() < this.tasks.getMedium().size() &&
				!Arrays.asList(TaskTier.HARD, TaskTier.ELITE, TaskTier.MASTER).contains(config.hideBelow())) {
			return TaskTier.MEDIUM;
		} else if (this.saveData.getProgress().get(TaskTier.HARD).size() < this.tasks.getHard().size() &&
				!Arrays.asList(TaskTier.ELITE, TaskTier.MASTER).contains(config.hideBelow())) {
			return TaskTier.HARD;
		} else if (this.saveData.getProgress().get(TaskTier.ELITE).size() < this.tasks.getElite().size() &&
				TaskTier.MASTER != config.hideBelow()) {
			return TaskTier.ELITE;
		} else {
			return TaskTier.MASTER;
		}
	}

	public TaskTier getSelectedTier() {
		return this.saveData.getSelectedTier();
	}

	public List<Task> findAvailableTasks() {
		return this.tasks.getForTier(getCurrentTier()).stream().filter(t -> !this.saveData.getProgress().get(getCurrentTier()).contains(t.getId())).collect(Collectors.toList());
	}

	private void hideTabs() {
		if (this.taskDashboardTab != null) {
			this.taskDashboardTab.setVisibility(false);
		}
		if (this.tabs != null) {
			this.tabs.forEach(t -> t.setVisibility(false));
		}
		if (this.taskListTab != null) {
			this.taskListTab.setVisibility(false);
		}
	}

	private void showTabs() {
		if (this.taskDashboardTab != null) {
			this.taskDashboardTab.setVisibility(true);
		}
		if (this.config.hideBelow() == TaskTier.MASTER) {
			this.taskListTab.setVisibility(true);
		} else {
			int tabIndex = 0;
			for (TaskTier tier : TaskTier.values()) {
				if (tabIndex > 0 || tier == config.hideBelow()) {
					this.tabs.get(tabIndex).setVisibility(true);
					tabIndex++;
				}
			}
		}
	}

	private void activateTaskDashboard() {
		this.taskDashboardTab.setSprites(DASHBOARD_TAB_HOVER_SPRITE_ID);
		this.taskList.setVisibility(false);
		this.taskDashboard.setVisibility(true);
		this.taskListTab.setSprites(TASKLIST_TAB_SPRITE_ID, TASKLIST_TAB_HOVER_SPRITE_ID);
		updateTabs();
		showTabs();
	}

	public void playFailSound() {
		client.playSoundEffect(2277);
	}

	public Map<TaskTier, Integer> completionPercentages() {
		Map<TaskTier, Integer> completionPercentages = new HashMap<>();
		for (TaskTier tier : TaskTier.values()) {
			completionPercentages.put(tier, (int) Math.round(((double) saveData.getProgress().get(tier).size() / (double) this.tasks.getForTier(tier).size()) * 100));
		}
		return completionPercentages;
	}

	@Provides
	LogMasterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LogMasterConfig.class);
	}
}
