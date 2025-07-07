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

        // Create scrollbar track
        scrollbarTrackWidget = window.createChild(-1, WidgetType.RECTANGLE);
        scrollbarTrackWidget.setFilled(true);
        scrollbarTrackWidget.setTextColor(0x665948); // Dark gray background
        scrollbarTrackWidget.setSize(SCROLLBAR_WIDTH - 2, 200); // Initial size, will be updated in updateScrollbar()
        scrollbarTrackWidget.setPos(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH + 5) + 1, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET);
        
        // Create scrollbar thumb (created after track to ensure it appears on top)
        scrollbarThumbWidget = window.createChild(-1, WidgetType.RECTANGLE);
        scrollbarThumbWidget.setFilled(true);
        scrollbarThumbWidget.setTextColor(0x473e33); // Light gray thumb
        scrollbarThumbWidget.setSize(SCROLLBAR_WIDTH - 6, SCROLLBAR_THUMB_MIN_HEIGHT); // Slightly smaller than track
        scrollbarThumbWidget.setPos(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH + 5) + 3, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET);

        Widget downWidget = window.createChild(-1, WidgetType.GRAPHIC);
        downArrow = new UIButton(downWidget);
        downArrow.setSprites(DOWN_ARROW_SPRITE_ID);
        downArrow.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        // Position will be set in updateScrollbar()
        downArrow.addAction("Scroll down", () -> refreshTasks(1));

        Widget pageDownWidget = window.createChild(-1, WidgetType.GRAPHIC);
        pageDownButton = new UIButton(pageDownWidget);
        pageDownButton.setSprites(PAGE_DOWN_ARROW_SPRITE_ID);
        pageDownButton.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        // Position will be set in updateScrollbar()
        pageDownButton.addAction("Page down", () -> refreshTasks(TASKS_PER_PAGE));

        this.add(upArrow);
        this.add(pageUpButton);
        this.add(downArrow);
        this.add(pageDownButton);
        
        updateScrollbar();
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
        // Calculate scrollbar track height based on tasks per page
        int scrollbarTrackHeight = (TASKS_PER_PAGE * TASK_HEIGHT) - (ARROW_SPRITE_HEIGHT * 4);
        
        // Calculate and update arrow positions
        int scrollbarEndY = ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET + scrollbarTrackHeight;
        
        // Force arrow position updates by hiding and showing them
        downArrow.getWidget().setHidden(true);
        downArrow.setPosition(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH+5), scrollbarEndY - 2);
        downArrow.getWidget().setHidden(false);
        downArrow.getWidget().revalidate();
        
        pageDownButton.getWidget().setHidden(true);
        pageDownButton.setPosition(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH+5), scrollbarEndY + ARROW_SPRITE_HEIGHT - 2);
        pageDownButton.getWidget().setHidden(false);
        pageDownButton.getWidget().revalidate();
    }

    private void updateScrollbar() {
        if (!this.isVisible()) {
            // Hide scrollbar components when not visible
            if (scrollbarTrackWidget != null) {
                scrollbarTrackWidget.setHidden(true);
            }
            if (scrollbarThumbWidget != null) {
                scrollbarThumbWidget.setHidden(true);
            }
            return;
        }

        TaskTier relevantTier = plugin.getSelectedTier();
        if (relevantTier == null) {
            relevantTier = TaskTier.MASTER;
        }
        
        int totalTasks = taskService.getTaskList().getForTier(relevantTier).size();
        
        // Calculate scrollbar track height based on tasks per page
        int scrollbarTrackHeight = (TASKS_PER_PAGE * TASK_HEIGHT) - (ARROW_SPRITE_HEIGHT * 4);
        
        // Force track size update by hiding and showing it
        scrollbarTrackWidget.setHidden(true);
        scrollbarTrackWidget.setSize(SCROLLBAR_WIDTH - 2, scrollbarTrackHeight);
        scrollbarTrackWidget.setHidden(false);
        scrollbarTrackWidget.revalidate();
        
        // Update arrow positions as well
        updateArrowPositions();
        
        if (totalTasks <= TASKS_PER_PAGE) {
            // Hide scrollbar if all tasks fit on one page
            scrollbarTrackWidget.setHidden(true);
            scrollbarThumbWidget.setHidden(true);
        } else {
            // Show scrollbar only if task list is visible
            scrollbarTrackWidget.setHidden(false);
            scrollbarThumbWidget.setHidden(false);
            
            // Ensure topTaskIndex is valid for current task count
            int maxTopIndex = Math.max(0, totalTasks - TASKS_PER_PAGE);
            topTaskIndex = Math.min(topTaskIndex, maxTopIndex);
            
            // Calculate thumb size based on visible ratio
            int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, (int)(scrollbarTrackHeight * ((double)TASKS_PER_PAGE / totalTasks)));

            // Calculate thumb position based on scroll position
            int maxScrollPosition = Math.max(1, totalTasks - TASKS_PER_PAGE);
            int thumbMaxY = scrollbarTrackHeight - thumbHeight;
            int thumbY = maxScrollPosition > 0 ? (int)(thumbMaxY * ((double)topTaskIndex / maxScrollPosition)) : 0;
            
            // Update thumb size and position
            scrollbarThumbWidget.setSize(SCROLLBAR_WIDTH - 6, thumbHeight);
            int newX = CANVAS_WIDTH - (ARROW_SPRITE_WIDTH + 5) + 3;
            int newY = ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET + thumbY;
            scrollbarThumbWidget.setPos(newX, newY);
            
            // Force redraw by hiding and showing the widget
            scrollbarThumbWidget.setHidden(true);
            scrollbarThumbWidget.setHidden(false);
            scrollbarThumbWidget.revalidate();
        }
        
        log.info("Updating scrollbar: totalTasks={}, TASKS_PER_PAGE={}, topTaskIndex={}, scrollbarTrackHeight={}, scrollbarEndY={}",
                totalTasks, TASKS_PER_PAGE, topTaskIndex, scrollbarTrackHeight, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET + scrollbarTrackHeight);
    }

    public void setVisibility(boolean visible) {
        super.setVisibility(visible);
        
        // Also control scrollbar visibility
        if (scrollbarTrackWidget != null) {
            scrollbarTrackWidget.setHidden(!visible);
        }
        if (scrollbarThumbWidget != null) {
            scrollbarThumbWidget.setHidden(!visible);
        }
        
        // Update scrollbar when becoming visible
        if (visible) {
            updateScrollbar();
        }
    }

    public void handleMousePress(int mouseX, int mouseY) {
        if (!this.isVisible()) {
            return;
        }
        
        clientThread.invoke(() -> {
            // Get absolute positions for the scrollbar thumb
            Widget collectionLogWrapper = window.getParent();
            int wrapperX = collectionLogWrapper.getRelativeX();
            int wrapperY = collectionLogWrapper.getRelativeY();
            int windowX = window.getRelativeX();
            int windowY = window.getRelativeY();
            
            int thumbAbsX = wrapperX + windowX + scrollbarThumbWidget.getRelativeX();
            int thumbAbsY = wrapperY + windowY + scrollbarThumbWidget.getRelativeY();
            int thumbWidth = scrollbarThumbWidget.getWidth();
            int thumbHeight = scrollbarThumbWidget.getHeight();
            
            // Check if click is on the scroll thumb
            if (mouseX >= thumbAbsX && mouseX <= thumbAbsX + thumbWidth && 
                mouseY >= thumbAbsY && mouseY <= thumbAbsY + thumbHeight) {
                isDraggingThumb = true;
                dragStartY = mouseY;
                dragStartTopIndex = topTaskIndex;
            }
        });
    }

    public void handleMouseDrag(int mouseX, int mouseY) {
        if (!isDraggingThumb || !this.isVisible()) {
            return;
        }
        
        clientThread.invoke(() -> {
            TaskTier relevantTier = plugin.getSelectedTier();
            if (relevantTier == null) {
                relevantTier = TaskTier.MASTER;
            }
            
            int totalTasks = taskService.getTaskList().getForTier(relevantTier).size();
            if (totalTasks <= TASKS_PER_PAGE) {
                return;
            }
            
            // Calculate scrollbar track height
            int scrollbarTrackHeight = (TASKS_PER_PAGE * TASK_HEIGHT) - (ARROW_SPRITE_HEIGHT * 4);
            int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, (int)(scrollbarTrackHeight * ((double)TASKS_PER_PAGE / totalTasks)));
            int thumbMaxY = scrollbarTrackHeight - thumbHeight;
            
            // Calculate how much the mouse has moved
            int deltaY = mouseY - dragStartY;
            
            // Convert mouse movement to scroll position
            int maxScrollPosition = totalTasks - TASKS_PER_PAGE;
            double scrollRatio = thumbMaxY > 0 ? (double)deltaY / thumbMaxY : 0;
            int newTopIndex = dragStartTopIndex + (int)(scrollRatio * maxScrollPosition);
            
            // Clamp to valid range
            newTopIndex = Math.clamp(newTopIndex, 0, maxScrollPosition);
            
            if (newTopIndex != topTaskIndex) {
                topTaskIndex = newTopIndex;
                refreshTasks(0, true);
            }
        });
    }

    public void handleMouseRelease() {
        isDraggingThumb = false;
    }
}
