package com.logmaster.ui.component;

import com.logmaster.LogMasterConfig;
import com.logmaster.LogMasterPlugin;
import com.logmaster.clog.ClogItemsManager;
import com.logmaster.domain.Task;
import com.logmaster.persistence.SaveDataManager;
import com.logmaster.task.TaskService;
import com.logmaster.ui.generic.UIButton;
import com.logmaster.ui.generic.UIGraphic;
import com.logmaster.ui.generic.UILabel;
import com.logmaster.ui.generic.UIPage;
import lombok.Getter;
import net.runelite.api.FontID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

import javax.swing.Timer;
import java.awt.Color;

import static com.logmaster.LogMasterPlugin.*;
import static com.logmaster.ui.InterfaceConstants.COLLECTION_LOG_WINDOW_HEIGHT;
import static com.logmaster.ui.InterfaceConstants.COLLECTION_LOG_WINDOW_WIDTH;

public class TaskDashboard extends UIPage {
    private final int DEFAULT_BUTTON_WIDTH = 140;
    private final int DEFAULT_BUTTON_HEIGHT = 30;
    private final int SMALL_BUTTON_WIDTH = 68;
    private final int DEFAULT_TASK_DETAILS_WIDTH = 300;
    private final int DEFAULT_TASK_DETAILS_HEIGHT = 75;
    private final int GENERATE_TASK_SPRITE_ID = -20001;
    private final int COMPLETE_TASK_SPRITE_ID = -20000;
    private final int GENERATE_TASK_HOVER_SPRITE_ID = -20003;
    private final int COMPLETE_TASK_HOVER_SPRITE_ID = -20002;
    private final int GENERATE_TASK_DISABLED_SPRITE_ID = -20005;
    private final int COMPLETE_TASK_DISABLED_SPRITE_ID = -20004;
    private final int TASK_BACKGROUND_SPRITE_ID = -20006;
    private final int FAQ_BUTTON_SPRITE_ID = -20027;
    private final int FAQ_BUTTON_HOVER_SPRITE_ID = -20028;
    private final int SYNC_BUTTON_SPRITE_ID = -20034;

    @Getter
    private Widget window;
    private LogMasterPlugin plugin;

    private LogMasterConfig config;

    private final TaskService taskService;
    private final SaveDataManager saveDataManager;
    private final ClogItemsManager clogItemsManager;

    private UILabel title;
    private UILabel taskLabel;
    private UILabel percentCompletion;

    private UIGraphic taskImage;
    private UIGraphic taskBg;

    private UIButton completeTaskBtn;
    private UIButton generateTaskBtn;
    private UIButton faqBtn;
    private UIButton syncBtn;

    public TaskDashboard(LogMasterPlugin plugin, LogMasterConfig config, Widget window, TaskService taskService, SaveDataManager saveDataManager, ClogItemsManager clogItemsManager) {
        this.window = window;
        this.plugin = plugin;
        this.config = config;
        this.taskService = taskService;
        this.saveDataManager = saveDataManager;
        this.clogItemsManager = clogItemsManager;

        createTaskDetails();

        Widget titleWidget = window.createChild(-1, WidgetType.TEXT);
        this.title = new UILabel(titleWidget);
        this.title.setFont(FontID.QUILL_CAPS_LARGE);
        this.title.setSize(COLLECTION_LOG_WINDOW_WIDTH, DEFAULT_TASK_DETAILS_HEIGHT);
        this.title.setPosition(getCenterX(window, COLLECTION_LOG_WINDOW_WIDTH), 24);
        this.title.setText("Current Task");

        Widget percentWidget = window.createChild(-1, WidgetType.TEXT);
        this.percentCompletion = new UILabel(percentWidget);
        this.percentCompletion.setFont(FontID.BOLD_12);
        this.percentCompletion.setSize(COLLECTION_LOG_WINDOW_WIDTH, 25);
        this.percentCompletion.setPosition(getCenterX(window, COLLECTION_LOG_WINDOW_WIDTH), COLLECTION_LOG_WINDOW_HEIGHT - 91);
        updatePercentages();

        Widget completeTaskWidget = window.createChild(-1, WidgetType.GRAPHIC);
        this.completeTaskBtn = new UIButton(completeTaskWidget);
        this.completeTaskBtn.setSize(DEFAULT_BUTTON_WIDTH, DEFAULT_BUTTON_HEIGHT);
        this.completeTaskBtn.setPosition(getCenterX(window, DEFAULT_BUTTON_WIDTH) + (DEFAULT_BUTTON_WIDTH / 2 + 15), getCenterY(window, DEFAULT_BUTTON_HEIGHT) + 62);
        this.completeTaskBtn.setSprites(COMPLETE_TASK_SPRITE_ID, COMPLETE_TASK_HOVER_SPRITE_ID);

        Widget generateTaskWidget = window.createChild(-1, WidgetType.GRAPHIC);
        this.generateTaskBtn = new UIButton(generateTaskWidget);
        this.generateTaskBtn.setSize(DEFAULT_BUTTON_WIDTH, DEFAULT_BUTTON_HEIGHT);
        this.generateTaskBtn.setPosition(getCenterX(window, DEFAULT_BUTTON_WIDTH) - (DEFAULT_BUTTON_WIDTH / 2 + 15), getCenterY(window, DEFAULT_BUTTON_HEIGHT) + 62);
        this.generateTaskBtn.setSprites(GENERATE_TASK_SPRITE_ID, GENERATE_TASK_HOVER_SPRITE_ID);

        Widget faqWidget = window.createChild(-1, WidgetType.GRAPHIC);
        this.faqBtn = new UIButton(faqWidget);
        this.faqBtn.setSize(SMALL_BUTTON_WIDTH, DEFAULT_BUTTON_HEIGHT);
        this.faqBtn.setPosition(getCenterX(window, SMALL_BUTTON_WIDTH) + 190, getCenterY(window, DEFAULT_BUTTON_HEIGHT) + 112);
        this.faqBtn.setSprites(FAQ_BUTTON_SPRITE_ID, FAQ_BUTTON_HOVER_SPRITE_ID);

        
        Widget syncWidget = window.createChild(-1, WidgetType.GRAPHIC);
        this.syncBtn = new UIButton(syncWidget);
        this.syncBtn.setSize(SMALL_BUTTON_WIDTH, DEFAULT_BUTTON_HEIGHT);
        this.syncBtn.setPosition(getCenterX(window, SMALL_BUTTON_WIDTH) - 190, getCenterY(window, DEFAULT_BUTTON_HEIGHT) + 112);
        this.syncBtn.setSprites(SYNC_BUTTON_SPRITE_ID, SYNC_BUTTON_SPRITE_ID);

        this.add(this.title);
        this.add(this.taskBg);
        this.add(this.taskLabel);
        this.add(this.taskImage);
        this.add(this.completeTaskBtn);
        this.add(this.generateTaskBtn);
        this.add(this.percentCompletion);
        this.add(faqBtn);
        this.add(syncBtn);
    }

    private void createTaskDetails() {
        final int POS_X = getCenterX(window, DEFAULT_TASK_DETAILS_WIDTH);
        final int POS_Y = getCenterY(window, DEFAULT_TASK_DETAILS_HEIGHT)-3;

        Widget taskBgWidget = window.createChild(-1, WidgetType.GRAPHIC);
        this.taskBg = new UIGraphic(taskBgWidget);
        this.taskBg.setSize(DEFAULT_TASK_DETAILS_WIDTH, DEFAULT_TASK_DETAILS_HEIGHT);
        this.taskBg.setPosition(POS_X, POS_Y);
        this.taskBg.setSprite(TASK_BACKGROUND_SPRITE_ID);

        Widget label = window.createChild(-1, WidgetType.TEXT);
        label.setTextColor(Color.WHITE.getRGB());
        label.setTextShadowed(true);
        label.setName("Task Label");
        this.taskLabel = new UILabel(label);
        this.taskLabel.setFont(496);
        this.taskLabel.setPosition(POS_X+60, POS_Y);
        this.taskLabel.setSize(DEFAULT_TASK_DETAILS_WIDTH-60, DEFAULT_TASK_DETAILS_HEIGHT);

        Widget taskImageWidget = window.createChild(-1, WidgetType.GRAPHIC);
        this.taskImage = new UIGraphic(taskImageWidget);
        this.taskImage.setPosition(POS_X+12, POS_Y+21);
        this.taskImage.getWidget().setItemQuantityMode(ItemQuantityMode.NEVER);
        this.taskImage.setSize(42, 36);
        this.taskImage.getWidget().setBorderType(1);
    }

    public void setTask(String desc, int taskItemID, java.util.List<Task> cyclingTasks) {
        if (cyclingTasks != null) {
            for (int i = 0; i < 250; i++) {
                Task displayTask = cyclingTasks.get((int) Math.floor(Math.random() * cyclingTasks.size()));
                // Seems the most natural timing
                double decay = 450.0 / ((double) config.rollTime());
                int delay = (int) ((config.rollTime() * 0.925) * Math.exp(-decay * i));
                Timer fakeTaskTimer = new Timer(delay, ae -> {
                    this.taskLabel.setText(displayTask.getDescription());
                    this.taskImage.setItem(displayTask.getItemID());
                });
                fakeTaskTimer.setRepeats(false);
                fakeTaskTimer.setCoalesce(true);
                fakeTaskTimer.start();
            }
            Timer realTaskTimer = new Timer(config.rollTime(), ae -> {
                this.taskLabel.setText(desc);
                this.taskImage.setItem(taskItemID);
                this.enableCompleteTask();
                this.enableFaqButton();
            });
            realTaskTimer.setRepeats(false);
            realTaskTimer.setCoalesce(true);
            realTaskTimer.start();
        } else {
            this.taskLabel.setText(desc);
            this.taskImage.setItem(taskItemID);
            this.enableCompleteTask();
            this.enableFaqButton();
        }
    }

    public void updatePercentages() {
        if (this.plugin != null && taskService.completionPercentages(saveDataManager.getSaveData()) != null && this.plugin.getCurrentTier() != null) {
            Integer percentage = taskService.completionPercentages(saveDataManager.getSaveData()).get(this.plugin.getCurrentTier());
            if (percentage != null) {
                this.percentCompletion.setText("<col=" + getCompletionColor(percentage) + ">" + percentage + "%</col> " + this.plugin.getCurrentTier().displayName + " Completed");
            }
        }
    }

    private String getCompletionColor(double percent) {
        int max = 255;
        int amount = (int) Math.round(((percent % 50) / 50) * max);

        if(percent == 100) {
            return "00ff00";
        }
        else if(percent > 50) {
            int redValue = max - amount;
            return String.format("%02x", redValue)+"ff00";

        }
        else if(percent == 50) {
            return "ffff00";
        }
        else {
            return "ff"+String.format("%02x", amount)+"00";
        }
    }

    public void disableGenerateTask() {
        disableGenerateTask(true);
    }

    public void disableGenerateTask(boolean enableComplete) {
        this.generateTaskBtn.setSprites(GENERATE_TASK_DISABLED_SPRITE_ID);
        this.generateTaskBtn.clearActions();

        this.generateTaskBtn.addAction("Disabled", plugin::playFailSound);

        if (enableComplete) {
            this.enableCompleteTask();
            this.enableFaqButton();
        }
    }

    public void enableGenerateTask() {
        this.generateTaskBtn.clearActions();
        this.generateTaskBtn.setSprites(GENERATE_TASK_SPRITE_ID, GENERATE_TASK_HOVER_SPRITE_ID);
        this.generateTaskBtn.addAction("Generate task", plugin::generateTask);

        this.disableCompleteTask();
    }

    public void disableCompleteTask() {
        this.completeTaskBtn.setSprites(COMPLETE_TASK_DISABLED_SPRITE_ID);
        this.completeTaskBtn.clearActions();
        this.completeTaskBtn.addAction("Disabled", plugin::playFailSound);
    }

    public void enableCompleteTask() {
        this.completeTaskBtn.clearActions();
        this.completeTaskBtn.setSprites(COMPLETE_TASK_SPRITE_ID, COMPLETE_TASK_HOVER_SPRITE_ID);
        this.completeTaskBtn.addAction("Complete", plugin::completeTask);
    }

    public void enableFaqButton() {
        this.faqBtn.clearActions();
        this.faqBtn.setSprites(FAQ_BUTTON_SPRITE_ID, FAQ_BUTTON_HOVER_SPRITE_ID);
        this.faqBtn.addAction("FAQ", plugin::visitFaq);
        this.enableSyncButton();
    }

    public void enableSyncButton() {
        this.syncBtn.clearActions();
        this.syncBtn.setSprites(SYNC_BUTTON_SPRITE_ID, SYNC_BUTTON_SPRITE_ID);
        this.syncBtn.addAction("Auto sync completed collection log slots", clogItemsManager::sync);
    }
}
