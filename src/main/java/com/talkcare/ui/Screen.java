package com.talkcare.ui;

/** A controller that owns one or more views and reacts to being shown or left. */
public interface Screen {
    /** Called after the view became visible. */
    void onShow(String viewId);

    /** Called before another view is shown (stop polling, close menus, ...). */
    default void onHide() {}
}
