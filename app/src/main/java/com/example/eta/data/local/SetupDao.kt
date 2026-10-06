package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.setup.UserSetup
import kotlinx.coroutines.flow.Flow

/** One row, keyed by `SETUP_ID` — there is one user and one configuration. */
@Dao
interface SetupDao {

    @Upsert
    suspend fun upsert(setup: UserSetup)

    @Query("SELECT * FROM user_setup WHERE id = :id")
    fun observe(id: Int): Flow<UserSetup?>

    @Query("SELECT * FROM user_setup WHERE id = :id")
    suspend fun find(id: Int): UserSetup?
}
