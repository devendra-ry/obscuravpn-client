// IObscuraVpnService.aidl
package net.obscura.vpnclientapp.services;

interface IObscuraVpnService {
    void stopTunnel();

    // Submits the command to the ObscuraLibrary.jsonFfi(String, CompletableFuture<String>)
    // function, returning back a unique ID. To receive the result of the command, listen on the
    // JsonFfiBroadcastReceiver for an Intent with the "id" extra, "value" extra
    // (indicating success) or "errorCode" extra (indicating an ErrorCodeException).
    void jsonFfi(long id, String command);

    void setAndroidSettings(String json);

    String getAndroidSettings();
}
