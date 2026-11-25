package com.godskingdomnow.familybank.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BankDao {
    /* ---------- children ---------- */

    @Query(
        """
        SELECT c.id AS id, c.name AS name, c.remoteId AS remoteId,
               COALESCE((SELECT SUM(CASE WHEN e.deposit = 1 THEN e.amountCents ELSE -e.amountCents END)
                           FROM entries e WHERE e.childId = c.id AND e.deleted = 0), 0) AS balanceCents,
               (SELECT MAX(e.at) FROM entries e WHERE e.childId = c.id AND e.deleted = 0) AS lastAt
          FROM children c
         WHERE c.deleted = 0
         ORDER BY c.name COLLATE NOCASE
        """
    )
    fun observeChildren(): Flow<List<ChildRow>>

    @Query("SELECT * FROM children WHERE id = :id")
    fun observeChild(id: Long): Flow<ChildEntity?>

    @Query("SELECT * FROM children WHERE id = :id")
    suspend fun getChild(id: Long): ChildEntity?

    @Query("SELECT * FROM children")
    suspend fun allChildren(): List<ChildEntity>

    @Insert
    suspend fun insertChild(c: ChildEntity): Long

    @Update
    suspend fun updateChild(c: ChildEntity)

    @Query("DELETE FROM children WHERE id = :id")
    suspend fun deleteChildRow(id: Long)

    /* ---------- deposits and withdrawals ---------- */

    @Query("SELECT * FROM entries WHERE childId = :childId AND deleted = 0 ORDER BY at DESC, id DESC")
    fun observeEntries(childId: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE childId = :childId AND deleted = 0 ORDER BY at DESC, id DESC")
    suspend fun entriesOf(childId: Long): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getEntry(id: Long): EntryEntity?

    @Query("SELECT * FROM entries")
    suspend fun allEntries(): List<EntryEntity>

    @Insert
    suspend fun insertEntry(e: EntryEntity): Long

    @Insert
    suspend fun insertEntries(e: List<EntryEntity>)

    @Update
    suspend fun updateEntry(e: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntryRow(id: Long)

    @Query("DELETE FROM entries WHERE childId = :childId")
    suspend fun deleteEntriesOfChild(childId: Long)

    @Query("UPDATE entries SET childId = :to WHERE childId = :from")
    suspend fun moveEntries(from: Long, to: Long)

    /* ---------- signing out ---------- */

    @Query("DELETE FROM entries WHERE remoteId IS NOT NULL OR childId IN (SELECT id FROM children WHERE remoteId IS NOT NULL)")
    suspend fun deleteSyncedEntries()

    @Query("DELETE FROM children WHERE remoteId IS NOT NULL")
    suspend fun deleteSyncedChildren()
}
