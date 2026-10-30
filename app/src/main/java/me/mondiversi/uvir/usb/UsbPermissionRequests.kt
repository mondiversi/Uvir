package me.mondiversi.uvir.usb

/** One access request per attachment. Discovery must not race Android's app chooser.
 * Denial is remembered until detach or an explicit retry, without persisting USB identities.
 */
internal class UsbPermissionRequests {
    @Volatile var requestedDeviceName: String? = null
        private set

    @Synchronized fun shouldRequest(name: String, alreadyGranted: Boolean, mayPrompt: Boolean): Boolean {
        if (alreadyGranted || !mayPrompt || requestedDeviceName == name) return false
        requestedDeviceName = name
        return true
    }

    fun acceptsReply(name: String?, stillAttached: Boolean): Boolean =
        name != null && name == requestedDeviceName && stillAttached

    @Synchronized fun clear() { requestedDeviceName = null }
}
