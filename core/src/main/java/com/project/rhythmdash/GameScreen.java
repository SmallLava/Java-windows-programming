package com.project.rhythmdash;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ScreenUtils;
import com.project.rhythmdash.model.ChartData;
import com.project.rhythmdash.model.NoteData;

public class GameScreen implements Screen {
    final MainGame game;
    
    RhythmManager rhythmManager;
    ChartData chartData;
    
    // 用來記錄我們讀到第幾顆音符了，避免重複檢查已經檢查過的音符
    int currentNoteIndex = 0;

    public GameScreen(MainGame game) {
        this.game = game;
        
        // 1. 讀取 JSON 譜面
        Json json = new Json();
        // 確保 assets 資料夾有這個檔案
        chartData = json.fromJson(ChartData.class, Gdx.files.internal("test_chart.json"));
        
        // 2. 初始化並播放音樂
        rhythmManager = new RhythmManager();
        // 確保 assets 資料夾有這個音樂檔
        rhythmManager.startMusic(chartData.songName); 
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0, 0, 0, 1);
        
        // 3. 更新時間
        rhythmManager.update(delta);
        float currentSongTime = rhythmManager.getSongPosition();

        // 4. 檢查是否有音符到了該打擊的時間 (模擬判定)
        // 這裡我們檢查：如果音符的時間 < 當前時間，代表它已經「經過」判定線了
        while (currentNoteIndex < chartData.notes.size()) {
            NoteData note = chartData.notes.get(currentNoteIndex);
            
            if (note.time <= currentSongTime) {
                // 觸發！(在 Console 印出來)
                System.out.println("Spawn Note! Time: " + note.time + " | Lane: " + note.lane);
                currentNoteIndex++;
            } else {
                // 因為音符是照時間排序的，如果這顆還沒到，後面的更不可能到，直接跳出
                break;
            }
        }
    }

    // ... 其他 override 方法保持預設 ...
    @Override public void show() {}
    @Override public void resize(int width, int height) {}
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override public void dispose() { rhythmManager.stop(); }
}