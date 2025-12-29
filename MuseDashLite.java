import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import javax.sound.sampled.*;
import javax.swing.*;

public class MuseDashLite extends JPanel implements KeyListener, Runnable {

    // --- 1. 參數設定 ---
    private final int GAME_WIDTH = 800;
    private final int GAME_HEIGHT = 450;
    
    private final int PLAYER_START_X = 150;
    private final int GROUND_Y = 300;
    private final int AIR_Y = 200;
    private final int TARGET_SIZE = 50;

    private final int HIT_RANGE = 60;      
    private final int BAD_RANGE = 110;     
    private final int PERFECT_RANGE = 25;  
    
    private String fontName = "SansSerif"; 

    // --- 譜面系統參數 ---
    private final int NOTE_SPEED = 6; // 音符移動速度
    private int gameTime = 0;         // 遊戲經過的時間 (幀數)

    enum GameState { MENU, PLAYING, GAME_OVER }
    private GameState currentState = GameState.MENU;
    
    private boolean isRunning = true;
    private Thread gameThread;
    
    private ArrayList<Note> notes;      // 畫面上的音符 (實體)
    private ArrayList<NoteData> mapData; // 等待生成的譜面數據 (資料)
    private ArrayList<HitEffect> effects;

    private int playerX = PLAYER_START_X;
    private int playerY = GROUND_Y;
    private int hp = 100;
    private final int MAX_HP = 100;
    private int score = 0;
    private int combo = 0;
    private int maxCombo = 0;

    private int fever = 0;
    private final int MAX_FEVER = 1000;
    private boolean isFeverMode = false;
    private final int FEVER_GAIN = 50;
    private final int FEVER_DRAIN = 3;

    private boolean isEnding = false;
    private int endTimer = 0;
    private final int END_DELAY = 180;

    private Clip bgmClip;

    public MuseDashLite() {
        this.setPreferredSize(new Dimension(GAME_WIDTH, GAME_HEIGHT));
        this.setBackground(Color.BLACK);
        this.setFocusable(true);
        this.addKeyListener(this);
        
        this.notes = new ArrayList<>();
        this.mapData = new ArrayList<>();
        this.effects = new ArrayList<>();
        
        selectBestRoundFont();
        
        gameThread = new Thread(this);
        gameThread.start();
    }
    
    // --- 核心修改：新增 NoteData 類別 (譜面資料結構) ---
    class NoteData {
        int appearTime; // 在第幾幀出現
        Track track;    // 在哪條軌道

        public NoteData(int time, Track track) {
            this.appearTime = time;
            this.track = track;
        }
    }

    enum Track { GROUND, AIR }

    class Note {
        int x, y;
        Track track;
        boolean isActive = true;
        boolean isMissed = false;
        public Note(int startX, Track track) {
            this.x = startX;
            this.track = track;
            this.y = (track == Track.GROUND) ? GROUND_Y : AIR_Y;
        }
    }

    class HitEffect {
        String text;
        int x, y; int life = 30; Color color;
        public HitEffect(String text, int x, int y, Color color) {
            this.text = text; this.x = x; this.y = y; this.color = color;
        }
        public boolean update() { y -= 1; life--; return life > 0; }
    }

    // --- 音效區 ---
    private void playBGM() {
        stopBGM();
        try {
            File soundFile = new File("bgm.wav");
            if (!soundFile.exists()) return;
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);
            bgmClip = AudioSystem.getClip();
            bgmClip.open(audioIn);
            try {
                FloatControl gainControl = (FloatControl) bgmClip.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(-10.0f); // BGM 小聲一點
            } catch (Exception e) {}
            bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
            bgmClip.start();
        } catch (Exception e) {}
    }

    private void stopBGM() {
        if (bgmClip != null) {
            if (bgmClip.isRunning()) bgmClip.stop();
            bgmClip.close();
            bgmClip = null;
        }
    }

    private void playHitSound() {
        new Thread(() -> {
            try {
                File soundFile = new File("hit.wav");
                if (!soundFile.exists()) return;
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);
                Clip clip = AudioSystem.getClip();
                clip.open(audioIn);
                try {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    gainControl.setValue(6.0f); // 打擊聲大聲一點
                } catch (Exception e) {}
                clip.start();
            } catch (Exception e) {}
        }).start();
    }

    private void selectBestRoundFont() {
        String[] candidates = { "Arial Rounded MT Bold", "Varela Round", "Comic Sans MS", "Microsoft YaHei", "SansSerif" };
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFonts = ge.getAvailableFontFamilyNames();
        for (String target : candidates) {
            for (String f : availableFonts) {
                if (f.equalsIgnoreCase(target)) { this.fontName = target; return; }
            }
        }
    }

    public void resetGame() {
        notes.clear();
        mapData.clear(); // 清空舊譜面
        effects.clear();
        score = 0; combo = 0; maxCombo = 0; hp = MAX_HP;
        fever = 0; isFeverMode = false;
        playerY = GROUND_Y; playerX = PLAYER_START_X;
        isEnding = false; endTimer = 0;
        
        gameTime = 0;    // 時間歸零
        loadLevelData(); // 載入這首歌的譜面
        
        playBGM();
        currentState = GameState.PLAYING;
    }

    // --- ⭐ 這裡就是你可以「設計關卡」的地方 ⭐ ---
    private void loadLevelData() {
        // 單位是 "Frame" (幀)
        // 假設每秒 60 幀 (60 FPS)
        // 1 秒 = 60
        // 2 秒 = 120
        
        // 測試範例：
        // 前面 2 秒留白給玩家準備
        
        // --- 第一段節奏 ---
        mapData.add(new NoteData(120, Track.GROUND)); // 2.0秒
        mapData.add(new NoteData(180, Track.GROUND)); // 3.0秒
        mapData.add(new NoteData(240, Track.GROUND)); // 4.0秒
        
        // --- 換到空中 ---
        mapData.add(new NoteData(300, Track.AIR));    // 5.0秒
        mapData.add(new NoteData(360, Track.AIR));    // 6.0秒
        
        // --- 快速連打 (間隔 30 = 0.5秒) ---
        mapData.add(new NoteData(420, Track.GROUND));
        mapData.add(new NoteData(450, Track.GROUND));
        mapData.add(new NoteData(480, Track.GROUND));
        
        // --- 雙軌並行 (縱連) ---
        mapData.add(new NoteData(540, Track.GROUND));
        mapData.add(new NoteData(540, Track.AIR));

        // 隨機生成一段，讓音樂跑久一點
        for (int i = 0; i < 30; i++) {
            int time = 600 + i * 40; // 越後面越快
            Track t = (i % 2 == 0) ? Track.GROUND : Track.AIR;
            mapData.add(new NoteData(time, t));
        }
    }

    @Override
    public void run() {
        while (isRunning) {
            if (currentState == GameState.PLAYING) {
                updateGame();
            }
            repaint();
            try { Thread.sleep(16); } catch (InterruptedException e) {}
        }
    }

    private void updateGame() {
        // 1. 推進時間
        if (!isEnding) {
            gameTime++;
            
            // 2. 檢查譜面：時間到了沒？
            Iterator<NoteData> it = mapData.iterator();
            while (it.hasNext()) {
                NoteData data = it.next();
                if (data.appearTime <= gameTime) {
                    // 時間到！生怪！
                    // 從 800 (右邊) 出生
                    notes.add(new Note(800, data.track));
                    it.remove(); // 移除這筆資料
                } else {
                    break; // 因為是照順序排的，如果這個還沒到，後面的肯定也沒到
                }
            }
        }

        // Fever 倒數
        if (isFeverMode) {
            fever -= FEVER_DRAIN;
            if (fever <= 0) { fever = 0; isFeverMode = false; }
        }

        boolean allNotesGone = true;
        for (Note note : notes) {
            if (!note.isActive) continue;
            if (note.x > -100) allNotesGone = false;

            note.x -= NOTE_SPEED; 

            if (!isEnding && !note.isMissed && note.x + TARGET_SIZE < playerX) {
                triggerMiss(note, "MISS");
            }
        }

        Iterator<HitEffect> itEffect = effects.iterator();
        while (itEffect.hasNext()) {
            if (!itEffect.next().update()) itEffect.remove();
        }

        // 結束條件：譜面沒了 且 畫面上的怪也沒了
        if (!isEnding) {
            if (hp <= 0) {
                isEnding = true; endTimer = 0;
                effects.add(new HitEffect("FAILED...", playerX, playerY - 50, Color.RED));
            } else if (mapData.isEmpty() && allNotesGone && notes.size() > 0) {
                isEnding = true; endTimer = 0;
                effects.add(new HitEffect("FINISH!", playerX, playerY - 50, Color.GREEN));
            }
        }

        if (isEnding) {
            endTimer++;
            if (hp > 0) playerX += 5; 
            if (endTimer > END_DELAY) {
                currentState = GameState.GAME_OVER;
                stopBGM();
            }
        }
    }

    // --- 以下繪圖與判定邏輯保持不變 ---
    private void performAction(Track track) {
        if (track == Track.GROUND) playerY = GROUND_Y; else playerY = AIR_Y;
        checkHit(track);
    }
    
    private void checkHit(Track track) {
        Note target = getClosestNote(track);
        if (target != null) {
            int distance = Math.abs((target.x + TARGET_SIZE/2) - (playerX + TARGET_SIZE/2));
            if (distance <= HIT_RANGE) {
                target.isActive = false;
                int scoreMultiplier = isFeverMode ? 2 : 1; 
                playHitSound(); // 播放音效
                if (distance <= PERFECT_RANGE) {
                    score += 100 * scoreMultiplier; combo++; addFever(FEVER_GAIN);
                    effects.add(new HitEffect(isFeverMode ? "FEVER!!" : "PERFECT", target.x, target.y, Color.YELLOW));
                } else {
                    score += 50 * scoreMultiplier; combo++; addFever(FEVER_GAIN/2);
                    effects.add(new HitEffect("GREAT", target.x, target.y, Color.CYAN));
                }
                if (combo > maxCombo) maxCombo = combo;
            } else if (distance <= BAD_RANGE) { 
                target.isActive = false; triggerMiss(target, "BAD");
            }
        }
    }
    private void addFever(int amount) {
        if (isFeverMode) return;
        fever += amount;
        if (fever >= MAX_FEVER) { fever = MAX_FEVER; isFeverMode = true; effects.add(new HitEffect("FEVER START!", playerX, playerY - 80, Color.MAGENTA)); }
    }
    private Note getClosestNote(Track track) {
        for (Note note : notes) {
            if (note.isActive && !note.isMissed && note.track == track) {
                if (note.x + TARGET_SIZE > playerX - HIT_RANGE) return note;
            }
        }
        return null;
    }
    private void triggerMiss(Note note, String text) {
        note.isMissed = true; combo = 0; hp -= 15; effects.add(new HitEffect(text, playerX, note.y, Color.RED));
    }
    
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g); Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(Color.BLACK); g2d.fillRect(0, 0, getWidth(), getHeight());
        double scale = Math.min((double)getWidth()/GAME_WIDTH, (double)getHeight()/GAME_HEIGHT);
        g2d.translate((getWidth()-GAME_WIDTH*scale)/2, (getHeight()-GAME_HEIGHT*scale)/2);
        g2d.scale(scale, scale); g2d.setClip(0, 0, GAME_WIDTH, GAME_HEIGHT);
        g2d.setColor(isFeverMode ? new Color(40, 40, 60) : new Color(30, 30, 40)); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawBackground(g2d);
        if (currentState == GameState.MENU) drawMenu(g2d);
        else if (currentState == GameState.PLAYING) { drawGameObjects(g2d); drawHUD(g2d); }
        else if (currentState == GameState.GAME_OVER) { drawGameObjects(g2d); drawGameOverScreen(g2d); }
    }
    
    private void drawBackground(Graphics2D g2d) {
        g2d.setColor(new Color(255, 255, 255, 30)); g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(0, GROUND_Y + TARGET_SIZE/2, GAME_WIDTH, GROUND_Y + TARGET_SIZE/2);
        g2d.drawLine(0, AIR_Y + TARGET_SIZE/2, GAME_WIDTH, AIR_Y + TARGET_SIZE/2);
        g2d.setStroke(new BasicStroke(3)); g2d.setColor(new Color(255, 255, 255, 50)); 
        g2d.drawOval(PLAYER_START_X, AIR_Y, TARGET_SIZE, TARGET_SIZE);
        g2d.drawOval(PLAYER_START_X, GROUND_Y, TARGET_SIZE, TARGET_SIZE);
        if (isFeverMode) { g2d.setColor(new Color(100, 255, 255, 150)); g2d.drawOval(PLAYER_START_X - 2, AIR_Y - 2, TARGET_SIZE + 4, TARGET_SIZE + 4); g2d.drawOval(PLAYER_START_X - 2, GROUND_Y - 2, TARGET_SIZE + 4, TARGET_SIZE + 4); }
        g2d.setStroke(new BasicStroke(1)); g2d.setColor(new Color(255, 255, 255, 50));
        int innerOffset = 10; int innerSize = TARGET_SIZE - 2 * innerOffset;
        g2d.drawOval(PLAYER_START_X + innerOffset, AIR_Y + innerOffset, innerSize, innerSize);
        g2d.drawOval(PLAYER_START_X + innerOffset, GROUND_Y + innerOffset, innerSize, innerSize);
    }
    private void drawGameObjects(Graphics2D g2d) {
        for (Note note : notes) {
            if (!note.isActive) continue;
            g2d.setColor(note.track == Track.GROUND ? new Color(255, 80, 80) : new Color(80, 80, 255));
            if (note.isMissed) g2d.setColor(Color.GRAY);
            g2d.fillRoundRect(note.x, note.y, TARGET_SIZE, TARGET_SIZE, 15, 15);
        }
        g2d.setColor(isFeverMode ? Color.CYAN : Color.PINK); if (hp <= 0) g2d.setColor(Color.GRAY);
        g2d.fillOval(playerX, playerY, TARGET_SIZE, TARGET_SIZE);
        g2d.setColor(Color.WHITE); g2d.setStroke(new BasicStroke(3)); g2d.drawOval(playerX, playerY, TARGET_SIZE, TARGET_SIZE);
        for (HitEffect effect : effects) {
            g2d.setColor(effect.color); g2d.setFont(new Font(fontName, Font.BOLD, 22)); g2d.drawString(effect.text, effect.x, effect.y);
        }
    }
    private void drawHUD(Graphics2D g2d) {
        g2d.setColor(Color.DARK_GRAY); g2d.fillRoundRect(20, 20, 200, 15, 5, 5);
        g2d.setColor(hp > 30 ? new Color(50, 255, 50) : Color.RED); g2d.fillRoundRect(20, 20, (int)(200 * (hp / (double)MAX_HP)), 15, 5, 5);
        int feverBarY = 40; g2d.setColor(new Color(0, 0, 50)); g2d.fillRoundRect(20, feverBarY, 200, 10, 5, 5);
        if (isFeverMode) g2d.setColor((System.currentTimeMillis() / 100 % 2 == 0) ? Color.CYAN : Color.WHITE); else g2d.setColor(Color.CYAN);
        g2d.fillRoundRect(20, feverBarY, (int)(200 * (fever / (double)MAX_FEVER)), 10, 5, 5);
        if (isFeverMode) { g2d.setFont(new Font(fontName, Font.BOLD | Font.ITALIC, 14)); g2d.setColor(Color.CYAN); g2d.drawString("FEVER!! x2", 230, feverBarY + 10); }
        g2d.setColor(Color.WHITE); g2d.setFont(new Font(fontName, Font.BOLD, 22)); g2d.drawString("Score: " + score, 20, 80);
        if (combo > 1) {
            String comboText = combo + " COMBO"; g2d.setFont(new Font(fontName, Font.BOLD | Font.ITALIC, 50));
            FontMetrics fm = g2d.getFontMetrics(); int textWidth = fm.stringWidth(comboText);
            int textX = (GAME_WIDTH - textWidth) / 2; int textY = 150; 
            g2d.setColor(new Color(0, 0, 0, 100)); g2d.drawString(comboText, textX + 3, textY + 3);
            g2d.setColor(Color.YELLOW); g2d.drawString(comboText, textX, textY);
        }
        if (isEnding && hp <= 0) {
            String deadText = "DEFEATED"; g2d.setColor(Color.RED); g2d.setFont(new Font(fontName, Font.BOLD, 40));
            int w = g2d.getFontMetrics().stringWidth(deadText); g2d.drawString(deadText, (GAME_WIDTH - w)/2, GAME_HEIGHT/2);
        }
    }
    private void drawMenu(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 150)); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        g2d.setColor(Color.WHITE); g2d.setFont(new Font(fontName, Font.BOLD | Font.ITALIC, 50));
        String title = "MUSE DASH LITE"; int w = g2d.getFontMetrics().stringWidth(title); g2d.drawString(title, (GAME_WIDTH - w)/2, 200);
        g2d.setFont(new Font(fontName, Font.PLAIN, 20)); String hint = "Controls: [D/F] Ground  |  [J/K] Air";
        w = g2d.getFontMetrics().stringWidth(hint); g2d.drawString(hint, (GAME_WIDTH - w)/2, 260);
        String start = "Press [ENTER] to Start"; w = g2d.getFontMetrics().stringWidth(start);
        g2d.setColor(Color.YELLOW); g2d.drawString(start, (GAME_WIDTH - w)/2, 300);
    }
    private void drawGameOverScreen(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 200)); g2d.fillRect(0, 0, GAME_WIDTH, GAME_HEIGHT);
        int boxW = 400, boxH = 300; int boxX = (GAME_WIDTH - boxW) / 2; int boxY = (GAME_HEIGHT - boxH) / 2;
        g2d.setColor(Color.WHITE); g2d.setStroke(new BasicStroke(5)); g2d.drawRoundRect(boxX, boxY, boxW, boxH, 30, 30);
        g2d.setColor(new Color(50, 50, 60)); g2d.fillRoundRect(boxX, boxY, boxW, boxH, 30, 30);
        g2d.setColor(hp <= 0 ? Color.RED : Color.CYAN); g2d.setFont(new Font(fontName, Font.BOLD, 40));
        String title = hp <= 0 ? "GAME OVER" : "STAGE CLEAR"; int w = g2d.getFontMetrics().stringWidth(title);
        g2d.drawString(title, boxX + (boxW - w)/2, boxY + 60);
        g2d.setColor(Color.WHITE); g2d.setFont(new Font(fontName, Font.PLAIN, 24));
        g2d.drawString("Final Score: " + score, boxX + 50, boxY + 120); g2d.drawString("Max Combo: " + maxCombo, boxX + 50, boxY + 160);
        String rank = "C"; if (score > 10000) rank = "S"; else if (score > 7000) rank = "A"; else if (score > 4000) rank = "B";
        g2d.setFont(new Font(fontName, Font.BOLD, 60)); g2d.setColor(Color.YELLOW); g2d.drawString(rank, boxX + 300, boxY + 150);
        g2d.setColor(Color.LIGHT_GRAY); g2d.setFont(new Font(fontName, Font.PLAIN, 16)); g2d.drawString("Press [ENTER] to Restart", boxX + 110, boxY + 250);
    }

    @Override public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (currentState == GameState.MENU || currentState == GameState.GAME_OVER) { if (key == KeyEvent.VK_ENTER) resetGame(); return; }
        if (currentState == GameState.PLAYING && !isEnding) {
            if (key == KeyEvent.VK_F || key == KeyEvent.VK_D) performAction(Track.GROUND);
            else if (key == KeyEvent.VK_J || key == KeyEvent.VK_K) performAction(Track.AIR);
        }
    }
    @Override public void keyReleased(KeyEvent e) {} @Override public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        JFrame frame = new JFrame("Muse Dash Lite v2.1 - Charting System");
        MuseDashLite game = new MuseDashLite();
        frame.add(game); frame.pack(); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true); frame.setMinimumSize(new Dimension(400, 225));
        frame.setLocationRelativeTo(null); frame.setVisible(true);
    }
}