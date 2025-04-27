package com.logmaster.ui.generic.dropdown;

import com.logmaster.ui.generic.ComponentEventListener;
import com.logmaster.ui.generic.MenuAction;
import com.logmaster.ui.generic.UIComponent;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.FontID;
import net.runelite.api.ScriptEvent;
import net.runelite.api.widgets.*;

@Slf4j
public class UIDropdownOption extends UIComponent {
    private static final int TEXT_COLOR_INACTIVE = 0xFF981F;
    private static final int TEXT_COLOR_HIGHLIGHT = 0xFFFFFF;
    private static final int TEXT_COLOR_ACTIVE = 0xC8C8C8;

    private static final int TEXT_OPACITY_INACTIVE = 0;
    private static final int TEXT_OPACITY_ACTIVE = 128;

    private static final int BG_OPACITY_INACTIVE = 255;
    private static final int BG_OPACITY_ACTIVE = 230;

    private static final String DEFAULT_ACTION_LABEL = "Enable";

    @Getter
    protected final boolean managed;

    @Getter
    private boolean enabled = false;

    @Getter
    protected String text;

    @Getter
    protected String actionText;

    protected final Widget bgWidget;

	protected final Widget labelWidget;

	@Setter
	private ComponentEventListener<UIDropdownOption> enabledListener;

	public UIDropdownOption(Widget layerWidget) {
        super(layerWidget);
        this.managed = true;
        this.bgWidget = this.widget.createChild(WidgetType.RECTANGLE);
        this.labelWidget = this.widget.createChild(WidgetType.TEXT);

        this.setup();
        this.setupManaged();
	}

	public UIDropdownOption(Widget bgWidget, Widget labelWidget) {
        super(labelWidget);
        this.managed = false;
        this.bgWidget = bgWidget;
        this.labelWidget = labelWidget;

        this.setup();
	}

    private void setup() {
        this.setText(this.labelWidget.getText());
        this.setActionText(DEFAULT_ACTION_LABEL);

        // preserve current action label if possible
        String[] originalActions = this.labelWidget.getActions();
        if (originalActions != null && originalActions.length > 0) {
            this.setActionText(originalActions[0]);
        }

        this.enabled = this.bgWidget.getOpacity() < BG_OPACITY_INACTIVE;
        this.updateStatefulStyles();
    }

    private void setupManaged() {
        this.widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);

        this.bgWidget.setFilled(true);
        this.bgWidget.setTextColor(0xFFFFFF);
        this.bgWidget.setWidthMode(WidgetSizeMode.MINUS);
        this.bgWidget.setHeightMode(WidgetSizeMode.MINUS);

        this.labelWidget.setTextShadowed(true);
        this.labelWidget.setFontId(FontID.PLAIN_12);
        this.labelWidget.setWidthMode(WidgetSizeMode.MINUS);
        this.labelWidget.setHeightMode(WidgetSizeMode.MINUS);
        this.labelWidget.setXTextAlignment(WidgetTextAlignment.CENTER);
        this.labelWidget.setYTextAlignment(WidgetTextAlignment.CENTER);

        this.setActionText(DEFAULT_ACTION_LABEL);
        this.enabled = false;
        this.updateStatefulStyles();
    }

    @Override
    @Deprecated
    public void addAction(String action, MenuAction callback) {
        throw new RuntimeException("Cannot add actions to this component; use .setActionLabel() to rename single action");
    }

    @Override
    public void setName(String name) {
        this.bgWidget.setName(name + "-bg");
        this.labelWidget.setName(name + "-label");
    }

    public void setText(String text) {
        this.text = text;
        this.labelWidget.setText(text);
    }

    public void setActionText(String actionText) {
        this.actionText = actionText;

        this.actions.clear();
        super.addAction(this.actionText, () -> this.setEnabled(true));
    }

    public void setEnabled(boolean enabled) {
        // prevents raising the events unnecessarily
        if (this.enabled == enabled) return;

        this.enabled = enabled;

        if (this.enabled && this.enabledListener != null) {
            this.enabledListener.onComponentEvent(this);
        }

        this.updateStatefulStyles();
    }

    @Override
    protected void onMouseHover(ScriptEvent e) {
        super.onMouseHover(e);

        if (this.isEnabled()) return;
        this.labelWidget.setTextColor(TEXT_COLOR_HIGHLIGHT);
    }

    @Override
    protected void onMouseLeave(ScriptEvent e) {
        super.onMouseLeave(e);

        if (this.isEnabled()) return;
        this.labelWidget.setTextColor(TEXT_COLOR_INACTIVE);
    }

    private void updateStatefulStyles() {
        if (this.isEnabled()) {
            this.bgWidget.setOpacity(BG_OPACITY_ACTIVE);
            this.labelWidget.setTextColor(TEXT_COLOR_ACTIVE);
            this.labelWidget.setOpacity(TEXT_OPACITY_ACTIVE);
        } else {
            this.bgWidget.setOpacity(BG_OPACITY_INACTIVE);
            this.labelWidget.setTextColor(TEXT_COLOR_INACTIVE);
            this.labelWidget.setOpacity(TEXT_OPACITY_INACTIVE);
        }

        this.revalidate();
    }

    public void revalidate() {
        super.revalidate();
        this.bgWidget.revalidate();
        this.labelWidget.revalidate();
    }
}
