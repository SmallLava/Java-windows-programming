package com.project.rhythmdash;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class ResultScreen implements Screen {
    final MainGame game;
    private final Stage stage;
    
    // 數據
    private final int score;
    private final int perfect;
    private final int good;
    private final int miss;
    private final int maxCombo;
    private float accuracy;
    private String rank;

    public ResultScreen(final MainGame game, int score, int perfect, int good, int miss, int maxCombo) {
        this.game = game;
        this.score = score;
        this.perfect = perfect;
        this.good = good;
        this.miss = miss;
        this.maxCombo = maxCombo;

        // 1. 計算準確率與評級
        calculateRank();

        // 2. 建立 UI
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        
        setupUI();
    }

    private void calculateRank() {
        // 簡單的加權計算：Perfect=100%, Good=50%, Miss=0%
        int totalNotes = perfect + good + miss;
        if (totalNotes == 0) totalNotes = 1; // 避免除以零

        float totalPoints = (perfect * 1f) + (good * 0.5f); 
        this.accuracy = (totalPoints / totalNotes) * 100;

        // osu! 風格評級標準
        if (accuracy >= 98f) rank = "S";      // SS 這裡先簡化為 S
        else if (accuracy >= 90f) rank = "A";
        else if (accuracy >= 80f) rank = "B";
        else if (accuracy >= 70f) rank = "C";
        else rank = "D";
        
        // 如果是 Full Combo，可以在 S 後面加個金邊或變色 (這裡先略過)
    }

    private void setupUI() {
        Table rootTable = new Table();
        rootTable.setFillParent(true);
        // rootTable.setDebug(true); // 如果想看排版線條，把這行打開
        stage.addActor(rootTable);

        // --- 1. 頂部：歌名與總分 ---
        Label songTitle = new Label("Override", game.skin, "title"); // 假設你有 title 樣式
        songTitle.setFontScale(0.6f); //稍微縮小一點標題
        
        Label scoreLabel = new Label(String.format("%08d", score), game.skin, "title"); // 8位數分數
        
        rootTable.add(songTitle).padTop(30).row();
        rootTable.add(scoreLabel).padBottom(30).row();

        // --- 2. 中間：數據區 (左) 與 評級區 (右) ---
        Table centerTable = new Table();
        
        // [左側詳細數據]
        Table statsTable = new Table();
        statsTable.defaults().align(Align.left).pad(5); // 設定預設對齊

        // 使用 "data" 樣式 (記得在 SkinTools 裡加)
        // --- PERFECT ---
        Label perfectLabel = new Label("PERFECT", game.skin, "data");
        perfectLabel.setColor(Color.CYAN); // 先設定顏色
        statsTable.add(perfectLabel);      // 再加入表格
        statsTable.add(new Label("x " + perfect, game.skin, "data")).row();

        // --- GOOD ---
        Label goodLabel = new Label("GOOD", game.skin, "data");
        goodLabel.setColor(Color.GREEN);
        statsTable.add(goodLabel);
        statsTable.add(new Label("x " + good, game.skin, "data")).row();

        // --- MISS ---
        Label missLabel = new Label("MISS", game.skin, "data");
        missLabel.setColor(Color.RED);
        statsTable.add(missLabel);
        statsTable.add(new Label("x " + miss, game.skin, "data")).row();
        
        statsTable.add(new Label("MAX COMBO", game.skin, "data")).padTop(20);
        statsTable.add(new Label(maxCombo + "x", game.skin, "data")).padTop(20).row();
        
        statsTable.add(new Label("ACCURACY", game.skin, "data"));
        statsTable.add(new Label(String.format("%.2f%%", accuracy), game.skin, "data")).row();

        // [右側巨大評級]
        Label rankLabel = new Label(rank, game.skin, "rank"); // 使用 "rank" 超大字體樣式
        // 根據評級變色
        switch (rank) {
            case "S":
                rankLabel.setColor(Color.GOLD);
                break;
            case "A":
                rankLabel.setColor(Color.GREEN);
                break;
            case "B":
                rankLabel.setColor(Color.BLUE);
                break;
            default:
                rankLabel.setColor(Color.GRAY);
                break;
        }

        // [加入動畫] 讓評級字有一個「蓋章」縮放的效果
        rankLabel.setOrigin(Align.center);
        rankLabel.addAction(Actions.sequence(
            Actions.alpha(0),          // 先隱藏
            Actions.scaleTo(3f, 3f),   // 變很大
            Actions.parallel(
                Actions.fadeIn(0.3f),  // 淡入
                Actions.scaleTo(1f, 1f, 0.3f, com.badlogic.gdx.math.Interpolation.swingOut) // 彈性縮小
            )
        ));

        // 將左右兩邊加入 Center Table
        centerTable.add(statsTable).padRight(100); // 左邊數據
        centerTable.add(rankLabel);                // 右邊評級
        
        rootTable.add(centerTable).expand().row(); // expand 讓中間區域佔據最多空間

        // --- 3. 底部：按鈕列 ---
        Table buttonTable = new Table();
        
        TextButton retryBtn = new TextButton("RETRY", game.skin);
        retryBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new GameScreen(game)); // 重玩
            }
        });

        TextButton backBtn = new TextButton("BACK", game.skin);
        backBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.setScreen(new MenuScreen(game)); // 回主選單
            }
        });

        buttonTable.add(retryBtn).width(150).padRight(20);
        buttonTable.add(backBtn).width(150);

        rootTable.add(buttonTable).padBottom(50);
    }

    @Override
    public void render(float delta) {
        // osu! 風格背景通常比較暗
        ScreenUtils.clear(0.05f, 0.05f, 0.1f, 1);
        
        stage.act();
        stage.draw();
    }

    @Override public void resize(int width, int height) { stage.getViewport().update(width, height, true); }
    @Override public void dispose() { stage.dispose(); }
    @Override public void show() {}
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
}