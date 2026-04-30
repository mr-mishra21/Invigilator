package app.invigilator.core.consent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class ConsentTextResult(
    val text: String,
    /** SHA-256 hex digest of [text] encoded as UTF-8. This is the hash stored in the consent record. */
    val hash: String,
)

/**
 * Resolves a [ConsentType] + version + language tag to the consent text asset,
 * computes its SHA-256, and returns both. Falls back to "en" if the requested
 * language file does not exist.
 */
@Singleton
internal class ConsentTextProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun resolve(type: ConsentType, version: String, language: String): ConsentTextResult? {
        val text = readAsset(assetName(type, version, language))
            ?: readAsset(assetName(type, version, "en"))
            ?: return null
        return ConsentTextResult(text = text, hash = sha256(text))
    }

    private fun assetName(type: ConsentType, version: String, language: String) =
        "${type.assetPrefix}_${version}_${language}.txt"

    private fun readAsset(filename: String): String? = try {
        context.assets.open("consents/$filename").bufferedReader(Charsets.UTF_8).readText()
    } catch (e: IOException) {
        null
    }

    internal fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
