package com.godskingdomnow.familybank.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A child whose money the parents are holding.
 *
 * [uid] is this phone's own id for the row (sent to the website so a repeated sync
 * never adds the same child twice). [remoteId] is the website's id once synced;
 * null means the child lives only on this phone.
 */
@Entity(tableName = "children", indices = [Index("remoteId"), Index(value = ["uid"], unique = true)])
data class ChildEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val remoteId: Long? = null,
    val name: String,
    /** Changed on the phone and not yet sent to the website. */
    val dirty: Boolean = true,
    /** Removed on the phone; the website is told on the next sync. */
    val deleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** One deposit (money the family now holds for the child) or withdrawal (money given back). */
@Entity(tableName = "entries", indices = [Index("childId"), Index("remoteId"), Index(value = ["uid"], unique = true)])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val remoteId: Long? = null,
    val childId: Long,
    val deposit: Boolean,
    val amountCents: Long,
    val note: String = "",
    /** When it happened (milliseconds). */
    val at: Long = System.currentTimeMillis(),
    /** Who recorded it (from the website), blank for this phone. */
    val byName: String = "",
    val dirty: Boolean = true,
    val deleted: Boolean = false,
) {
    val signedCents get() = if (deposit) amountCents else -amountCents
}

/** A child with their balance, for the home screen. */
data class ChildRow(
    val id: Long,
    val name: String,
    val remoteId: Long?,
    val balanceCents: Long,
    val lastAt: Long?,
)
