package com.project.rhythmdash.components;

import com.badlogic.gdx.graphics.Color;

public class BasicColor {
    // 霓虹藍 (地面)
    private static final Color COLOR_GROUND = new Color(0.2f, 0.9f, 1.0f, 1f); 
    // 霓虹粉 (空中)
    private static final Color COLOR_AIR = new Color(1.0f, 0.2f, 0.6f, 1f);    
    // 判定線 (亮白)
    private static final Color COLOR_JUDGE = new Color(1.0f, 1.0f, 1.0f, 0.8f);
    // 軌道線 (暗灰)
    private static final Color COLOR_TRACK = new Color(1.0f, 1.0f, 1.0f, 0.1f);

    private static final Color COLOR_PERFECT = new Color(1f, 0.9f, 0.3f, 1f); // 金黃色
    private static final Color COLOR_GOOD = new Color(0.4f, 0.8f, 1f, 1f);    // 淺藍色
    private static final Color COLOR_MISS = new Color(1f, 0.3f, 0.3f, 1f);    // 紅色

    public enum BasicColors {Ground, Air, Judge, Track, Perfect, Good, Miss};
    public static Color getColor(BasicColors colors) {
        switch (colors) {
            case Ground:
                return COLOR_GROUND;
            case Air:
                return COLOR_AIR;
            case Judge:
                return COLOR_JUDGE;
            case Track:
                return COLOR_TRACK;
            case Perfect:
                return COLOR_PERFECT;
            case Good:
                return COLOR_GOOD;
            case Miss:
                return COLOR_MISS;
        }
        return new Color(0,0,0,0);
    }
}