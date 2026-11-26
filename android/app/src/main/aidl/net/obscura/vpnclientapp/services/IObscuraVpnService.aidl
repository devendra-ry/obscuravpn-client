// IObscuraVpnService.aidl
package net.obscura.vpnclientapp.services;

interface IObscuraVpnService {
    void stopTunnel();

    // Submits the command to the ObscuraLibrary.jsonFfi(String, CompletableFuture<String>)
    // function under the caller's unique ID. The service and the UI share one process: the result
    // goes to JsonFfiResults.deliverInProcess, which completes the caller's waitForResponse.
    void jsonFfi(long id, String command);

    void setAndroidSettings(String json);

    String getAndroidSettings();
}
