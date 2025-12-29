package com.project.rhythmdash.model;

public class NoteData {
    // 音符出現的時間點 (單位：秒)，例如 1.5 代表音樂第 1.5 秒時要打擊
    public float time;
    
    // 軌道：0 = 地面 (Ground), 1 = 空中 (Air)
    public int lane;
    
    // 類型：0 = 普通怪, 1 = 大怪, 2 = 連打開始, 3 = 連打結束
    public int type;

    public boolean isHit = false;

    // 空建構子是為了給 JSON 解析器用的，必須保留
    public NoteData() {}

    public NoteData(float time, int lane, int type) {
        this.time = time;
        this.lane = lane;
        this.type = type;
    }
}