package me.mondiversi.uvir

/** Successful writes are accumulated per page, including queued device acknowledgements after Back. */
internal class UvirSettingsExitConfirmation(var changed: Boolean = false) {
    private var announce: (() -> Unit)? = null
    private var announced = false
    fun saved() { changed = true; emitIfReady() }
    fun leave(onSaved: () -> Unit) { announce = onSaved; emitIfReady() }
    private fun emitIfReady() {
        if (changed && !announced && announce != null) {
            announced = true
            announce?.invoke()
        }
    }
}

/** Close the previous visit after the replacement page and its window have been applied. */
internal class UvirSettingsExitTransition {
    private var page: String? = null
    private var confirmation: UvirSettingsExitConfirmation? = null
    fun updatePage(next: String?, current: UvirSettingsExitConfirmation, onSaved: () -> Unit) {
        val previous = confirmation.takeIf { page != null && page != next }
        page = next
        confirmation = current
        previous?.leave(onSaved)
    }
}
