package se.dick.geolert;

import android.view.View;
import android.view.WindowInsets;

/** Keeps content clear of the status bar, navigation bar, camera cutout and keyboard. */
public final class Insets {

    private Insets() {
    }

    public static void apply(View root) {
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(
                    left + bars.left,
                    top + bars.top,
                    right + bars.right,
                    bottom + Math.max(bars.bottom, ime.bottom));
            return WindowInsets.CONSUMED;
        });
        root.requestApplyInsets();
    }
}
