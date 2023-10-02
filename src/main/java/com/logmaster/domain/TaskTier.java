package com.logmaster.domain;

import static com.logmaster.ui.InterfaceConstants.*;

public enum TaskTier {
    EASY("Easy", TASKLIST_EASY_TAB_SPRITE_ID, TASKLIST_EASY_TAB_HOVER_SPRITE_ID),
    MEDIUM("Medium", TASKLIST_MEDIUM_TAB_SPRITE_ID, TASKLIST_MEDIUM_TAB_HOVER_SPRITE_ID),
    HARD("Hard", TASKLIST_HARD_TAB_SPRITE_ID, TASKLIST_HARD_TAB_HOVER_SPRITE_ID),
    ELITE("Elite", TASKLIST_ELITE_TAB_SPRITE_ID, TASKLIST_ELITE_TAB_HOVER_SPRITE_ID),
    MASTER("Master", TASKLIST_MASTER_TAB_SPRITE_ID, TASKLIST_MASTER_TAB_HOVER_SPRITE_ID);

    public final String displayName;
    public final int tabSpriteId;
    public final int tabSpriteHoverId;

    TaskTier(String displayName, int tabSpriteId, int tabSpriteHoverId) {
        this.displayName = displayName;
        this.tabSpriteId = tabSpriteId;
        this.tabSpriteHoverId = tabSpriteHoverId;
    }
}
