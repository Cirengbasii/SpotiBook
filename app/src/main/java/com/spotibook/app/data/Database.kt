package com.spotibook.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(indices = [Index(value = ["hash"], unique = true)])
data class Book(
    @PrimaryKey val id: String, val title: String, val author: String = "Unknown author",
    val format: String, val fileName: String, val sourceUri: String = "", val hash: String,
    val cover: String? = null, val total: Int = 0, val position: Int = 0, val offset: Float = 0f,
    val zoom: Float = 1f, val progress: Float = 0f, val status: String = "To read",
    val added: Long = System.currentTimeMillis(), val opened: Long = 0, val completed: Long? = null
)
@Serializable @Entity data class Shelf(@PrimaryKey val id: String, val name: String, val icon: String = "Library")
@Serializable
@Entity(primaryKeys = ["bookId", "shelfId"], foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = Shelf::class, parentColumns = ["id"], childColumns = ["shelfId"], onDelete = ForeignKey.CASCADE)], indices = [Index("shelfId")])
data class BookShelf(val bookId: String, val shelfId: String)
@Serializable
@Entity(indices = [Index("bookId"), Index("date")])
data class Session(@PrimaryKey val id: String, val bookId: String, val bookTitle: String, val date: String,
    val start: Long, val end: Long, val millis: Long, val startPosition: Int, val endPosition: Int,
    val pages: Int = 0, val zone: String,
    @ColumnInfo(defaultValue = "0") val historyHidden: Boolean = false,
    @ColumnInfo(defaultValue = "''") val focusId: String = "")
@Serializable
@Entity(foreignKeys = [ForeignKey(entity = Book::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)], indices = [Index("bookId")])
data class Annotation(@PrimaryKey val id: String, val bookId: String, val kind: String, val position: Int,
    val offset: Float, val text: String = "", val note: String = "", val textStart: Int = -1, val textEnd: Int = -1,
    val color: String = "#E8C97D", val created: Long = System.currentTimeMillis())
@Serializable @Entity data class Achievement(@PrimaryKey val name: String, val unlocked: Long = System.currentTimeMillis())
@Serializable @Entity data class FocusRecord(@PrimaryKey val id: String, val bookId: String?, val plannedMillis: Long, val completed: Long)

@Dao interface LibraryDao {
    @Query("SELECT * FROM Book ORDER BY opened DESC, added DESC") fun books(): Flow<List<Book>>
    @Query("SELECT * FROM Shelf ORDER BY name") fun shelves(): Flow<List<Shelf>>
    @Query("SELECT * FROM BookShelf") fun memberships(): Flow<List<BookShelf>>
    @Query("SELECT * FROM Session ORDER BY start DESC") fun sessions(): Flow<List<Session>>
    @Query("SELECT * FROM Annotation ORDER BY created DESC") fun annotations(): Flow<List<Annotation>>
    @Query("SELECT * FROM Achievement ORDER BY unlocked") fun achievements(): Flow<List<Achievement>>
    @Query("SELECT * FROM Book WHERE id=:id") suspend fun book(id: String): Book?
    @Query("SELECT * FROM Book WHERE hash=:hash") suspend fun byHash(hash: String): Book?
    @Query("SELECT * FROM Session WHERE id=:id") suspend fun session(id: String): Session?
    @Query("SELECT * FROM Book") suspend fun allBooks(): List<Book>
    @Query("SELECT * FROM Shelf") suspend fun allShelves(): List<Shelf>
    @Query("SELECT * FROM BookShelf") suspend fun allMemberships(): List<BookShelf>
    @Query("SELECT * FROM Session") suspend fun allSessions(): List<Session>
    @Query("SELECT * FROM Annotation") suspend fun allAnnotations(): List<Annotation>
    @Query("SELECT * FROM Achievement") suspend fun allAchievements(): List<Achievement>
    @Query("SELECT * FROM FocusRecord") suspend fun allFocus(): List<FocusRecord>
    @Upsert suspend fun putBook(book: Book)
    @Upsert suspend fun putShelf(shelf: Shelf)
    @Upsert suspend fun putSession(session: Session)
    @Upsert suspend fun putAnnotation(annotation: Annotation)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putAchievement(achievement: Achievement)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putFocus(record: FocusRecord)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putMembership(link: BookShelf)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun putMemberships(links: List<BookShelf>)
    @Query("SELECT id FROM Book") suspend fun allBookIds(): List<String>
    @Query("DELETE FROM Book WHERE id=:id") suspend fun deleteBook(id: String)
    @Query("DELETE FROM Shelf WHERE id=:id") suspend fun deleteShelf(id: String)
    @Query("DELETE FROM BookShelf WHERE bookId=:book AND shelfId=:shelf") suspend fun unlink(book: String, shelf: String)
    @Query("DELETE FROM Annotation WHERE id=:id") suspend fun deleteAnnotation(id: String)
    @Query("UPDATE Session SET historyHidden=1 WHERE id IN (:ids)") suspend fun hideHistory(ids: List<String>)
    @Query("UPDATE Session SET historyHidden=1 WHERE date < :cutoff AND historyHidden=0") suspend fun expireHistory(cutoff: String)
    @Query("DELETE FROM BookShelf WHERE shelfId=:shelf AND bookId IN (:ids)") suspend fun unlinkBooks(ids: List<String>, shelf: String)
    @Query("DELETE FROM Book WHERE id IN (:ids)") suspend fun deleteBooks(ids: List<String>)
    @Query("DELETE FROM Session") suspend fun clearSessions()
    @Query("DELETE FROM Achievement") suspend fun clearAchievements()
    @Query("DELETE FROM FocusRecord") suspend fun clearFocus()
}
@Database(entities = [Book::class, Shelf::class, BookShelf::class, Session::class, Annotation::class, Achievement::class, FocusRecord::class], version = 2, exportSchema = true)
abstract class SpotibookDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Session ADD COLUMN historyHidden INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Session ADD COLUMN focusId TEXT NOT NULL DEFAULT ''")
            }
        }
        fun open(context: Context) = Room.databaseBuilder(context, SpotibookDatabase::class.java, "spotibook.db").addMigrations(MIGRATION_1_2).build()
    }
}
