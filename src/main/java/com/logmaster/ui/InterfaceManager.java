package com.logmaster.ui;

import com.google.gson.Gson;
import com.logmaster.LogMasterConfig;
import com.logmaster.LogMasterPlugin;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskTier;
import com.logmaster.persistence.SaveDataManager;
import com.logmaster.task.TaskService;
import com.logmaster.ui.component.TaskDashboard;
import com.logmaster.ui.component.TaskList;
import com.logmaster.ui.generic.UIButton;
import com.logmaster.ui.generic.UICheckBox;
import com.logmaster.ui.generic.UIComponent;
import com.logmaster.ui.generic.UIGraphic;
import com.logmaster.util.FileUtils;
import net.runelite.api.Client;
import net.runelite.api.SoundEffectID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.SpriteManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.logmaster.ui.InterfaceConstants.*;

@Singleton
public class InterfaceManager {

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
    private SpriteManager spriteManager;

    @Inject
    private Gson gson;

    @Inject
    private SaveDataManager saveDataManager;


    private SpriteDefinition[] spriteDefinitions;

    private TaskDashboard taskDashboard;
    private TaskList taskList;
    private UICheckBox taskDashboardCheckbox;

    private List<UIButton> tabs;
    private UIButton taskListTab;
    private UIButton taskDashboardTab;

    public void initialise() {
        this.spriteDefinitions = FileUtils.loadDefinitionResource(SpriteDefinition[].class, DEF_FILE_SPRITES, gson);
        this.spriteManager.addSpriteOverrides(spriteDefinitions);
    }

    public void updateAfterConfigChange() {
        hideTabs();
        updateTabs();
        if (this.config.hideBelow() == TaskTier.MASTER && this.saveDataManager.getSaveData().getSelectedTier() == TaskTier.MASTER && !this.taskDashboard.isVisible()) {
            this.taskListTab.setSprites(TASKLIST_TAB_HOVER_SPRITE_ID);
        }
        if (this.taskDashboard != null && this.taskDashboardCheckbox.isEnabled()) {
            showTabs();
            if (this.saveDataManager.getSaveData().getSelectedTier() != null && Arrays.asList(TaskTier.values()).indexOf(this.saveDataManager.getSaveData().getSelectedTier()) < Arrays.asList(TaskTier.values()).indexOf(this.config.hideBelow())) {
                activateTaskDashboard();
            }
            this.taskDashboard.updatePercentages();
        }
    }

    public void handleCollectionLogOpen() {
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
            if (this.saveDataManager.getSaveData().getSelectedTier() != TaskTier.MASTER) {
                this.taskList.goToTop();
                this.saveDataManager.getSaveData().setSelectedTier(TaskTier.MASTER);
                this.saveDataManager.save();
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

    public void handleCollectionLogClose() {
        this.taskDashboard.setVisibility(false);
        this.taskList.setVisibility(false);
        hideTabs();
        this.taskDashboardCheckbox.setEnabled(false);
    }

    public boolean isDashboardOpen() {
        return this.taskDashboard != null && this.taskDashboard.isVisible();
    }

    public void updateTaskListBounds() {
        if (this.taskList != null) {
            taskList.updateBounds();
        }
    }

    public void handleMouseWheel(MouseWheelEvent event) {
        if(this.taskList != null) {
            taskList.handleWheel(event);
        }
    }

    public void disableGenerateTaskButton() {
        this.taskDashboard.disableGenerateTask();
    }

    private void createTaskCheckbox() {
        Widget window = client.getWidget(40697857);
        if (window != null) {
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
    }

    private void updateTabs() {
        int tabIndex = 0;
        for (TaskTier tier : TaskTier.values()) {
            if (tabIndex > 0 || tier == config.hideBelow()) {
                if (tabs == null) {
                    return;
                }
                if (this.saveDataManager.getSaveData().getSelectedTier() == tier && !this.taskDashboard.isVisible()) {
                    tabs.get(tabIndex).setSprites(tier.tabSpriteHoverId);
                } else {
                    tabs.get(tabIndex).setSprites(tier.tabSpriteId, tier.tabSpriteHoverId);
                }
                int finalTabIndex = tabIndex;
                tabs.get(tabIndex).clearActions();
                tabs.get(tabIndex).setSize(66, 21);
                tabs.get(tabIndex).addAction(String.format("View <col=ff9040>%s Task List</col>", tier.displayName), () -> {
                    taskDashboardTab.setSprites(DASHBOARD_TAB_SPRITE_ID, DASHBOARD_TAB_HOVER_SPRITE_ID);
                    if (this.saveDataManager.getSaveData().getSelectedTier() != tier) {
                        this.taskList.goToTop();
                        this.saveDataManager.getSaveData().setSelectedTier(tier);
                        this.saveDataManager.save();
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

    private void createTaskDashboard(Widget window) {
        this.taskDashboard = new TaskDashboard(plugin, config, window, taskService, saveDataManager);
        this.taskDashboard.setVisibility(false);
    }

    private void createTaskList(Widget window) {
        this.taskList = new TaskList(window, taskService, plugin, clientThread, this.saveDataManager);
        this.taskList.setVisibility(false);
    }

    private void toggleTaskDashboard(UIComponent src) {
        if(this.taskDashboard == null) return;

        if (saveDataManager.getSaveData().getActiveTaskPointer() != null) {
            this.taskDashboard.setTask(this.saveDataManager.getSaveData().getActiveTaskPointer().getTask().getDescription(), this.saveDataManager.getSaveData().getActiveTaskPointer().getTask().getItemID(), null);
            this.taskDashboard.disableGenerateTask();
        } else {
            plugin.nullCurrentTask();
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

    public void rollTask(String description, int itemID, List<Task> tasks) {
        this.taskDashboard.setTask(description, itemID, tasks);
        this.taskDashboard.disableGenerateTask(false);
        this.taskList.refreshTasks(0);
        this.taskDashboard.updatePercentages();
    }

    public void completeTask() {
        this.taskDashboard.updatePercentages();
        taskList.refreshTasks(0);
    }

    public void clearCurrentTask() {
        this.taskDashboard.setTask("No task.", -1, null);
        this.taskDashboard.enableGenerateTask();
        this.taskDashboard.enableFaqButton();
    }
}
