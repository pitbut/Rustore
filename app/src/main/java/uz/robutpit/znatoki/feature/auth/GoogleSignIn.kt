package uz.robutpit.znatoki.feature.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import uz.robutpit.znatoki.BuildConfig

/** Вход через Google по Credential Manager: возвращает ID-токен, который проверяет наш сервер. */
object GoogleSignIn {
    val available: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    sealed interface Result {
        data class Token(val idToken: String) : Result
        data object Cancelled : Result
        data object Unavailable : Result
        data class Failed(val message: String) : Result
    }

    suspend fun signIn(activity: Activity): Result {
        if (!available) return Result.Unavailable
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val cred = CredentialManager.create(activity).getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Result.Token(GoogleIdTokenCredential.createFrom(cred.data).idToken)
            } else Result.Failed("unexpected credential")
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: NoCredentialException) {
            Result.Unavailable // нет Google-аккаунта / сервисов Google (например, Huawei)
        } catch (e: GetCredentialException) {
            Result.Failed(e.message ?: e.type)
        }
    }
}
