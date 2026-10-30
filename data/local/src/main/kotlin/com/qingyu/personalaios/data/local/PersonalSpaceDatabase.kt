package com.qingyu.personalaios.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.room.*
import com.qingyu.personalaios.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID

@Entity(tableName = "profile") data class ProfileEntity(@PrimaryKey val id: Int = 0, val avatar: String)
@Entity(tableName = "posts") data class PostEntity(@PrimaryKey val id: String, val createdAt: Long, val body: String, val images: String)
@Dao interface PersonalSpaceDao {
    @Query("SELECT * FROM profile WHERE id = 0") fun profile(): Flow<ProfileEntity?>
    @Query("SELECT * FROM posts ORDER BY createdAt DESC, id DESC") fun posts(): Flow<List<PostEntity>>
    @Upsert suspend fun profile(value: ProfileEntity)
    @Insert suspend fun post(value: PostEntity)
    @Query("DELETE FROM posts WHERE id = :id") suspend fun deletePost(id: String)
}
@Database(entities = [ProfileEntity::class, PostEntity::class], version = 1, exportSchema = true)
abstract class PersonalSpaceDatabase : RoomDatabase() { abstract fun dao(): PersonalSpaceDao }

class LocalPersonalSpaceRepository(context: Context) : PersonalSpaceRepository {
    private val app = context.applicationContext
    private val db = Room.databaseBuilder(app, PersonalSpaceDatabase::class.java, "personal-space.db").build()
    private val dao = db.dao()
    private val preferences=SpacePreferences(app)
    override val space = combine(dao.profile(), dao.posts(),preferences.userName) { profile, posts,name ->
        PersonalSpace(profile?.avatar, posts.map { row ->
            val json = JSONArray(row.images)
            SpacePost(row.id, row.createdAt, row.body, List(json.length()) { json.getString(it) })
        },name)
    }
    private fun import(source: String, maxSize: Int): File {
        val directory = File(app.filesDir, "space-media").apply { mkdirs() }
        val result = File(directory, "${UUID.randomUUID()}.jpg")
        val temporary = File(directory, "${result.name}.tmp")
        try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, Uri.parse(source))) { decoder, info, _ ->
                val ratio = minOf(1f, maxSize.toFloat() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize(maxOf(1, (info.size.width * ratio).toInt()), maxOf(1, (info.size.height * ratio).toInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try { temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)) } }
            finally { bitmap.recycle() }
            check(temporary.renameTo(result))
            return result
        } catch (error: Throwable) { temporary.delete(); result.delete(); throw error }
    }
    override suspend fun avatar(source: String) = withContext(Dispatchers.IO) {
        val file = import(source, 1024)
        try { withContext(NonCancellable) { dao.profile(ProfileEntity(avatar = file.absolutePath)) } }
        catch (error: Throwable) { file.delete(); throw error }
    }
    override suspend fun publish(body: String, sources: List<String>) = withContext(Dispatchers.IO) {
        require(body.isNotBlank() || sources.isNotEmpty())
        require(sources.size <= PhotoPageFill.MaxImages && body.length <= 10000)
        val files = mutableListOf<File>()
        try {
            sources.forEach { files += import(it, 2048) }
            withContext(NonCancellable) { dao.post(PostEntity(UUID.randomUUID().toString(), System.currentTimeMillis(), body.trim(), JSONArray(files.map { it.absolutePath }).toString())) }
        } catch (error: Throwable) { files.forEach { it.delete() }; throw error }
    }
    override suspend fun deletePost(id: String) = withContext(Dispatchers.IO) {
        // Keep imported media: another saved item may reference the same image.
        dao.deletePost(id)
    }
    override suspend fun rename(name:String) { preferences.setUserName(name) }
}
