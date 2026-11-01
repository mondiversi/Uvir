package me.mondiversi.uvir.usb

import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class UsbHeadChecksTest {
    private class Bus(
        val absent: Set<Int> = emptySet(), val wrongVisibleId: Boolean = false,
        val uvReadFails: Boolean = false, val uvConfigFails: Boolean = false
    ): I2cBus {
        val addresses = mutableListOf<Int>()
        private var gain = 9
        override fun transfer(address: Int, write: ByteArray, readCount: Int): ByteArray {
            addresses += address
            if(address in absent) throw I2cNack(address)
            val register = write[0].toInt() and 255
            if(readCount == 0) {
                if(address == 0x39 && register == 0xc6) gain = write[1].toInt() and 15
                if(address == 0x74 && register == 7 && uvConfigFails) throw I2cNack(address)
                return byteArrayOf()
            }
            if(address == 0x74) return when {
                register == 2 && readCount == 1 -> byteArrayOf(0x21)
                register == 0 && readCount == 2 -> byteArrayOf(3,8)
                register == 2 && readCount == 6 -> {
                    if(uvReadFails) throw IOException("UV transfer timed out")
                    ByteArray(6)
                }
                else -> ByteArray(readCount)
            }
            return when {
                register == 0x5a -> byteArrayOf(if(wrongVisibleId) 0x42 else 0x81.toByte())
                register == 0x90 -> byteArrayOf(0x40)
                register == 0x94 -> byteArrayOf(gain.toByte()) + ByteArray(36)
                else -> ByteArray(readCount)
            }
        }
        override fun close() {}
    }
    private fun inspect(bus: Bus): Pair<UsbOpticalCheck,UsbOpticalCheck> =
        checkUsbOpticalModules(As7331(bus) {},As7343(bus) {},{true},{_,_->})

    @Test fun realDriverSequencesRecognizeBothIdsAndAcceptGenuineDarkness() {
        val (uv,visible)=inspect(Bus())
        assertTrue(uv.passed);assertTrue(visible.passed)
        assertEquals(0x21,uv.chipId);assertEquals(0x81,visible.chipId)
    }
    @Test fun missingUvNeverSuppressesTheVisibleProbeAndMissingVisibleNeverHidesUv() {
        for(address in listOf(0x74,0x39)) {
            val bus=Bus(absent=setOf(address));val (uv,visible)=inspect(bus)
            val missing=if(address==0x74) uv else visible
            val present=if(address==0x74) visible else uv
            assertEquals(false,missing.recognized);assertEquals(false,missing.initialized);assertNull(missing.readable)
            assertTrue(present.passed);assertTrue(bus.addresses.containsAll(listOf(0x74,0x39)))
        }
    }
    @Test fun wrongChipIdIsReportedAsWrongHardwareAndIsNeverReadAsASupportedSensor() {
        val (uv,visible)=inspect(Bus(wrongVisibleId=true))
        assertTrue(uv.passed);assertEquals(false,visible.recognized)
        assertEquals(0x42,visible.chipId);assertEquals(false,visible.initialized);assertNull(visible.readable)
    }
    @Test fun recognizedChipWithConfigurationFailureIsDifferentFromAnAbsentChip() {
        val (uv,visible)=inspect(Bus(uvConfigFails=true))
        assertEquals(true,uv.recognized);assertEquals(false,uv.initialized);assertNull(uv.readable)
        assertTrue(visible.passed)
    }
    @Test fun readFailureKeepsRecognitionAndStillTestsTheOtherModule() {
        val (uv,visible)=inspect(Bus(uvReadFails=true))
        assertEquals(true,uv.recognized);assertEquals(true,uv.initialized);assertEquals(false,uv.readable)
        assertTrue(visible.passed);assertEquals("UV transfer timed out",uv.error)
    }
    @Test fun noModulesIsACompletedFailedDiagnosticRatherThanAnUnavailableButton() {
        val (uv,visible)=inspect(Bus(absent=setOf(0x74,0x39)))
        val checks=UsbHeadChecks(1,"head",true,true,true,uv,visible,true)
        assertTrue(checks.adapterPassed);assertFalse(checks.passed)
        assertEquals(false,uv.recognized);assertEquals(false,visible.recognized)
    }
    @Test fun corruptAndIncompleteFramesCannotPassAndCancellationIsPropagated() {
        for(values in listOf(DoubleArray(2),DoubleArray(3) { Double.NaN },DoubleArray(3) { -1.0 })) {
            val check=checkUsbOpticalComponent({}, {0x21},{true},{values},3,{true},{})
            assertEquals(true,check.recognized);assertEquals(false,check.readable)
        }
        try {
            checkUsbOpticalComponent({throw CancellationException("cancel")},{null},{true},{DoubleArray(3)},3,{true},{fail("Cancellation is not a hardware error")})
            fail("Cancellation must propagate")
        } catch(expected:CancellationException) {}
    }
    @Test fun aReconnectedHeadCannotFinishAnOlderDiagnostic() {
        var current=true
        try {
            checkUsbOpticalComponent({}, {0x21},{true},{current=false;DoubleArray(3)},3,{current},{fail("Stale head")})
            fail("Changed attachment must stop the probe")
        } catch(expected:CancellationException) {}
    }
}
