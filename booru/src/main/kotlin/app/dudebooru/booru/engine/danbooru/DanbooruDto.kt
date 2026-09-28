package app.dudebooru.booru.engine.danbooru

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class DanbooruPostDto(
    val id: Long,
    @SerialName("created_at") val createdAt: String? = null,
    val score: Int = 0,
    val source: String? = null,
    val md5: String? = null,
    val rating: String? = null,
    @SerialName("image_width") val imageWidth: Int = 0,
    @SerialName("image_height") val imageHeight: Int = 0,
    @SerialName("fav_count") val favCount: Int? = null,
    @SerialName("file_ext") val fileExt: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("parent_id") val parentId: Long? = null,
    @SerialName("has_children") val hasChildren: Boolean = false,
    @SerialName("has_active_children") val hasActiveChildren: Boolean? = null,
    @SerialName("pixiv_id") val pixivId: Long? = null,
    @SerialName("is_pending") val isPending: Boolean = false,
    @SerialName("is_flagged") val isFlagged: Boolean = false,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("tag_string_general") val tagsGeneral: String = "",
    @SerialName("tag_string_artist") val tagsArtist: String = "",
    @SerialName("tag_string_character") val tagsCharacter: String = "",
    @SerialName("tag_string_copyright") val tagsCopyright: String = "",
    @SerialName("tag_string_meta") val tagsMeta: String = "",
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("large_file_url") val largeFileUrl: String? = null,
    @SerialName("preview_file_url") val previewFileUrl: String? = null,
    @SerialName("media_asset") val mediaAsset: DanbooruMediaAssetDto? = null,
)

@Serializable
internal data class DanbooruMediaAssetDto(
    val variants: List<DanbooruVariantDto> = emptyList(),
)

@Serializable
internal data class DanbooruVariantDto(
    val type: String,
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("file_ext") val fileExt: String? = null,
)

@Serializable
internal data class DanbooruAutocompleteDto(
    val type: String? = null,
    val label: String? = null,
    val value: String,
    val category: Int? = null,
    @SerialName("post_count") val postCount: Long? = null,
    val antecedent: String? = null,
)

@Serializable
internal data class DanbooruTagDto(
    val name: String,
    @SerialName("post_count") val postCount: Long? = null,
    val category: Int = 0,
)

@Serializable
internal data class DanbooruArtistDto(
    val name: String,
    @SerialName("other_names") val otherNames: List<String> = emptyList(),
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("is_banned") val isBanned: Boolean = false,
    val urls: List<DanbooruArtistUrlDto> = emptyList(),
)

@Serializable
internal data class DanbooruArtistUrlDto(
    val url: String,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
internal data class DanbooruProfileDto(
    val id: Long? = null,
    val name: String? = null,
    val level: Int? = null,
    @SerialName("level_string") val levelString: String? = null,
    /** У Platinum и выше лимита нет: сайт присылает null (Float::INFINITY). */
    @SerialName("tag_query_limit") val tagQueryLimit: JsonElement? = null,
    @SerialName("blacklisted_tags") val blacklistedTags: String? = null,
    @SerialName("favorite_count") val favoriteCount: Int? = null,
)

@Serializable
internal data class DanbooruPoolDto(
    val id: Long,
    val name: String,
    @SerialName("post_count") val postCount: Int? = null,
)
