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
    private final int ARROW_SPRITE_WIDTH = 39;
    private final int ARROW_SPRITE_HEIGHT = 20;
    private final int ARROW_Y_OFFSET = 4;
    private final int SCROLLBAR_WIDTH = 39; // Match arrow width
    private final int SCROLLBAR_THUMB_MIN_HEIGHT = 8;
    
    private int TASKS_PER_PAGE = 20; // Default value, will be updated based on window size

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
    private Widget scrollbarThumbWidget;
    private UIButton downArrow;
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

        Widget pageUpWidget = window.createChild(-1, WidgetType.GRAPHIC);
        UIButton pageUpButton = new UIButton(pageUpWidget);
        pageUpButton.setSprites(PAGE_UP_ARROW_SPRITE_ID);
        pageUpButton.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        pageUpButton.setPosition(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH+5), ARROW_SPRITE_HEIGHT + ARROW_Y_OFFSET);
        pageUpButton.addAction("Page up", () -> refreshTasks(-TASKS_PER_PAGE));

        Widget upWidget = window.createChild(-1, WidgetType.GRAPHIC);
        UIButton upArrow = new UIButton(upWidget);
        upArrow.setSprites(UP_ARROW_SPRITE_ID);
        upArrow.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        upArrow.setPosition(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH+5), ARROW_SPRITE_HEIGHT*2 + ARROW_Y_OFFSET);
        upArrow.addAction("Scroll up", () -> refreshTasks(-1));

        createScrollbarComponents();
        this.add(upArrow);
        this.add(pageUpButton);
        this.add(downArrow);
        this.add(pageDownButton);
        updateScrollbar();
    }

    private void createScrollbarComponents() {
        scrollbarTrackWidget = window.createChild(-1, WidgetType.RECTANGLE);
        scrollbarTrackWidget.setFilled(true);
        scrollbarTrackWidget.setTextColor(0x665948);
        scrollbarTrackWidget.setSize(SCROLLBAR_WIDTH - 2, 200);
        scrollbarTrackWidget.setPos(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH + 5) + 1, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET);
        
        scrollbarThumbWidget = window.createChild(-1, WidgetType.RECTANGLE);
        scrollbarThumbWidget.setFilled(true);
        scrollbarThumbWidget.setTextColor(0x473e33);
        scrollbarThumbWidget.setSize(SCROLLBAR_WIDTH - 6, SCROLLBAR_THUMB_MIN_HEIGHT);
        scrollbarThumbWidget.setPos(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH + 5) + 3, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET);

        Widget downWidget = window.createChild(-1, WidgetType.GRAPHIC);
        downArrow = new UIButton(downWidget);
        downArrow.setSprites(DOWN_ARROW_SPRITE_ID);
        downArrow.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        downArrow.addAction("Scroll down", () -> refreshTasks(1));

        Widget pageDownWidget = window.createChild(-1, WidgetType.GRAPHIC);
        pageDownButton = new UIButton(pageDownWidget);
        pageDownButton.setSprites(PAGE_DOWN_ARROW_SPRITE_ID);
        pageDownButton.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        pageDownButton.addAction("Page down", () -> refreshTasks(TASKS_PER_PAGE));
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
            topTaskIndex = Math.clamp(newIndex, 0, taskService.getTaskList().getForTier(relevantTier).size() - TASKS_PER_PAGE - 1);
        }

        final int POS_X = getCenterX(window, TASK_WIDTH);

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
                taskBg.getWidget().setHidden(false); // Ensure it's visible
            }

            taskBg.clearActions();
            taskBg.setSize(TASK_WIDTH, TASK_HEIGHT);
            taskBg.setPosition(POS_X, POS_Y);
            taskBg.getWidget().setPos(POS_X, POS_Y);
            TaskTier finalRelevantTier = relevantTier;
            taskBg.addAction("Mark", () -> plugin.completeTask(task.getId(), finalRelevantTier));

            if (saveDataManager.getSaveData().getProgress().get(relevantTier).contains(task.getId())) {
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
                taskLabel.getWidget().setHidden(false); // Ensure it's visible
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
                taskImage.getWidget().setHidden(false); // Ensure it's visible
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
        // Hide unused task backgrounds
        for (int i = visibleCount; i < taskBackgrounds.size(); i++) {
            taskBackgrounds.get(i).getWidget().setHidden(true);
        }
        
        // Hide unused task labels
        for (int i = visibleCount; i < taskLabels.size(); i++) {
            taskLabels.get(i).getWidget().setHidden(true);
        }
        
        // Hide unused task images
        for (int i = visibleCount; i < taskImages.size(); i++) {
            taskImages.get(i).getWidget().setHidden(true);
        }
    }

    public void goToTop() {
        topTaskIndex = 0;
        updateScrollbar(); // Update scrollbar when going to top
    }

    // Add a method to refresh the scrollbar when external changes occur
    public void refreshScrollbar() {
        updateScrollbar();
    }

    private List<Task> getTasksToShow(TaskTier relevantTier, int topTaskIndex) {
        List<Task> tasksToShow = new ArrayList<>();
        List<Task> taskList = taskService.getTaskList().getForTier(relevantTier);
        for (int i = 0; i < TASKS_PER_PAGE; i++) {
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
            TASKS_PER_PAGE = 20; // Default value, will be updated based on window size
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
        if (newTasksPerPage != TASKS_PER_PAGE) {
            TASKS_PER_PAGE = newTasksPerPage;
            // Ensure topTaskIndex is valid for the new page size
            TaskTier relevantTier = plugin.getSelectedTier();
            if (relevantTier == null) {
                relevantTier = TaskTier.MASTER;
            }
            int maxTopIndex = Math.max(0, taskService.getTaskList().getForTier(relevantTier).size() - TASKS_PER_PAGE);
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
        int scrollbarTrackHeight = (TASKS_PER_PAGE * TASK_HEIGHT) - (ARROW_SPRITE_HEIGHT * 4);
        int scrollbarEndY = ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET + scrollbarTrackHeight;
        
        forceWidgetPositionUpdate(downArrow, CANVAS_WIDTH - (ARROW_SPRITE_WIDTH+5), scrollbarEndY - 2);
        forceWidgetPositionUpdate(pageDownButton, CANVAS_WIDTH - (ARROW_SPRITE_WIDTH+5), scrollbarEndY + ARROW_SPRITE_HEIGHT - 2);
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
        int scrollbarTrackHeight = (TASKS_PER_PAGE * TASK_HEIGHT) - (ARROW_SPRITE_HEIGHT * 4);
        
        forceWidgetUpdate(scrollbarTrackWidget, SCROLLBAR_WIDTH - 2, scrollbarTrackHeight);
        updateArrowPositions();
        
        if (totalTasks <= TASKS_PER_PAGE) {
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
        topTaskIndex = Math.min(topTaskIndex, Math.max(0, totalTasks - TASKS_PER_PAGE));
        
        int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, (int)(scrollbarTrackHeight * ((double)TASKS_PER_PAGE / totalTasks)));
        int maxScrollPosition = Math.max(1, totalTasks - TASKS_PER_PAGE);
        int thumbY = maxScrollPosition > 0 ? (int)((scrollbarTrackHeight - thumbHeight) * ((double)topTaskIndex / maxScrollPosition)) : 0;
        
        scrollbarThumbWidget.setSize(SCROLLBAR_WIDTH - 6, thumbHeight);
        scrollbarThumbWidget.setPos(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH + 5) + 3, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET + thumbY);
        
        forceWidgetUpdate(scrollbarThumbWidget, SCROLLBAR_WIDTH - 6, thumbHeight);
    }

    private void setScrollbarVisibility(boolean visible) {
        if (scrollbarTrackWidget != null) scrollbarTrackWidget.setHidden(!visible);
        if (scrollbarThumbWidget != null) scrollbarThumbWidget.setHidden(!visible);
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
            if (totalTasks <= TASKS_PER_PAGE) return;
            
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
        
        int thumbX = baseX + scrollbarThumbWidget.getRelativeX();
        int thumbY = baseY + scrollbarThumbWidget.getRelativeY();
        
        return mouseX >= thumbX && mouseX <= thumbX + scrollbarThumbWidget.getWidth() && 
               mouseY >= thumbY && mouseY <= thumbY + scrollbarThumbWidget.getHeight();
    }

    private int calculateNewScrollPosition(int mouseY, int totalTasks) {
        int scrollbarTrackHeight = (TASKS_PER_PAGE * TASK_HEIGHT) - (ARROW_SPRITE_HEIGHT * 4);
        int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, (int)(scrollbarTrackHeight * ((double)TASKS_PER_PAGE / totalTasks)));
        int deltaY = mouseY - dragStartY;
        
        double scrollRatio = (scrollbarTrackHeight - thumbHeight) > 0 ? (double)deltaY / (scrollbarTrackHeight - thumbHeight) : 0;
        int newTopIndex = dragStartTopIndex + (int)(scrollRatio * (totalTasks - TASKS_PER_PAGE));
        
        return Math.clamp(newTopIndex, 0, totalTasks - TASKS_PER_PAGE);
    }

    public void handleMouseRelease() {
        isDraggingThumb = false;
    }
}
