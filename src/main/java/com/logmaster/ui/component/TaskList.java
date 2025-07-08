package com.logmaster.ui.component;

import com.logmaster.LogMasterPlugin;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskTier;
import com.logmaster.domain.TieredTaskList;
import com.logmaster.persistence.SaveDataManager;
import com.logmaster.task.TaskService;
import com.logmaster.ui.generic.UIButton;
import com.logmaster.ui.generic.UIGraphic;
import com.logmaster.ui.generic.UILabel;
import com.logmaster.ui.generic.UIPage;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;

import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;

import static com.logmaster.LogMasterPlugin.getCenterX;
import static com.logmaster.ui.InterfaceConstants.*;

@Slf4j
public class TaskList extends UIPage {
    private final int OFFSET_X = 0;
    private final int OFFSET_Y = 22;
    private final int CANVAS_WIDTH = 480;
    private final int CANVAS_HEIGHT = 252;
    private final int TASK_WIDTH = 300;
    private final int TASK_HEIGHT = 50;
    private final int TASK_ITEM_HEIGHT = 32;
    private final int TASK_ITEM_WIDTH = 36;
    private final int PAGE_UP_ARROW_SPRITE_ID = -20029;
    private final int UP_ARROW_SPRITE_ID = -20014;
    private final int DOWN_ARROW_SPRITE_ID = -20015;
    private final int PAGE_DOWN_ARROW_SPRITE_ID = -20030;
    private final int THUMB_TOP_SPRITE_ID = -20031;
    private final int THUMB_MIDDLE_SPRITE_ID = -20032;
    private final int THUMB_BOTTOM_SPRITE_ID = -20033;
    private final int ARROW_SPRITE_WIDTH = 39;
    private final int ARROW_SPRITE_HEIGHT = 20;
    private final int ARROW_Y_OFFSET = 4;
    private final int SCROLLBAR_WIDTH = 35; // Match arrow width
    private final int SCROLLBAR_THUMB_MIN_HEIGHT = 8;
    
    private int tasksPerPage = 20; // Default value, will be updated based on window size

    private final Widget window;
    private final TaskService taskService;
    private final LogMasterPlugin plugin;
    private final ClientThread clientThread;

    private final SaveDataManager saveDataManager;

    private Rectangle bounds = new Rectangle();

    private List<UIGraphic> taskBackgrounds = new ArrayList<>();
    private List<UILabel> taskLabels = new ArrayList<>();
    private List<UIGraphic> taskImages = new ArrayList<>();
    private Widget scrollbarTrackWidget;
    private Widget scrollbarThumbTopWidget;
    private Widget scrollbarThumbMiddleWidget;
    private Widget scrollbarThumbBottomWidget;
    private UIButton pageUpButton;
    private UIButton upArrowButton;
    private UIButton downArrowButton;
    private UIButton pageDownButton;
    private boolean isDraggingThumb = false;
    private int dragStartY = 0;
    private int dragStartTopIndex = 0;
    private int topTaskIndex = 0;

    public TaskList(Widget window, TaskService taskService, LogMasterPlugin plugin, ClientThread clientThread, SaveDataManager saveDataManager) {
        this.window = window;
        this.taskService = taskService;
        this.plugin = plugin;
        this.clientThread = clientThread;
        this.saveDataManager = saveDataManager;

        updateBounds();
        refreshTasks(0);

        createScrollbarComponents();
        this.add(upArrowButton);
        this.add(pageUpButton);
        this.add(downArrowButton);
        this.add(pageDownButton);
        updateScrollbar();
    }

    private void createScrollbarComponents() {
        Widget pageUpWidget = window.createChild(-1, WidgetType.GRAPHIC);
        pageUpButton = new UIButton(pageUpWidget);
        pageUpButton.setSprites(PAGE_UP_ARROW_SPRITE_ID);
        pageUpButton.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        pageUpButton.setPosition(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH+5), ARROW_SPRITE_HEIGHT + ARROW_Y_OFFSET);
        pageUpButton.addAction("Page up", () -> refreshTasks(-tasksPerPage));

        Widget upWidget = window.createChild(-1, WidgetType.GRAPHIC);
        upArrowButton = new UIButton(upWidget);
        upArrowButton.setSprites(UP_ARROW_SPRITE_ID);
        upArrowButton.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        upArrowButton.setPosition(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH+5), ARROW_SPRITE_HEIGHT*2 + ARROW_Y_OFFSET);
        upArrowButton.addAction("Scroll up", () -> refreshTasks(-1));

        scrollbarTrackWidget = window.createChild(-1, WidgetType.RECTANGLE);
        scrollbarTrackWidget.setFilled(true);
        scrollbarTrackWidget.setTextColor(0x665948);
        scrollbarTrackWidget.setSize(SCROLLBAR_WIDTH, 200);
        scrollbarTrackWidget.setPos(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH + 5) + 2, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET);
        
        scrollbarThumbTopWidget = window.createChild(-1, WidgetType.GRAPHIC);
        scrollbarThumbTopWidget.setSpriteId(THUMB_TOP_SPRITE_ID);
        scrollbarThumbTopWidget.setSize(SCROLLBAR_WIDTH, 2);
        scrollbarThumbTopWidget.setPos(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH + 5) + 2, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET);

        scrollbarThumbMiddleWidget = window.createChild(-1, WidgetType.GRAPHIC);
        scrollbarThumbMiddleWidget.setSpriteId(THUMB_MIDDLE_SPRITE_ID);
        scrollbarThumbMiddleWidget.setSize(SCROLLBAR_WIDTH, SCROLLBAR_THUMB_MIN_HEIGHT - 4);
        scrollbarThumbMiddleWidget.setPos(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH + 5) + 2, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET + 2);

        scrollbarThumbBottomWidget = window.createChild(-1, WidgetType.GRAPHIC);
        scrollbarThumbBottomWidget.setSpriteId(THUMB_BOTTOM_SPRITE_ID);
        scrollbarThumbBottomWidget.setSize(SCROLLBAR_WIDTH, 2);
        scrollbarThumbBottomWidget.setPos(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH + 5) + 2, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET + SCROLLBAR_THUMB_MIN_HEIGHT - 2);

        Widget downWidget = window.createChild(-1, WidgetType.GRAPHIC);
        downArrowButton = new UIButton(downWidget);
        downArrowButton.setSprites(DOWN_ARROW_SPRITE_ID);
        downArrowButton.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        downArrowButton.addAction("Scroll down", () -> refreshTasks(1));

        Widget pageDownWidget = window.createChild(-1, WidgetType.GRAPHIC);
        pageDownButton = new UIButton(pageDownWidget);
        pageDownButton.setSprites(PAGE_DOWN_ARROW_SPRITE_ID);
        pageDownButton.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        pageDownButton.addAction("Page down", () -> refreshTasks(tasksPerPage));
    }

    public void refreshTasks(int dir) {
        refreshTasks(dir, false);
    }
    
    public void refreshTasks(int dir, boolean forceRefresh) {
        TaskTier relevantTier = plugin.getSelectedTier();
        if (relevantTier == null) {
            relevantTier = TaskTier.MASTER;
        }
        
        if (!forceRefresh) {
            int newIndex = topTaskIndex + dir;
            // Ensure we don't go past the valid range
            topTaskIndex = Math.min(taskService.getTaskList().getForTier(relevantTier).size() - tasksPerPage, Math.max(0, newIndex));
        }

        final int POS_X = CANVAS_WIDTH / 2 - TASK_WIDTH / 2;

        int i = 0;
        for(Task task : getTasksToShow(relevantTier, topTaskIndex)) {
            final int POS_Y = OFFSET_Y+(i*TASK_HEIGHT);

            UIGraphic taskBg;
            if(taskBackgrounds.size() <= i) {
                taskBg = new UIGraphic(window.createChild(-1, WidgetType.GRAPHIC));
                taskBackgrounds.add(taskBg);
                this.add(taskBg);
            }
            else {
                taskBg = taskBackgrounds.get(i);
                taskBg.getWidget().setHidden(false);
            }

            taskBg.clearActions();
            taskBg.getWidget().clearActions();
            taskBg.setSize(TASK_WIDTH, TASK_HEIGHT);
            taskBg.setPosition(POS_X, POS_Y);
            taskBg.getWidget().setPos(POS_X, POS_Y);

            boolean taskCompleted = plugin.isTaskCompleted(task.getId(), relevantTier);
            taskBg.addAction("Mark as " + (taskCompleted ? "<col=e74c3c>incomplete" : "<col=2ecc71>completed") + "</col>", () -> plugin.completeTask(task.getId(), relevantTier));
            
            if (task.getCheck().length <= 24 && task.getCheck().length > 0) {
                for (int checkID : task.getCheck()) {
                    String itemName = plugin.itemManager.getItemComposition(checkID).getName();
                    taskBg.addAction((plugin.clogItemsManager.isCollectionLogItemUnlocked(checkID) ? "<col=2ecc71>" : "<col=e74c3c>") + itemName + "</col>", () -> {});
                }
            }

            if (taskCompleted) {
                taskBg.setSprite(TASK_COMPLETE_BACKGROUND_SPRITE_ID);
            } else if (saveDataManager.getSaveData().getActiveTaskPointer() != null && saveDataManager.getSaveData().getActiveTaskPointer().getTaskTier() == relevantTier && saveDataManager.getSaveData().getActiveTaskPointer().getTask().getId() == task.getId()) {
                taskBg.setSprite(TASK_CURRENT_BACKGROUND_SPRITE_ID);
            } else {
                taskBg.setSprite(TASK_LIST_BACKGROUND_SPRITE_ID);
            }

            UILabel taskLabel;
            if (taskLabels.size() <= i) {
                taskLabel = new UILabel(window.createChild(-1, WidgetType.TEXT));
                this.add(taskLabel);
                taskLabels.add(taskLabel);
            } else {
                taskLabel = taskLabels.get(i);
                taskLabel.getWidget().setHidden(false);
            }

            taskLabel.getWidget().setTextColor(Color.WHITE.getRGB());
            taskLabel.getWidget().setTextShadowed(true);
            taskLabel.getWidget().setName(task.getDescription());
            taskLabel.setFont(496);
            taskLabel.setPosition(POS_X+60, POS_Y);
            taskLabel.setSize(TASK_WIDTH-60, TASK_HEIGHT);
            taskLabel.setText(task.getDescription());

            UIGraphic taskImage;
            if(taskImages.size() <= i) {
                taskImage = new UIGraphic(window.createChild(-1, WidgetType.GRAPHIC));
                this.add(taskImage);
                taskImages.add(taskImage);
            }
            else {
                taskImage = taskImages.get(i);
                taskImage.getWidget().setHidden(false);
            }

            taskImage.setPosition(POS_X+12, POS_Y+6);
            taskImage.getWidget().setBorderType(1);
            taskImage.getWidget().setItemQuantityMode(ItemQuantityMode.NEVER);
            taskImage.setSize(TASK_ITEM_WIDTH, TASK_ITEM_HEIGHT);
            taskImage.setItem(task.getItemID());

            i++;
        }
        
        // Hide any remaining task UI elements that are no longer visible
        hideUnusedTaskElements(i);
        updateScrollbar();
    }

    private void hideUnusedTaskElements(int visibleCount) {
        for (int i = visibleCount; i < taskBackgrounds.size(); i++) {
            taskBackgrounds.get(i).getWidget().setHidden(true);
        }
        
        for (int i = visibleCount; i < taskLabels.size(); i++) {
            taskLabels.get(i).getWidget().setHidden(true);
        }
        
        for (int i = visibleCount; i < taskImages.size(); i++) {
            taskImages.get(i).getWidget().setHidden(true);
        }
    }

    public void goToTop() {
        topTaskIndex = 0;
        updateScrollbar();
    }

    private List<Task> getTasksToShow(TaskTier relevantTier, int topTaskIndex) {
        List<Task> tasksToShow = new ArrayList<>();
        List<Task> taskList = taskService.getTaskList().getForTier(relevantTier);
        for (int i = 0; i < tasksPerPage; i++) {
            if (topTaskIndex + i >= taskList.size()) break;
            tasksToShow.add(taskList.get(topTaskIndex + i));
        }
        return tasksToShow;
    }

    public void handleWheel(final MouseWheelEvent event)
    {
        if (!this.isVisible() || !bounds.contains(event.getPoint()))
        {
            return;
        }

        event.consume();

        clientThread.invoke(() -> refreshTasks(event.getWheelRotation()));
    }

    public void updateBounds()
    {
        if (!this.isVisible()) {
            tasksPerPage = 20; // Default value, will be updated based on window size
            return;
        }

        Widget collectionLogWrapper = window.getParent();
        int wrapperX = collectionLogWrapper.getRelativeX();
        int wrapperY = collectionLogWrapper.getRelativeY();
        int windowWidth = window.getWidth();
        int windowHeight = window.getHeight();
        int windowX = window.getRelativeX();
        int windowY = window.getRelativeY();

        // Recalculate how many tasks can be displayed
        int newTasksPerPage = Math.max(1, (windowHeight - OFFSET_Y) / TASK_HEIGHT);
        if (newTasksPerPage != tasksPerPage) {
            tasksPerPage = newTasksPerPage;
            // Ensure topTaskIndex is valid for the new page size
            TaskTier relevantTier = plugin.getSelectedTier();
            if (relevantTier == null) {
                relevantTier = TaskTier.MASTER;
            }
            int maxTopIndex = Math.max(0, taskService.getTaskList().getForTier(relevantTier).size() - tasksPerPage);
            topTaskIndex = Math.min(topTaskIndex, maxTopIndex);
            
            // Update arrow positions immediately when page size changes
            updateArrowPositions();
            
            // Update scrollbar immediately when size changes
            updateScrollbar();
            
            // Force refresh the task display
            refreshTasks(0, true);
        }

        bounds.setLocation(wrapperX + windowX + OFFSET_X, wrapperY + windowY + OFFSET_Y);
        bounds.setSize(windowWidth - OFFSET_X, windowHeight - OFFSET_Y);
    }

    private void updateArrowPositions() {
        int scrollbarTrackHeight = (tasksPerPage * TASK_HEIGHT) - (ARROW_SPRITE_HEIGHT * 4);
        int scrollbarEndY = ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET + scrollbarTrackHeight;
        
        forceWidgetPositionUpdate(downArrowButton, CANVAS_WIDTH - (ARROW_SPRITE_WIDTH+5), scrollbarEndY);
        forceWidgetPositionUpdate(pageDownButton, CANVAS_WIDTH - (ARROW_SPRITE_WIDTH+5), scrollbarEndY + ARROW_SPRITE_HEIGHT);
    }

    private void forceWidgetPositionUpdate(UIButton button, int x, int y) {
        button.getWidget().setHidden(true);
        button.setPosition(x, y);
        button.getWidget().setHidden(false);
        button.getWidget().revalidate();
    }

    private void updateScrollbar() {
        if (!this.isVisible()) {
            setScrollbarVisibility(false);
            return;
        }

        TaskTier relevantTier = plugin.getSelectedTier();
        if (relevantTier == null) relevantTier = TaskTier.MASTER;
        
        int totalTasks = taskService.getTaskList().getForTier(relevantTier).size();
        int scrollbarTrackHeight = (tasksPerPage * TASK_HEIGHT) - (ARROW_SPRITE_HEIGHT * 4);
        
        forceWidgetUpdate(scrollbarTrackWidget, SCROLLBAR_WIDTH, scrollbarTrackHeight);
        updateArrowPositions();
        
        if (totalTasks <= tasksPerPage) {
            setScrollbarVisibility(false);
        } else {
            setScrollbarVisibility(true);
            updateScrollbarThumb(totalTasks, scrollbarTrackHeight);
        }
    }

    private void forceWidgetUpdate(Widget widget, int width, int height) {
        widget.setHidden(true);
        widget.setSize(width, height);
        widget.setHidden(false);
        widget.revalidate();
    }

    private void updateScrollbarThumb(int totalTasks, int scrollbarTrackHeight) {
        topTaskIndex = Math.min(topTaskIndex, Math.max(0, totalTasks - tasksPerPage));
        
        int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, (int)(scrollbarTrackHeight * ((double)tasksPerPage / totalTasks)));
        int maxScrollPosition = Math.max(1, totalTasks - tasksPerPage);
        int thumbY = maxScrollPosition > 0 ? (int)((scrollbarTrackHeight - thumbHeight) * ((double)topTaskIndex / maxScrollPosition)) : 0;
        
        int thumbX = CANVAS_WIDTH - (ARROW_SPRITE_WIDTH + 5) + 2;
        int thumbStartY = ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET + thumbY;
        
        // Update top edge (2px height)
        scrollbarThumbTopWidget.setPos(thumbX, thumbStartY);
        scrollbarThumbTopWidget.setSize(SCROLLBAR_WIDTH, 2);
        
        // Update middle section (variable height)
        int middleHeight = Math.max(0, thumbHeight - 4);
        scrollbarThumbMiddleWidget.setPos(thumbX, thumbStartY + 2);
        scrollbarThumbMiddleWidget.setSize(SCROLLBAR_WIDTH, middleHeight);
        
        // Update bottom edge (2px height)
        scrollbarThumbBottomWidget.setPos(thumbX, thumbStartY + thumbHeight - 2);
        scrollbarThumbBottomWidget.setSize(SCROLLBAR_WIDTH, 2);
        
        // Force redraw all thumb components
        forceThumbWidgetUpdate(scrollbarThumbTopWidget, SCROLLBAR_WIDTH, 2);
        forceThumbWidgetUpdate(scrollbarThumbMiddleWidget, SCROLLBAR_WIDTH, middleHeight);
        forceThumbWidgetUpdate(scrollbarThumbBottomWidget, SCROLLBAR_WIDTH, 2);
    }

    private void forceThumbWidgetUpdate(Widget widget, int width, int height) {
        widget.setHidden(true);
        widget.setSize(width, height);
        widget.setHidden(false);
        widget.revalidate();
    }

    private void setScrollbarVisibility(boolean visible) {
        if (scrollbarTrackWidget != null) scrollbarTrackWidget.setHidden(!visible);
        if (scrollbarThumbTopWidget != null) scrollbarThumbTopWidget.setHidden(!visible);
        if (scrollbarThumbMiddleWidget != null) scrollbarThumbMiddleWidget.setHidden(!visible);
        if (scrollbarThumbBottomWidget != null) scrollbarThumbBottomWidget.setHidden(!visible);
    }

    public void setVisibility(boolean visible) {
        super.setVisibility(visible);
        setScrollbarVisibility(visible && this.isVisible());
        if (visible) updateScrollbar();
    }

    public void handleMousePress(int mouseX, int mouseY) {
        if (!this.isVisible()) return;
        
        clientThread.invoke(() -> {
            if (isPointInScrollThumb(mouseX, mouseY)) {
                isDraggingThumb = true;
                dragStartY = mouseY;
                dragStartTopIndex = topTaskIndex;
            }
        });
    }

    public void handleMouseDrag(int mouseX, int mouseY) {
        if (!isDraggingThumb || !this.isVisible()) return;
        
        clientThread.invoke(() -> {
            TaskTier relevantTier = plugin.getSelectedTier();
            if (relevantTier == null) relevantTier = TaskTier.MASTER;
            
            int totalTasks = taskService.getTaskList().getForTier(relevantTier).size();
            if (totalTasks <= tasksPerPage) return;
            
            int newTopIndex = calculateNewScrollPosition(mouseY, totalTasks);
            if (newTopIndex != topTaskIndex) {
                topTaskIndex = newTopIndex;
                refreshTasks(0, true);
            }
        });
    }

    private boolean isPointInScrollThumb(int mouseX, int mouseY) {
        Widget collectionLogWrapper = window.getParent();
        int baseX = collectionLogWrapper.getRelativeX() + window.getRelativeX();
        int baseY = collectionLogWrapper.getRelativeY() + window.getRelativeY();
        
        // Check if point is in any of the three thumb components
        int thumbX = baseX + scrollbarThumbTopWidget.getRelativeX();
        int thumbTopY = baseY + scrollbarThumbTopWidget.getRelativeY();
        int thumbBottomY = baseY + scrollbarThumbBottomWidget.getRelativeY() + scrollbarThumbBottomWidget.getHeight();
        int thumbWidth = scrollbarThumbTopWidget.getWidth();
        
        return mouseX >= thumbX && mouseX <= thumbX + thumbWidth && 
               mouseY >= thumbTopY && mouseY <= thumbBottomY;
    }

    private int calculateNewScrollPosition(int mouseY, int totalTasks) {
        int scrollbarTrackHeight = (tasksPerPage * TASK_HEIGHT) - (ARROW_SPRITE_HEIGHT * 4);
        int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, (int)(scrollbarTrackHeight * ((double)tasksPerPage / totalTasks)));
        int deltaY = mouseY - dragStartY;
        
        double scrollRatio = (scrollbarTrackHeight - thumbHeight) > 0 ? (double)deltaY / (scrollbarTrackHeight - thumbHeight) : 0;
        int newTopIndex = dragStartTopIndex + (int)(scrollRatio * (totalTasks - tasksPerPage));
        
        return Math.min(totalTasks - tasksPerPage, Math.max(newTopIndex, 0));
    }

    public void handleMouseRelease() {
        isDraggingThumb = false;
    }
}
