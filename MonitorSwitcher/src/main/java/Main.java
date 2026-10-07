import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

public class Main {

    private static final int TV_TARGET_ID = 50336000;

    // Цвета
    private static final Color BG_DARK       = new Color(15, 15, 15);
    private static final Color TEXT_ACTIVE   = new Color(240, 240, 240);
    private static final Color TEXT_INACTIVE = new Color(90, 90, 90);
    private static final Color PLATE_LIGHT   = new Color(210, 210, 210);
    private static final Color PLATE_MID     = new Color(160, 160, 160);
    private static final Color PLATE_DARK    = new Color(90, 90, 90);
    private static final Color RING_DARK     = new Color(60, 60, 60);
    private static final Color LEVER_COLOR   = new Color(220, 220, 225);
    private static final Color LEVER_SHADOW  = new Color(120, 120, 130);
    private static final Color ACCENT_GREEN  = new Color(80, 200, 120);

    private static TrayIcon trayIcon;
    private static JFrame frame;
    private static ToggleSwitch switcher;

    public static void main(String[] args) {
        if (!SystemTray.isSupported()) {
            System.err.println("Системный трей не поддерживается");
            return;
        }
        SwingUtilities.invokeLater(Main::buildUi);
    }

    private static void buildUi() {
        frame = new JFrame("Переключатель дисплеев");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(420, 280);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG_DARK);

        JPanel root = new JPanel();
        root.setBackground(BG_DARK);
        root.setLayout(new GridBagLayout());

        switcher = new ToggleSwitch();
        switcher.setPreferredSize(new Dimension(360, 240));

        switcher.addActionListener(e -> {
            boolean on = switcher.isOn();
            if (on) {
                if (!DisplayManager.isTvActive(TV_TARGET_ID)) {
                    DisplayManager.enableTv(TV_TARGET_ID);
                    DisplayManager.applyCloneMode();
                }
            } else {
                if (DisplayManager.isTvActive(TV_TARGET_ID)) {
                    DisplayManager.disconnectDisplay(TV_TARGET_ID);
                }
            }
        });

        root.add(switcher);
        frame.add(root);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                frame.setVisible(false);
            }
        });

        setupTray();
        syncSwitcherWithSystem();
        frame.setVisible(true);
    }

    // ================= Кастомный toggle switch =================
    static class ToggleSwitch extends JComponent {

        private boolean on = false;
        private boolean hover = false;
        private final java.util.List<java.awt.event.ActionListener> listeners
                = new java.util.ArrayList<>();

        public ToggleSwitch() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(false);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setOn(!on);
                    for (java.awt.event.ActionListener l : new java.util.ArrayList<>(listeners)) {
                        l.actionPerformed(new java.awt.event.ActionEvent(
                                ToggleSwitch.this,
                                java.awt.event.ActionEvent.ACTION_PERFORMED, "toggle"));
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        public boolean isOn() { return on; }

        public void setOn(boolean value) {
            if (this.on != value) {
                this.on = value;
                repaint();
            }
        }

        public void addActionListener(java.awt.event.ActionListener l) {
            listeners.add(l);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int w = getWidth();
            int h = getHeight();

            // Тумблер смещаем влево, справа оставляем место под надпись
            int plateW = 120;
            int plateH = 170;
            int plateX = (w - plateW) / 2 - 40;
            int plateY = (h - plateH) / 2;

            // ==== 1. Металлическая пластина ====
            Path2D topTab = new Path2D.Float();
            topTab.moveTo(plateX + 20, plateY);
            topTab.lineTo(plateX + plateW - 20, plateY);
            topTab.lineTo(plateX + plateW - 30, plateY + 16);
            topTab.lineTo(plateX + 30, plateY + 16);
            topTab.closePath();

            Path2D bottomTab = new Path2D.Float();
            bottomTab.moveTo(plateX + 30, plateY + plateH - 16);
            bottomTab.lineTo(plateX + plateW - 30, plateY + plateH - 16);
            bottomTab.lineTo(plateX + plateW - 20, plateY + plateH);
            bottomTab.lineTo(plateX + 20, plateY + plateH);
            bottomTab.closePath();

            Path2D plate = new Path2D.Float();
            plate.append(new java.awt.geom.RoundRectangle2D.Float(
                    plateX, plateY, plateW, plateH, 8, 8), false);

            GradientPaint metalGradient = new GradientPaint(
                    plateX, plateY, PLATE_LIGHT,
                    plateX + plateW, plateY + plateH, PLATE_DARK);
            g2.setPaint(metalGradient);
            g2.fill(plate);
            g2.fill(topTab);
            g2.fill(bottomTab);

            g2.setColor(new Color(40, 40, 40));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(plate);
            g2.draw(topTab);
            g2.draw(bottomTab);

            g2.setColor(new Color(255, 255, 255, 60));
            g2.fillRoundRect(plateX + 4, plateY + 4, plateW - 8, 12, 6, 6);

            // ==== 2. Углубление ====
            int bowlSize = 90;
            int bowlX = plateX + (plateW - bowlSize) / 2;
            int bowlY = plateY + (plateH - bowlSize) / 2;

            GradientPaint bowlGradient = new GradientPaint(
                    bowlX, bowlY, RING_DARK,
                    bowlX, bowlY + bowlSize, new Color(30, 30, 30));
            g2.setPaint(bowlGradient);
            g2.fillOval(bowlX, bowlY, bowlSize, bowlSize);

            g2.setColor(new Color(20, 20, 20));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(bowlX, bowlY, bowlSize, bowlSize);

            int ringSize = bowlSize - 10;
            int ringX = bowlX + 5;
            int ringY = bowlY + 5;
            GradientPaint ringGradient = new GradientPaint(
                    ringX, ringY, PLATE_LIGHT,
                    ringX, ringY + ringSize, PLATE_MID);
            g2.setPaint(ringGradient);
            g2.fillOval(ringX, ringY, ringSize, ringSize);
            g2.setColor(new Color(50, 50, 50));
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval(ringX, ringY, ringSize, ringSize);

            int holeSize = ringSize - 14;
            int holeX = ringX + 7;
            int holeY = ringY + 7;
            g2.setColor(new Color(25, 25, 25));
            g2.fillOval(holeX, holeY, holeSize, holeSize);

            // ==== 3. Рычаг (вертикально: вверх = ON, вниз = OFF) ====
            double pivotX = holeX + holeSize / 2.0;
            double pivotY = holeY + holeSize / 2.0;

            // -90° = вверх, +90° = вниз
            double angleDeg = on ? -180 : 180;
            double angle = Math.toRadians(angleDeg);
            double leverLength = 52;

            double endX = pivotX + leverLength * Math.sin(angle);
            double endY = pivotY - leverLength * Math.cos(angle);

            // Тень
            g2.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0, 0, 0, 90));
            g2.drawLine((int) pivotX + 2, (int) pivotY + 4,
                    (int) endX + 2,    (int) endY + 4);

            // Тело
            g2.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(LEVER_SHADOW);
            g2.drawLine((int) pivotX, (int) pivotY, (int) endX, (int) endY);

            g2.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(LEVER_COLOR);
            g2.drawLine((int) pivotX, (int) pivotY, (int) endX, (int) endY);

            // Блик
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 255, 255, 180));
            int offsetX = (int) ((endX - pivotX) * 0.15);
            int offsetY = (int) ((endY - pivotY) * 0.15);
            g2.drawLine(
                    (int) (pivotX + offsetX),
                    (int) (pivotY + offsetY),
                    (int) (endX - offsetX * 0.5),
                    (int) (endY - offsetY * 0.5));

            // Сферическая ручка
            int knobSize = 22;
            int knobX = (int) endX - knobSize / 2;
            int knobY = (int) endY - knobSize / 2;

            g2.setColor(new Color(0, 0, 0, 100));
            g2.fillOval(knobX + 2, knobY + 3, knobSize, knobSize);

            GradientPaint knobGradient = new GradientPaint(
                    knobX, knobY, LEVER_COLOR,
                    knobX + knobSize, knobY + knobSize, LEVER_SHADOW);
            g2.setPaint(knobGradient);
            g2.fillOval(knobX, knobY, knobSize, knobSize);

            g2.setColor(new Color(255, 255, 255, 200));
            g2.fillOval(knobX + 4, knobY + 3, 7, 5);

            // ==== 4. Надпись "Вкл. ТВ" справа ====
            String label = "ТВ вкл.";
            Font labelFont = new Font("Microsoft YaHei", Font.BOLD, 18);
            g2.setFont(labelFont);
            FontMetrics fm = g2.getFontMetrics();

            int labelW = fm.stringWidth(label);
            int labelH = fm.getHeight();
            int labelX = plateX + plateW + 40;              // отступ справа от тумблера
            int labelY = plateY + plateH / 2 - labelH / 2;

            if (on) {
                // Светящаяся надпись с рамкой
                int padX = 14;
                int padY = 10;
                int boxX = labelX - padX;
                int boxY = labelY - padY;
                int boxW = labelW + padX * 2;
                int boxH = labelH + padY * 2;

                // Заливка-подсветка
                g2.setColor(new Color(80, 200, 120, 40));
                g2.fillRoundRect(boxX, boxY, boxW, boxH, 12, 12);

                // Рамка
                g2.setColor(ACCENT_GREEN);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(boxX, boxY, boxW, boxH, 12, 12);

                // Текст
                g2.setColor(ACCENT_GREEN);
                g2.drawString(label, labelX, labelY + fm.getAscent());
            } else {
                // Тусклая надпись без рамки
                g2.setColor(TEXT_INACTIVE);
                g2.drawString(label, labelX, labelY + fm.getAscent());
            }

            // ==== 5. Hover-подсветка тумблера ====
            if (hover) {
                g2.setColor(new Color(255, 255, 255, 30));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(plateX - 6, plateY - 6,
                        plateW + 12, plateH + 12, 12, 12);
            }

            g2.dispose();
        }
    }

    // ================= Трей =================
    private static void setupTray() {
        Image icon = createTrayIcon();

        PopupMenu menu = new PopupMenu();
        MenuItem showItem = new MenuItem("Показать");
        showItem.addActionListener(e -> {
            frame.setVisible(true);
            frame.setState(Frame.NORMAL);
            frame.toFront();
        });

        MenuItem exitItem = new MenuItem("Выход");
        exitItem.addActionListener(e -> {
            SystemTray.getSystemTray().remove(trayIcon);
            System.exit(0);
        });

        menu.add(showItem);
        menu.addSeparator();
        menu.add(exitItem);

        trayIcon = new TrayIcon(icon, "Переключатель дисплеев", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    frame.setVisible(true);
                    frame.setState(Frame.NORMAL);
                    frame.toFront();
                }
            }
        });

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException ex) {
            System.err.println("Не удалось добавить иконку в трей: " + ex.getMessage());
        }
    }

    private static Image createTrayIcon() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(60, 60, 60));
        g.fillRoundRect(1, 3, 9, 7, 2, 2);
        g.fillRoundRect(6, 7, 9, 7, 2, 2);
        g.setColor(Color.WHITE);
        g.drawRoundRect(1, 3, 9, 7, 2, 2);
        g.drawRoundRect(6, 7, 9, 7, 2, 2);
        g.dispose();
        return img;
    }

    // ================= Синхронизация =================
    private static void syncSwitcherWithSystem() {
        boolean tvActive = DisplayManager.isTvActive(TV_TARGET_ID);
        switcher.setOn(tvActive);
        switcher.repaint();
    }
}