package app.dudebooru.booru.engine.moebooru

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MoebooruPostDto(
    val id: Long,
    val tags: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
    val score: Int = 0,
    val source: String? = null,
    val md5: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("file_ext") val fileExt: String? = null,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("sample_url") val sampleUrl: String? = null,
    @SerialName("sample_width") val sampleWidth: Int? = null,
    @SerialName("sample_height") val sampleHeight: Int? = null,
    @SerialName("sample_file_size") val sampleFileSize: Long? = null,
    @SerialName("jpeg_url") val jpegUrl: String? = null,
    @SerialName("jpeg_width") val jpegWidth: Int? = null,
    @SerialName("jpeg_height") val jpegHeight: Int? = null,
    @SerialName("jpeg_file_size") val jpegFileSize: Long? = null,
    val rating: String? = null,
    @SerialName("has_children") val hasChildren: Boolean = false,
    @SerialName("parent_id") val parentId: Long? = null,
    val status: String? = null,
    val width: Int = 0,
    val height: Int = 0,
)

/** Ответ `post.json?api_version=2&include_tags=1&include_pools=1`. */
@Serializable
internal data class MoebooruPostsV2Dto(
    val posts: List<MoebooruPostDto> = emptyList(),
    @SerialName("pool_posts") val poolPosts: List<MoebooruPoolPostDto> = emptyList(),
    val pools: List<MoebooruPoolDto> = emptyList(),
    /** Имя тега → тип: `artist`, `copyright`, `character`, `circle`, `general`, `faults`. */
    val tags: Map<String, String> = emptyMap(),
)

@Serializable
internal data class MoebooruPoolPostDto(
    @SerialName("post_id") val postId: Long,
    @SerialName("pool_id") val poolId: Long,
)

@Serializable
internal data class MoebooruTagDto(
    val name: String,
    val count: Long? = null,
    val type: Int = 0,
)

@Serializable
internal data class MoebooruArtistDto(
    val id: Long,
    val name: String,
    @SerialName("alias_id") val aliasId: Long? = null,
    @SerialName("group_id") val groupId: Long? = null,
    val urls: List<String> = emptyList(),
)

@Serializable
internal data class MoebooruUserDto(
    val id: Long,
    val name: String,
)

@Serializable
internal data class MoebooruVoteDto(
    val success: Boolean = false,
    val vote: Int? = null,
)

@Serializable
internal data class MoebooruTagSummaryDto(
    val version: Long = 0,
    val data: String = "",
)

@Serializable
internal data class MoebooruPoolDto(
    val id: Long,
    val name: String = "",
    @SerialName("post_count") val postCount: Int? = null,
)
