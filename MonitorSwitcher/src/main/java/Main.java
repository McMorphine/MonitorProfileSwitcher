
import javax.swing.*;
import java.awt.*;

public class Main {

    // Имя ТВ-дисплея (из PowerShell при расширенном режиме)
    private static final String TV_DEVICE_NAME = "\\\\.\\DISPLAY3";

    // adapterId + targetId ТВ (из CCD-диагностики)
    private static final long TV_ADAPTER_ID = 99937L;
    private static final int  TV_TARGET_ID  = 50336000;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Переключатель дисплеев");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(360, 200);
            frame.setLayout(new GridLayout(2, 1, 8, 8));
            frame.setLocationRelativeTo(null);

            JButton pcMode = new JButton("Только монитор");
            pcMode.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            pcMode.addActionListener(e -> DisplayManager.disconnectTv(TV_DEVICE_NAME));

            JButton tvMode = new JButton("С телевизором");
            tvMode.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
            tvMode.addActionListener(e -> {
                DisplayManager.enableTv(TV_ADAPTER_ID, TV_TARGET_ID);
                // После включения ТВ восстанавливаем дублирование 4K + ТВ
                DisplayManager.applyCloneMode();
            });

            frame.add(pcMode);
            frame.add(tvMode);
            frame.setVisible(true);
        });
    }
}