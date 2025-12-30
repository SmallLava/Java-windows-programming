import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*; 
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList; 
import java.util.regex.*; 
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;

public class MuseDashLite extends JPanel implements KeyListener, Runnable {

    // --- 1. 系統參數 ---
    private final int GAME_WIDTH = 800;
    private final int GAME_HEIGHT = 450;
    
    private double currentBpm = 120.0; 
    private int audioOffset = 0;   
    private int masterVolume = 80; 
    private boolean isNoFailMode = false; 
    
    // 判定與顯示
    private final int PLAYER_X = 150;
    private final int GROUND_Y = 300;
    private final int AIR_Y = 200;
    private final int TARGET_SIZE = 60; 
    
    // 判定範圍
    private final int HIT_RANGE = 70;      
    private final int BAD_RANGE = 120;     
    private final int PERFECT_RANGE = 40;  
    
    private String fontName = "SansSerif"; 
    private final double NOTE_SPEED = 0.7; 
    
    // --- 視覺素材 ---
    private static final Font FONT_SCORE = new Font("SansSerif", Font.BOLD, 22);
    private static final Font FONT_COMBO = new Font("SansSerif", Font.BOLD | Font.ITALIC, 40);
    private static final Font FONT_EFFECT = new Font("SansSerif", Font.BOLD, 22);
    private static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 60);
    private static final Font FONT_MENU = new Font("SansSerif", Font.PLAIN, 24);
    
    private static final Color COL_TRACK_BG = new Color(255, 255, 255, 20);
    private static final Color COL_TRACK_BORDER = new Color(255, 255, 255, 50);
    private static final Color COL_AIR_PRESS = new Color(80, 80, 255);
    private static final Color COL_AIR_IDLE = new Color(80, 80, 255, 100);
    private static final Color COL_GND_PRESS = new Color(255, 80, 80);
    private static final Color COL_GND_IDLE = new Color(255, 80, 80, 100);
    private static final Color COL_SHADOW = new Color(0, 0, 0, 150);
    
    private static final Stroke STROKE_THICK = new BasicStroke(4);
    private static final Stroke STROKE_NORMAL = new BasicStroke(2);
    private static final Stroke STROKE_THIN = new BasicStroke(3);

    private BufferedImage imgPlayer; 
    private BufferedImage imgNoteGround;
    private BufferedImage imgNoteAir;
    private BufferedImage imgGameBG;
    
    // --- 音效系統 ---
    private List<Clip> hitSoundPool = new ArrayList<>();
    private int poolIndex = 0;
    
    // 動畫變數
    private float pulseScale = 1.0f; 
    private float bgScrollX = 0; 
    private final float BG_SPEED = 2.0f; 

    // --- 校正模式變數 ---
    private ArrayList<Long> calibrationDeltas = new ArrayList<>();
    private long lastBeatTime = 0;
    private int calibrationBeatCount = 0;
    private final int TOTAL_CALIBRATION_BEATS = 16;

    // --- 2. 資料結構 (更新版) ---
    
    // 🔥 新增：用來儲存單個譜面的詳細資訊 (難度、等級、顏色)
    class MapDetail {
        File file;
        String difficultyLabel; // e.g. "Easy", "Hard"
        String levelVal;        // e.g. "9", "0"
        Color color;            // UI 顯示顏色
        
        public MapDetail(File file, String label, String level, Color color) {
            this.file = file;
            this.difficultyLabel = label;
            this.levelVal = level;
            this.color = color;
        }
    }

    class SongInfo {
        String title = "Unknown"; String artist = "Unknown"; double bpm = 120.0;
        BufferedImage coverImage; File folder; File musicFile; 
        
        // 🔥 修改：現在存的是 MapDetail 物件，而不只是 File
        ArrayList<MapDetail> maps = new ArrayList<>();
        
        public SongInfo(File folder) { this.folder = folder; }
    }
    
    enum GameState { TITLE_SCREEN, OFFSET_WIZARD, SONG_SELECT, DIFFICULTY_SELECT, PLAYING, GAME_OVER }
    private GameState currentState = GameState.TITLE_SCREEN; 
    
    private ArrayList<SongInfo> songList; 
    private int songSelection = 0;       
    private int diffSelection = 0;
    private int titleSelection = 0; 
    
    private boolean isRunning = true;
    private Thread gameThread;
    
    private CopyOnWriteArrayList<Note> notes;      
    private ArrayList<NoteData> mapData; 
    private CopyOnWriteArrayList<HitEffect> effects;
    
    private boolean keyF = false; private boolean keyD = false;
    private boolean keyJ = false; private boolean keyK = false;
    private boolean isGroundPressed() { return keyF || keyD; }
    private boolean isAirPressed() { return keyJ || keyK; }
    
    private int playerY = GROUND_Y;
    
    private int hp = 100; private final int MAX_HP = 100;
    private int score = 0; private int combo = 0; private int maxCombo = 0;
    private int fever = 0; private final int MAX_FEVER = 1000; private boolean isFeverMode = false;
    private final int FEVER_GAIN = 5; private final int FEVER_DRAIN = 3;
    
    private boolean isEnding = false; 
    private int endTimer = 0; 
    private final int END_DELAY = 180;
    
    private Clip bgmClip;
    private long currentMusicTime = 0;
    private long startTimeNano = 0;

    public MuseDashLite() {
        this.setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));
        this.setBackground(Color.BLACK);
        this.setFocusable(true);
        this.addKeyListener(this);
        
        this.notes = new CopyOnWriteArrayList<>();
        this.effects = new CopyOnWriteArrayList<>();
        this.mapData = new ArrayList<>();
        this.songList = new ArrayList<>();
        
        selectBestRoundFont();
        loadAssets(); 
        loadSongsFromFolder(); 
        
        gameThread = new Thread(this);
        gameThread.start();
    }
    
    private void loadAssets() {
        try { if (new File("player.png").exists()) imgPlayer = ImageIO.read(new File("player.png")); } catch (Exception e) {}
        try { if (new File("note_ground.png").exists()) imgNoteGround = ImageIO.read(new File("note_ground.png")); } catch (Exception e) {}
        try { if (new File("note_air.png").exists()) imgNoteAir = ImageIO.read(new File("note_air.png")); } catch (Exception e) {}
        try { 
            File bg = new File("bg_game.jpg");
            if(bg.exists()) imgGameBG = ImageIO.read(bg);
            else { bg = new File("bg_game.jpg.jpg"); if(bg.exists()) imgGameBG = ImageIO.read(bg); }
        } catch (Exception e) {}

        File soundFile = new File("hit.wav");
        if (soundFile.exists()) {
            try {
                byte[] audioData = getBytesFromFile(soundFile);
                for (int i = 0; i < 10; i++) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(AudioSystem.getAudioInputStream(new ByteArrayInputStream(audioData)));
                    hitSoundPool.add(clip);
                }
                System.out.println("✅ Audio Pool initialized.");
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
    
    private byte[] getBytesFromFile(File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            int read; byte[] buff = new byte[1024];
            while ((read = in.read(buff)) > 0) out.write(buff, 0, read);
        }
        return out.toByteArray();
    }

    private void loadSongsFromFolder() {
        File songsDir = new File("Songs");
        if (!songsDir.exists()) { songsDir.mkdir(); System.out.println("已建立 Songs 資料夾"); }
        File[] folders = songsDir.listFiles();
        songList.clear();
        if (folders != null) {
            for (File folder : folders) {
                if (folder.isDirectory()) {
                    SongInfo song = parseSongFolder(folder);
                    if (song != null && !song.maps.isEmpty()) songList.add(song);
                }
            }
        }
        if (songList.isEmpty()) {
            SongInfo dummy = new SongInfo(null);
            dummy.title = "No Songs Found"; dummy.artist = "Check /Songs folder"; songList.add(dummy);
        }
    }
    
    // 🔥🔥 修改：讀取 JSON 中的難度並匹配到檔案
    private SongInfo parseSongFolder(File folder) {
        SongInfo song = new SongInfo(folder);
        File jsonFile = new File(folder, "info.json");
        
        // 預設難度值
        String[] diffLevels = {"?", "?", "?", "?"}; 
        
        if (jsonFile.exists()) {
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new FileReader(jsonFile))) {
                    String line; while ((line = br.readLine()) != null) sb.append(line);
                }
                String jsonContent = sb.toString();
                song.title = extractRegex(jsonContent, "name");
                if (song.title.equals("Unknown")) song.title = extractRegex(jsonContent, "name_en");
                song.artist = extractRegex(jsonContent, "author");
                if (song.artist.equals("Unknown")) song.artist = extractRegex(jsonContent, "artist");
                String bpmStr = extractRegex(jsonContent, "bpm");
                if (!bpmStr.equals("Unknown")) song.bpm = Double.parseDouble(bpmStr.split(" ")[0]); // 處理 "166 (150-166)" 這種格式
                
                diffLevels[0] = extractRegex(jsonContent, "difficulty1");
                diffLevels[1] = extractRegex(jsonContent, "difficulty2");
                diffLevels[2] = extractRegex(jsonContent, "difficulty3");
                diffLevels[3] = extractRegex(jsonContent, "difficulty4");

            } catch (Exception e) {}
        }
        song.musicFile = new File(folder, "music.wav");
        if (!song.musicFile.exists()) return null;
        try { File img = new File(folder, "cover.png"); if (img.exists()) song.coverImage = ImageIO.read(img); } catch (Exception e) {}
        
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".bms"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                String fname = f.getName().toLowerCase();
                String label = "Unknown";
                String lv = "?";
                Color col = Color.GRAY;

                // 優先嘗試用檔名判斷
                if (fname.contains("map1") || fname.contains("easy")) { 
                    label = "Easy"; lv = diffLevels[0]; col = Color.GREEN;
                } else if (fname.contains("map2") || fname.contains("hard")) { 
                    label = "Hard"; lv = diffLevels[1]; col = Color.CYAN;
                } else if (fname.contains("map3") || fname.contains("master")) { 
                    label = "Master"; lv = diffLevels[2]; col = new Color(200, 50, 200);
                } else if (fname.contains("map4") || fname.contains("hidden")) { 
                    label = "Hidden"; lv = diffLevels[3]; col = Color.RED;
                } 
                // 🔥 如果檔名亂取 (例如 01.bms)，就依照檔案順序 (i) 來硬塞難度
                else {
                    if (i == 0) { label = "Easy"; lv = diffLevels[0]; col = Color.GREEN; }
                    else if (i == 1) { label = "Hard"; lv = diffLevels[1]; col = Color.CYAN; }
                    else if (i == 2) { label = "Master"; lv = diffLevels[2]; col = new Color(200, 50, 200); }
                    else { label = "Extra"; lv = diffLevels[3]; col = Color.MAGENTA; }
                }
                
                // 處理 JSON 裡寫 "0" 或抓不到的情況
                if (lv.equals("0") || lv.equals("Unknown") || lv.equals("?")) {
                    lv = "?"; // 顯示問號
                    // 這裡可以選擇：如果是 0 是否還要顯示？目前邏輯是顯示但標示 ?
                }

                song.maps.add(new MapDetail(f, label, lv, col));
            }
        }
        return song;
    }
    
    private String extractRegex(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^,\"}]+)\"?");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1).trim();
        return "Unknown";
    }

    class NoteData implements Comparable<NoteData> {
        long appearTime; long duration; Track track;
        public NoteData(long time, long duration, Track track) { this.appearTime = time; this.duration = duration; this.track = track; }
        @Override public int compareTo(NoteData o) { return Long.compare(this.appearTime, o.appearTime); }
    }
    enum Track { GROUND, AIR }
    
    class Note {
        long appearTime; long duration; long endTime; int x, y; int width; Track track; 
        boolean isActive = true; boolean isMissed = false; boolean isHolding = false; boolean hasHitHead = false;
        int graceFrames = 15; 
        
        public Note(long appearTime, long duration, Track track) {
            this.appearTime = appearTime; this.duration = duration; this.endTime = appearTime + duration;
            this.track = track; this.y = (track == Track.GROUND) ? GROUND_Y : AIR_Y; 
            this.x = GAME_WIDTH; this.width = TARGET_SIZE;
        }
    }
    
    class HitEffect {
        String text; int x, y; int life = 40; Color baseColor;
        public HitEffect(String text, int x, int y, Color color) { this.text = text; this.x = x; this.y = y; this.baseColor = color; }
        public boolean update() { y -= 1; life--; return life > 0; }
        public Color getCurrentColor() {
            int alpha = (int)((life / 40.0) * 255);
            if (alpha < 0) alpha = 0; if (alpha > 255) alpha = 255;
            return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
        }
    }

    private void playBGM(File file) {
        stopBGM();
        try {
            if (file == null || !file.exists()) return;
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(file);
            bgmClip = AudioSystem.getClip();
            bgmClip.open(audioIn);
            updateVolume(bgmClip); 
            bgmClip.start();
            if (currentState == GameState.SONG_SELECT || currentState == GameState.OFFSET_WIZARD) bgmClip.loop(Clip.LOOP_CONTINUOUSLY); 
        } catch (Exception e) {}
    }
    
    private void stopBGM() { if (bgmClip != null) { bgmClip.stop(); bgmClip.close(); bgmClip = null; } }
    
    private void playHitSound() {
        if (hitSoundPool.isEmpty()) return;
        try {
            Clip clip = hitSoundPool.get(poolIndex);
            if (clip.isRunning()) clip.stop();
            clip.setFramePosition(0);
            updateVolume(clip);       
            clip.start();             
            poolIndex++;
            if (poolIndex >= hitSoundPool.size()) poolIndex = 0; 
        } catch (Exception e) {}
    }

    private void updateVolume(Clip clip) {
        if (clip == null) return;
        try {
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            if (masterVolume <= 0) {
                gainControl.setValue(gainControl.getMinimum()); 
            } else {
                float gain = (float) (Math.log10(masterVolume / 100.0) * 20.0);
                gainControl.setValue(gain);
            }
        } catch (Exception e) {}
    }
    
    private void selectBestRoundFont() {
        String[] candidates = { "Arial Rounded MT Bold", "Varela Round", "Comic Sans MS", "Microsoft YaHei", "SansSerif" };
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFonts = ge.getAvailableFontFamilyNames();
        for (String target : candidates) { for (String f : availableFonts) { if (f.equalsIgnoreCase(target)) { this.fontName = target; return; } } }
    }

    // 🔥 修改：傳入 MapDetail 來啟動遊戲
    public void startGame(SongInfo song, MapDetail mapDetail) {
        notes.clear(); mapData.clear(); effects.clear();
        score = 0; combo = 0; maxCombo = 0; hp = MAX_HP;
        fever = 0; isFeverMode = false; 
        isEnding = false; endTimer = 0; currentMusicTime = 0;
        this.currentBpm = song.bpm;
        this.bgScrollX = 0;
        this.playerY = GROUND_Y;
        this.startTimeNano = System.nanoTime();
        loadBMS(mapDetail.file); 
        playBGM(song.musicFile);
        currentState = GameState.PLAYING;
    }
    
    private void startAutoCalibration() {
        if (songList.isEmpty()) return;
        SongInfo song = songList.get(0);
        this.currentBpm = song.bpm;
        calibrationDeltas.clear();
        calibrationBeatCount = 0;
        lastBeatTime = 0;
        currentState = GameState.OFFSET_WIZARD;
        playBGM(song.musicFile);
    }
    
    private void updateCalibration() {
        if (bgmClip != null && bgmClip.isRunning()) {
            long musicTime = bgmClip.getMicrosecondPosition() / 1000;
            double msPerBeat = 60000.0 / currentBpm;
            if (musicTime > lastBeatTime + msPerBeat) {
                lastBeatTime = (long)(musicTime / msPerBeat) * (long)msPerBeat; 
                pulseScale = 1.3f; 
            }
        }
        if (pulseScale > 1.0f) pulseScale -= 0.05f;
    }
    
    private void recordCalibrationHit() {
        if (bgmClip == null) return;
        long musicTime = bgmClip.getMicrosecondPosition() / 1000;
        double msPerBeat = 60000.0 / currentBpm;
        long closestBeat = Math.round(musicTime / msPerBeat) * (long)msPerBeat;
        long delta = musicTime - closestBeat; 
        
        if (Math.abs(delta) < 300) {
            calibrationDeltas.add(delta);
            calibrationBeatCount++;
            effects.add(new HitEffect(delta + "ms", GAME_WIDTH/2, GAME_HEIGHT/2 - 50, delta > 0 ? Color.YELLOW : Color.CYAN));
            playHitSound();
            
            if (calibrationBeatCount >= TOTAL_CALIBRATION_BEATS) {
                long sum = 0;
                for (Long d : calibrationDeltas) sum += d;
                audioOffset = (int)(sum / calibrationDeltas.size()); 
                stopBGM();
                currentState = GameState.TITLE_SCREEN;
                JOptionPane.showMessageDialog(this, "Calibration Complete!\nNew Offset: " + audioOffset + " ms");
            }
        }
    }

    private void loadBMS(File file) {
        double msPerBeat = 60000.0 / currentBpm;
        double msPerMeasure = msPerBeat * 4;
        Map<Integer, Long> pendingHolds = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.matches("#\\d{3}\\d{2}:[0-9A-Z]+")) {
                    String measureStr = line.substring(1, 4);
                    String channelStr = line.substring(4, 6);
                    String dataStr = line.split(":")[1];
                    int measureIndex = Integer.parseInt(measureStr);
                    int channel = Integer.parseInt(channelStr);
                    Track track = null;
                    boolean isHoldChannel = false;
                    if (channel == 11 || channel == 13 || channel == 15) track = Track.GROUND;
                    else if (channel == 12 || channel == 14 || channel == 16) track = Track.AIR;
                    else if (channel == 51 || channel == 53 || channel == 55) { track = Track.GROUND; isHoldChannel = true; }
                    else if (channel == 52 || channel == 54 || channel == 56) { track = Track.AIR; isHoldChannel = true; }
                    else continue; 
                    
                    int totalObjects = dataStr.length() / 2;
                    for (int i = 0; i < totalObjects; i++) {
                        String hex = dataStr.substring(i * 2, i * 2 + 2);
                        if (!hex.equals("00")) { 
                            double measureStartTime = measureIndex * msPerMeasure;
                            double positionRatio = (double) i / totalObjects;
                            long noteTime = (long)(measureStartTime + (positionRatio * msPerMeasure));
                            if (isHoldChannel) {
                                if (!pendingHolds.containsKey(channel)) { pendingHolds.put(channel, noteTime); } 
                                else { 
                                    long startTime = pendingHolds.remove(channel); 
                                    long duration = noteTime - startTime; 
                                    if (duration > 10) { mapData.add(new NoteData(startTime, duration, track)); }
                                }
                            } else { mapData.add(new NoteData(noteTime, 0, track)); }
                        }
                    }
                }
            }
            Collections.sort(mapData);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void run() {
        while (isRunning) {
            if (currentState == GameState.PLAYING) updateGame();
            else if (currentState == GameState.OFFSET_WIZARD) updateCalibration();
            repaint();
            try { Thread.sleep(16); } catch (InterruptedException e) {}
        }
    }

    private void updateGame() {
        if (bgmClip != null && bgmClip.isRunning()) {
            currentMusicTime = (bgmClip.getMicrosecondPosition() / 1000) - audioOffset;
        } else if (currentState == GameState.PLAYING) {
            currentMusicTime = ((System.nanoTime() - startTimeNano) / 1000000) - audioOffset;
        }
        
        bgScrollX -= BG_SPEED; 
        if (bgScrollX <= -GAME_WIDTH) { bgScrollX = 0; }
        if (pulseScale > 1.0f) pulseScale -= 0.05f;
        
        Iterator<NoteData> it = mapData.iterator();
        while (it.hasNext()) {
            NoteData data = it.next();
            double timeToReach = (GAME_WIDTH - PLAYER_X) / NOTE_SPEED; 
            if (data.appearTime - currentMusicTime <= timeToReach) {
                notes.add(new Note(data.appearTime, data.duration, data.track));
                it.remove();
            } else { break; }
        }
        
        boolean groundShield = false, airShield = false;
        for (Note note : notes) {
            if (note.isActive && note.duration > 0 && note.hasHitHead) {
                if (note.track == Track.GROUND) groundShield = true; else airShield = true;
            }
        }

        boolean allNotesGone = true;
        for (Note note : notes) {
            if (!note.isActive) continue;
            
            if (note.duration > 0 && note.hasHitHead) {
                note.x = PLAYER_X;
                long remainingTime = note.endTime - currentMusicTime;
                note.width = (int)(remainingTime * NOTE_SPEED);
                if (note.width < 0) note.width = 0;
            } else {
                long timeDiff = note.appearTime - currentMusicTime;
                note.x = PLAYER_X + (int)(timeDiff * NOTE_SPEED);
                if (note.duration > 0) note.width = (int)(note.duration * NOTE_SPEED);
            }

            if (note.x + note.width > -100) allNotesGone = false;
            
            if (note.isActive && note.duration > 0 && note.hasHitHead) {
                boolean holding = (note.track == Track.GROUND) ? isGroundPressed() : isAirPressed();
                if (holding) {
                    note.graceFrames = 15; note.isHolding = true;
                    if (currentMusicTime % 10 == 0) { score += 10; addFever(1); }
                    if (Math.random() > 0.8) effects.add(new HitEffect("HOLD...", PLAYER_X + 20, note.y - 20, Color.YELLOW));
                    if (currentMusicTime >= note.endTime) { 
                        note.isActive = false; 
                        effects.add(new HitEffect("PERFECT", PLAYER_X, note.y, Color.YELLOW)); 
                        combo++; pulseScale = 1.5f; 
                    }
                } else { 
                    note.isHolding = false;
                    note.graceFrames--;
                    if (note.graceFrames <= 0) { 
                        note.isActive = false; triggerMiss(note, "MISS"); 
                    }
                }
            }
            
            if (!isEnding && !note.isMissed && !note.hasHitHead && note.x + note.width < PLAYER_X - BAD_RANGE) {
                boolean hasShield = (note.track == Track.GROUND) ? groundShield : airShield;
                if (hasShield) note.isActive = false; else triggerMiss(note, "MISS");
            }
        }
        
        if (isFeverMode) { fever -= FEVER_DRAIN; if (fever <= 0) { fever = 0; isFeverMode = false; } }
        
        // 使用執行緒安全的方式更新 effects
        for (HitEffect effect : effects) {
            if (!effect.update()) {
                effects.remove(effect);
            }
        }
        
        if (!isEnding) {
            if (hp <= 0 && !isNoFailMode) { 
                isEnding = true; endTimer = 0; effects.add(new HitEffect("FAILED...", PLAYER_X, 250, Color.RED)); 
            }
            else if ((bgmClip == null || !bgmClip.isRunning()) && mapData.isEmpty() && allNotesGone && currentMusicTime > 5000) {
                 isEnding = true; endTimer = 0; effects.add(new HitEffect("FINISH!", PLAYER_X, 250, Color.GREEN));
            }
        }
        if (isEnding) { endTimer++; if (endTimer > END_DELAY) { currentState = GameState.GAME_OVER; stopBGM(); } }
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g); Graphics2D g2d = (Graphics2D) g;
        
        g2d.setColor(Color.BLACK); g2d.fillRect(0, 0, getWidth(), getHeight());
        double scale = Math.min((double)getWidth()/GAME_WIDTH, (double)getHeight()/GAME_HEIGHT);
        g2d.translate((getWidth()-GAME_WIDTH*scale)/2, (getHeight()-GAME_HEIGHT*scale)/2);
        g2d.scale(scale, scale); g2d.setClip(0, 0, GAME_WIDTH, GAME_HEIGHT);
        
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (currentState == GameState.PLAYING) {
            drawGameBackground(g2d);
        } else if (currentState == GameState.OFFSET_WIZARD) {
            g2d.setColor(Color.DARK_GRAY); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
            g2d.setColor(new Color(255, 255, 255, 30));
            g2d.setStroke(STROKE_THICK);
            g2d.drawLine(GAME_WIDTH/2, 0, GAME_WIDTH/2, GAME_HEIGHT);
        } else {
            GradientPaint gp = new GradientPaint(0, 0, new Color(10, 0, 20), 0, GAME_HEIGHT, new Color(30, 0, 40));
            g2d.setPaint(gp); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        }

        switch (currentState) {
            case TITLE_SCREEN: drawTitleScreen(g2d); break;
            case SONG_SELECT: drawSongSelect(g2d); break;
            case DIFFICULTY_SELECT: drawDifficultySelect(g2d); break;
            case OFFSET_WIZARD: drawOffsetWizard(g2d); break;
            case PLAYING: 
                drawTrack(g2d); 
                drawPlayer(g2d); 
                drawGameObjects(g2d); 
                drawEffects(g2d); 
                drawHUD(g2d); 
                drawInputDebug(g2d); 
                break;
            case GAME_OVER: drawGameObjects(g2d); drawGameOverScreen(g2d); break;
        }
    }
    
    private void drawGameBackground(Graphics2D g2d) {
        if (imgGameBG != null) {
            g2d.drawImage(imgGameBG, (int)bgScrollX, 0, GAME_WIDTH, GAME_HEIGHT, null);
            g2d.drawImage(imgGameBG, (int)bgScrollX + GAME_WIDTH, 0, GAME_WIDTH, GAME_HEIGHT, null);
            g2d.setColor(new Color(0, 0, 0, 100)); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        } else {
            GradientPaint gp = new GradientPaint(0, 0, new Color(20, 20, 40), 0, GAME_HEIGHT, new Color(40, 30, 60));
            g2d.setPaint(gp); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
            g2d.setColor(COL_TRACK_BG);
            for (int i = 0; i < 10; i++) {
                int lineX = (i * 150 + (int)bgScrollX) % GAME_WIDTH;
                if (lineX < 0) lineX += GAME_WIDTH;
                g2d.fillRect(lineX, 0, 5, GAME_HEIGHT);
            }
        }
    }
    
    private void drawPlayer(Graphics2D g2d) {
        if (imgPlayer != null) {
            g2d.drawImage(imgPlayer, PLAYER_X, playerY, TARGET_SIZE, TARGET_SIZE, null);
        } else {
            g2d.setColor(isFeverMode ? Color.CYAN : Color.PINK);
            g2d.fillOval(PLAYER_X, playerY, TARGET_SIZE, TARGET_SIZE);
            g2d.setColor(Color.WHITE);
            g2d.setStroke(STROKE_THIN);
            g2d.drawOval(PLAYER_X, playerY, TARGET_SIZE, TARGET_SIZE);
        }
    }
    
    private void drawTitleScreen(Graphics2D g2d) {
        g2d.setColor(Color.WHITE); g2d.setFont(FONT_TITLE);
        drawShadowText(g2d, "MUSE DASH LITE", 60, 150, Color.CYAN);
        g2d.setFont(FONT_MENU);
        String[] options = { "START GAME", "OFFSET: " + audioOffset + " ms", "VOLUME: " + masterVolume + "%", "NO FAIL: " + (isNoFailMode ? "ON" : "OFF"), "AUTO OFFSET (WIZARD)", "EXIT" };
        int startY = 220;
        for (int i = 0; i < options.length; i++) {
            if (i == titleSelection) {
                g2d.setColor(Color.YELLOW); g2d.setFont(new Font(fontName, Font.BOLD, 30));
                g2d.drawString("> " + options[i] + " <", (GAME_WIDTH - g2d.getFontMetrics().stringWidth("> " + options[i] + " <"))/2, startY + i * 40);
            } else {
                g2d.setColor(Color.GRAY); g2d.setFont(new Font(fontName, Font.PLAIN, 24));
                g2d.drawString(options[i], (GAME_WIDTH - g2d.getFontMetrics().stringWidth(options[i]))/2, startY + i * 40);
            }
        }
    }
    
    private void drawOffsetWizard(Graphics2D g2d) {
        g2d.setColor(Color.WHITE); g2d.setFont(new Font(fontName, Font.BOLD, 40));
        String title = "AUTO CALIBRATION";
        g2d.drawString(title, (GAME_WIDTH - g2d.getFontMetrics().stringWidth(title))/2, 100);
        g2d.setFont(new Font(fontName, Font.PLAIN, 20));
        String sub = "Tap any key to the beat! (" + calibrationBeatCount + "/" + TOTAL_CALIBRATION_BEATS + ")";
        g2d.drawString(sub, (GAME_WIDTH - g2d.getFontMetrics().stringWidth(sub))/2, 150);
        int r = (int)(100 * pulseScale);
        int alpha = (int)(255 * (pulseScale - 1.0) * 3);
        if (alpha > 255) alpha = 255; if (alpha < 0) alpha = 0;
        g2d.setColor(new Color(255, 255, 0, alpha)); g2d.fillOval(GAME_WIDTH/2 - r/2, GAME_HEIGHT/2 - r/2, r, r);
        g2d.setColor(Color.WHITE); g2d.drawOval(GAME_WIDTH/2 - 50, GAME_HEIGHT/2 - 50, 100, 100);
        drawEffects(g2d);
    }
    
    // 🔥🔥 修改：難度顯示美化 (讀取真實 JSON 數據)
    private void drawDifficultySelect(Graphics2D g2d) {
        SongInfo song = songList.get(songSelection);
        if (song.coverImage != null) {
            g2d.drawImage(song.coverImage, 0, 0, GAME_WIDTH, GAME_HEIGHT, null);
            g2d.setColor(new Color(0, 0, 0, 200)); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        }
        drawShadowText(g2d, "SELECT DIFFICULTY", 40, 100, Color.WHITE);
        
        int startY = 180; int lineHeight = 60; // 稍微拉開距離
        
        if (song.maps.isEmpty()) {
             drawShadowText(g2d, "No Maps Found", 30, 250, Color.RED);
             return;
        }

        for (int i = 0; i < song.maps.size(); i++) {
            MapDetail map = song.maps.get(i);
            
            // 組合顯示字串： "Easy Lv.4" 或 "Hard Lv.9"
            String displayText = map.difficultyLabel + "  Lv." + map.levelVal;
            
            if (i == diffSelection) {
                // 選中時：字體變大，使用對應的難度顏色
                g2d.setColor(map.color); 
                g2d.setFont(new Font(fontName, Font.BOLD, 40));
                g2d.drawString("> " + displayText + " <", (GAME_WIDTH - g2d.getFontMetrics().stringWidth("> " + displayText + " <"))/2, startY + i * lineHeight);
            } else {
                // 未選中：灰色
                g2d.setColor(Color.DARK_GRAY); 
                g2d.setFont(new Font(fontName, Font.PLAIN, 28));
                g2d.drawString(displayText, (GAME_WIDTH - g2d.getFontMetrics().stringWidth(displayText))/2, startY + i * lineHeight);
            }
        }
    }
    
    private void drawTrack(Graphics2D g2d) {
        g2d.setColor(COL_TRACK_BG);
        g2d.fillRect(0, AIR_Y - TARGET_SIZE/2 - 10, GAME_WIDTH, TARGET_SIZE + 20); 
        g2d.fillRect(0, GROUND_Y - TARGET_SIZE/2 - 10, GAME_WIDTH, TARGET_SIZE + 20); 
        g2d.setStroke(STROKE_THIN);
        g2d.setColor(COL_TRACK_BORDER);
        g2d.drawOval(PLAYER_X, AIR_Y, TARGET_SIZE, TARGET_SIZE);
        g2d.drawOval(PLAYER_X, GROUND_Y, TARGET_SIZE, TARGET_SIZE);
    }

    private void drawInputDebug(Graphics2D g2d) {
        int x = 20, y = 100; int size = 30;
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.setColor(keyD ? Color.WHITE : COL_TRACK_BORDER); g2d.fillRoundRect(x, y, size, size, 5, 5); g2d.setColor(Color.WHITE); g2d.drawString("D", x+10, y+20);
        g2d.setColor(keyF ? Color.WHITE : COL_TRACK_BORDER); g2d.fillRoundRect(x+35, y, size, size, 5, 5); g2d.setColor(Color.WHITE); g2d.drawString("F", x+45, y+20);
        g2d.setColor(keyJ ? Color.WHITE : COL_TRACK_BORDER); g2d.fillRoundRect(x+80, y, size, size, 5, 5); g2d.setColor(Color.WHITE); g2d.drawString("J", x+90, y+20);
        g2d.setColor(keyK ? Color.WHITE : COL_TRACK_BORDER); g2d.fillRoundRect(x+115, y, size, size, 5, 5); g2d.setColor(Color.BLACK); g2d.drawString("K", x+125, y+20);
    }
    
    private void drawSongSelect(Graphics2D g2d) {
        if (songList.isEmpty()) return;
        SongInfo song = songList.get(songSelection);
        if (song.coverImage != null) {
            g2d.drawImage(song.coverImage, 0, 0, GAME_WIDTH, GAME_HEIGHT, null);
            g2d.setColor(new Color(0, 0, 0, 180)); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        }
        drawCover(g2d, song, 40, 180); 
        drawShadowText(g2d, song.title, 36, 280, Color.WHITE);
        drawShadowText(g2d, "Artist: " + song.artist + "  |  BPM: " + song.bpm, 20, 320, Color.CYAN);
        g2d.setColor(Color.LIGHT_GRAY); g2d.setFont(new Font(fontName, Font.PLAIN, 16));
        g2d.drawString("< Left/Right > Select Song  |  [ENTER] Confirm  |  [ESC] Back", (GAME_WIDTH - 420)/2, 410);
    }
    
    private void drawCover(Graphics2D g2d, SongInfo song, int y, int size) {
        int x = (GAME_WIDTH - size) / 2;
        if (song.coverImage != null) { 
            g2d.setColor(Color.WHITE); g2d.setStroke(STROKE_THIN); g2d.drawRoundRect(x - 3, y - 3, size + 6, size + 6, 10, 10);
            g2d.drawImage(song.coverImage, x, y, size, size, null); 
        } 
        else { g2d.setColor(Color.DARK_GRAY); g2d.fillRoundRect(x, y, size, size, 10, 10); g2d.setColor(Color.WHITE); g2d.drawString("No Cover", x + 60, y + 100); }
    }
    
    private void drawShadowText(Graphics2D g2d, String text, int size, int y, Color color) {
        g2d.setFont(new Font(fontName, Font.BOLD, size));
        int x = (GAME_WIDTH - g2d.getFontMetrics().stringWidth(text))/2;
        g2d.setColor(COL_SHADOW); g2d.drawString(text, x+2, y+2); 
        g2d.setColor(color); g2d.drawString(text, x, y);
    }

    private void drawGameObjects(Graphics2D g2d) {
        for (Note note : notes) {
            if (!note.isActive) continue;
            BufferedImage sprite = (note.track == Track.GROUND) ? imgNoteGround : imgNoteAir;
            Color baseColor = (note.track == Track.GROUND) ? COL_GND_PRESS : COL_AIR_PRESS;
            if (note.duration > 0) {
                if (note.hasHitHead) {
                    g2d.setColor(baseColor); g2d.fillRoundRect(note.x, note.y + 10, note.width, TARGET_SIZE - 20, 10, 10);
                    g2d.setColor(Color.WHITE); g2d.drawLine(note.x, note.y + TARGET_SIZE/2, note.x + note.width, note.y + TARGET_SIZE/2);
                    g2d.setColor(new Color(255, 255, 255, 150)); g2d.setStroke(STROKE_THIN); g2d.drawOval(PLAYER_X - 10, note.y - 10, TARGET_SIZE + 20, TARGET_SIZE + 20);
                } else {
                    g2d.setColor(baseColor.darker()); g2d.fillRoundRect(note.x, note.y + 10, note.width, TARGET_SIZE - 20, 10, 10);
                    g2d.setColor(Color.LIGHT_GRAY); g2d.drawLine(note.x, note.y + TARGET_SIZE/2, note.x + note.width, note.y + TARGET_SIZE/2);
                }
            }
            if (note.duration > 0 && note.hasHitHead) continue;
            if (sprite != null) {
                g2d.drawImage(sprite, note.x, note.y, TARGET_SIZE, TARGET_SIZE, null);
            } else {
                g2d.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 100));
                g2d.fillOval(note.x - 5, note.y - 5, TARGET_SIZE + 10, TARGET_SIZE + 10);
                g2d.setColor(baseColor); g2d.fillOval(note.x, note.y, TARGET_SIZE, TARGET_SIZE);
                g2d.setColor(Color.WHITE); g2d.setStroke(STROKE_NORMAL); g2d.drawOval(note.x + 10, note.y + 10, TARGET_SIZE - 20, TARGET_SIZE - 20);
            }
        }
    }
    
    private void drawEffects(Graphics2D g2d) {
        for (HitEffect effect : effects) { 
            g2d.setColor(effect.getCurrentColor()); 
            g2d.setFont(FONT_EFFECT); 
            // 🔥 強制將字體畫在 PLAYER_X 上方，確保不消失且位置正確
            g2d.drawString(effect.text, PLAYER_X, effect.y); 
        }
    }
    
    private void drawHUD(Graphics2D g2d) {
        g2d.setColor(new Color(50, 50, 50)); g2d.fillRoundRect(20, 20, 200, 15, 15, 15);
        if (hp > 0) {
            GradientPaint hpPaint = new GradientPaint(20, 20, new Color(255, 100, 100), 220, 20, new Color(50, 255, 50));
            g2d.setPaint(hpPaint); g2d.fillRoundRect(20, 20, (int)(200 * (hp / (double)MAX_HP)), 15, 15, 15);
        }
        g2d.setColor(Color.WHITE); g2d.setStroke(STROKE_NORMAL); g2d.drawRoundRect(20, 20, 200, 15, 15, 15);

        int feverBarY = 45;
        g2d.setColor(new Color(0, 0, 50)); g2d.fillRoundRect(20, feverBarY, 200, 10, 10, 10);
        if (fever > 0) {
            Color feverColor = isFeverMode ? Color.CYAN : new Color(255, 0, 255);
            g2d.setColor(feverColor); g2d.fillRoundRect(20, feverBarY, (int)(200 * (fever / (double)MAX_FEVER)), 10, 10, 10);
        }
        
        g2d.setColor(Color.WHITE); g2d.setFont(FONT_SCORE); g2d.drawString("Score: " + score, 20, 85);
        
        if (isNoFailMode) {
            g2d.setColor(new Color(200, 100, 255)); g2d.setFont(new Font(fontName, Font.BOLD, 16));
            g2d.drawString("[ NO FAIL MODE ]", 20, 110);
        }
        
        if (combo > 1) { 
            AffineTransform old = g2d.getTransform(); g2d.translate(350, 150); g2d.scale(pulseScale, pulseScale); 
            String comboText = combo + " COMBO"; g2d.setFont(FONT_COMBO); 
            int w = g2d.getFontMetrics().stringWidth(comboText);
            g2d.setColor(new Color(0, 0, 0, 100)); g2d.drawString(comboText, -w/2+3, 3);
            g2d.setColor(isFeverMode ? Color.CYAN : Color.YELLOW); g2d.drawString(comboText, -w/2, 0);
            g2d.setTransform(old);
        }
    }
    
    private void drawGameOverScreen(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 200)); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        int boxX = 200, boxY = 75, boxW = 400, boxH = 300;
        g2d.setColor(Color.WHITE); g2d.setStroke(new BasicStroke(5)); g2d.drawRoundRect(boxX, boxY, boxW, boxH, 30, 30);
        g2d.setColor(new Color(50, 50, 60)); g2d.fillRoundRect(boxX, boxY, boxW, boxH, 30, 30);
        g2d.setColor(hp <= 0 ? Color.RED : Color.CYAN); g2d.setFont(new Font(fontName, Font.BOLD, 40));
        String title = hp <= 0 ? "GAME OVER" : "STAGE CLEAR"; g2d.drawString(title, boxX + (boxW - g2d.getFontMetrics().stringWidth(title))/2, boxY + 60);
        g2d.setColor(Color.WHITE); g2d.setFont(new Font(fontName, Font.PLAIN, 24));
        g2d.drawString("Final Score: " + score, boxX + 50, boxY + 120); g2d.drawString("Max Combo: " + maxCombo, boxX + 50, boxY + 160);
        g2d.setColor(Color.LIGHT_GRAY); g2d.setFont(new Font(fontName, Font.PLAIN, 16)); g2d.drawString("Press [ENTER] to Back to Menu", boxX + 100, boxY + 250);
    }

    @Override public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        
        if (currentState == GameState.OFFSET_WIZARD) {
            recordCalibrationHit();
            return;
        }
        
        if (currentState == GameState.TITLE_SCREEN) {
            if (key == KeyEvent.VK_DOWN) { titleSelection++; if(titleSelection > 5) titleSelection = 0; }
            else if (key == KeyEvent.VK_UP) { titleSelection--; if(titleSelection < 0) titleSelection = 5; }
            else if (key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_LEFT) {
                int change = (key == KeyEvent.VK_RIGHT) ? 1 : -1;
                if (titleSelection == 1) audioOffset += change * 5; 
                else if (titleSelection == 2) { 
                    masterVolume += change * 5; 
                    if (masterVolume > 100) masterVolume = 100; if (masterVolume < 0) masterVolume = 0;
                    playHitSound(); 
                }
                else if (titleSelection == 3) isNoFailMode = !isNoFailMode; 
            }
            else if (key == KeyEvent.VK_ENTER) {
                if (titleSelection == 0) {
                    currentState = GameState.SONG_SELECT; 
                    if (!songList.isEmpty() && songList.get(0).musicFile.exists()) {
                        playBGM(songList.get(0).musicFile); 
                    }
                }
                else if (titleSelection == 3) isNoFailMode = !isNoFailMode; 
                else if (titleSelection == 4) startAutoCalibration(); 
                else if (titleSelection == 5) System.exit(0); 
            }
            return;
        }

        if (currentState == GameState.SONG_SELECT) {
            if (key == KeyEvent.VK_RIGHT) { 
                songSelection++; if (songSelection >= songList.size()) songSelection = 0; 
                playBGM(songList.get(songSelection).musicFile);
            }
            else if (key == KeyEvent.VK_LEFT) { 
                songSelection--; if (songSelection < 0) songSelection = songList.size() - 1; 
                playBGM(songList.get(songSelection).musicFile);
            }
            else if (key == KeyEvent.VK_ENTER) { diffSelection = 0; currentState = GameState.DIFFICULTY_SELECT; }
            else if (key == KeyEvent.VK_ESCAPE) { currentState = GameState.TITLE_SCREEN; stopBGM(); } 
            return;
        }
        
        if (currentState == GameState.DIFFICULTY_SELECT) {
            SongInfo song = songList.get(songSelection);
            if (key == KeyEvent.VK_DOWN) { diffSelection++; if (diffSelection >= song.maps.size()) diffSelection = 0; }
            else if (key == KeyEvent.VK_UP) { diffSelection--; if (diffSelection < 0) diffSelection = song.maps.size() - 1; }
            // 🔥 修改：從 song.maps 中取得 MapDetail 並傳入
            else if (key == KeyEvent.VK_ENTER) { 
                if (!song.maps.isEmpty()) {
                    startGame(song, song.maps.get(diffSelection)); 
                }
            }
            else if (key == KeyEvent.VK_ESCAPE) { currentState = GameState.SONG_SELECT; }
            return;
        }
        
        if (currentState == GameState.GAME_OVER) {
            if (key == KeyEvent.VK_ENTER) { currentState = GameState.SONG_SELECT; playBGM(songList.get(songSelection).musicFile); }
            return;
        }
        
        if (currentState == GameState.PLAYING && !isEnding) {
            if (key == KeyEvent.VK_ESCAPE) { currentState = GameState.SONG_SELECT; stopBGM(); playBGM(songList.get(songSelection).musicFile); } 
            
            if (key == KeyEvent.VK_F) keyF = true; if (key == KeyEvent.VK_D) keyD = true;
            if (key == KeyEvent.VK_J) keyJ = true; if (key == KeyEvent.VK_K) keyK = true;
            
            if (key == KeyEvent.VK_F || key == KeyEvent.VK_D) {
                playerY = GROUND_Y; // 瞬移到地面
                checkHit(Track.GROUND);
            }
            if (key == KeyEvent.VK_J || key == KeyEvent.VK_K) {
                playerY = AIR_Y; // 瞬移到空中
                checkHit(Track.AIR);
            }
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_F) keyF = false; if (key == KeyEvent.VK_D) keyD = false;
        if (key == KeyEvent.VK_J) keyJ = false; if (key == KeyEvent.VK_K) keyK = false;
    }
    @Override public void keyTyped(KeyEvent e) {}
    
    // 🔥 回歸 v8.0 判定 (AOE + 黑洞)
    private void checkHit(Track track) {
        boolean hasHitAny = false;
        for (Note note : notes) {
            if (note.track == track && note.isActive && !note.isMissed && !note.hasHitHead) {
                int distance = Math.abs((note.x + TARGET_SIZE/2) - (PLAYER_X + TARGET_SIZE/2));
                
                if (distance <= HIT_RANGE) {
                    hasHitAny = true; 
                    int scoreMultiplier = isFeverMode ? 2 : 1; 
                    
                    if (note.duration > 0) {
                        note.hasHitHead = true; note.graceFrames = 15; score += 50; 
                        effects.add(new HitEffect("HOLD!", note.x, note.y, Color.GREEN));
                        cleanUpStackedNotes(note);
                    } else {
                        note.isActive = false;
                        if (distance <= PERFECT_RANGE) { 
                            score += 100 * scoreMultiplier; combo++; addFever(FEVER_GAIN); 
                            effects.add(new HitEffect(isFeverMode ? "FEVER!!" : "PERFECT", note.x, note.y, Color.YELLOW)); pulseScale = 1.2f; 
                        } else { 
                            score += 50 * scoreMultiplier; combo++; addFever(FEVER_GAIN/2); 
                            effects.add(new HitEffect("GREAT", note.x, note.y, Color.CYAN)); pulseScale = 1.1f; 
                        }
                    }
                    if (combo > maxCombo) maxCombo = combo;
                }
                // 保留 BAD 判定
                else if (distance <= BAD_RANGE) {
                    note.isActive = false; triggerMiss(note, "BAD"); 
                }
            }
        }
        if (hasHitAny) playHitSound();
    }
    
    private void cleanUpStackedNotes(Note longNote) {
        long startTime = longNote.appearTime; long endTime = longNote.endTime; long safeZone = 30; 
        for (Note other : notes) {
            if (other != longNote && other.track == longNote.track && other.isActive) {
                boolean overlappingStart = (Math.abs(other.appearTime - startTime) <= 10);
                boolean insideBody = (other.appearTime > startTime && other.appearTime < (endTime - safeZone));
                if (overlappingStart || insideBody) other.isActive = false; 
            }
        }
    }
    
    private void addFever(int amount) { if (isFeverMode) return; fever += amount; if (fever >= MAX_FEVER) { fever = MAX_FEVER; isFeverMode = true; effects.add(new HitEffect("FEVER START!", PLAYER_X, playerY - 80, Color.MAGENTA)); } }
    private void triggerMiss(Note note, String text) { note.isMissed = true; combo = 0; hp -= 15; effects.add(new HitEffect(text, PLAYER_X, note.y, Color.RED)); }
    public static void main(String[] args) {
        JFrame frame = new JFrame("Muse Dash Lite v18.0 - UI Update & Fix");
        MuseDashLite game = new MuseDashLite();
        frame.add(game); frame.pack(); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true); frame.setMinimumSize(new Dimension(400, 225));
        frame.setLocationRelativeTo(null); frame.setVisible(true);
    }
}