package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.crypto.CryptoEngine
import com.example.ui.SimpleMathEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Calculator", appName)
  }

  @Test
  fun `crypto engine encrypts and decrypts with valid room code`() {
    val secretMessage = "I love you 3000 - Stark Protocol"
    val roomCode = "MARK-85"

    val encrypted = CryptoEngine.encrypt(secretMessage, roomCode)
    assertNotEquals(secretMessage, encrypted.cipherTextBase64)

    val decrypted = CryptoEngine.decrypt(encrypted, roomCode)
    assertEquals(secretMessage, decrypted)

    // Decrypt with wrong room code should fail
    val invalidDecrypted = CryptoEngine.decrypt(encrypted, "WRONG_CODE")
    assertTrue(invalidDecrypted.startsWith("[ENCRYPTED_PAYLOAD"))
  }

  @Test
  fun `math evaluator computes standard expressions`() {
    val res1 = SimpleMathEvaluator.evaluate("2+2*3")
    assertEquals(8.0, res1, 0.001)

    val res2 = SimpleMathEvaluator.evaluate("100/4-5")
    assertEquals(20.0, res2, 0.001)
  }
}
