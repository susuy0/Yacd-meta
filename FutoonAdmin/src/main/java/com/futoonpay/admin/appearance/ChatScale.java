package com.futoonpay.admin.appearance;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

/**
 * Scales every dp/sp in the app so the short screen side equals the 390-point canvas of the iOS reference.
 * One reference point is then exactly one dp on phones, whatever their physical density or width.
 */
public final class ChatScale {
    public static final float DESIGN_WIDTH = 390f;
    private ChatScale() {}

    /** Densities for a short side in pixels; tablets and unknown sizes keep the system density. */
    public static int densityDpi(int shortSidePx, float systemDensity) {
        if (shortSidePx <= 0 || systemDensity <= 0f || shortSidePx / systemDensity >= 600f) return 0;
        return Math.round(shortSidePx * 160f / DESIGN_WIDTH);
    }

    public static Context wrap(Context base) {
        if (base == null) return null;
        DisplayMetrics metrics = base.getResources().getDisplayMetrics();
        int dpi = densityDpi(Math.min(metrics.widthPixels, metrics.heightPixels), metrics.density);
        if (dpi == 0) return base;
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.densityDpi = dpi;
        return base.createConfigurationContext(configuration);
    }
}
