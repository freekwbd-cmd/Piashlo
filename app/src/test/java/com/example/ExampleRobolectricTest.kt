package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.ProviderType
import com.example.security.SecureKeyVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app_name resource and encrypted keystore vault roundtrip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PiashAgent", appName)

        val vault = SecureKeyVault(context)
        vault.saveApiKey(ProviderType.ANTHROPIC_CLAUDE, "sk-ant-test-key-987654321")
        assertTrue(vault.hasApiKey(ProviderType.ANTHROPIC_CLAUDE))
        assertEquals("sk-ant-test-key-987654321", vault.getApiKey(ProviderType.ANTHROPIC_CLAUDE))
        assertTrue(vault.maskKey(vault.getApiKey(ProviderType.ANTHROPIC_CLAUDE)).contains("••••"))
    }
}
