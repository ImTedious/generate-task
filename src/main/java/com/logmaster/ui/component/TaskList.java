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
        // Initial position will be set in updateArrowPositions()
        pageUpButton.setPosition(-ARROW_SPRITE_WIDTH, 0);
        pageUpButton.addAction("Page up", () -> refreshTasks(-tasksPerPage));

        Widget upWidget = window.createChild(-1, WidgetType.GRAPHIC);
        upArrowButton = new UIButton(upWidget);
        upArrowButton.setSprites(UP_ARROW_SPRITE_ID);
        upArrowButton.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        upArrowButton.setPosition(-ARROW_SPRITE_WIDTH, 0);
        upArrowButton.addAction("Scroll up", () -> refreshTasks(-1));

        scrollbarTrackWidget = window.createChild(-1, WidgetType.RECTANGLE);
        scrollbarTrackWidget.setFilled(true);
        scrollbarTrackWidget.setTextColor(0x665948);
        scrollbarTrackWidget.setSize(SCROLLBAR_WIDTH, 200);
        scrollbarTrackWidget.setPos(-ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET);
        
        scrollbarThumbTopWidget = window.createChild(-1, WidgetType.GRAPHIC);
        scrollbarThumbTopWidget.setSpriteId(THUMB_TOP_SPRITE_ID);
        scrollbarThumbTopWidget.setSize(SCROLLBAR_WIDTH, 2);
        scrollbarThumbTopWidget.setPos(-ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET);

        scrollbarThumbMiddleWidget = window.createChild(-1, WidgetType.GRAPHIC);
        scrollbarThumbMiddleWidget.setSpriteId(THUMB_MIDDLE_SPRITE_ID);
        scrollbarThumbMiddleWidget.setSize(SCROLLBAR_WIDTH, SCROLLBAR_THUMB_MIN_HEIGHT - 4);
        scrollbarThumbMiddleWidget.setPos(-ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET + 2);

        scrollbarThumbBottomWidget = window.createChild(-1, WidgetType.GRAPHIC);
        scrollbarThumbBottomWidget.setSpriteId(THUMB_BOTTOM_SPRITE_ID);
        scrollbarThumbBottomWidget.setSize(SCROLLBAR_WIDTH, 2);
        scrollbarThumbBottomWidget.setPos(-ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET + SCROLLBAR_THUMB_MIN_HEIGHT - 2);

        Widget downWidget = window.createChild(-1, WidgetType.GRAPHIC);
        downArrowButton = new UIButton(downWidget);
        downArrowButton.setSprites(DOWN_ARROW_SPRITE_ID);
        downArrowButton.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        downArrowButton.setPosition(-ARROW_SPRITE_WIDTH, 0);
        downArrowButton.addAction("Scroll down", () -> refreshTasks(1));

        Widget pageDownWidget = window.createChild(-1, WidgetType.GRAPHIC);
        pageDownButton = new UIButton(pageDownWidget);
        pageDownButton.setSprites(PAGE_DOWN_ARROW_SPRITE_ID);
        pageDownButton.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        pageDownButton.setPosition(-ARROW_SPRITE_WIDTH, 0);
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
        int windowWidth = window.getWidth();
        int windowHeight = window.getHeight();
        int columnSpacing = 24; // Space between columns
        int minColumns = 1;
        int maxColumns = Math.max(minColumns, (windowWidth - SCROLLBAR_WIDTH - 40) / (TASK_WIDTH + columnSpacing));
        int columns = Math.max(1, maxColumns);
        int tasksToShowCount = (columns >= 2) ? tasksPerPage * columns : tasksPerPage;
        int totalTasks = taskService.getTaskList().getForTier(relevantTier).size();
        if (!forceRefresh) {
            int newIndex = topTaskIndex + dir * (columns >= 2 ? columns : 1);
            topTaskIndex = Math.min(Math.max(0, totalTasks - tasksToShowCount), Math.max(0, newIndex));
        }
        int visibleTasks = Math.min(tasksToShowCount, totalTasks - topTaskIndex);
        int rows = (int)Math.ceil((double)visibleTasks / columns);
        int totalTasksHeight = rows * TASK_HEIGHT;
        int availableHeight = windowHeight - OFFSET_Y;
        int verticalMargin = 0;
        if (rows > 1) {
            verticalMargin = Math.max(0, (availableHeight - totalTasksHeight) / (rows - 1));
        }
        int totalHeightWithMargin = totalTasksHeight + (rows > 1 ? (rows - 1) * verticalMargin : 0);
        int startY = OFFSET_Y + Math.max(0, (windowHeight - OFFSET_Y - totalHeightWithMargin) / 2);
        int totalWidth = columns * TASK_WIDTH + (columns - 1) * columnSpacing;
        int startX = (windowWidth - totalWidth - SCROLLBAR_WIDTH - 10) / 2;
        int POS_Y = startY;
        int widgetsToShow = visibleTasks + 1;
        hideUnusedTaskElements(widgetsToShow);
        List<Task> tasksToShow = getTasksToShow(relevantTier, topTaskIndex, visibleTasks);
        int widgetIndex = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int i = row * columns + col;
                boolean isExtra = (i == visibleTasks);
                if (i > visibleTasks) break;
                int taskY = isExtra ? 0 : POS_Y + (row * (TASK_HEIGHT + verticalMargin));
                int taskX = isExtra ? -1000 : startX + col * (TASK_WIDTH + columnSpacing);
                UIGraphic taskBg;
                if (taskBackgrounds.size() <= widgetIndex) {
                    taskBg = new UIGraphic(window.createChild(-1, WidgetType.GRAPHIC));
                    taskBackgrounds.add(taskBg);
                    this.add(taskBg);
                } else {
                    taskBg = taskBackgrounds.get(widgetIndex);
                }
                taskBg.getWidget().setHidden(false);
                taskBg.clearActions();
                taskBg.setSize(TASK_WIDTH, TASK_HEIGHT);
                taskBg.setPosition(taskX, taskY);
                taskBg.getWidget().setPos(taskX, taskY);
                taskBg.getWidget().revalidate();
                if (!isExtra && i < tasksToShow.size()) {
                    Task task = tasksToShow.get(i);
                    TaskTier finalRelevantTier = relevantTier;
                    taskBg.addAction("Mark", () -> plugin.completeTask(task.getId(), finalRelevantTier));
                    if (saveDataManager.getSaveData().getProgress().get(relevantTier).contains(task.getId())) {
                        taskBg.setSprite(TASK_COMPLETE_BACKGROUND_SPRITE_ID);
                    } else if (saveDataManager.getSaveData().getActiveTaskPointer() != null && saveDataManager.getSaveData().getActiveTaskPointer().getTaskTier() == relevantTier && saveDataManager.getSaveData().getActiveTaskPointer().getTask().getId() == task.getId()) {
                        taskBg.setSprite(TASK_CURRENT_BACKGROUND_SPRITE_ID);
                    } else {
                        taskBg.setSprite(TASK_LIST_BACKGROUND_SPRITE_ID);
                    }
                } else {
                    taskBg.setSprite(TASK_LIST_BACKGROUND_SPRITE_ID);
                }
                UILabel taskLabel;
                if (taskLabels.size() <= widgetIndex) {
                    taskLabel = new UILabel(window.createChild(-1, WidgetType.TEXT));
                    this.add(taskLabel);
                    taskLabels.add(taskLabel);
                } else {
                    taskLabel = taskLabels.get(widgetIndex);
                }
                taskLabel.getWidget().setHidden(false);
                taskLabel.getWidget().setTextColor(Color.WHITE.getRGB());
                taskLabel.getWidget().setTextShadowed(true);
                if (!isExtra && i < tasksToShow.size()) {
                    Task task = tasksToShow.get(i);
                    taskLabel.getWidget().setName(task.getDescription());
                    taskLabel.setText(task.getDescription());
                } else {
                    taskLabel.getWidget().setName("");
                    taskLabel.setText("");
                }
                taskLabel.setFont(496);
                taskLabel.setPosition(isExtra ? -1000 : taskX+60, isExtra ? 0 : taskY);
                taskLabel.setSize(TASK_WIDTH-60, TASK_HEIGHT);
                taskLabel.getWidget().revalidate();
                UIGraphic taskImage;
                if(taskImages.size() <= widgetIndex) {
                    taskImage = new UIGraphic(window.createChild(-1, WidgetType.GRAPHIC));
                    this.add(taskImage);
                    taskImages.add(taskImage);
                } else {
                    taskImage = taskImages.get(widgetIndex);
                }
                taskImage.getWidget().setHidden(false);
                taskImage.setPosition(isExtra ? -1000 : taskX+12, isExtra ? 0 : taskY+6);
                taskImage.getWidget().setBorderType(1);
                taskImage.getWidget().setItemQuantityMode(ItemQuantityMode.NEVER);
                taskImage.setSize(TASK_ITEM_WIDTH, TASK_ITEM_HEIGHT);
                if (!isExtra && i < tasksToShow.size()) {
                    Task task = tasksToShow.get(i);
                    taskImage.setItem(task.getItemID());
                } else {
                    taskImage.setItem(-1);
                }
                taskImage.getWidget().revalidate();
                widgetIndex++;
            }
        }
        // Render the extra widget offscreen if needed
        if (widgetIndex == visibleTasks) {
            UIGraphic taskBg;
            if (taskBackgrounds.size() <= widgetIndex) {
                taskBg = new UIGraphic(window.createChild(-1, WidgetType.GRAPHIC));
                taskBackgrounds.add(taskBg);
                this.add(taskBg);
            } else {
                taskBg = taskBackgrounds.get(widgetIndex);
            }
            taskBg.getWidget().setHidden(false);
            taskBg.setPosition(-1000, 0);
            taskBg.getWidget().setPos(-1000, 0);
            taskBg.getWidget().revalidate();
            UILabel taskLabel;
            if (taskLabels.size() <= widgetIndex) {
                taskLabel = new UILabel(window.createChild(-1, WidgetType.TEXT));
                this.add(taskLabel);
                taskLabels.add(taskLabel);
            } else {
                taskLabel = taskLabels.get(widgetIndex);
            }
            taskLabel.getWidget().setHidden(false);
            taskLabel.setPosition(-1000, 0);
            taskLabel.getWidget().setPos(-1000, 0);
            taskLabel.getWidget().revalidate();
            UIGraphic taskImage;
            if(taskImages.size() <= widgetIndex) {
                taskImage = new UIGraphic(window.createChild(-1, WidgetType.GRAPHIC));
                this.add(taskImage);
                taskImages.add(taskImage);
            } else {
                taskImage = taskImages.get(widgetIndex);
            }
            taskImage.getWidget().setHidden(false);
            taskImage.setPosition(-1000, 0);
            taskImage.getWidget().setPos(-1000, 0);
            taskImage.getWidget().revalidate();
        }
        updateScrollbar();
    }

    // Overload getTasksToShow to accept a count
    private List<Task> getTasksToShow(TaskTier relevantTier, int topTaskIndex, int count) {
        List<Task> tasksToShow = new ArrayList<>();
        List<Task> taskList = taskService.getTaskList().getForTier(relevantTier);
        for (int i = 0; i < count; i++) {
            if (topTaskIndex + i >= taskList.size()) break;
            tasksToShow.add(taskList.get(topTaskIndex + i));
        }
        return tasksToShow;
    }

    private void hideUnusedTaskElements(int visibleCount) {
        // Only hide widgets beyond visibleCount (which is visibleTasks+1)
        for (int i = visibleCount; i < taskBackgrounds.size(); i++) {
            UIGraphic bg = taskBackgrounds.get(i);
            bg.getWidget().setHidden(true);
            bg.setPosition(-1000, 0);
            bg.getWidget().setPos(-1000, 0);
        }
        for (int i = visibleCount; i < taskLabels.size(); i++) {
            UILabel label = taskLabels.get(i);
            label.getWidget().setHidden(true);
            label.setPosition(-1000, 0);
            label.getWidget().setPos(-1000, 0);
        }
        for (int i = visibleCount; i < taskImages.size(); i++) {
            UIGraphic img = taskImages.get(i);
            img.getWidget().setHidden(true);
            img.setPosition(-1000, 0);
            img.getWidget().setPos(-1000, 0);
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
        } else {
            // Still update arrow positions and scrollbar if window size changes
            updateArrowPositions();
            updateScrollbar();
        }
        // Always force refresh the task display to keep it centered, but defer to after layout
        clientThread.invokeLater(() -> refreshTasks(0, true));

        bounds.setLocation(wrapperX + windowX + OFFSET_X, wrapperY + windowY + OFFSET_Y);
        bounds.setSize(windowWidth - OFFSET_X, windowHeight - OFFSET_Y);
    }

    private void updateArrowPositions() {
        int windowWidth = window.getWidth();
        int windowHeight = window.getHeight();
        int scrollbarX = windowWidth - ARROW_SPRITE_WIDTH - 5;
        // Position arrows vertically in order: page up, up, down, page down
        int pageUpY = ARROW_SPRITE_HEIGHT + ARROW_Y_OFFSET;
        int upArrowY = ARROW_SPRITE_HEIGHT * 2 + ARROW_Y_OFFSET;
        int downArrowY = windowHeight - ARROW_SPRITE_HEIGHT * 2;
        int pageDownY = windowHeight - ARROW_SPRITE_HEIGHT;
        forceWidgetPositionUpdate(pageUpButton, scrollbarX, pageUpY);
        forceWidgetPositionUpdate(upArrowButton, scrollbarX, upArrowY);
        forceWidgetPositionUpdate(downArrowButton, scrollbarX, downArrowY);
        forceWidgetPositionUpdate(pageDownButton, scrollbarX, pageDownY);
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
        int windowWidth = window.getWidth();
        int windowHeight = window.getHeight();
        int columnSpacing = 24;
        int columns = Math.max(1, (windowWidth - SCROLLBAR_WIDTH - 40) / (TASK_WIDTH + columnSpacing));
        int tasksPerPageActual = (columns > 1) ? tasksPerPage * columns : tasksPerPage;
        // The track should fill between the up and down arrows
        int trackY = ARROW_SPRITE_HEIGHT * 3 + ARROW_Y_OFFSET;
        int scrollbarTrackHeight = windowHeight - trackY - ARROW_SPRITE_HEIGHT * 2;
        int scrollbarX = windowWidth - SCROLLBAR_WIDTH - 9;
        forceWidgetUpdate(scrollbarTrackWidget, SCROLLBAR_WIDTH, scrollbarTrackHeight);
        scrollbarTrackWidget.setPos(scrollbarX + 2, trackY);
        updateArrowPositions();
        if (totalTasks <= tasksPerPageActual) {
            setScrollbarVisibility(false);
        } else {
            setScrollbarVisibility(true);
            updateScrollbarThumb(totalTasks, tasksPerPageActual, scrollbarTrackHeight, scrollbarX);
        }
    }

    private void forceWidgetUpdate(Widget widget, int width, int height) {
        widget.setHidden(true);
        widget.setSize(width, height);
        widget.setHidden(false);
        widget.revalidate();
    }

    private void updateScrollbarThumb(int totalTasks, int tasksPerPageActual, int scrollbarTrackHeight, int scrollbarX) {
        topTaskIndex = Math.min(topTaskIndex, Math.max(0, totalTasks - tasksPerPageActual));
        int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, (int)(scrollbarTrackHeight * ((double)tasksPerPageActual / totalTasks)));
        int maxScrollPosition = Math.max(1, totalTasks - tasksPerPageActual);
        int thumbY = maxScrollPosition > 0 ? (int)((scrollbarTrackHeight - thumbHeight) * ((double)topTaskIndex / maxScrollPosition)) : 0;
        int thumbStartY = ARROW_SPRITE_HEIGHT*3 + ARROW_Y_OFFSET + thumbY;
        int thumbX = scrollbarX + 2;
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
        int windowWidth = window.getWidth();
        int windowHeight = window.getHeight();
        int columnSpacing = 24;
        int columns = Math.max(1, (windowWidth - SCROLLBAR_WIDTH - 40) / (TASK_WIDTH + columnSpacing));
        int tasksPerPageActual = (columns > 1) ? tasksPerPage * columns : tasksPerPage;
        int scrollbarTrackHeight = (tasksPerPageActual / columns) * TASK_HEIGHT - (ARROW_SPRITE_HEIGHT * 4);
        int thumbHeight = Math.max(SCROLLBAR_THUMB_MIN_HEIGHT, (int)(scrollbarTrackHeight * ((double)tasksPerPageActual / totalTasks)));
        int deltaY = mouseY - dragStartY;
        int maxTopIndex = Math.max(0, totalTasks - tasksPerPageActual);
        double scrollRatio = (scrollbarTrackHeight - thumbHeight) > 0 ? (double)deltaY / (scrollbarTrackHeight - thumbHeight) : 0;
        int newTopIndex = dragStartTopIndex + (int)(scrollRatio * (totalTasks - tasksPerPageActual));
        return Math.max(0, Math.min(maxTopIndex, newTopIndex));
    }

    public void handleMouseRelease() {
        isDraggingThumb = false;
    }
}
