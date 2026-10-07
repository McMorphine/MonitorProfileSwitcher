import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class Main {

    private static final int TV_TARGET_ID = 50336000;

    private static TrayIcon trayIcon;
    private static JFrame frame;
    private static JToggleButton switcher;

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
        frame.setSize(360, 180);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        JLabel title = new JLabel("Режим вывода", SwingConstants.CENTER);
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        title.setBorder(BorderFactory.createEmptyBorder(16, 12, 8, 12));
        frame.add(title, BorderLayout.NORTH);

        switcher = new JToggleButton("Только монитор");
        switcher.setFont(new Font("Microsoft YaHei", Font.PLAIN, 15));
        switcher.setFocusPainted(false);
        switcher.setOpaque(true);
        switcher.setContentAreaFilled(true);
        switcher.setPreferredSize(new Dimension(200, 44));
        switcher.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 120, 120), 1, true),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));

        switcher.addItemListener(e -> {
            if (switcher.isSelected()) {
                switcher.setText("С телевизором");
                switcher.setBackground(new Color(200, 230, 200));
                if (!DisplayManager.isTvActive(TV_TARGET_ID)) {
                    DisplayManager.enableTv(TV_TARGET_ID);
                    DisplayManager.applyCloneMode();
                }
            } else {
                switcher.setText("Только монитор");
                switcher.setBackground(new Color(230, 200, 200));
                if (DisplayManager.isTvActive(TV_TARGET_ID)) {
                    DisplayManager.disconnectDisplay(TV_TARGET_ID);
                }
            }
        });

        JPanel center = new JPanel(new GridBagLayout());
        center.add(switcher);
        frame.add(center, BorderLayout.CENTER);

        JLabel hint = new JLabel("При закрытии окна программа сворачивается в трей",
                SwingConstants.CENTER);
        hint.setFont(new Font("Microsoft YaHei", Font.PLAIN, 11));
        hint.setForeground(new Color(120, 120, 120));
        hint.setBorder(BorderFactory.createEmptyBorder(8, 12, 12, 12));
        frame.add(hint, BorderLayout.SOUTH);

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

    private static void syncSwitcherWithSystem() {
        boolean tvActive = DisplayManager.isTvActive(TV_TARGET_ID);

        java.awt.event.ItemListener[] listeners = switcher.getItemListeners();
        for (java.awt.event.ItemListener l : listeners) switcher.removeItemListener(l);

        switcher.setSelected(tvActive);
        switcher.setText(tvActive ? "Только монитор" : "С телевизором");
        switcher.setBackground(tvActive
                ? new Color(200, 230, 200)
                : new Color(230, 200, 200));

        for (java.awt.event.ItemListener l : listeners) switcher.addItemListener(l);
    }
}