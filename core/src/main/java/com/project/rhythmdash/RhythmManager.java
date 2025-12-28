package com.project.rhythmdash;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;

public class RhythmManager {
    private Music music;
    private float songPosition; // 當前歌曲播放到的時間 (秒)
    private boolean isPlaying;

    public void startMusic(String filePath) {
        // 載入音樂 (記得要在 assets 資料夾放入 test_music.mp3)
        music = Gdx.audio.newMusic(Gdx.files.internal(filePath));
        music.setLooping(false);
        music.play();
        
        isPlaying = true;
        songPosition = 0;
    }

    public void update(float delta) {
        if (!isPlaying) return;

        // 核心邏輯：每一幀加上經過的時間
        // 這樣能保證畫面絲滑流暢，不會因為音訊回報延遲而卡頓
        songPosition += delta;

        // 校正機制 (Sync)：
        // 雖然我們自己算時間很平滑，但長時間下來可能會跟實際音樂產生誤差
        // 所以我們檢查：如果誤差超過 0.05 秒 (50ms)，就強制同步回音樂的時間
        float actualMusicPosition = music.getPosition();
        if (Math.abs(songPosition - actualMusicPosition) > 0.05f) {
            // 注意：這裡只在音樂確實有在播放時才校正，避免音樂剛開始時的數值不穩
            if (actualMusicPosition > 0) {
                songPosition = actualMusicPosition;
            }
        }
    }

    public float getSongPosition() {
        return songPosition;
    }

    public void stop() {
        if (music != null) {
            music.stop();
            music.dispose();
        }
    }
}