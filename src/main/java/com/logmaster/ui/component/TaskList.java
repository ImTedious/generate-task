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
    private final int TASKS_PER_PAGE = 5;
    private final int UP_ARROW_SPRITE_ID = -20014;
    private final int DOWN_ARROW_SPRITE_ID = -20015;
    private final int ARROW_SPRITE_WIDTH = 39;
    private final int ARROW_SPRITE_HEIGHT = 20;
    private final int ARROW_Y_OFFSET = 4;

    private final Widget window;
    private final TaskService taskService;
    private final LogMasterPlugin plugin;
    private final ClientThread clientThread;

    private final SaveDataManager saveDataManager;

    private Rectangle bounds = new Rectangle();

    private List<UIGraphic> taskBackgrounds = new ArrayList<>();
    private List<UILabel> taskLabels = new ArrayList<>();
    private List<UIGraphic> taskImages = new ArrayList<>();

    private int topTaskIndex = 0;

    public TaskList(Widget window, TaskService taskService, LogMasterPlugin plugin, ClientThread clientThread, SaveDataManager saveDataManager) {
        this.window = window;
        this.taskService = taskService;
        this.plugin = plugin;
        this.clientThread = clientThread;
        this.saveDataManager = saveDataManager;

        refreshTasks(0);

        updateBounds();

        Widget upWidget = window.createChild(-1, WidgetType.GRAPHIC);
        UIButton upArrow = new UIButton(upWidget);
        upArrow.setSprites(UP_ARROW_SPRITE_ID);
        upArrow.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        upArrow.setPosition(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH+5), ARROW_SPRITE_HEIGHT + ARROW_Y_OFFSET);
        upArrow.addAction("Scroll up", () -> refreshTasks(-1));

        Widget downWidget = window.createChild(-1, WidgetType.GRAPHIC);
        UIButton downArrow = new UIButton(downWidget);
        downArrow.setSprites(DOWN_ARROW_SPRITE_ID);
        downArrow.setSize(ARROW_SPRITE_WIDTH, ARROW_SPRITE_HEIGHT);
        downArrow.setPosition(CANVAS_WIDTH - (ARROW_SPRITE_WIDTH+5), ARROW_SPRITE_HEIGHT*2 + ARROW_Y_OFFSET);
        downArrow.addAction("Scroll down", () -> refreshTasks(1));

        this.add(upArrow);
        this.add(downArrow);
    }

    public void refreshTasks(int dir) {
        TaskTier relevantTier = plugin.getSelectedTier();
        if (relevantTier == null) {
            relevantTier = TaskTier.MASTER;
        }
        if (topTaskIndex+dir < 0 || topTaskIndex + dir + TASKS_PER_PAGE > taskService.getTaskList().getForTier(relevantTier).size()) {
            return;
        }

        topTaskIndex += dir;

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
            }

            taskImage.setPosition(POS_X+12, POS_Y+6);
            taskImage.getWidget().setBorderType(1);
            taskImage.getWidget().setItemQuantityMode(ItemQuantityMode.NEVER);
            taskImage.setSize(TASK_ITEM_WIDTH, TASK_ITEM_HEIGHT);
            taskImage.setItem(task.getItemID());

            i++;
        }
    }

    public void goToTop() {
        topTaskIndex = 0;
    }

    private List<Task> getTasksToShow(TaskTier relevantTier, int topTaskIndex) {
        List<Task> tasksToShow = new ArrayList<>();
        List<Task> taskList = taskService.getTaskList().getForTier(relevantTier);
        for(int i=0;i<TASKS_PER_PAGE;i++) {
            if(topTaskIndex + i > taskList.size()) break;
            tasksToShow.add(taskList.get(topTaskIndex+i));
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
        if (!this.isVisible())
            return;

        Widget collectionLogWrapper = window.getParent();
        int relativeX = collectionLogWrapper.getRelativeX();
        int relativeY = collectionLogWrapper.getRelativeY();

        bounds.setSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        bounds.setLocation(OFFSET_X + relativeX, OFFSET_Y + relativeY);
    }
}
