package com.project.rhythmdash;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.ScreenUtils;

public class MenuScreen implements Screen {

    final MainGame game; // 拿到經理的參考，這樣我們才能切換畫面

    public MenuScreen(final MainGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        // 畫面剛顯示時執行 (類似初始化)
    }

    @Override
    public void render(float delta) {
        // 1. 清除螢幕 (用深藍色填滿背景，模擬 Muse Dash 的夜景氛圍)
        ScreenUtils.clear(0, 0, 0.2f, 1);

        // 2. 開始繪圖
        game.batch.begin();
        // 這裡以後會畫 LOGO 和 "Press Start"
        // 目前我們先留空，或者你可以試著畫一行字
        game.batch.end();

        // 3. 簡單的點擊測試：如果點擊螢幕，就印出一行字
        if (Gdx.input.isTouched()) {
            System.out.println("Go to Song Select!");
            // game.setScreen(new SongSelectScreen(game)); // 未來會寫這行
        }
    }

    @Override
    public void resize(int width, int height) { }

    @Override
    public void pause() { }

    @Override
    public void resume() { }

    @Override
    public void hide() { }

    @Override
    public void dispose() { }
}