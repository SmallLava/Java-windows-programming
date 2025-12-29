import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*; 
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
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
    
    // 判定與顯示
    private final int PLAYER_X = 150;
    private final int GROUND_Y = 300;
    private final int AIR_Y = 200;
    private final int TARGET_SIZE = 60; 
    private final int HIT_RANGE = 60;      
    private final int BAD_RANGE = 110;     
    private final int PERFECT_RANGE = 35;
    
    private String fontName = "SansSerif"; 
    private final double NOTE_SPEED = 0.7; 
    
    // --- 視覺素材 ---
    private BufferedImage imgPlayer;
    private BufferedImage imgNoteGround;
    private BufferedImage imgNoteAir;
    private BufferedImage imgGameBG;
    
    // 動畫變數
    private float pulseScale = 1.0f; 
    private float bgScrollX = 0; 
    private final float BG_SPEED = 2.0f; // 固定背景速度

    // --- 2. 資料結構 ---
    class SongInfo {
        String title = "Unknown"; String artist = "Unknown"; double bpm = 120.0;
        BufferedImage coverImage; File folder; File musicFile; ArrayList<File> maps = new ArrayList<>();
        public SongInfo(File folder) { this.folder = folder; }
    }
    
    enum GameState { SONG_SELECT, DIFFICULTY_SELECT, PLAYING, GAME_OVER }
    private GameState currentState = GameState.SONG_SELECT;
    
    private ArrayList<SongInfo> songList; 
    private int songSelection = 0;       
    private int diffSelection = 0;       
    
    private boolean isRunning = true;
    private Thread gameThread;
    
    private ArrayList<Note> notes;      
    private ArrayList<NoteData> mapData; 
    private ArrayList<HitEffect> effects;
    
    // 按鍵狀態
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

    public MuseDashLite() {
        this.setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));
        this.setBackground(Color.BLACK);
        this.setFocusable(true);
        this.addKeyListener(this);
        
        this.notes = new ArrayList<>();
        this.mapData = new ArrayList<>();
        this.effects = new ArrayList<>();
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
        // 嘗試讀取背景圖 (如果讀不到會用程式碼畫預設背景)
        try { 
            File bg = new File("bg_game.jpg");
            if(bg.exists()) imgGameBG = ImageIO.read(bg);
            else {
                // 嘗試讀取可能是副檔名被隱藏的情況
                bg = new File("bg_game.jpg.jpg");
                if(bg.exists()) imgGameBG = ImageIO.read(bg);
            }
        } catch (Exception e) {}
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
    
    private SongInfo parseSongFolder(File folder) {
        SongInfo song = new SongInfo(folder);
        File jsonFile = new File(folder, "info.json");
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
                if (!bpmStr.equals("Unknown")) song.bpm = Double.parseDouble(bpmStr);
            } catch (Exception e) {}
        }
        song.musicFile = new File(folder, "music.wav");
        if (!song.musicFile.exists()) return null;
        try { File img = new File(folder, "cover.png"); if (img.exists()) song.coverImage = ImageIO.read(img); } catch (Exception e) {}
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".bms"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File f : files) song.maps.add(f);
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
        String text; int x, y; int life = 30; Color color;
        public HitEffect(String text, int x, int y, Color color) { this.text = text; this.x = x; this.y = y; this.color = color; }
        public boolean update() { y -= 1; life--; return life > 0; }
    }

    private void playBGM(File file) {
        stopBGM();
        try {
            if (file == null || !file.exists()) return;
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(file);
            bgmClip = AudioSystem.getClip();
            bgmClip.open(audioIn);
            try { FloatControl gainControl = (FloatControl) bgmClip.getControl(FloatControl.Type.MASTER_GAIN); gainControl.setValue(-10.0f); } catch (Exception e) {}
            bgmClip.start();
        } catch (Exception e) {}
    }
    private void stopBGM() { if (bgmClip != null) { bgmClip.stop(); bgmClip.close(); bgmClip = null; } }
    private void playHitSound() {
        new Thread(() -> {
            try {
                File soundFile = new File("hit.wav"); if (!soundFile.exists()) return;
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);
                Clip clip = AudioSystem.getClip(); clip.open(audioIn);
                try { FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN); gainControl.setValue(6.0f); } catch (Exception e) {}
                clip.start();
            } catch (Exception e) {}
        }).start();
    }
    private void selectBestRoundFont() {
        String[] candidates = { "Arial Rounded MT Bold", "Varela Round", "Comic Sans MS", "Microsoft YaHei", "SansSerif" };
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFonts = ge.getAvailableFontFamilyNames();
        for (String target : candidates) { for (String f : availableFonts) { if (f.equalsIgnoreCase(target)) { this.fontName = target; return; } } }
    }

    public void startGame(SongInfo song, File mapFile) {
        notes.clear(); mapData.clear(); effects.clear();
        score = 0; combo = 0; maxCombo = 0; hp = MAX_HP;
        fever = 0; isFeverMode = false; playerY = GROUND_Y;
        isEnding = false; endTimer = 0; currentMusicTime = 0;
        this.currentBpm = song.bpm;
        this.bgScrollX = 0;
        loadBMS(mapFile); 
        playBGM(song.musicFile);
        currentState = GameState.PLAYING;
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
                            } else { 
                                mapData.add(new NoteData(noteTime, 0, track)); 
                            }
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
            repaint();
            try { Thread.sleep(16); } catch (InterruptedException e) {}
        }
    }

    private void updateGame() {
        if (bgmClip != null && bgmClip.isRunning()) {
            currentMusicTime = (bgmClip.getMicrosecondPosition() / 1000) - audioOffset;
        }
        
        // 🔥 移除加速：現在無論有沒有 Fever，背景速度都是固定的
        bgScrollX -= BG_SPEED; 
        if (bgScrollX <= -GAME_WIDTH) {
            bgScrollX = 0; 
        }
        
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
                    if (currentMusicTime >= note.endTime) { note.isActive = false; effects.add(new HitEffect("PERFECT", PLAYER_X, note.y, Color.YELLOW)); combo++; pulseScale = 1.5f; }
                } else { 
                    note.isHolding = false; note.graceFrames--;
                    if (note.graceFrames <= 0) { note.isActive = false; triggerMiss(note, "MISS"); }
                }
            }
            
            if (!isEnding && !note.isMissed && !note.hasHitHead && note.x + note.width < PLAYER_X - BAD_RANGE) {
                boolean hasShield = (note.track == Track.GROUND) ? groundShield : airShield;
                if (hasShield) note.isActive = false; else triggerMiss(note, "MISS");
            }
        }
        
        if (isFeverMode) { fever -= FEVER_DRAIN; if (fever <= 0) { fever = 0; isFeverMode = false; } }
        Iterator<HitEffect> itEffect = effects.iterator();
        while (itEffect.hasNext()) { if (!itEffect.next().update()) itEffect.remove(); }
        if (!isEnding) {
            if (hp <= 0) { isEnding = true; endTimer = 0; effects.add(new HitEffect("FAILED...", PLAYER_X, playerY - 50, Color.RED)); }
            else if (bgmClip != null && !bgmClip.isRunning() && bgmClip.getMicrosecondPosition() > 0 && mapData.isEmpty() && allNotesGone) {
                 isEnding = true; endTimer = 0; effects.add(new HitEffect("FINISH!", PLAYER_X, playerY - 50, Color.GREEN));
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
            if (imgGameBG != null) {
                g2d.drawImage(imgGameBG, (int)bgScrollX, 0, GAME_WIDTH, GAME_HEIGHT, null);
                g2d.drawImage(imgGameBG, (int)bgScrollX + GAME_WIDTH, 0, GAME_WIDTH, GAME_HEIGHT, null);
                g2d.setColor(new Color(0, 0, 0, 100)); 
                g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
            } else {
                // 無圖片時的預設背景：向後飛的垂直光柱
                GradientPaint gp = new GradientPaint(0, 0, new Color(20, 20, 40), 0, GAME_HEIGHT, new Color(40, 30, 60));
                g2d.setPaint(gp); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
                g2d.setColor(new Color(255, 255, 255, 30));
                for (int i = 0; i < 10; i++) {
                    int lineX = (i * 150 + (int)bgScrollX) % GAME_WIDTH;
                    if (lineX < 0) lineX += GAME_WIDTH;
                    g2d.fillRect(lineX, 0, 5, GAME_HEIGHT);
                }
            }
        } else {
            g2d.setColor(Color.BLACK); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        }

        switch (currentState) {
            case SONG_SELECT: drawSongSelect(g2d); break;
            case DIFFICULTY_SELECT: drawDifficultySelect(g2d); break;
            case PLAYING: 
                drawTrack(g2d); 
                drawGameObjects(g2d); 
                drawHUD(g2d); 
                drawInputDebug(g2d); 
                break;
            case GAME_OVER: drawGameObjects(g2d); drawGameOverScreen(g2d); break;
        }
    }
    
    private void drawTrack(Graphics2D g2d) {
        g2d.setColor(new Color(255, 255, 255, 20));
        g2d.fillRect(0, AIR_Y - TARGET_SIZE/2 - 10, GAME_WIDTH, TARGET_SIZE + 20); 
        g2d.fillRect(0, GROUND_Y - TARGET_SIZE/2 - 10, GAME_WIDTH, TARGET_SIZE + 20); 
        g2d.setStroke(new BasicStroke(3));
        g2d.setColor(new Color(255, 255, 255, 50));
        g2d.drawOval(PLAYER_X, AIR_Y, TARGET_SIZE, TARGET_SIZE);
        g2d.drawOval(PLAYER_X, GROUND_Y, TARGET_SIZE, TARGET_SIZE);
    }

    private void drawInputDebug(Graphics2D g2d) {
        int x = 20, y = 100; int size = 30;
        g2d.setFont(new Font("Arial", Font.BOLD, 12));
        g2d.setColor(keyD ? Color.WHITE : new Color(255, 255, 255, 50)); g2d.fillRoundRect(x, y, size, size, 5, 5); g2d.setColor(Color.WHITE); g2d.drawString("D", x+10, y+20);
        g2d.setColor(keyF ? Color.WHITE : new Color(255, 255, 255, 50)); g2d.fillRoundRect(x+35, y, size, size, 5, 5); g2d.setColor(Color.WHITE); g2d.drawString("F", x+45, y+20);
        g2d.setColor(keyJ ? Color.WHITE : new Color(255, 255, 255, 50)); g2d.fillRoundRect(x+80, y, size, size, 5, 5); g2d.setColor(Color.BLACK); g2d.drawString("J", x+90, y+20);
        g2d.setColor(keyK ? Color.WHITE : new Color(255, 255, 255, 50)); g2d.fillRoundRect(x+115, y, size, size, 5, 5); g2d.setColor(Color.BLACK); g2d.drawString("K", x+125, y+20);
    }
    
    private void drawSongSelect(Graphics2D g2d) {
        if (songList.isEmpty()) return;
        SongInfo song = songList.get(songSelection);
        
        if (song.coverImage != null) {
            g2d.drawImage(song.coverImage, 0, 0, GAME_WIDTH, GAME_HEIGHT, null);
            g2d.setColor(new Color(0, 0, 0, 180)); 
            g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        } else {
            g2d.setColor(Color.DARK_GRAY); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        }

        drawCover(g2d, song, 40, 180); 
        
        drawShadowText(g2d, song.title, 36, 280, Color.WHITE);
        drawShadowText(g2d, "Artist: " + song.artist + "  |  BPM: " + song.bpm, 20, 320, Color.CYAN);
        drawShadowText(g2d, "Audio Offset: " + (audioOffset > 0 ? "+" : "") + audioOffset + " ms", 18, 360, Color.YELLOW);
        
        g2d.setColor(Color.LIGHT_GRAY); g2d.setFont(new Font(fontName, Font.PLAIN, 16));
        g2d.drawString("< Left/Right to Switch >   |   ^ Up/Down to Adjust Offset", (GAME_WIDTH - 400)/2, 410);
    }
    
    private void drawDifficultySelect(Graphics2D g2d) {
        SongInfo song = songList.get(songSelection);
        if (song.coverImage != null) {
            g2d.drawImage(song.coverImage, 0, 0, GAME_WIDTH, GAME_HEIGHT, null);
            g2d.setColor(new Color(0, 0, 0, 200)); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        } else { g2d.setColor(Color.BLACK); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT); }

        drawShadowText(g2d, "SELECT DIFFICULTY", 40, 100, Color.WHITE);
        int startY = 180; int lineHeight = 50;
        for (int i = 0; i < song.maps.size(); i++) {
            String mapName = song.maps.get(i).getName();
            if (i == diffSelection) {
                g2d.setColor(Color.YELLOW); g2d.setFont(new Font(fontName, Font.BOLD, 30));
                g2d.drawString("> " + mapName + " <", (GAME_WIDTH - g2d.getFontMetrics().stringWidth("> " + mapName + " <"))/2, startY + i * lineHeight);
            } else {
                g2d.setColor(Color.GRAY); g2d.setFont(new Font(fontName, Font.PLAIN, 24));
                g2d.drawString(mapName, (GAME_WIDTH - g2d.getFontMetrics().stringWidth(mapName))/2, startY + i * lineHeight);
            }
        }
    }
    
    private void drawCover(Graphics2D g2d, SongInfo song, int y, int size) {
        int x = (GAME_WIDTH - size) / 2;
        if (song.coverImage != null) { 
            g2d.setColor(Color.WHITE); g2d.setStroke(new BasicStroke(3)); g2d.drawRoundRect(x - 3, y - 3, size + 6, size + 6, 10, 10);
            g2d.drawImage(song.coverImage, x, y, size, size, null); 
        } 
        else { g2d.setColor(Color.DARK_GRAY); g2d.fillRoundRect(x, y, size, size, 10, 10); g2d.setColor(Color.WHITE); g2d.drawString("No Cover", x + 60, y + 100); }
    }
    
    private void drawShadowText(Graphics2D g2d, String text, int size, int y, Color color) {
        g2d.setFont(new Font(fontName, Font.BOLD, size));
        int x = (GAME_WIDTH - g2d.getFontMetrics().stringWidth(text))/2;
        g2d.setColor(new Color(0, 0, 0, 150)); g2d.drawString(text, x+2, y+2); 
        g2d.setColor(color); g2d.drawString(text, x, y);
    }

    private void drawGameObjects(Graphics2D g2d) {
        for (Note note : notes) {
            if (!note.isActive) continue;
            
            BufferedImage sprite = (note.track == Track.GROUND) ? imgNoteGround : imgNoteAir;
            Color baseColor = (note.track == Track.GROUND) ? new Color(255, 80, 80) : new Color(80, 80, 255);
            
            if (note.duration > 0) {
                if (note.hasHitHead) {
                    g2d.setColor(baseColor); g2d.fillRoundRect(note.x, note.y + 10, note.width, TARGET_SIZE - 20, 10, 10);
                    g2d.setColor(Color.WHITE); g2d.drawLine(note.x, note.y + TARGET_SIZE/2, note.x + note.width, note.y + TARGET_SIZE/2);
                    g2d.setColor(new Color(255, 255, 255, 150)); g2d.setStroke(new BasicStroke(3)); g2d.drawOval(PLAYER_X - 10, note.y - 10, TARGET_SIZE + 20, TARGET_SIZE + 20);
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
                g2d.setColor(Color.WHITE); g2d.setStroke(new BasicStroke(2)); g2d.drawOval(note.x + 10, note.y + 10, TARGET_SIZE - 20, TARGET_SIZE - 20);
            }
        }
        
        if (imgPlayer != null) {
            g2d.drawImage(imgPlayer, PLAYER_X, playerY, TARGET_SIZE, TARGET_SIZE, null);
        } else {
            g2d.setColor(isFeverMode ? Color.CYAN : Color.PINK); if (hp <= 0) g2d.setColor(Color.GRAY);
            g2d.fillOval(PLAYER_X, playerY, TARGET_SIZE, TARGET_SIZE);
            g2d.setColor(Color.WHITE); g2d.setStroke(new BasicStroke(3)); g2d.drawOval(PLAYER_X, playerY, TARGET_SIZE, TARGET_SIZE);
        }
        
        for (HitEffect effect : effects) { 
            g2d.setColor(effect.color); g2d.setFont(new Font(fontName, Font.BOLD, 22)); g2d.drawString(effect.text, effect.x, effect.y); 
        }
    }
    
    private void drawHUD(Graphics2D g2d) {
        g2d.setColor(new Color(50, 50, 50)); g2d.fillRoundRect(20, 20, 200, 15, 15, 15);
        if (hp > 0) {
            GradientPaint hpPaint = new GradientPaint(20, 20, new Color(255, 100, 100), 220, 20, new Color(50, 255, 50));
            g2d.setPaint(hpPaint); g2d.fillRoundRect(20, 20, (int)(200 * (hp / (double)MAX_HP)), 15, 15, 15);
        }
        g2d.setColor(Color.WHITE); g2d.setStroke(new BasicStroke(2)); g2d.drawRoundRect(20, 20, 200, 15, 15, 15);

        int feverBarY = 45;
        g2d.setColor(new Color(0, 0, 50)); g2d.fillRoundRect(20, feverBarY, 200, 10, 10, 10);
        if (fever > 0) {
            Color feverColor = isFeverMode ? Color.CYAN : new Color(255, 0, 255);
            g2d.setColor(feverColor); g2d.fillRoundRect(20, feverBarY, (int)(200 * (fever / (double)MAX_FEVER)), 10, 10, 10);
        }
        
        g2d.setColor(Color.WHITE); g2d.setFont(new Font(fontName, Font.BOLD, 22)); g2d.drawString("Score: " + score, 20, 85);
        
        if (combo > 1) { 
            AffineTransform old = g2d.getTransform(); g2d.translate(350, 150); g2d.scale(pulseScale, pulseScale); 
            String comboText = combo + " COMBO"; g2d.setFont(new Font(fontName, Font.BOLD | Font.ITALIC, 40)); 
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

    // --- 輸入控制 ---
    @Override public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (currentState == GameState.SONG_SELECT) {
            if (key == KeyEvent.VK_RIGHT) { songSelection++; if (songSelection >= songList.size()) songSelection = 0; }
            else if (key == KeyEvent.VK_LEFT) { songSelection--; if (songSelection < 0) songSelection = songList.size() - 1; }
            else if (key == KeyEvent.VK_UP) { audioOffset += 10; }
            else if (key == KeyEvent.VK_DOWN) { audioOffset -= 10; }
            else if (key == KeyEvent.VK_ENTER) { diffSelection = 0; currentState = GameState.DIFFICULTY_SELECT; }
            return;
        }
        if (currentState == GameState.DIFFICULTY_SELECT) {
            SongInfo song = songList.get(songSelection);
            if (key == KeyEvent.VK_DOWN) { diffSelection++; if (diffSelection >= song.maps.size()) diffSelection = 0; }
            else if (key == KeyEvent.VK_UP) { diffSelection--; if (diffSelection < 0) diffSelection = song.maps.size() - 1; }
            else if (key == KeyEvent.VK_ENTER) { startGame(song, song.maps.get(diffSelection)); }
            else if (key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_BACK_SPACE) { currentState = GameState.SONG_SELECT; }
            return;
        }
        if (currentState == GameState.GAME_OVER) {
            if (key == KeyEvent.VK_ENTER) { currentState = GameState.SONG_SELECT; stopBGM(); }
            return;
        }
        if (currentState == GameState.PLAYING && !isEnding) {
            if (key == KeyEvent.VK_F) keyF = true; if (key == KeyEvent.VK_D) keyD = true;
            if (key == KeyEvent.VK_J) keyJ = true; if (key == KeyEvent.VK_K) keyK = true;
            if (key == KeyEvent.VK_F || key == KeyEvent.VK_D) performAction(Track.GROUND);
            if (key == KeyEvent.VK_J || key == KeyEvent.VK_K) performAction(Track.AIR);
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_F) keyF = false; if (key == KeyEvent.VK_D) keyD = false;
        if (key == KeyEvent.VK_J) keyJ = false; if (key == KeyEvent.VK_K) keyK = false;
    }
    @Override public void keyTyped(KeyEvent e) {}
    
    private void performAction(Track track) { if (track == Track.GROUND) playerY = GROUND_Y; else playerY = AIR_Y; checkHit(track); }
    private void checkHit(Track track) {
        boolean hasHitAny = false;
        for (Note note : notes) {
            if (note.track == track && note.isActive && !note.isMissed && !note.hasHitHead) {
                int distance = Math.abs((note.x + TARGET_SIZE/2) - (PLAYER_X + TARGET_SIZE/2));
                if (distance <= HIT_RANGE) {
                    hasHitAny = true; int scoreMultiplier = isFeverMode ? 2 : 1; 
                    if (note.duration > 0) {
                        note.hasHitHead = true; note.graceFrames = 15; score += 50; 
                        effects.add(new HitEffect("HOLD!", note.x, note.y, Color.GREEN));
                        cleanUpStackedNotes(note);
                    } else {
                        note.isActive = false;
                        if (distance <= PERFECT_RANGE) { score += 100 * scoreMultiplier; combo++; addFever(FEVER_GAIN); effects.add(new HitEffect(isFeverMode ? "FEVER!!" : "PERFECT", note.x, note.y, Color.YELLOW)); pulseScale = 1.2f; } 
                        else { score += 50 * scoreMultiplier; combo++; addFever(FEVER_GAIN/2); effects.add(new HitEffect("GREAT", note.x, note.y, Color.CYAN)); pulseScale = 1.1f; }
                    }
                    if (combo > maxCombo) maxCombo = combo;
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
        JFrame frame = new JFrame("Muse Dash Lite v8.0 - Final Stable");
        MuseDashLite game = new MuseDashLite();
        frame.add(game); frame.pack(); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true); frame.setMinimumSize(new Dimension(400, 225));
        frame.setLocationRelativeTo(null); frame.setVisible(true);
    }
}