package net.obscura.vpnclientapp.client;

import android.content.Context;
import androidx.annotation.Keep;
import java.util.concurrent.CompletableFuture;

public class ObscuraLibrary {
    /** Opaque handle returned by Rust initialization, required by all subsequent native FFI calls. */
    @Keep
    public static class FfiHandle {
        private FfiHandle() {}
    }

    private static volatile FfiHandle cachedHandle;

    static synchronized FfiHandle load(
            Context context,
            String userAgent,
            byte[] wgSecretKey,
            byte[] configKey,
            boolean persistLogs) {
        if (cachedHandle != null) {
            return cachedHandle;
        }
        System.loadLibrary("obscuravpn_client");
        cachedHandle = ObscuraLibrary.initialize(
                context.getFilesDir().getAbsolutePath(),
                userAgent,
                wgSecretKey,
                configKey,
                persistLogs);
        return cachedHandle;
    }

    static native FfiHandle initialize(
            String configDir,
            String userAgent,
            byte[] wgSecretKey,
            byte[] configKey,
            boolean persistLogs);

    static native void jsonFfi(FfiHandle handle, String json, CompletableFuture<String> future);

    static native void setNetworkInterface(
            FfiHandle handle,
            String name,
            int index,
            long networkHandle,
            int mtuHint);

    static native void unsetNetworkInterface(FfiHandle handle);

    static native void wake(FfiHandle handle);

    static native void forwardLog(int level, String tag, String message, String messageId, String throwableString);

    static native void flushAndStopPersistedLog();
}
