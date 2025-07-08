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
        // Remove any existing tabs from the window
        if (tabs != null) {
            for (UIButton tab : tabs) {
                if (tab != null && tab.getWidget() != null) {
                    tab.getWidget().setHidden(true);
                }
            }
        }
        tabs = new ArrayList<>();
        // Remove and recreate dashboard tab
        if (taskDashboardTab != null && taskDashboardTab.getWidget() != null) {
            taskDashboardTab.getWidget().setHidden(true);
        }
        Widget dashboardTabWidget = window.createChild(-1, WidgetType.GRAPHIC);
        taskDashboardTab = new UIButton(dashboardTabWidget);
        taskDashboardTab.setSprites(DASHBOARD_TAB_SPRITE_ID, DASHBOARD_TAB_HOVER_SPRITE_ID);
        taskDashboardTab.setSize(95, 21);
        taskDashboardTab.setPosition(10, 0);
        taskDashboardTab.addAction("View <col=ff9040>Dashboard</col>", this::activateTaskDashboard);
        taskDashboardTab.setVisibility(false);
        // Always create all tabs for all tiers
        for (TaskTier tier : TaskTier.values()) {
            Widget tabWidget = window.createChild(-1, WidgetType.GRAPHIC);
            UIButton tab = new UIButton(tabWidget);
            tab.setSize(66, 21);
            tab.setVisibility(false);
            tabs.add(tab);
        }
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
        
        // Force widget position updates for all tabs
        taskDashboardTab.getWidget().revalidate();
        for (UIButton tab : tabs) {
            tab.getWidget().revalidate();
        }

        // Update tab positions
        updateTabPositions();
    }

    private void updateTabPositions() {
        int windowWidth = window.getWidth();
        int availableWidth = windowWidth - 20; // 10px margin on each side
        int dashboardTabWidth = 95;
        int regularTabWidth = 66;
        // Count only visible tabs
        int visibleTierTabs = 0;
        for (TaskTier tier : TaskTier.values()) {
            if (tier.ordinal() >= config.hideBelow().ordinal()) {
                visibleTierTabs++;
            }
        }
        int totalTabsWidth = dashboardTabWidth + (visibleTierTabs * regularTabWidth);
        int spacing = Math.max(10, (availableWidth - totalTabsWidth) / (visibleTierTabs + 2)); // Minimum 10px spacing
        int dashboardX = 10 + spacing;
        taskDashboardTab.setPosition(dashboardX, 0);
        taskDashboardTab.getWidget().setPos(dashboardX, 0);
        int currentX = dashboardX + dashboardTabWidth + spacing;
        int tabIndex = 0;
        for (TaskTier tier : TaskTier.values()) {
            if (tier.ordinal() >= config.hideBelow().ordinal()) {
                UIButton tab = tabs.get(tabIndex);
                tab.setPosition(currentX, 0);
                tab.getWidget().setPos(currentX, 0);
                currentX += regularTabWidth + spacing;
            }
            tabIndex++;
        }
        // Revalidate all tabs
        for (UIButton tab : tabs) {
            tab.revalidate();
            tab.getWidget().revalidate();
        }
    }

    public void updateTabs() {
        hideTabs();
        int tabIndex = 0;
        for (TaskTier tier : TaskTier.values()) {
            if (tier.ordinal() >= config.hideBelow().ordinal()) {
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
            }
            tabIndex++;
        }
        showTabs();
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
        this.taskList.refreshTasks(0, true);
        this.taskList.setVisibility(true);
    }

    public void activateTaskDashboard() {
        this.taskDashboardTab.setSprites(DASHBOARD_TAB_HOVER_SPRITE_ID);
        this.taskList.setVisibility(false);
        this.taskDashboard.setVisibility(true);
        updateTabs();
    }

    public void hideTabs() {
        if (this.taskDashboardTab != null) {
            this.taskDashboardTab.setVisibility(false);
        }
        if (this.tabs != null) {
            this.tabs.forEach(t -> {
                t.setVisibility(false);
            });
        }
    }

    public void showTabs() {
        // Hide tabs if neither list is visible
        if (!this.taskList.isVisible() && !this.taskDashboard.isVisible()) {
            this.hideTabs();
            return;
        }
        if (this.taskDashboardTab != null) {
            this.taskDashboardTab.setVisibility(true);
        }
        int tabIndex = 0;
        for (TaskTier tier : TaskTier.values()) {
            UIButton tab = this.tabs.get(tabIndex);
            if (tier.ordinal() >= config.hideBelow().ordinal()) {
                tab.setVisibility(true);
            } else {
                // Move out of view before hiding
                tab.setPosition(-1000, 0);
                tab.getWidget().setPos(-1000, 0);
                tab.setVisibility(false);
            }
            tabIndex++;
        }
        updateTabPositions();
    }

    public void onConfigChanged() {
        createTabs();
        updateTabs();
    }
}
