package singlaunch;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class SingularityLauncher {

    private static final String VERSIONS_DIR = "versions";
    private static final Color BG       = new Color(0x1a, 0x1a, 0x1a);
    private static final Color BG2      = new Color(0x23, 0x23, 0x23);
    private static final Color BG3      = new Color(0x2d, 0x2d, 0x2d);
    private static final Color ACCENT   = new Color(0xff, 0xd3, 0x79);
    private static final Color ACCENT_B = new Color(0xff, 0xe8, 0x9c);
    private static final Color TEXT     = new Color(0xe8, 0xe8, 0xe8);
    private static final Color TEXT_DIM = new Color(0x6a, 0x6a, 0x6a);
    private static final Color BORDER_C = new Color(0x3a, 0x3a, 0x3a);

    private final ArrayList<File> jarFiles = new ArrayList<>();
    private File selectedJar;
    private JLabel statusLabel;
    private JFrame frame;

    public SingularityLauncher() {
        scanVersions();
        createUI();
    }

    private void scanVersions() {
        File dir = new File(VERSIONS_DIR);
        if (!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jar"));
        if (files != null) {
            for (File f : files) {
                jarFiles.add(f);
                if (selectedJar == null) selectedJar = f;
            }
        }
    }

    private void createUI() {
        frame = new JFrame("Singularity Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        frame.setBackground(BG2);
        frame.setSize(920, 580);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG2);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(2, 2, getWidth() - 5, getHeight() - 5);
                g2.dispose();
            }
        };
        root.setOpaque(true);
        root.setBorder(new EmptyBorder(4, 4, 4, 4));

        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(BORDER_C);
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                g2.dispose();
            }
        };
        header.setOpaque(true);
        header.setPreferredSize(new Dimension(0, 52));

        JPanel logoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        logoPanel.setOpaque(false);

        JLabel logoMark = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT);
                g2.fillRect(0, 0, 22, 22);
                g2.setColor(BG);
                g2.fillRect(5, 5, 12, 12);
                g2.dispose();
            }
        };
        logoMark.setPreferredSize(new Dimension(22, 22));
        logoMark.setOpaque(false);

        JLabel logoText = new JLabel("MINDUSTRY");
        logoText.setFont(new Font("Monospaced", Font.BOLD, 20));
        logoText.setForeground(ACCENT);

        JLabel logoSub = new JLabel("  // LAUNCHER");
        logoSub.setFont(new Font("Monospaced", Font.PLAIN, 20));
        logoSub.setForeground(TEXT_DIM);

        logoPanel.add(logoMark);
        logoPanel.add(logoText);
        logoPanel.add(logoSub);

        JLabel versionLabel = new JLabel("Singularity v0.1 — build 7281");
        versionLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        versionLabel.setForeground(TEXT_DIM);

        header.add(logoPanel, BorderLayout.WEST);
        header.add(versionLabel, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel mainArea = new JPanel(new BorderLayout());
        mainArea.setOpaque(true);
        mainArea.setBackground(BG2);

        PreviewPanel preview = new PreviewPanel();
        mainArea.add(preview, BorderLayout.CENTER);

        JPanel menu = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG2);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(BORDER_C);
                g2.fillRect(0, 0, 1, getHeight());
                g2.dispose();
            }
        };
        menu.setOpaque(true);
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBorder(new EmptyBorder(24, 20, 24, 20));
        menu.setPreferredSize(new Dimension(280, 0));
        menu.setMinimumSize(new Dimension(280, 0));

        JButton playBtn = createMenuButton("ИГРАТЬ", true);
        JButton settingsBtn = createMenuButton("НАСТРОЙКИ", false);
        JButton modsBtn = createMenuButton("МОДЫ", false);
        JButton exitBtn = createMenuButton("ВЫХОД", false);

        playBtn.addActionListener(e -> launchSelected());
        settingsBtn.addActionListener(e -> showStatus("Открыты настройки"));
        modsBtn.addActionListener(e -> showStatus("Загрузка менеджера модов"));
        exitBtn.addActionListener(e -> System.exit(0));

        menu.add(playBtn);
        menu.add(Box.createRigidArea(new Dimension(0, 10)));
        menu.add(settingsBtn);
        menu.add(Box.createRigidArea(new Dimension(0, 10)));
        menu.add(modsBtn);
        menu.add(Box.createRigidArea(new Dimension(0, 10)));
        menu.add(exitBtn);

        mainArea.add(menu, BorderLayout.EAST);
        root.add(mainArea, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(BORDER_C);
                g2.fillRect(0, 0, getWidth(), 1);
                g2.dispose();
            }
        };
        footer.setOpaque(true);
        footer.setBorder(new EmptyBorder(0, 16, 0, 16));
        footer.setPreferredSize(new Dimension(0, 44));

        statusLabel = new JLabel("Система готова");
        statusLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        statusLabel.setForeground(TEXT_DIM);
        statusLabel.setIcon(new StatusDot(statusLabel));
        statusLabel.setIconTextGap(8);

        JLabel buildLabel = new JLabel("Игроков в сети: 1 247");
        buildLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        buildLabel.setForeground(TEXT_DIM);

        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(buildLabel, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        final Point[] dragOffset = new Point[1];
        MouseAdapter dragListener = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                dragOffset[0] = e.getPoint();
            }
            public void mouseDragged(MouseEvent e) {
                Point loc = frame.getLocation();
                frame.setLocation(loc.x + e.getX() - dragOffset[0].x, loc.y + e.getY() - dragOffset[0].y);
            }
        };
        header.addMouseListener(dragListener);
        header.addMouseMotionListener(dragListener);
        logoPanel.addMouseListener(dragListener);
        logoPanel.addMouseMotionListener(dragListener);

        frame.setContentPane(root);
        frame.setVisible(true);
    }

    private JButton createMenuButton(String text, boolean primary) {
        MenuButton btn = new MenuButton(text, primary);
        btn.setMaximumSize(new Dimension(260, 46));
        btn.setPreferredSize(new Dimension(260, 46));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        return btn;
    }

    private void launchSelected() {
        if (selectedJar != null) {
            showStatus("Запуск: " + selectedJar.getName());
            try {
                new ProcessBuilder("java", "-jar", selectedJar.getAbsolutePath())
                        .inheritIO()
                        .start();
                System.exit(0);
            } catch (IOException e) {
                showStatus("Ошибка: " + e.getMessage());
            }
        } else {
            showStatus("Нет версий для запуска");
        }
    }

    private void showStatus(String msg) {
        statusLabel.setText(msg);
    }

    static class MenuButton extends JButton {
        private final boolean primary;

        MenuButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(new Font("Monospaced", Font.BOLD, 13));
            setForeground(primary ? BG : ACCENT);
            setBackground(primary ? ACCENT : BG3);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            boolean hover = getModel().isRollover();
            boolean pressed = getModel().isPressed();

            if (pressed) g2.translate(0, 1);

            Color bg, fg, marker;
            if (primary) {
                bg = hover ? ACCENT_B : ACCENT;
                fg = BG;
                marker = BG;
            } else if (hover) {
                bg = ACCENT;
                fg = BG;
                marker = BG;
            } else {
                bg = BG3;
                fg = ACCENT;
                marker = ACCENT;
            }

            int w = getWidth(), h = getHeight();

            g2.setColor(bg);
            g2.fillRect(0, 0, w, h);

            g2.setColor(ACCENT);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(1, 1, w - 2, h - 2);

            g2.setColor(marker);
            g2.fillRect(4, 6, 7, h - 12);

            if (!primary && hover) {
                int cx = w - 18;
                int cy = h / 2;
                g2.setColor(BG);
                int[] xs = {cx, cx + 5, cx};
                int[] ys = {cy - 3, cy, cy + 3};
                g2.fillPolygon(xs, ys, 3);
            }

            g2.setColor(fg);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int ty = (h + fm.getAscent() - fm.getDescent()) / 2 - 1;
            g2.drawString(getText(), 20, ty);

            g2.dispose();
        }

        @Override
        public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override
        public Dimension getMaximumSize() { return getPreferredSize(); }
    }

    static class PreviewPanel extends JPanel {
        private final ArrayList<Block> blocks = new ArrayList<>();
        private final Random rng = new Random();
        private int tick = 0;
        private static final int BLOCK = 38;

        PreviewPanel() {
            setBackground(BG);
            setOpaque(true);
            Timer timer = new Timer(16, e -> { tick++; repaint(); });
            timer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) { g2.dispose(); return; }

            g2.setColor(BG);
            g2.fillRect(0, 0, w, h);

            try {
                RadialGradientPaint glow = new RadialGradientPaint(
                    w * 0.15f, h * 0.2f, w * 0.55f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 211, 121, 15), new Color(255, 211, 121, 0)}
                );
                g2.setPaint(glow);
                g2.fillRect(0, 0, w, h);
            } catch (Exception ignored) {}

            g2.setColor(new Color(255, 211, 121, 12));
            g2.setStroke(new BasicStroke(1f));
            for (int x = 0; x <= w; x += BLOCK) g2.drawLine(x, 0, x, h);
            for (int y = 0; y <= h; y += BLOCK) g2.drawLine(0, y, w, y);

            int cols = (w / BLOCK) + 2;
            int rows = (h / BLOCK) + 2;
            while (blocks.size() < cols * rows / 5) {
                blocks.add(new Block(
                    rng.nextInt(cols) * BLOCK,
                    rng.nextInt(rows) * BLOCK,
                    rng.nextDouble() > 0.85, rng
                ));
            }

            for (Block b : blocks) {
                b.life++;
                if (b.life > b.maxLife) {
                    b.life = 0;
                    b.maxLife = 180 + rng.nextInt(220);
                }
                double phase = (double) b.life / b.maxLife;
                float alpha = (float) (phase < 0.5 ? phase * 2 : (1 - phase) * 2);
                if (b.turret) {
                    g2.setStroke(new BasicStroke(2f));
                    g2.setColor(new Color(255, 211, 121, (int)(35 + alpha * 55)));
                    g2.drawRect(b.x + 7, b.y + 7, BLOCK - 14, BLOCK - 14);
                    g2.setColor(new Color(255, 211, 121, (int)(40 + alpha * 50)));
                    g2.fillRect(b.x + BLOCK / 2 - 3, b.y + BLOCK / 2 - 3, 6, 6);
                } else {
                    g2.setColor(new Color(255, 211, 121, (int)(alpha * 12)));
                    g2.fillRect(b.x + 5, b.y + 5, BLOCK - 10, BLOCK - 10);
                    g2.setStroke(new BasicStroke(1f));
                    g2.setColor(new Color(255, 211, 121, (int)(8 + alpha * 28)));
                    g2.drawRect(b.x + 5, b.y + 5, BLOCK - 10, BLOCK - 10);
                }
            }

            int cy = (h / 2 / BLOCK) * BLOCK + BLOCK / 2;
            g2.setColor(new Color(255, 211, 121, 80));
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(0, cy, w, cy);

            g2.setColor(new Color(255, 211, 121, 127));
            float[] dash = {8f, 14f};
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, dash, -tick * 1.2f));
            g2.drawLine(0, cy, w, cy);

            int resX = (int)((tick * 1.4) % (w + 30)) - 15;
            g2.setColor(ACCENT);
            g2.fillRect(resX, cy - 5, 10, 10);
            g2.setColor(new Color(26, 26, 26, 153));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(resX, cy - 5, 10, 10);

            float dx = (float)(Math.sin(tick * 0.008) * 0.5 + 0.5) * w;
            float dy = cy - 60 - (float)(Math.sin(tick * 0.04)) * 14;
            g2.setColor(new Color(255, 211, 121, 216));
            g2.fillRect((int)dx - 5, (int)dy - 5, 10, 10);
            g2.setColor(new Color(255, 211, 121, 102));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine((int)dx, (int)dy + 5, (int)dx, (int)dy + 14);

            g2.setColor(new Color(26, 26, 26, 216));
            g2.fillRect(w - 82, 16, 66, 22);
            g2.setColor(ACCENT);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRect(w - 82, 16, 66, 22);
            g2.setFont(new Font("Monospaced", Font.BOLD, 10));
            g2.drawString("STABLE", w - 68, 31);

            g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();

            String s1 = "Последняя сессия: ";
            String s2 = "Серпуло";
            g2.setColor(TEXT_DIM);
            g2.drawString(s1, 16, h - 28);
            g2.setColor(ACCENT);
            g2.drawString(s2, 16 + fm.stringWidth(s1), h - 28);

            String s3 = "Время в игре: ";
            String s4 = "47ч 12м";
            g2.setColor(TEXT_DIM);
            g2.drawString(s3, 16, h - 14);
            g2.setColor(ACCENT);
            g2.drawString(s4, 16 + fm.stringWidth(s3), h - 14);

            g2.dispose();
        }

        static class Block {
            int x, y, life, maxLife;
            boolean turret;
            Block(int x, int y, boolean turret, Random rng) {
                this.x = x;
                this.y = y;
                this.turret = turret;
                this.maxLife = 180 + rng.nextInt(220);
            }
        }
    }

    static class StatusDot implements Icon {
        private int animTick = 0;
        private final JLabel parent;
        private final Timer timer;

        StatusDot(JLabel parent) {
            this.parent = parent;
            this.timer = new Timer(100, e -> { animTick++; parent.repaint(); });
            this.timer.start();
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float phase = (float)(Math.sin(animTick * 0.35) * 0.5 + 0.5);
            g2.setColor(new Color(255, 211, 121, (int)(80 + phase * 175)));
            g2.fillOval(x, y + 3, 7, 7);
            g2.dispose();
        }

        @Override public int getIconWidth() { return 7; }
        @Override public int getIconHeight() { return 15; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SingularityLauncher::new);
    }
}
