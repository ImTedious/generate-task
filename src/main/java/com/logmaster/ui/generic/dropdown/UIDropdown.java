package com.logmaster.ui.generic.dropdown;

import com.logmaster.ui.generic.ComponentEventListener;
import com.logmaster.ui.generic.UIComponent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

import java.util.*;

@Slf4j
public class UIDropdown extends UIComponent {
    private static final int BORDER_WIDTH = 6;

    // TODO: find a better way to identify our widgets
    public static final String WIDGET_NAME = "clogmaster";

    private final List<UIDropdownOption> options;

	@Setter
	private ComponentEventListener<UIDropdownOption> optionEnabledListener;

	public UIDropdown(Widget widget) {
		super(widget, Set.of(WidgetType.LAYER));
        this.options = new ArrayList<>();
        this.setup();
	}

    private void setup() {
        Widget[] children = this.widget.getChildren();
        if (children == null) {
            return;
        }

        for (Widget c : children) {
            if (!c.getName().isEmpty()) {
                continue;
            }

            if (c.getType() == WidgetType.RECTANGLE) {
                // we assume every background (rectangle) widget is
                // directly succeeded by the matching text widget
                Widget textWidget = children[c.getIndex() + 1];
                UIDropdownOption option = new UIDropdownOption(c, textWidget);
                option.setEnabledListener(this::onOptionEnabled);
                this.options.add(option);
            }
        }
    }

    public void cleanup() {
        Widget tabContainer = this.widget;
        Widget[] children = tabContainer.getChildren();
        if (children == null || children.length == 0) {
            return;
        }

        for (UIDropdownOption opt : this.options) {
            if (!opt.isManaged()) continue;

            children[opt.getWidget().getIndex()] = null;
        }

        Widget[] newChildren = Arrays.stream(children)
            .filter(Objects::nonNull)
            .toArray(Widget[]::new);

        Widget[] nzNewChildren = Arrays.copyOf(children, newChildren.length);
        System.arraycopy(newChildren, 0, nzNewChildren, 0, newChildren.length);

        tabContainer.setChildren(nzNewChildren);
        tabContainer.revalidate();

        resizeTabsContainer();
    }

    public UIDropdownOption getEnabledOption() {
        for (UIDropdownOption opt : this.options) {
            if (opt.isEnabled()) {
                return opt;
            }
        }

        return null;
    }

    public void setEnabledOption(String text) {
        for (UIDropdownOption opt : this.options) {
            if (opt.getText().equals(text)) {
                opt.setEnabled(true);
            }
        }
    }

    public void addOption(String text, String actionText) {
        Widget tabContainer = this.widget;
        Widget[] children = tabContainer.getChildren();
        if (children == null || children.length == 0) {
            return;
        }

        Widget lastOption = this.options.get(this.options.size() - 1).getWidget();

        UIDropdownOption newOption = new UIDropdownOption(tabContainer.createChild(WidgetType.LAYER));
        newOption.setName(WIDGET_NAME);
        newOption.setSize(lastOption.getOriginalWidth(), lastOption.getOriginalHeight());
        newOption.setPosition(lastOption.getOriginalX(), lastOption.getOriginalY() + lastOption.getOriginalHeight());
        newOption.setText(text);
        newOption.setActionText(actionText);
        newOption.revalidate();
        newOption.setEnabledListener(this::onOptionEnabled);

        this.options.add(newOption);

        resizeTabsContainer();
    }

    void onOptionEnabled(UIDropdownOption src) {
        for (UIDropdownOption ui : this.options) {
            if (ui == src) continue;
            ui.setEnabled(false);
        }

        // close dropdown
        this.widget.setHidden(true)
            .revalidate();

        if (this.optionEnabledListener != null) {
            this.optionEnabledListener.onComponentEvent(src);
        }
    }

    void resizeTabsContainer() {
        Widget tabContainer = this.widget;
        Widget[] children = tabContainer.getChildren();
        if (children == null) {
            return;
        }

        Widget lastOption = this.options.get(this.options.size() - 1).getWidget();

        tabContainer.setOriginalHeight(lastOption.getOriginalY() + lastOption.getOriginalHeight() + BORDER_WIDTH);
        tabContainer.revalidate();

        // recalculates the position and sizes for the border and background widgets
        for (Widget c : children) {
            c.revalidate();
        }
    }
}
