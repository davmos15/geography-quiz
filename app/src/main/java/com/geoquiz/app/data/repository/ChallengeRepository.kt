package com.geoquiz.app.data.repository

import com.geoquiz.app.data.local.db.ChallengeDao
import com.geoquiz.app.data.local.db.ChallengeEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChallengeRepository @Inject constructor(
    private val challengeDao: ChallengeDao
) {
    val allChallenges: Flow<List<ChallengeEntity>> = challengeDao.getAllChallenges()

    suspend fun createOutgoingChallenge(
        categoryType: String,
        categoryValue: String,
        categoryDisplayName: String,
        challengerName: String,
        score: Int?,
        total: Int?,
        time: Int?
    ): ChallengeEntity {
        val challenge = ChallengeEntity(
            id = UUID.randomUUID().toString(),
            categoryType = categoryType,
            categoryValue = categoryValue,
            categoryDisplayName = categoryDisplayName,
            challengerName = challengerName,
            challengerScore = score,
            challengerTotal = total,
            challengerTime = time,
            myScore = score,
            myTotal = total,
            myTime = time,
            direction = "outgoing",
            status = if (score != null) "completed" else "pending",
            createdAtMillis = System.currentTimeMillis()
        )
        challengeDao.insertChallenge(challenge)
        return challenge
    }

    suspend fun createIncomingChallenge(
        id: String,
        categoryType: String,
        categoryValue: String,
        categoryDisplayName: String,
        challengerName: String,
        challengerScore: Int?,
        challengerTotal: Int?,
        challengerTime: Int?
    ): ChallengeEntity {
        val challenge = ChallengeEntity(
            id = id,
            categoryType = categoryType,
            categoryValue = categoryValue,
            categoryDisplayName = categoryDisplayName,
            challengerName = challengerName,
            challengerScore = challengerScore,
            challengerTotal = challengerTotal,
            challengerTime = challengerTime,
            myScore = null,
            myTotal = null,
            myTime = null,
            direction = "incoming",
            status = "pending",
            createdAtMillis = System.currentTimeMillis()
        )
        challengeDao.insertChallenge(challenge)
        return challenge
    }

    suspend fun updateMyResult(id: String, score: Int, total: Int, time: Int) {
        challengeDao.updateMyResult(id, score, total, time)
    }

    suspend fun getChallengeById(id: String): ChallengeEntity? {
        return challengeDao.getChallengeById(id)
    }
}
