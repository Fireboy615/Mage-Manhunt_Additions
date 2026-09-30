package net.fireboy.mageadditions.client.state;

/** Client-only state used by Capture's struggle input and private target outline. */
public final class ClientCaptureState {
    private static final long TARGET_HIGHLIGHT_MILLIS = 5_000L;

    private static boolean captured;
    private static int selectedTargetId = -1;
    private static long selectedTargetUntilMillis;

    private ClientCaptureState() {
    }

    public static boolean isCaptured() {
        return captured;
    }

    public static void setCaptured(boolean value) {
        captured = value;
    }

    public static void setSelectedTarget(int entityId) {
        selectedTargetId = entityId;
        selectedTargetUntilMillis = entityId < 0
                ? 0L
                : System.currentTimeMillis() + TARGET_HIGHLIGHT_MILLIS;
    }

    public static boolean isSelectedTarget(int entityId) {
        if (selectedTargetId < 0 || selectedTargetId != entityId) {
            return false;
        }
        if (System.currentTimeMillis() > selectedTargetUntilMillis) {
            selectedTargetId = -1;
            selectedTargetUntilMillis = 0L;
            return false;
        }
        return true;
    }
}
