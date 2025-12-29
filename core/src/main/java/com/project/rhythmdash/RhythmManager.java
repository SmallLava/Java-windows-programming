package com.project.rhythmdash;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;

public class RhythmManager {
    private Music music;
    private float songPosition;
    private boolean isPlaying;

    public void pause() {
        if (music != null && music.isPlaying()) {
            music.pause();
            isPlaying = false; // 停止內部的計時更新
        }
    }

    public void resume() {
        if (music != null && !music.isPlaying()) {
            music.play();
            isPlaying = true; // 恢復內部的計時更新
        }
    }

    public boolean getStatus() {
        return isPlaying;
    }

    public void startMusic(String filePath) {
        
        music = Gdx.audio.newMusic(Gdx.files.internal(filePath));
        music.setLooping(false);
        music.play();
        
        isPlaying = true;
        songPosition = 0;
    }

    public void update(float delta) {
        if (!isPlaying) return;

        songPosition += delta;

        float actualMusicPosition = music.getPosition();
        if (Math.abs(songPosition - actualMusicPosition) > 0.05f) {
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

    public void setVolume(Float Volume) {
        music.setVolume(Volume);
    }
}