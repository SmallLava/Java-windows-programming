package com.project.rhythmdash.tools;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class DrawNotes {
    public static void drawNotes(float x, float y, ShapeRenderer shapeRenderer, Color color) {
        float radius = 30f;

        Color originalColor = color;

        shapeRenderer.setColor(Color.BLACK);
        shapeRenderer.circle(x, y, radius + 4); // 半徑 +4 像素作為邊框厚度

        shapeRenderer.setColor(originalColor);
        shapeRenderer.circle(x, y, radius);
        
        shapeRenderer.setColor(0.8f, 0.8f, 0.8f, 1f);
        shapeRenderer.circle(x, y, radius * 0.85f);

        shapeRenderer.setColor(originalColor);
        shapeRenderer.circle(x, y, radius * 0.70f);
    }
}