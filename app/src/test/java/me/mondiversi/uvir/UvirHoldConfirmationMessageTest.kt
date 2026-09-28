package me.mondiversi.uvir

import org.junit.Assert.assertEquals
import org.junit.Test

class UvirHoldConfirmationMessageTest {
    @Test
    fun separatesFinalHoldInstruction() {
        assertEquals(
            "Records will be deleted." to "Hold the button for 5 seconds.",
            splitUvirHoldConfirmationMessage(
                "Records will be deleted. Hold the button for 5 seconds."
            )
        )
    }

    @Test
    fun separatesInstructionWithoutWhitespaceAfterCjkPunctuation() {
        assertEquals(
            "記録を削除します。" to "確認するにはボタンを押し続けます。",
            splitUvirHoldConfirmationMessage(
                "記録を削除します。確認するにはボタンを押し続けます。"
            )
        )
    }

    @Test
    fun leavesSingleSentenceUnchanged() {
        assertEquals(
            "Hold the button." to null,
            splitUvirHoldConfirmationMessage("Hold the button.")
        )
    }
}
