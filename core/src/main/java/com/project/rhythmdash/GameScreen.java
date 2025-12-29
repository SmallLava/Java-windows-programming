package com.project.rhythmdash;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.project.rhythmdash.components.BasicColor;
import com.project.rhythmdash.model.ChartData;
import com.project.rhythmdash.model.NoteData;
import com.project.rhythmdash.tools.DrawNotes;

public class GameScreen implements Screen, InputProcessor {
    final MainGame game;

    private final ShapeRenderer shapeRenderer;

    private final float JUDGE_LINE_X = 200f;
    private final float NOTE_SPEED = 600f;
    private final float LANE_HEIGHT = 100f;

    private final float PERFECT_WINDOW = 0.08f; // 80ms 內算 Perfect
    private final float GOOD_WINDOW = 0.15f;    // 150ms 內算 Good

    // 統計數據
    private int score = 0;
    private int combo = 0;
    private int maxCombo = 0;
    private int perfectCount = 0;
    private int goodCount = 0;
    private int missCount = 0;

    private String judgeText = "";      // 要顯示的文字 (PERFECT, GOOD, MISS)
    private Color judgeColor = Color.WHITE; // 文字顏色
    private float judgeTimer = 0f;      // 計時器 (控制文字顯示多久)
    private final float JUDGE_DURATION = 0.5f; // 文字顯示持續 0.5 秒

    private enum State {
        PLAYING, // 遊玩中
        PAUSED   // 暫停中
    }
    private State state = State.PLAYING;
    private final Stage pauseStage;
    private Table pauseTable;

    private final Viewport gameViewport;
    private final Viewport stageViewport;
    private final OrthographicCamera gameCamera;

    private final float WORLD_WIDTH = 1280f;
    private final float WORLD_HEIGHT = 720f;

    RhythmManager rhythmManager;
    ChartData chartData;
    
    // 用來記錄我們讀到第幾顆音符了，避免重複檢查已經檢查過的音符
    int currentNoteIndex = 0;

    public GameScreen(MainGame game) {
        this.game = game;
        
        // 讀取 JSON 譜面
        Json json = new Json();
        // 確保 assets 資料夾有這個檔案
        chartData = json.fromJson(ChartData.class, Gdx.files.internal("test_chart.json"));
        
        // 初始化並播放音樂
        rhythmManager = new RhythmManager();
        // 確保 assets 資料夾有這個音樂檔
        rhythmManager.startMusic(chartData.songName); 

        gameCamera = new OrthographicCamera();
        gameViewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, gameCamera);
        stageViewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT);
        pauseStage = new Stage(stageViewport);

        shapeRenderer = new ShapeRenderer();

        createPauseMenu();
        
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(pauseStage); // 優先讓 UI 接收點擊
        multiplexer.addProcessor(this);       // 其次是遊戲按鍵
        Gdx.input.setInputProcessor(multiplexer);
    }

    private void createPauseMenu() {
        // 建立一個半透明的表格，蓋在畫面中間
        pauseTable = new Table();
        pauseTable.setFillParent(true);
        pauseTable.setVisible(false); // 預設隱藏
        pauseStage.addActor(pauseTable);

        // --- 標題 "PAUSED" ---
        Label pauseLabel = new Label("PAUSED", game.skin, "title"); // 使用你的大字體樣式
        pauseTable.add(pauseLabel).padBottom(50).row();

        // --- 繼續遊戲按鈕 ---
        TextButton resumeBtn = new TextButton("RESUME", game.skin);
        resumeBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                resumeGame(); // 點擊後執行繼續遊戲
            }
        });
        pauseTable.add(resumeBtn).width(200).height(60).padBottom(20).row();

        // --- 退出按鈕 ---
        TextButton exitBtn = new TextButton("EXIT", game.skin);
        exitBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                rhythmManager.stop(); // 停止音樂
                game.setScreen(new MenuScreen(game)); // 回到主選單
            }
        });
        pauseTable.add(exitBtn).width(200).height(60).row();
    }    

    private void pauseGame() {
        state = State.PAUSED;
        rhythmManager.pause(); // 暫停音樂
        pauseTable.setVisible(true); // 顯示選單
    }

    private void resumeGame() {
        state = State.PLAYING;
        rhythmManager.resume(); // 恢復音樂
        pauseTable.setVisible(false); // 隱藏選單
    }

    public void activateInputProcessor() {
        Gdx.input.setInputProcessor(this);
    }

    @Override
    public void render(float delta) {
        // 1. 清除螢幕 (背景色)
        ScreenUtils.clear(0.05f, 0.05f, 0.05f, 1);

        // ==========================================
        //  PART A: 邏輯更新 (只有遊玩中才執行)
        // ==========================================
        if (state == State.PLAYING) {
            // 讓音樂時間流動
            rhythmManager.update(delta);
            
            float currentSongTime = rhythmManager.getSongPosition();

            // 檢查 MISS (這是邏輯，所以放在這裡)
            for (NoteData note : chartData.notes) {
                if (note.isHit) continue; // 已經打過或處理過的就跳過

                // 如果音符已經超過判定線太遠 (變負值且超過 Good 區間)
                if (note.time < currentSongTime - GOOD_WINDOW) {
                    // 觸發 Miss 邏輯
                    System.out.println("MISS"); // Debug 用
                    showJudgment("MISS", BasicColor.getColor(BasicColor.BasicColors.Miss)); // 顯示 UI 文字
                    
                    combo = 0;       // 斷連
                    missCount++;     // 記錄 Miss
                    note.isHit = true; // 標記為已處理，避免下一幀重複扣分
                }
            }
        }

        // ==========================================
        //  PART B: 畫面繪製 (無論暫停或遊玩都要畫)
        // ==========================================
        
        // 取得當前音樂時間 (如果暫停中，這個時間會停住不變，剛好讓我們畫出定格畫面)
        float songTime = rhythmManager.getSongPosition();

        // 開啟混合模式 (讓光暈漂亮一點)
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        gameViewport.apply();

        shapeRenderer.setProjectionMatrix(gameViewport.getCamera().combined);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        
        float centerY = WORLD_HEIGHT / 2f;
        

        // 1. 繪製軌道線 (背景裝飾)
        shapeRenderer.setColor(1, 1, 1, 0.1f);
        shapeRenderer.rect(0, centerY, WORLD_WIDTH, 2);
        
        // 2. 繪製判定線
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.rect(JUDGE_LINE_X, centerY - LANE_HEIGHT, 4, LANE_HEIGHT * 2);

        // 3. 繪製音符 (這是繪圖，所以移出 if (PLAYING) 區塊)
        for (NoteData note : chartData.notes) {
            // 如果已經被打掉(isHit)就不要畫了
            if (note.isHit) continue;

            // 計算座標
            float noteX = JUDGE_LINE_X + (note.time - songTime) * NOTE_SPEED;

            // 優化：只畫螢幕看得到的音符
            if (noteX > -100 && noteX < WORLD_WIDTH + 100) {
                if (note.lane == 0) { // 地面
                    DrawNotes.drawNotes(
                        noteX, 
                        centerY - LANE_HEIGHT + 40, 
                        shapeRenderer, 
                        BasicColor.getColor(BasicColor.BasicColors.Ground)
                    );
                } else { // 空中
                    DrawNotes.drawNotes(
                        noteX, 
                        centerY + 40, 
                        shapeRenderer, 
                        BasicColor.getColor(BasicColor.BasicColors.Air)
                    );
                }
            }
        }

        shapeRenderer.end();

        Gdx.gl.glDisable(GL20.GL_BLEND); // 關閉混合

        // ==========================================
        //  PART C: UI 繪製 (分數、Combo、暫停選單)
        // ==========================================
        game.batch.setProjectionMatrix(gameViewport.getCamera().combined);
        
        game.batch.begin();
        
        // 1. 繪製判定文字 (Perfect/Good/Miss)
        if (judgeTimer > 0) {
            // 只有在遊玩狀態下，時間才會倒數 (讓暫停時字會停留在畫面上)
            if (state == State.PLAYING) {
                judgeTimer -= delta;
            }
            
            // 取得字型 (使用大標題字型比較有魄力)
            // 假設你的 Skin 裡有 "title-font"，如果沒有就改用 "default"
            Label.LabelStyle style = game.skin.get("title", Label.LabelStyle.class);
            com.badlogic.gdx.graphics.g2d.BitmapFont font = style.font;
            
            // 計算透明度 (隨著時間變少，字變透明)
            float alpha = judgeTimer / JUDGE_DURATION;
            // 設定顏色 (使用 judgeColor)
            font.setColor(judgeColor.r, judgeColor.g, judgeColor.b, alpha);

            // 繪製文字 (位置設在左側 100px, 垂直置中)
            // GlyphLayout 用來計算文字寬度，這裡先簡單畫
            font.draw(game.batch, judgeText, 100, centerY);
            
            // 畫完後記得把字型顏色設回白色，以免影響其他地方
            font.setColor(Color.WHITE);
        }

        // 2. 繪製 Combo 數
        if (combo > 0) {
            // 取得小一點的字型
            Label.LabelStyle style = game.skin.get("default", Label.LabelStyle.class);
            com.badlogic.gdx.graphics.g2d.BitmapFont smallFont = style.font;
            
            smallFont.setColor(Color.WHITE);
            // 畫在判定文字的下方
            smallFont.draw(game.batch, "Combo: " + combo, 100, Gdx.graphics.getHeight() / 2f - 60);
        }

        // 3. 繪製分數 (右上角)
        Label.LabelStyle style = game.skin.get("default", Label.LabelStyle.class);
        style.font.draw(game.batch, "Score: " + score, Gdx.graphics.getWidth() - 200, Gdx.graphics.getHeight() - 50);

        game.batch.end();

        // ==========================================
        //  PART D: 暫停遮罩與選單
        // ==========================================
        if (state == State.PAUSED) {
            // 畫半透明黑底
            Gdx.gl.glEnable(GL20.GL_BLEND);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(0, 0, 0, 0.6f);
            shapeRenderer.rect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);
            shapeRenderer.end();
            
            // 畫暫停按鈕
            pauseStage.act();
            pauseStage.draw();
        }

        int finishedNotes = 0;
        for (NoteData note : chartData.notes) {
            if (note.isHit) {
                finishedNotes++;
            }
        }
        boolean isMusicEnded = !rhythmManager.getStatus();
        boolean isAllNotesDone = (finishedNotes >= chartData.notes.size());

        float lastNoteTime = chartData.notes.get(chartData.notes.size() - 1).time;
        boolean isTimeOver = rhythmManager.getSongPosition() > lastNoteTime + 2.0f;
        if (isAllNotesDone || (isMusicEnded && isTimeOver)) {
            // 停止音樂 (以防萬一)
            rhythmManager.stop();
    
            // 切換到結算畫面
            game.setScreen(new ResultScreen(
                game, 
                score, 
                perfectCount, 
                goodCount, 
                missCount,  
                maxCombo
            ));
        }
    }

    @Override public void show() {}
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override
    public void dispose() {
        rhythmManager.stop();
        shapeRenderer.dispose();
        pauseStage.dispose(); // 新增這一行
    }
    
    @Override
    public void resize(int width, int height) {
        // 1. 更新遊戲視口
        gameViewport.update(width, height, true);

        // 2. 更新 UI 視口
        stageViewport.update(width, height, true);
    }
    @Override
    public boolean keyDown(int keycode) {
        // --- 全域按鍵：ESC ---
        if (keycode == Input.Keys.ESCAPE) {
            if (state == State.PLAYING) {
                pauseGame();
            } else {
                resumeGame();
            }
            return true;
        }

        // --- 如果暫停中，忽略所有打擊按鍵 ---
        if (state == State.PAUSED) {
            return false;
        }

        // --- 以下是原本的打擊邏輯 ---
        int targetLane = -1;
        if (keycode == Input.Keys.D || keycode == Input.Keys.F) {
            targetLane = 0;
        } else if (keycode == Input.Keys.J || keycode == Input.Keys.K) {
            targetLane = 1;
        }
        
        checkHit(targetLane);
        
        return true;
    }

    private void checkHit(int lane) {
        float currentSongTime = rhythmManager.getSongPosition();
        
        NoteData closestNote = null;
        float minDiff = 1000f; 

        for (NoteData note : chartData.notes) {
            // 只檢查沒被打過、同軌道、且在判定範圍內的
            if (!note.isHit && note.lane == lane) {
                float diff = Math.abs(note.time - currentSongTime);

                if (note.time > currentSongTime + GOOD_WINDOW) break; 

                if (diff < GOOD_WINDOW) {
                    if (diff < minDiff) {
                        minDiff = diff;
                        closestNote = note;
                    }
                }
            }
        }

        // --- 修改後的結算邏輯 ---
        if (closestNote != null) {
            closestNote.isHit = true; // 標記為已處理

            // 判斷 Perfect 或 Good
            if (minDiff <= PERFECT_WINDOW) {
                // 觸發顯示 PERFECT
                showJudgment("PERFECT", BasicColor.getColor(BasicColor.BasicColors.Perfect));
                
                score += 100;
                combo++;
                perfectCount++;
            } else {
                // 觸發顯示 GOOD
                showJudgment("GOOD", BasicColor.getColor(BasicColor.BasicColors.Good));

                score += 50;
                combo++;
                goodCount++;
            }
            
            // 更新最大連擊
            if (combo > maxCombo) maxCombo = combo;
            
        } else {
            // 空揮 (這裡通常不做事，或者你可以扣分)
        }
    }

    private void showJudgment(String text, Color color) {
        this.judgeText = text;
        this.judgeColor = color;
        this.judgeTimer = JUDGE_DURATION; // 重置計時器，開始顯示
    }

    // 必須實作的其他空方法 (直接複製貼上即可)
    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
}