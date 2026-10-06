

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

import java.util.Arrays;
import java.util.List;

public class DisplayManager {

    // ================= ChangeDisplaySettingsEx =================
    public static final int CDS_UPDATEREGISTRY = 0x00000001;
    public static final int CDS_NORESET        = 0x10000000;
    public static final int DM_POSITION        = 0x00000020;
    public static final int DM_PELSWIDTH       = 0x00080000;
    public static final int DM_PELSHEIGHT      = 0x00100000;
    public static final int ENUM_CURRENT_SETTINGS = -1;

    // ================= CCD API =================
    private static final int QDC_DATABASE_CURRENT  = 0x00000004;
    private static final int SDC_APPLY             = 0x00000080;
    private static final int SDC_ALLOW_CHANGES     = 0x00000400;
    private static final int SDC_TOPOLOGY_SUPPLIED = 0x00000010;
    private static final int SDC_TOPOLOGY_CLONE    = 0x00000002;

    private static final int PATH_INFO_SIZE    = 72;
    private static final int PATH_FLAGS_OFFSET = 68;
    private static final int PATH_ACTIVE_FLAG  = 0x00000001;
    private static final int MODE_INFO_SIZE    = 64;

    private static final int TARGET_INFO_OFFSET       = 20;
    private static final int TARGET_ADAPTER_ID_OFFSET = 0;
    private static final int TARGET_ID_OFFSET         = 8;
    private static final int TARGET_MODE_IDX_OFFSET   = 12;
    private static final int SOURCE_MODE_IDX_OFFSET   = 12;
    private static final int DISPLAYCONFIG_PATH_MODE_IDX_INVALID = 0xFFFFFFFF;

    // ================= JNA =================
    public interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

        int ChangeDisplaySettingsExW(String lpszDeviceName, DEVMODE lpDevMode,
                                     WinDef.HWND hwnd, int dwflags, Pointer lParam);

        boolean EnumDisplaySettingsW(String lpszDeviceName, int iModeNum, DEVMODE lpDevMode);

        int GetDisplayConfigBufferSizes(int flags,
                                        IntByReference numPathArrayElements,
                                        IntByReference numModeInfoArrayElements);

        int QueryDisplayConfig(int flags,
                               IntByReference numPathArrayElements,
                               Pointer pathArray,
                               IntByReference numModeInfoArrayElements,
                               Pointer modeInfoArray,
                               Pointer currentTopologyId);

        int SetDisplayConfig(int numPathArrayElements,
                             Pointer pathArray,
                             int numModeInfoArrayElements,
                             Pointer modeInfoArray,
                             int flags);
    }

    // ================= DEVMODE =================
    public static class DEVMODE extends Structure {
        public char[] dmDeviceName = new char[32];
        public short dmSpecVersion;
        public short dmDriverVersion;
        public short dmSize;
        public short dmDriverExtra;
        public int dmFields;
        public int dmPositionX;
        public int dmPositionY;
        public int dmDisplayOrientation;
        public int dmDisplayFixedOutput;
        public short dmColor;
        public short dmDuplex;
        public short dmYResolution;
        public short dmTTOption;
        public short dmCollate;
        public char[] dmFormName = new char[32];
        public short dmLogPixels;
        public int dmBitsPerPel;
        public int dmPelsWidth;
        public int dmPelsHeight;
        public int dmDisplayFlags;
        public int dmDisplayFrequency;
        public int dmICMMethod;
        public int dmICMIntent;
        public int dmMediaType;
        public int dmDitherType;
        public int dmReserved1;
        public int dmReserved2;
        public int dmPanningWidth;
        public int dmPanningHeight;

        public DEVMODE() {
            dmSize = (short) size();
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList(
                    "dmDeviceName", "dmSpecVersion", "dmDriverVersion", "dmSize", "dmDriverExtra",
                    "dmFields", "dmPositionX", "dmPositionY", "dmDisplayOrientation", "dmDisplayFixedOutput",
                    "dmColor", "dmDuplex", "dmYResolution", "dmTTOption", "dmCollate",
                    "dmFormName", "dmLogPixels", "dmBitsPerPel", "dmPelsWidth", "dmPelsHeight",
                    "dmDisplayFlags", "dmDisplayFrequency", "dmICMMethod", "dmICMIntent",
                    "dmMediaType", "dmDitherType", "dmReserved1", "dmReserved2",
                    "dmPanningWidth", "dmPanningHeight"
            );
        }
    }

    // ================= Отключение ТВ =================
    /**
     * Отключает ТВ через ChangeDisplaySettingsEx.
     * 4K-монитор не трогается. Путь ТВ остаётся в базе Windows,
     * поэтому его потом можно включить обратно.
     *
     * @param tvDeviceName имя вида "\\.\DISPLAY2"
     */
    public static void disconnectTv(String tvDeviceName) {
        DEVMODE devMode = new DEVMODE();

        if (!User32.INSTANCE.EnumDisplaySettingsW(tvDeviceName, ENUM_CURRENT_SETTINGS, devMode)) {
            System.err.println("EnumDisplaySettingsW failed for " + tvDeviceName);
            return;
        }

        // Отключаем только ТВ: обнуляем позицию и разрешение
        devMode.dmFields = DM_POSITION | DM_PELSWIDTH | DM_PELSHEIGHT;
        devMode.dmPelsWidth = 0;
        devMode.dmPelsHeight = 0;
        devMode.dmPositionX = 0;
        devMode.dmPositionY = 0;

        // Записываем в реестр без применения
        int ret1 = User32.INSTANCE.ChangeDisplaySettingsExW(
                tvDeviceName, devMode, null,
                CDS_UPDATEREGISTRY | CDS_NORESET, null);
        System.out.println("disconnect step 1: " + ret1);

        // Применяем — один раз, без DEVMODE (только применить накопленное)
        int ret2 = User32.INSTANCE.ChangeDisplaySettingsExW(null, null, null, 0, null);
        System.out.println("disconnect step 2: " + ret2 + " (0 = успех)");
    }

    // ================= Включение ТВ =================
    /**
     * Включает ТВ: находит его путь в базе, ставит ACTIVE и сбрасывает modeIdx,
     * чтобы Windows сама подобрала режим.
     */
    public static void enableTv(long tvAdapterId, int tvTargetId) {
        IntByReference pathCount = new IntByReference();
        IntByReference modeCount = new IntByReference();
        IntByReference topologyId = new IntByReference();

        // Пробуем несколько раз на случай 122
        Pointer pathArray = null;
        int ret = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            ret = User32.INSTANCE.GetDisplayConfigBufferSizes(
                    QDC_DATABASE_CURRENT, pathCount, modeCount);
            if (ret != 0) {
                System.err.println("GetDisplayConfigBufferSizes: " + ret);
                return;
            }
            pathArray = new Memory((long) pathCount.getValue() * PATH_INFO_SIZE);
            Pointer modeArray = new Memory((long) modeCount.getValue() * MODE_INFO_SIZE);

            ret = User32.INSTANCE.QueryDisplayConfig(
                    QDC_DATABASE_CURRENT,
                    pathCount, pathArray,
                    modeCount, modeArray,
                    topologyId.getPointer());
            if (ret == 122) continue;
            if (ret != 0) {
                System.err.println("QueryDisplayConfig: " + ret);
                return;
            }
            break;
        }
        if (ret != 0) {
            System.err.println("Не удалось получить пути");
            return;
        }

        System.out.println("Путей в базе: " + pathCount.getValue());

        boolean found = false;
        for (int i = 0; i < pathCount.getValue(); i++) {
            Pointer pathPtr = pathArray.share((long) i * PATH_INFO_SIZE);
            long adapterId = pathPtr.getLong(TARGET_INFO_OFFSET + TARGET_ADAPTER_ID_OFFSET);
            int id = pathPtr.getInt(TARGET_INFO_OFFSET + TARGET_ID_OFFSET);

            System.out.println("Путь " + i + ": adapterId=" + adapterId + ", targetId=" + id);

            if (adapterId == tvAdapterId && id == tvTargetId) {
                int flags = pathPtr.getInt(PATH_FLAGS_OFFSET);
                pathPtr.setInt(PATH_FLAGS_OFFSET, flags | PATH_ACTIVE_FLAG);

                pathPtr.setInt(SOURCE_MODE_IDX_OFFSET, DISPLAYCONFIG_PATH_MODE_IDX_INVALID);
                pathPtr.setInt(TARGET_INFO_OFFSET + TARGET_MODE_IDX_OFFSET,
                        DISPLAYCONFIG_PATH_MODE_IDX_INVALID);

                System.out.println("Путь " + i + " активирован");
                found = true;
            }
        }

        if (!found) {
            System.err.println("Не найден путь ТВ (adapterId=" + tvAdapterId
                    + ", targetId=" + tvTargetId + ")");
            return;
        }

        int apply = User32.INSTANCE.SetDisplayConfig(
                pathCount.getValue(), pathArray,
                0, null,
                SDC_APPLY | SDC_TOPOLOGY_SUPPLIED | SDC_ALLOW_CHANGES);
        System.out.println("SetDisplayConfig (enable): " + apply + " (0 = успех)");
    }

    // ================= Дублирование 4K + ТВ =================
    /**
     * Применяет последнюю сохранённую в Windows топологию дублирования.
     */
    public static void applyCloneMode() {
        int ret = User32.INSTANCE.SetDisplayConfig(
                0, null, 0, null,
                SDC_APPLY | SDC_TOPOLOGY_CLONE);
        System.out.println("Clone mode: " + ret + " (0 = успех)");
    }
}