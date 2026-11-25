package com.godskingdomnow.familybank.sync

import android.os.Build
import androidx.room.withTransaction
import com.godskingdomnow.familybank.data.AppDatabase
import com.godskingdomnow.familybank.data.ChildEntity
import com.godskingdomnow.familybank.data.EntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Optional sync with the Family Bank on the website. Nothing here runs unless the phone is signed in.
 *
 * One sync = send what a parent changed on this phone, then take the website's full copy
 * of the family's bank. The website is the record: a child or entry removed there is
 * removed here too.
 */
class SyncManager(
    private val db: AppDatabase,
    private val api: SyncApi,
    private val sessions: SessionStore,
    private val scope: CoroutineScope,
) {
    private val dao = db.dao()
    private val mutex = Mutex()
    private var pending: Job? = null

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    /** Called after every local change: sync a few seconds later (changes are batched). */
    fun requestSoon() {
        pending?.cancel()
        pending = scope.launch {
            delay(3000)
            if (sessions.get().signedIn) syncNow()
        }
    }

    sealed interface Outcome {
        data object Ok : Outcome
        data class Failed(val message: String) : Outcome
        data object NotSignedIn : Outcome
    }

    suspend fun signIn(baseUrl: String, username: String, password: String): Outcome {
        val url = normalizeUrl(baseUrl)
        return try {
            val r = api.login(url, username.trim(), password, "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            val token = r.token
            if (!r.ok || token.isNullOrEmpty()) return Outcome.Failed(r.error ?: "Couldn’t sign in.")
            val u = r.user ?: UserDto()
            sessions.update {
                it.copy(baseUrl = url, token = token, username = u.username, displayName = u.displayName,
                    role = u.role, familyName = u.familyName, lastSyncAt = 0, lastError = "")
            }
            syncNow()
            Outcome.Ok
        } catch (e: IOException) {
            Outcome.Failed(networkMessage(e))
        }
    }

    /** Signs out. The family's children leave this phone (they're safe on the website); phone-only ones stay. */
    suspend fun signOut() = mutex.withLock {
        val s = sessions.get()
        if (s.signedIn) runCatching { api.logout(s.baseUrl, s.token) }   // best effort; works offline too
        db.withTransaction {
            dao.deleteSyncedEntries()
            dao.deleteSyncedChildren()
        }
        sessions.update { it.copy(token = "", lastSyncAt = 0, lastError = "", role = "", familyName = "", displayName = "") }
    }

    suspend fun syncNow(): Outcome = mutex.withLock {
        val s = sessions.get()
        if (!s.signedIn) return Outcome.NotSignedIn
        _syncing.value = true
        try {
            runSync(s)
            Outcome.Ok
        } catch (e: SignedOutException) {
            sessions.update { it.copy(token = "", lastError = e.message ?: "Signed out.") }
            db.withTransaction { dao.deleteSyncedEntries(); dao.deleteSyncedChildren() }
            Outcome.Failed(e.message ?: "Signed out.")
        } catch (e: IOException) {
            val msg = networkMessage(e)
            sessions.update { it.copy(lastError = msg) }
            Outcome.Failed(msg)
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun runSync(s: Session) {
        // ---------- 1. what a parent changed on this phone ----------
        val children = dao.allChildren()
        val childById = children.associateBy { it.id }
        val sentKids: List<ChildEntity>
        val sentEntries: List<EntryEntity>
        if (s.isParent) {
            sentKids = children.filter { (it.remoteId == null && !it.deleted) || (it.remoteId != null && it.dirty) }
            sentEntries = dao.allEntries().filter { e ->
                val c = childById[e.childId] ?: return@filter false
                if (c.deleted) false
                else (e.remoteId == null && !e.deleted) || (e.remoteId != null && e.deleted)
            }
        } else {
            sentKids = emptyList(); sentEntries = emptyList()   // children see the bank read-only
        }
        val req = SyncReq(
            kids = sentKids.map { KidDto(cid = it.uid, rid = it.remoteId, name = it.name, deleted = if (it.deleted) 1 else 0) },
            entries = sentEntries.map { e ->
                val c = childById.getValue(e.childId)
                EntryDto(cid = e.uid, rid = e.remoteId, kidRid = c.remoteId, kidCid = c.uid,
                    type = if (e.deposit) "deposit" else "withdraw", amount = e.amountCents / 100.0,
                    note = e.note, at = e.at, deleted = if (e.deleted) 1 else 0)
            },
        )

        // ---------- 2. send it, get the family's bank back ----------
        val res = api.sync(s.baseUrl, s.token, req)
        if (!res.ok) throw IOException(res.error ?: "The website couldn’t sync.")

        // ---------- 3. take the website's copy ----------
        db.withTransaction {
            // children this phone sent
            sentKids.forEach { sent ->
                val cur = dao.getChild(sent.id) ?: return@forEach
                if (sent.deleted) { dao.deleteEntriesOfChild(cur.id); dao.deleteChildRow(cur.id); return@forEach }
                val rid = res.map.kids[sent.uid] ?: cur.remoteId ?: return@forEach
                // the website joined it to a child of the same name that this phone already has
                val twin = dao.allChildren().firstOrNull { it.remoteId == rid && it.id != cur.id }
                if (twin != null) {
                    dao.moveEntries(cur.id, twin.id)
                    dao.deleteChildRow(cur.id)
                } else {
                    dao.updateChild(cur.copy(remoteId = rid, dirty = cur.dirty && cur.updatedAt != sent.updatedAt))
                }
            }
            // entries this phone sent
            sentEntries.forEach { sent ->
                val cur = dao.getEntry(sent.id) ?: return@forEach
                if (sent.deleted) { dao.deleteEntryRow(cur.id); return@forEach }
                val rid = res.map.entries[sent.uid] ?: return@forEach   // refused: kept on the phone
                dao.updateEntry(cur.copy(remoteId = rid, dirty = false))
            }

            // the family's children
            val localKids = dao.allChildren()
            val byRemote = localKids.filter { it.remoteId != null }.associateBy { it.remoteId!! }
            val serverKidIds = HashSet<Long>()
            val localIdOf = HashMap<Long, Long>()
            res.kids.forEach { k ->
                val rid = k.rid ?: return@forEach
                serverKidIds += rid
                val local = byRemote[rid]
                localIdOf[rid] = when {
                    local == null -> dao.insertChild(ChildEntity(remoteId = rid, name = k.name, dirty = false))
                    local.dirty -> local.id    // changed again during the sync: send next time
                    else -> {
                        if (local.name != k.name || local.deleted) dao.updateChild(local.copy(name = k.name, deleted = false))
                        local.id
                    }
                }
            }
            // removed on the website
            localKids.filter { it.remoteId != null && it.remoteId !in serverKidIds }.forEach {
                dao.deleteEntriesOfChild(it.id); dao.deleteChildRow(it.id)
            }

            // the family's deposits and withdrawals
            val localEntries = dao.allEntries()
            val entryByRemote = localEntries.filter { it.remoteId != null }.associateBy { it.remoteId!! }
            val serverEntryIds = HashSet<Long>()
            val inserts = ArrayList<EntryEntity>()
            res.entries.forEach { e ->
                val rid = e.rid ?: return@forEach
                val childId = e.kidRid?.let { localIdOf[it] } ?: return@forEach
                serverEntryIds += rid
                val fresh = EntryEntity(remoteId = rid, childId = childId, deposit = e.type != "withdraw",
                    amountCents = Math.round(e.amount * 100), note = e.note, at = e.at, byName = e.who, dirty = false)
                val local = entryByRemote[rid]
                when {
                    local == null -> inserts += fresh
                    local.deleted && local.dirty -> Unit   // deleted here during the sync: send next time
                    local != fresh.copy(id = local.id, uid = local.uid) -> dao.updateEntry(fresh.copy(id = local.id, uid = local.uid))
                }
            }
            if (inserts.isNotEmpty()) dao.insertEntries(inserts)
            localEntries.filter { it.remoteId != null && it.remoteId !in serverEntryIds }.forEach { dao.deleteEntryRow(it.id) }
        }

        val u = res.user
        sessions.update {
            it.copy(lastSyncAt = System.currentTimeMillis(), lastError = if (res.skipped > 0)
                "${res.skipped} entr${if (res.skipped == 1) "y was" else "ies were"} not saved by the website (the child may have been removed there)." else "",
                displayName = u?.displayName ?: it.displayName, role = u?.role ?: it.role,
                familyName = u?.familyName ?: it.familyName)
        }
    }

    companion object {
        fun normalizeUrl(u: String): String {
            var url = u.trim().ifEmpty { Session.DEFAULT_BASE_URL }
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            if (url.startsWith("http://")) url = "https://" + url.removePrefix("http://") // signing in only over HTTPS
            return if (url.endsWith("/")) url else "$url/"
        }

        fun networkMessage(e: IOException): String = when (e) {
            is java.net.UnknownHostException -> "No internet connection (or the website address is wrong)."
            is java.net.SocketTimeoutException -> "The website took too long to answer."
            else -> e.message ?: "Couldn’t reach the website."
        }
    }
}
