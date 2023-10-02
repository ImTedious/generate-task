package com.logmaster.ui.component;

import com.logmaster.LogMasterConfig;
import com.logmaster.LogMasterPlugin;
import com.logmaster.domain.Task;
import com.logmaster.persistence.SaveDataManager;
import com.logmaster.ui.InterfaceManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;

@Singleton
public class TaskOverlay extends Overlay {

    private static final Dimension EMPTY = new Dimension(0, 0);

    private static final int WIDTH_ADDITION = 51;
    private static final int MIN_HEIGHT = 46;

    private static final float OUTER_COLOR_OFFSET = 0.8f;
    private static final float INNER_COLOR_OFFSET = 1.2f;
    private static final float ALPHA_OFFSET = 1.4f;

    private static final int MAX_BYTE = 255;

    @Inject
    private LogMasterPlugin plugin;

    @Inject
    private LogMasterConfig config;

    @Inject
    private RuneLiteConfig runeLiteConfig;

    @Inject
    private ItemManager itemManager;

    @Inject
    private InterfaceManager interfaceManager;

    @Inject
    private SaveDataManager saveDataManager;

    @Override
    public Dimension render(Graphics2D g) {
        try {
            Task currentTask = saveDataManager.currentTask();
            if (!config.displayCurrentTaskOverlay() || currentTask == null || interfaceManager.isDashboardOpen()) {
                return EMPTY;
            }

            Image icon = itemManager.getImage(currentTask.getItemID());
            String task = currentTask.getDescription();


            FontMetrics fm = g.getFontMetrics();

            int width = fm.stringWidth(task) + WIDTH_ADDITION;
            int height = MIN_HEIGHT;

            Color border = outsideColor(this.runeLiteConfig.overlayBackgroundColor());

            g.setColor(this.runeLiteConfig.overlayBackgroundColor());
            g.fillRect(0, 0, width, height);
            g.setColor(border);
            g.drawRect(0, 0, width, height);

//            int textX = 46;
            int textX = width - fm.stringWidth(task) - 5;
            int textY = 30;

            g.setFont(g.getFont().deriveFont(16f));
            g.setColor(Color.BLACK);
            g.drawString(task, textX + 1, textY + 1);
            g.setColor(Color.WHITE);
            g.drawString(task, textX, textY);

            int iconWidth = icon.getWidth(null);
            int iconHeight = icon.getHeight(null);

            g.drawImage(icon, 5 + 18 - (iconWidth / 2), 5 + 18 - (iconHeight / 2), iconWidth, iconHeight, null);
            return new Dimension(width, height);
        } catch (Throwable t) {
            t.printStackTrace();
            return EMPTY;
        }
    }

    private Color outsideColor(Color base) {
        return new Color(
                Math.round(base.getRed() * OUTER_COLOR_OFFSET),
                Math.round(base.getGreen() * OUTER_COLOR_OFFSET),
                Math.round(base.getBlue() * OUTER_COLOR_OFFSET),
                Math.min(MAX_BYTE, Math.round(base.getAlpha() * ALPHA_OFFSET))
        );
    }

    private Color innerColor(Color base) {
        return new Color(
                Math.min(MAX_BYTE, Math.round(base.getRed() * INNER_COLOR_OFFSET)),
                Math.min(MAX_BYTE, Math.round(base.getGreen() * INNER_COLOR_OFFSET)),
                Math.min(MAX_BYTE, Math.round(base.getBlue() * INNER_COLOR_OFFSET)),
                Math.min(MAX_BYTE, Math.round(base.getAlpha() * ALPHA_OFFSET))
        );
    }
}
