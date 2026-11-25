package com.godskingdomnow.familybank.sync

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The family-app sign-in. Kept out of backups (see res/xml/backup_rules.xml). */
private val Context.sessionStore by preferencesDataStore(name = "session")

data class Session(
    val baseUrl: String = DEFAULT_BASE_URL,
    val token: String = "",
    val username: String = "",
    val displayName: String = "",
    val role: String = "",
    val familyName: String = "",
    val lastSyncAt: Long = 0,
    val lastError: String = "",
) {
    val signedIn get() = token.isNotEmpty()
    val isParent get() = role == "parent"

    /** May this phone change a child? Phone-only children always; family children only for parents. */
    fun canEdit(remoteId: Long?) = !signedIn || isParent || remoteId == null

    /** May this phone add a child? (Children signed in can only look.) */
    val canAdd get() = !signedIn || isParent

    companion object {
        const val DEFAULT_BASE_URL = "https://www.godskingdomnow.com/family/"
    }
}

class SessionStore(private val context: Context) {
    private object K {
        val baseUrl = stringPreferencesKey("base_url")
        val token = stringPreferencesKey("token")
        val username = stringPreferencesKey("username")
        val displayName = stringPreferencesKey("display_name")
        val role = stringPreferencesKey("role")
        val familyName = stringPreferencesKey("family_name")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastError = stringPreferencesKey("last_error")
    }

    val session: Flow<Session> = context.sessionStore.data.map { it.toSession() }

    suspend fun get(): Session = session.first()

    suspend fun update(change: (Session) -> Session) {
        context.sessionStore.edit { p ->
            val s = change(p.toSession())
            p[K.baseUrl] = s.baseUrl
            p[K.token] = s.token
            p[K.username] = s.username
            p[K.displayName] = s.displayName
            p[K.role] = s.role
            p[K.familyName] = s.familyName
            p[K.lastSyncAt] = s.lastSyncAt
            p[K.lastError] = s.lastError
        }
    }

    private fun Preferences.toSession() = Session(
        baseUrl = this[K.baseUrl] ?: Session.DEFAULT_BASE_URL,
        token = this[K.token] ?: "",
        username = this[K.username] ?: "",
        displayName = this[K.displayName] ?: "",
        role = this[K.role] ?: "",
        familyName = this[K.familyName] ?: "",
        lastSyncAt = this[K.lastSyncAt] ?: 0,
        lastError = this[K.lastError] ?: "",
    )
}
