-keep class net.obscura.vpnclientapp.client.ObscuraLibrary$FfiHandle { *; }
-keep class net.obscura.vpnclientapp.client.ErrorCodeException { *; }
-keepclassmembers class net.obscura.vpnclientapp.services.ObscuraVpnService {
    public static int ffiSetNetworkConfig(java.lang.String);
    public static java.lang.String ffiGetProcessExitReasons();
    public static boolean ffiStoreWgSecretKey(byte[]);
}
