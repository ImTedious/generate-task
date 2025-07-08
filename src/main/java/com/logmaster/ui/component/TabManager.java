package com.logmaster.ui.component;

import com.logmaster.LogMasterConfig;
import com.logmaster.LogMasterPlugin;
import com.logmaster.domain.TaskTier;
import com.logmaster.persistence.SaveDataManager;
import com.logmaster.ui.generic.UIButton;
import com.logmaster.ui.generic.UIGraphic;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

import java.util.ArrayList;
import java.util.List;

import static com.logmaster.ui.InterfaceConstants.*;

public class TabManager {
    private final LogMasterConfig config;
    private final SaveDataManager saveDataManager;
    private final Widget window;
    
    private List<UIButton> tabs;
    private UIButton taskListTab;
    private UIButton taskDashboardTab;
    
    private TaskDashboard taskDashboard;
    private TaskList taskList;
    private UIGraphic divider;

    public TabManager(Widget window, LogMasterConfig config, SaveDataManager saveDataManager) {
        this.window = window;
        this.config = config;
        this.saveDataManager = saveDataManager;
        
        createTabs();
        createDivider();
    }

    public void setComponents(TaskDashboard taskDashboard, TaskList taskList) {
        this.taskDashboard = taskDashboard;
        this.taskList = taskList;
    }

    private void createTabs() {
        Widget dashboardTabWidget = window.createChild(-1, WidgetType.GRAPHIC);
        taskDashboardTab = new UIButton(dashboardTabWidget);
        taskDashboardTab.setSprites(DASHBOARD_TAB_SPRITE_ID, DASHBOARD_TAB_HOVER_SPRITE_ID);
        taskDashboardTab.setSize(95, 21);
        taskDashboardTab.setPosition(10, 0);
        taskDashboardTab.addAction("View <col=ff9040>Dashboard</col>", this::activateTaskDashboard);
        taskDashboardTab.setVisibility(false);

        tabs = new ArrayList<>();
        // Will be positioned dynamically in updateTabPositions()
        for (int i = 0; i < 5; i++) {
            Widget tabWidget = window.createChild(-1, WidgetType.GRAPHIC);
            UIButton tab = new UIButton(tabWidget);
            tab.setSize(66, 21);
            tab.setVisibility(false);
            tabs.add(tab);
        }

        Widget tabWidget = window.createChild(-1, WidgetType.GRAPHIC);
        taskListTab = new UIButton(tabWidget);
        taskListTab.setSize(95, 21);
        taskListTab.setSprites(TASKLIST_TAB_SPRITE_ID, TASKLIST_TAB_HOVER_SPRITE_ID);
        taskListTab.setVisibility(false);
        taskListTab.addAction("View <col=ff9040>Task List</col>", this::activateTaskList);
    }

    private void createDivider() {
        Widget dividerWidget = window.createChild(-1, WidgetType.GRAPHIC);
        divider = new UIGraphic(dividerWidget);
        divider.setSprite(DIVIDER_SPRITE_ID);
        divider.setSize(window.getWidth() - 20, 1); // Full width minus margins
        divider.setPosition(10, 20);
    }

    public void updateBounds() {
        // Update divider width to match window width
        int windowWidth = window.getWidth();
        divider.setSize(windowWidth - 20, 1);
        divider.getWidget().setSize(windowWidth - 20, 1);
        divider.getWidget().revalidate();
        
        // Update tab positions
        updateTabPositions();
        
        // Force widget position updates for all tabs
        taskDashboardTab.getWidget().revalidate();
        taskListTab.getWidget().revalidate();
        for (UIButton tab : tabs) {
            tab.getWidget().revalidate();
        }
    }

    private void updateTabPositions() {
        int windowWidth = window.getWidth();
        int availableWidth = windowWidth - 20; // 10px margin on each side
        int dashboardTabWidth = 95;
        int masterTabWidth = 95;
        int regularTabWidth = 66;
        
        // Calculate number of visible tier tabs
        int visibleTierTabs = 0;
        for (TaskTier tier : TaskTier.values()) {
            if (visibleTierTabs > 0 || tier == config.hideBelow()) {
                visibleTierTabs++;
            }
        }
        
        if (config.hideBelow() == TaskTier.MASTER) {
            // Only dashboard and master tabs
            int totalTabsWidth = dashboardTabWidth + masterTabWidth;
            int spacing = Math.max(10, (availableWidth - totalTabsWidth) / 3); // Minimum 10px spacing
            
            int dashboardX = 10 + spacing;
            int masterX = 10 + spacing + dashboardTabWidth + spacing;
            
            taskDashboardTab.setPosition(dashboardX, 0);
            taskDashboardTab.getWidget().setPos(dashboardX, 0);
            
            taskListTab.setPosition(masterX, 0);
            taskListTab.getWidget().setPos(masterX, 0);
        } else {
            // Dashboard tab + tier tabs
            int totalTabsWidth = dashboardTabWidth + (visibleTierTabs * regularTabWidth);
            int spacing = Math.max(10, (availableWidth - totalTabsWidth) / (visibleTierTabs + 2)); // Minimum 10px spacing
            
            int dashboardX = 10 + spacing;
            taskDashboardTab.setPosition(dashboardX, 0);
            taskDashboardTab.getWidget().setPos(dashboardX, 0);
            
            int currentX = 10 + spacing + dashboardTabWidth + spacing;
            int tabIndex = 0;
            for (TaskTier tier : TaskTier.values()) {
                if (tabIndex > 0 || tier == config.hideBelow()) {
                    if (tabIndex < tabs.size()) {
                        tabs.get(tabIndex).setPosition(currentX, 0);
                        tabs.get(tabIndex).getWidget().setPos(currentX, 0);
                        currentX += regularTabWidth + spacing;
                    }
                    tabIndex++;
                }
            }
        }
    }

    public void updateTabs() {
        updateTabPositions(); // Update positions first
        
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
                    activateTaskListForTier(tier, finalTabIndex);
                });
                tabIndex++;
            }
        }
    }

    private void activateTaskListForTier(TaskTier tier, int tabIndex) {
        taskDashboardTab.setSprites(DASHBOARD_TAB_SPRITE_ID, DASHBOARD_TAB_HOVER_SPRITE_ID);
        if (this.saveDataManager.getSaveData().getSelectedTier() != tier) {
            this.taskList.goToTop();
            this.saveDataManager.getSaveData().setSelectedTier(tier);
            this.saveDataManager.save();
        }
        updateTabs();
        tabs.get(tabIndex).setSprites(tier.tabSpriteHoverId);
        this.taskDashboard.setVisibility(false);
        this.taskList.refreshTasks(0);
        this.taskList.setVisibility(true);
    }

    private void activateTaskList() {
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
    }

    public void activateTaskDashboard() {
        this.taskDashboardTab.setSprites(DASHBOARD_TAB_HOVER_SPRITE_ID);
        this.taskList.setVisibility(false);
        this.taskDashboard.setVisibility(true);
        this.taskListTab.setSprites(TASKLIST_TAB_SPRITE_ID, TASKLIST_TAB_HOVER_SPRITE_ID);
        updateTabs();
        showTabs();
    }

    public void hideTabs() {
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

    public void showTabs() {
        updateTabPositions(); // Update positions when showing tabs
        
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

    public void updateAfterConfigChange() {
        if (this.config.hideBelow() == TaskTier.MASTER && this.saveDataManager.getSaveData().getSelectedTier() == TaskTier.MASTER && !this.taskDashboard.isVisible()) {
            this.taskListTab.setSprites(TASKLIST_TAB_HOVER_SPRITE_ID);
        }
    }
}
