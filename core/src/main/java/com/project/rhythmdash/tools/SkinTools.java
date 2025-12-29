package com.project.rhythmdash.tools;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

public class SkinTools {
    public static Skin createBasicSkin() {
        Skin skin = new Skin();

        SkinTools.setWhitePixel(skin);

        SkinTools.setTextFont(skin);
        
        SkinTools.setLabelFont(skin);

        SkinTools.setBottonFont(skin);

        SkinTools.setSliderFont(skin);
        return skin;
    }

    private static void setWhitePixel(Skin skin) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new Texture(pixmap));
    }

    private static void setTextFont(Skin skin) {
        // 1. 準備產生器
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("font.ttf"));
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();

        // 2. 設定通用參數 (中文支援、邊框等)
        parameter.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "開始遊戲設定音量得分連擊完美結束返回";
        parameter.borderWidth = 2; // 標題邊框可以粗一點
        parameter.borderColor = Color.BLACK;
        
        // --- A. 生成 [大標題] 字型 ---
        parameter.size = 72; // 設定成原本的 3 倍大 (或是你想要的任意大小)
        BitmapFont titleFont = generator.generateFont(parameter);
        // 設定線性過濾 (讓邊緣平滑)
        titleFont.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        // 加入 Skin，命名為 "title-font"
        skin.add("title-font", titleFont);

        // --- B. 生成 [普通內文] 字型 ---
        parameter.size = 24; // 回復成一般大小
        parameter.borderWidth = 1; // 邊框改細
        BitmapFont textFont = generator.generateFont(parameter);
        textFont.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        skin.add("default", textFont);

        // --- C. 生成 [超大評級] 字型 (給 S, A, B 用的) ---
        parameter.size = 120; // 超大尺寸
        parameter.borderWidth = 3;
        parameter.borderColor = Color.GOLD; // 金邊
        parameter.shadowOffsetX = 4;
        parameter.shadowOffsetY = 4;
        BitmapFont rankFont = generator.generateFont(parameter);
        rankFont.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        skin.add("rank-font", rankFont);

        // --- D. 生成 [數據數字] 字型 (給 Combo, Accuracy 用的) ---
        parameter.size = 36; // 中等尺寸
        parameter.borderWidth = 1;
        parameter.borderColor = Color.BLACK;
        parameter.shadowOffsetX = 1;
        parameter.shadowOffsetY = 1;
        BitmapFont dataFont = generator.generateFont(parameter);
        dataFont.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        skin.add("data-font", dataFont);

        // 3. 記得銷毀產生器 (要在兩個字型都生完之後才銷毀)
        generator.dispose();

        // --- C. 設定 Label 樣式 ---
        
        // 樣式 1: 標題專用樣式 ("title")
        Label.LabelStyle titleStyle = new Label.LabelStyle();
        titleStyle.font = skin.getFont("title-font"); // 使用剛剛的大字體
        skin.add("title", titleStyle);

        // 樣式 2: 預設樣式 ("default")
        Label.LabelStyle defaultStyle = new Label.LabelStyle();
        defaultStyle.font = skin.getFont("default"); // 使用小字體
        skin.add("default", defaultStyle);

        Label.LabelStyle rankStyle = new Label.LabelStyle();
        rankStyle.font = skin.getFont("rank-font");
        skin.add("rank", rankStyle);

        Label.LabelStyle dataStyle = new Label.LabelStyle();
        dataStyle.font = skin.getFont("data-font");
        skin.add("data", dataStyle);
    }

    private static void setLabelFont(Skin skin) {
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = skin.getFont("default");
        skin.add("default", labelStyle);
    }

    private static void setBottonFont(Skin skin) {
        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.font = skin.getFont("default");
        textButtonStyle.up = skin.newDrawable("white", Color.DARK_GRAY); // 沒按時的顏色
        textButtonStyle.down = skin.newDrawable("white", Color.GRAY);    // 按下時的顏色
        textButtonStyle.over = skin.newDrawable("white", Color.LIGHT_GRAY); // 滑鼠懸停顏色
        skin.add("default", textButtonStyle);
    }

    private static void setSliderFont(Skin skin) {
        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = skin.newDrawable("white", Color.DARK_GRAY); // 軌道顏色
        sliderStyle.knob = skin.newDrawable("white", Color.CYAN); // 滑動鈕顏色 (設為霓虹藍)
        sliderStyle.knob.setMinHeight(20);
        sliderStyle.knob.setMinWidth(20);
        skin.add("default-horizontal", sliderStyle);
    }
}