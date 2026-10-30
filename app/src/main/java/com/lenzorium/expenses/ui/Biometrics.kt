package com.lenzorium.expenses.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object Biometrics {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun findActivity(context: Context): FragmentActivity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }

    fun prompt(context: Context, title: String, onSuccess: () -> Unit, onFail: (String) -> Unit = {}) {
        val activity = findActivity(context) ?: return onFail("Biometrics unavailable")
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Lenzorium Expenses")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(AUTH)
            .build()
        prompt.authenticate(info)
    }
}
