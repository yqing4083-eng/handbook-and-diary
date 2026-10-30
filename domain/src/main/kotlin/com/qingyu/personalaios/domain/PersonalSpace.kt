package com.qingyu.personalaios.domain

data class SpacePost(val id: String, val createdAt: Long, val body: String, val images: List<String>)
data class PersonalSpace(val avatar: String? = null, val posts: List<SpacePost> = emptyList(), val name:String="Doki")
interface PersonalSpaceRepository {
    val space: kotlinx.coroutines.flow.Flow<PersonalSpace>
    suspend fun avatar(source: String)
    suspend fun publish(body: String, sources: List<String>)
    suspend fun deletePost(id: String)
    suspend fun rename(name:String)
}
