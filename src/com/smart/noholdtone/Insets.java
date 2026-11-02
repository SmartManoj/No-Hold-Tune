package com.smart.noholdtone;

import android.view.View;
import android.view.WindowInsets;

/** Target 36 forces edge-to-edge; pad the root view so content clears the status and navigation bars. */
final class Insets {
    static void apply(View root) {
        int l = root.getPaddingLeft(), t = root.getPaddingTop(), r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, in) -> {
            android.graphics.Insets bars = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom);
            return in;
        });
    }
}
