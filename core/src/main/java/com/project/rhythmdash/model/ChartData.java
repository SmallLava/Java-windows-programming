package com.project.rhythmdash.model;

import java.util.ArrayList;

public class ChartData {
    public String songName;   // 歌曲檔案名稱 (例如 "music.mp3")
    public float bpm;         // BPM 速度 (影響音符移動速度或背景動畫)
    public float offset;      // 延遲校正 (秒)，用來微調音樂和譜面的整體位移
    public ArrayList<NoteData> notes = new ArrayList<>(); // 所有的音符

    public ChartData() {}
}