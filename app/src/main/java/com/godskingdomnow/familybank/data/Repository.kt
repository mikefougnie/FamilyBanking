package com.godskingdomnow.familybank.data

import androidx.room.withTransaction

/**
 * Every change the screens make goes through here. [onChange] asks the sync to run a few
 * seconds later (it does nothing unless the phone is signed in).
 */
class Repository(private val db: AppDatabase, private val onChange: () -> Unit) {
    private val dao = db.dao()

    val children = dao.observeChildren()
    fun child(id: Long) = dao.observeChild(id)
    fun entries(childId: Long) = dao.observeEntries(childId)
    suspend fun entriesOf(childId: Long) = dao.entriesOf(childId)

    suspend fun addChild(name: String): Long {
        val id = dao.insertChild(ChildEntity(name = name.trim().take(80)))
        onChange()
        return id
    }

    suspend fun renameChild(id: Long, name: String) {
        val c = dao.getChild(id) ?: return
        dao.updateChild(c.copy(name = name.trim().take(80), dirty = true, updatedAt = System.currentTimeMillis()))
        onChange()
    }

    /** Removes the child and their history. */
    suspend fun removeChild(id: Long) {
        db.withTransaction {
            val c = dao.getChild(id) ?: return@withTransaction
            if (c.remoteId == null) {
                dao.deleteEntriesOfChild(id)
                dao.deleteChildRow(id)
            } else {
                dao.updateChild(c.copy(deleted = true, dirty = true, updatedAt = System.currentTimeMillis()))
            }
        }
        onChange()
    }

    suspend fun addEntry(childId: Long, deposit: Boolean, cents: Long, note: String, at: Long) {
        dao.insertEntry(EntryEntity(childId = childId, deposit = deposit, amountCents = cents, note = note.trim().take(255), at = at))
        onChange()
    }

    suspend fun deleteEntry(id: Long) {
        val e = dao.getEntry(id) ?: return
        if (e.remoteId == null) dao.deleteEntryRow(id) else dao.updateEntry(e.copy(deleted = true, dirty = true))
        onChange()
    }
}
