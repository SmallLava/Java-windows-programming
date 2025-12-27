import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import javax.swing.*;

public class MuseDashLite extends JPanel implements KeyListener, Runnable {

    // --- 1. 參數設定 ---
    private final int WINDOW_WIDTH = 800;
    private final int WINDOW_HEIGHT = 450;
    private final int PLAYER_START_X = 150;
    private final int GROUND_Y = 300;
    private final int AIR_Y = 200;
    private final int TARGET_SIZE = 50;

    // 判定參數
    private final int HIT_RANGE = 80;
    private final int PERFECT_RANGE = 20;
    
    // --- 2. 遊戲狀態管理 ---
    enum GameState { MENU, PLAYING, GAME_OVER }
    private GameState currentState = GameState.MENU;
    
    private boolean isRunning = true;
    private Thread gameThread;
    
    private ArrayList<Note> notes;
    private ArrayList<HitEffect> effects;

    // 玩家數據
    private int playerX = PLAYER_START_X;
    private int playerY = GROUND_Y;
    private int hp = 100;
    private final int MAX_HP = 100;
    private int score = 0;
    private int combo = 0;
    private int maxCombo = 0;

    // 結束緩衝
    private boolean isEnding = false;
    private int endTimer = 0;
    private final int END_DELAY = 180;

    public MuseDashLite() {
        this.setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
        this.setBackground(new Color(30, 30, 40));
        this.setFocusable(true);
        this.addKeyListener(this);
        
        this.notes = new ArrayList<>();
        this.effects = new ArrayList<>();
        
        gameThread = new Thread(this);
        gameThread.start();
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
        int x, y;
        int life = 30;
        Color color;

        public HitEffect(String text, int x, int y, Color color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }
        public boolean update() {
            y -= 1; life--; return life > 0;
        }
    }

    public void resetGame() {
        notes.clear();
        effects.clear();
        score = 0;
        combo = 0;
        maxCombo = 0;
        hp = MAX_HP;
        playerY = GROUND_Y;
        playerX = PLAYER_START_X;
        isEnding = false;
        endTimer = 0;
        generateLevel();
        currentState = GameState.PLAYING;
    }

    private void generateLevel() {
        int startX = 800;
        for (int i = 0; i < 50; i++) {
            Track t = (Math.random() > 0.5) ? Track.GROUND : Track.AIR;
            int gap = 200 + (int)(Math.random() * 300);
            startX += gap;
            notes.add(new Note(startX, t));
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
        boolean allNotesGone = true;

        for (Note note : notes) {
            if (!note.isActive) continue;
            if (note.x > -100) allNotesGone = false;

            note.x -= 6; 

            if (!isEnding && !note.isMissed && note.x + TARGET_SIZE < playerX) {
                triggerMiss(note);
            }
        }

        Iterator<HitEffect> itEffect = effects.iterator();
        while (itEffect.hasNext()) {
            if (!itEffect.next().update()) itEffect.remove();
        }

        if (!isEnding) {
            if (hp <= 0) {
                isEnding = true;
                endTimer = 0;
                effects.add(new HitEffect("FAILED...", playerX, playerY - 50, Color.RED));
            } else if (allNotesGone && notes.size() > 0) {
                isEnding = true;
                endTimer = 0;
                effects.add(new HitEffect("FINISH!", playerX, playerY - 50, Color.GREEN));
            }
        }

        if (isEnding) {
            endTimer++;
            if (hp > 0) playerX += 5; 
            if (endTimer > END_DELAY) currentState = GameState.GAME_OVER;
        }
    }

    private void performAction(Track track) {
        if (track == Track.GROUND) playerY = GROUND_Y;
        else playerY = AIR_Y;
        checkHit(track);
    }

    private void checkHit(Track track) {
        Note target = getClosestNote(track);
        if (target != null) {
            int distance = Math.abs((target.x + TARGET_SIZE/2) - (playerX + TARGET_SIZE/2));
            if (distance <= HIT_RANGE) {
                target.isActive = false;
                if (distance <= PERFECT_RANGE) {
                    score += 100;
                    combo++;
                    hp = Math.min(hp + 2, MAX_HP);
                    effects.add(new HitEffect("PERFECT", target.x, target.y, Color.YELLOW));
                } else {
                    score += 50;
                    combo++;
                    effects.add(new HitEffect("GREAT", target.x, target.y, Color.CYAN));
                }
                if (combo > maxCombo) maxCombo = combo;
            }
        }
    }

    private Note getClosestNote(Track track) {
        for (Note note : notes) {
            if (note.isActive && !note.isMissed && note.track == track) {
                if (note.x + TARGET_SIZE > playerX - HIT_RANGE) return note;
            }
        }
        return null;
    }

    private void triggerMiss(Note note) {
        note.isMissed = true;
        combo = 0;
        hp -= 15;
        effects.add(new HitEffect("MISS", playerX, note.y, Color.RED));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBackground(g2d);

        if (currentState == GameState.MENU) {
            drawMenu(g2d);
        } else if (currentState == GameState.PLAYING) {
            drawGameObjects(g2d);
            drawHUD(g2d);
        } else if (currentState == GameState.GAME_OVER) {
            drawGameObjects(g2d);
            drawGameOverScreen(g2d);
        }
    }

    private void drawBackground(Graphics2D g2d) {
        g2d.setColor(new Color(255, 255, 255, 30));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(0, GROUND_Y + TARGET_SIZE/2, WINDOW_WIDTH, GROUND_Y + TARGET_SIZE/2);
        g2d.drawLine(0, AIR_Y + TARGET_SIZE/2, WINDOW_WIDTH, AIR_Y + TARGET_SIZE/2);
        
        g2d.setStroke(new BasicStroke(3));
        g2d.setColor(new Color(255, 255, 255, 50)); 
        g2d.drawOval(PLAYER_START_X, AIR_Y, TARGET_SIZE, TARGET_SIZE);
        g2d.drawOval(PLAYER_START_X, GROUND_Y, TARGET_SIZE, TARGET_SIZE);
        
        g2d.setStroke(new BasicStroke(1));
        int innerOffset = 10;
        int innerSize = TARGET_SIZE - 2 * innerOffset;
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

        g2d.setColor(Color.PINK);
        if (hp <= 0) g2d.setColor(Color.GRAY);
        g2d.fillOval(playerX, playerY, TARGET_SIZE, TARGET_SIZE);
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawOval(playerX, playerY, TARGET_SIZE, TARGET_SIZE);

        for (HitEffect effect : effects) {
            g2d.setColor(effect.color);
            g2d.setFont(new Font("Arial", Font.BOLD, 20));
            g2d.drawString(effect.text, effect.x, effect.y);
        }
    }

    private void drawHUD(Graphics2D g2d) {
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRoundRect(20, 20, 200, 20, 10, 10);
        g2d.setColor(hp > 30 ? new Color(50, 255, 50) : Color.RED);
        g2d.fillRoundRect(20, 20, (int)(200 * (hp / (double)MAX_HP)), 20, 10, 10);
        
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.drawString("Score: " + score, 20, 65);
        
        if (combo > 1) {
            String comboText = combo + " COMBO";
            Font comboFont = new Font("SansSerif", Font.BOLD | Font.ITALIC, 50);
            g2d.setFont(comboFont);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(comboText);
            int textX = (WINDOW_WIDTH - textWidth) / 2;
            int textY = 150; 
            
            g2d.setColor(new Color(0, 0, 0, 100));
            g2d.drawString(comboText, textX + 3, textY + 3);
            g2d.setColor(Color.YELLOW);
            g2d.drawString(comboText, textX, textY);
        }

        if (isEnding && hp <= 0) {
            String deadText = "DEFEATED";
            g2d.setColor(Color.RED);
            g2d.setFont(new Font("Arial", Font.BOLD, 40));
            int w = g2d.getFontMetrics().stringWidth(deadText);
            g2d.drawString(deadText, (WINDOW_WIDTH - w)/2, WINDOW_HEIGHT/2);
        }
    }

    private void drawMenu(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 40));
        String title = "MUSE DASH LITE";
        int w = g2d.getFontMetrics().stringWidth(title);
        g2d.drawString(title, (WINDOW_WIDTH - w)/2, 200);
        
        g2d.setFont(new Font("Arial", Font.PLAIN, 20));
        // 更新操作說明
        String hint = "Controls: [D/F] Ground  |  [J/K] Air";
        w = g2d.getFontMetrics().stringWidth(hint);
        g2d.drawString(hint, (WINDOW_WIDTH - w)/2, 260);

        String start = "Press [ENTER] to Start";
        w = g2d.getFontMetrics().stringWidth(start);
        g2d.setColor(Color.YELLOW);
        g2d.drawString(start, (WINDOW_WIDTH - w)/2, 300);
    }

    private void drawGameOverScreen(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 200));
        g2d.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        int boxW = 400, boxH = 300;
        int boxX = (WINDOW_WIDTH - boxW) / 2;
        int boxY = (WINDOW_HEIGHT - boxH) / 2;
        
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(5));
        g2d.drawRoundRect(boxX, boxY, boxW, boxH, 30, 30);
        g2d.setColor(new Color(50, 50, 60));
        g2d.fillRoundRect(boxX, boxY, boxW, boxH, 30, 30);

        g2d.setColor(hp <= 0 ? Color.RED : Color.CYAN);
        g2d.setFont(new Font("Arial", Font.BOLD, 40));
        String title = hp <= 0 ? "GAME OVER" : "STAGE CLEAR";
        int w = g2d.getFontMetrics().stringWidth(title);
        g2d.drawString(title, boxX + (boxW - w)/2, boxY + 60);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.PLAIN, 24));
        g2d.drawString("Final Score: " + score, boxX + 50, boxY + 120);
        g2d.drawString("Max Combo: " + maxCombo, boxX + 50, boxY + 160);

        String rank = "C";
        if (score > 4000) rank = "S";
        else if (score > 3000) rank = "A";
        else if (score > 2000) rank = "B";
        
        g2d.setFont(new Font("Arial", Font.BOLD, 60));
        g2d.setColor(Color.YELLOW);
        g2d.drawString(rank, boxX + 300, boxY + 150);

        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setFont(new Font("Arial", Font.PLAIN, 16));
        g2d.drawString("Press [ENTER] to Restart", boxX + 110, boxY + 250);
    }

    // --- 輸入處理 (修改重點：多鍵支援) ---
    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (currentState == GameState.MENU || currentState == GameState.GAME_OVER) {
            if (key == KeyEvent.VK_ENTER) resetGame();
            return;
        }

        if (currentState == GameState.PLAYING && !isEnding) {
            // 地面：F 或 D
            if (key == KeyEvent.VK_F || key == KeyEvent.VK_D) {
                performAction(Track.GROUND);
            }
            // 空中：J 或 K
            else if (key == KeyEvent.VK_J || key == KeyEvent.VK_K) {
                performAction(Track.AIR);
            }
        }
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        JFrame frame = new JFrame("Muse Dash Lite v1.5 - Multi-Key Support");
        MuseDashLite game = new MuseDashLite();
        frame.add(game);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}