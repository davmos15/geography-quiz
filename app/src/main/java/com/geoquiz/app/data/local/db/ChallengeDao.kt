package com.geoquiz.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {
    @Query("SELECT * FROM challenges ORDER BY createdAtMillis DESC")
    fun getAllChallenges(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun getChallengeById(id: String): ChallengeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChallenge(challenge: ChallengeEntity)

    @Query("UPDATE challenges SET myScore = :score, myTotal = :total, myTime = :time, status = 'completed' WHERE id = :id")
    suspend fun updateMyResult(id: String, score: Int, total: Int, time: Int)
}
