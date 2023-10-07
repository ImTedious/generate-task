package com.logmaster.chat;

import com.logmaster.LogMasterPlugin;
import com.logmaster.domain.Task;
import com.logmaster.domain.TaskTier;
import com.logmaster.persistence.SaveDataManager;
import com.logmaster.task.TaskService;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.ItemComposition;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class ChatProcessor {

    @Inject
    private TaskService taskService;

    @Inject
    private ItemManager itemManager;

    @Inject
    private Client client;

    @Inject
    private SaveDataManager saveDataManager;

    @Inject
    private LogMasterPlugin plugin;

    private Map<Integer, Integer> chatSpriteMap = new HashMap<>();

    private void populateChatSpriteMap() {
        Set<Integer> itemIdsToLoad = new HashSet<>();
        for (TaskTier tier : TaskTier.values()) {
            itemIdsToLoad.addAll(taskService.getForTier(tier).stream().map(Task::getItemID).collect(Collectors.toList()));
        }
        List<Integer> itemIdsToLoadOrdered = new ArrayList<>(itemIdsToLoad);
        final IndexedSprite[] modIcons = client.getModIcons();

        final IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + itemIdsToLoadOrdered.size());
        int modIconIdx = modIcons.length;

        for (int i = 0; i < itemIdsToLoadOrdered.size(); i++)
        {
            final Integer itemId = itemIdsToLoadOrdered.get(i);
            final ItemComposition itemComposition = itemManager.getItemComposition(itemId);
            final BufferedImage image = ImageUtil.resizeImage(itemManager.getImage(itemComposition.getId()), 18, 16);
            final IndexedSprite sprite = ImageUtil.getImageIndexedSprite(image, client);
            final int spriteIndex = modIconIdx + i;

            newModIcons[spriteIndex] = sprite;
            chatSpriteMap.put(itemId, spriteIndex);
        }

        client.setModIcons(newModIcons);
    }

    public void getTaskCommandData(ChatMessage chatMessage, String message) {
//		if (!config.taskChatCommand()) {
//			return;
//		}

        Integer percentage = taskService.completionPercentages(saveDataManager.getSaveData()).get(plugin.getCurrentTier());

        ChatMessageBuilder chatMessageBuilder =
                new ChatMessageBuilder()
                        .append(ChatColorType.NORMAL)
                        .append("Progress: ")
                        .append(ChatColorType.HIGHLIGHT)
                        .append(percentage + "% " + plugin.getCurrentTier().displayName);

        if (saveDataManager.getSaveData().getActiveTaskPointer() != null) {
            chatMessageBuilder
                    .append(ChatColorType.NORMAL)
                    .append(" Current task: ")
                    .img(chatSpriteMap.getOrDefault(saveDataManager.getSaveData().getActiveTaskPointer().getTask().getItemID(), Integer.MIN_VALUE))
                    .append(ChatColorType.HIGHLIGHT)
                    .append(saveDataManager.getSaveData().getActiveTaskPointer().getTask().getDescription());
        } else {
            chatMessageBuilder
                    .append(ChatColorType.NORMAL)
                    .append(" No current task.");
        }

        final String response = chatMessageBuilder.build();

        final MessageNode messageNode = chatMessage.getMessageNode();
        messageNode.setRuneLiteFormatMessage(response);
        client.refreshChat();
    }
}
