import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import javax.swing.*;

public class RhythmGameDemo extends JPanel implements ActionListener, KeyListener {
    // 遊戲參數
    private final int JUDGE_LINE_X = 100; // 判定線位置
    private final int NOTE_SPEED = 5;     // 音符移動速度
    private Timer timer;
    private final ArrayList<Rectangle> notes;   // 儲存所有音符
    private String feedback = "Ready";    // 顯示判定結果

    public RhythmGameDemo() {
        this.setFocusable(true);
        this.notes = new ArrayList<>();
        
        // 模擬產生音符 (每隔一段時間加一個)
        notes.add(new Rectangle(800, 200, 30, 30)); 
        notes.add(new Rectangle(1000, 200, 30, 30));
    }

    public void initGame() {
        this.addKeyListener(this);
        // 使用 Swing Timer 進行 60FPS 的畫面更新 (簡易版做法)
        timer = new Timer(16, this); // ~60 FPS
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // 1. 畫背景
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, getWidth(), getHeight());

        // 2. 畫判定線
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(3));
        g2d.drawLine(JUDGE_LINE_X, 0, JUDGE_LINE_X, getHeight());

        // 3. 畫音符
        g2d.setColor(Color.CYAN);
        for (Rectangle note : notes) {
            g2d.fill(note);
        }

        // 4. 畫回饋文字
        g2d.setColor(Color.YELLOW);
        g2d.setFont(new Font("Arial", Font.BOLD, 30));
        g2d.drawString(feedback, 50, 50);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // 更新音符位置
        for (Rectangle note : notes) {
            note.x -= NOTE_SPEED;
        }
        
        // 移除跑出畫面的音符
        notes.removeIf(n -> n.x + n.width < 0);
        
        repaint(); // 重繪畫面
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) { // 按下空白鍵打擊
            checkHit();
        }
    }

    private void checkHit() {
        boolean hit = false;
        Iterator<Rectangle> it = notes.iterator();
        while (it.hasNext()) {
            Rectangle note = it.next();
            // 計算音符中心點與判定線的距離
            int distance = Math.abs(note.x - JUDGE_LINE_X);

            if (distance < 50) { // 判定範圍
                if (distance < 15) feedback = "PERFECT!!";
                else feedback = "Great!";
                it.remove(); // 打中後移除音符
                hit = true;
                break; // 一次只打一個
            }
        }
        if (!hit) feedback = "Miss...";
    }

    // 必要的介面實作 (空方法)
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        JFrame frame = new JFrame("Swing Rhythm Game Demo");
        RhythmGameDemo game = new RhythmGameDemo();
        game.initGame();
        frame.add(game);
        frame.setSize(800, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}