package me.mondiversi.uvir.usb

import org.junit.Assert.*
import org.junit.Test

class UsbPermissionRequestsTest {
    @Test fun repeatedScansAndADeniedReplyCannotRequestAgainForTheSameAttachment() {
        val gate = UsbPermissionRequests()
        assertTrue(gate.shouldRequest("head", false, true))
        assertTrue(gate.acceptsReply("head", true))
        repeat(10) { assertFalse(gate.shouldRequest("head", false, true)) }
    }

    @Test fun attachDiscoveryDoesNotCompeteWithTheAndroidChooser() {
        val gate = UsbPermissionRequests()
        assertFalse(gate.shouldRequest("head", false, false))
        assertNull(gate.requestedDeviceName)
        // A normal manual launch can still request access if the OS has not granted it.
        assertTrue(gate.shouldRequest("head", false, true))
    }

    @Test fun chooserGrantedAccessNeedsNoSeparatePermissionRequest() {
        val gate = UsbPermissionRequests()
        assertFalse(gate.shouldRequest("head", true, true))
        assertNull(gate.requestedDeviceName)
    }

    @Test fun detachedAndUnrelatedRepliesAreIgnoredAndExplicitRetryIsPossible() {
        val gate = UsbPermissionRequests()
        assertTrue(gate.shouldRequest("old", false, true))
        assertFalse(gate.acceptsReply("old", false))
        assertFalse(gate.acceptsReply("other", true))
        gate.clear()
        assertFalse(gate.acceptsReply("old", true))
        assertTrue(gate.shouldRequest("new", false, true))
        assertFalse(gate.acceptsReply("old", true))
        assertTrue(gate.acceptsReply("new", true))
        gate.clear()
        assertTrue(gate.shouldRequest("new", false, true))
    }
}
