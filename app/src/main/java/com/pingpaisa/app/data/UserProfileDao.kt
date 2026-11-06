package com.pingpaisa.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    /** Emits null while no account exists, so the app knows to show sign-up. */
    @Query("SELECT * FROM user_profile WHERE id = ${UserProfile.SINGLE_USER_ID}")
    fun observe(): Flow<UserProfile?>

    /**
     * Creates the account only if none exists yet. Returns the new row id, or -1
     * if an account was already there (nothing is changed). Because there is one
     * fixed row id, this is a single atomic step: it can never overwrite an
     * existing account's password.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(profile: UserProfile): Long

    /** Creates the account, or replaces it (used by "change password" / profile edits). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: UserProfile)

    @Query("SELECT * FROM user_profile WHERE id = ${UserProfile.SINGLE_USER_ID}")
    suspend fun get(): UserProfile?

    @Query("SELECT EXISTS(SELECT 1 FROM user_profile)")
    suspend fun exists(): Boolean

    @Query("UPDATE user_profile SET name = :name, monthlySalary = :salary WHERE id = ${UserProfile.SINGLE_USER_ID}")
    suspend fun updateNameAndSalary(name: String, salary: Double)

    @Query("UPDATE user_profile SET appLockEnabled = :enabled WHERE id = ${UserProfile.SINGLE_USER_ID}")
    suspend fun setAppLock(enabled: Boolean)

    @Query("UPDATE user_profile SET biometricEnabled = :enabled WHERE id = ${UserProfile.SINGLE_USER_ID}")
    suspend fun setBiometric(enabled: Boolean)

    @Query("DELETE FROM user_profile")
    suspend fun delete()
}
