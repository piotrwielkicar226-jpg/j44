package pl.piotr.mailbox

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("mail_account", Context.MODE_PRIVATE)

    fun save(config: MailConfig) {
        val encryptedPassword = encrypt(config.password)
        preferences.edit()
            .putString("email", config.email)
            .putString("imapHost", config.imapHost)
            .putInt("imapPort", config.imapPort)
            .putString("smtpHost", config.smtpHost)
            .putInt("smtpPort", config.smtpPort)
            .putString("password", encryptedPassword)
            .apply()
    }

    fun load(): MailConfig? {
        return try {
            val email = preferences.getString("email", null) ?: return null
            val imapHost = preferences.getString("imapHost", null) ?: return null
            val smtpHost = preferences.getString("smtpHost", null) ?: return null
            val encryptedPassword = preferences.getString("password", null) ?: return null
            MailConfig(
                email = email,
                password = decrypt(encryptedPassword),
                imapHost = imapHost,
                imapPort = preferences.getInt("imapPort", 993),
                smtpHost = smtpHost,
                smtpPort = preferences.getInt("smtpPort", 587)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12) { "Encrypted password is invalid" }
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val currentKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (currentKey != null) return currentKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val parameters = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)
            .build()
        generator.init(parameters)
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "poczta-mail-password-key"
    }
}
