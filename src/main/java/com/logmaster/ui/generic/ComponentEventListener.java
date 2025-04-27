package com.logmaster.ui.generic;

/**
 * A listener interface for receiving UI component events
 * @author Antipixel
 */
public interface ComponentEventListener<T extends UIComponent>
{
	/**
	 * Invoked upon a component event
	 * @param src the source component responsible for the event
	 */
	void onComponentEvent(T src);
}

