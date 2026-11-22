package com.example.ui.auth

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "FvgAuthHelper"

object AuthHelper {

    suspend fun attemptSilentGoogleSignIn(context: Context, auth: FirebaseAuth): Boolean {
        if (auth.currentUser != null) return true
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(context.getString(R.string.default_web_client_id))
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(context, request)
            val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            auth.signInWithCredential(firebaseCred).await()
            true
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No authorized Google credentials for silent sign-in: ${e.message}")
            false
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Silent sign-in cancelled")
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Silent sign-in skipped", e)
            false
        }
    }

    suspend fun signInWithGoogleExplicit(
        context: Context,
        auth: FirebaseAuth
    ): Result<Unit> {
        return try {
            val credentialManager = CredentialManager.create(context)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
                context.getString(R.string.default_web_client_id)
            ).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()
            val result = credentialManager.getCredential(context, request)
            val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            auth.signInWithCredential(firebaseCred).await()
            Result.success(Unit)
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "User cancelled Google Sign-In")
            Result.failure(CancelledAuthException())
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager Google Sign-In error", e)
            Result.failure(IllegalStateException(e.message ?: "Google Sign-In failed. Ensure a Google account is signed in on this device."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Google Sign-In error", e)
            Result.failure(e)
        }
    }

    suspend fun sendEmailVerification(auth: FirebaseAuth): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("No authenticated user")
        user.sendEmailVerification().await()
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun sendPasswordReset(auth: FirebaseAuth, email: String): Result<Unit> = runCatching {
        require(email.contains("@")) { "Valid email address is required" }
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun verifyBeforeUpdateEmail(auth: FirebaseAuth, newEmail: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("No authenticated user")
        require(newEmail.contains("@")) { "Valid new email address is required" }
        user.verifyBeforeUpdateEmail(newEmail.trim()).await()
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun reloadCurrentUser(auth: FirebaseAuth): Boolean = runCatching {
        auth.currentUser?.reload()?.await()
        true
    }.getOrDefault(false)

    fun currentDeviceLabel(): String {
        val manu = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        return listOf(manu, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android Device" }
    }

    suspend fun signOut(context: Context, auth: FirebaseAuth) {
        withContext(NonCancellable) {
            auth.signOut()
            try {
                CredentialManager.create(context)
                    .clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed clearing CredentialManager state", e)
            }
        }
    }

    class CancelledAuthException : Exception("Sign-in cancelled")
}
