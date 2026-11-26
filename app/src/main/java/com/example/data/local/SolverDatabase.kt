package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "solve_sessions")
data class SolveSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val subjectName: String,
    val languageName: String,
    val previewImagePath: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = SolveSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val role: String,
    val text: String,
    val imagePath: String? = null,
    val languageLabel: String? = null,
    val subjectLabel: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class SessionWithCountRow(
    val id: Long,
    val title: String,
    val subjectName: String,
    val languageName: String,
    val previewImagePath: String?,
    val updatedAt: Long,
    val messageCount: Int
)

@Dao
interface SolverDao {
    @Query(
        """
        SELECT s.id, s.title, s.subjectName, s.languageName, s.previewImagePath, s.updatedAt,
               COUNT(m.id) AS messageCount
        FROM solve_sessions s
        LEFT JOIN chat_messages m ON s.id = m.sessionId
        GROUP BY s.id
        ORDER BY s.updatedAt DESC
        """
    )
    fun observeSessions(): Flow<List<SessionWithCountRow>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun observeMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SolveSessionEntity): Long

    @Query("UPDATE solve_sessions SET updatedAt = :updatedAt, subjectName = :subjectName, languageName = :languageName WHERE id = :sessionId")
    suspend fun updateSessionMetadata(
        sessionId: Long,
        updatedAt: Long,
        subjectName: String,
        languageName: String
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM solve_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}

@Database(
    entities = [SolveSessionEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SolverDatabase : RoomDatabase() {
    abstract fun solverDao(): SolverDao

    companion object {
        @Volatile
        private var INSTANCE: SolverDatabase? = null

        fun getInstance(context: Context): SolverDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SolverDatabase::class.java,
                    "subject_solver.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
