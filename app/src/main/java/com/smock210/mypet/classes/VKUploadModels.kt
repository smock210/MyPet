package com.smock210.mypet.classes

/** Ответ photos.getWallUploadServer (FIX_PLAN.md, 6.7). */
data class VKServerUploadInfo(
    val uploadUrl: String,
    val albumId: Int,
    val userId: Int
)

/** Ответ загрузки файла на сервер VK. */
data class VKFileUploadInfo(
    val server: String,
    val photo: String,
    val hash: String
)

/** Ответ photos.saveWallPhoto. */
data class VKSaveInfo(
    val id: Int,
    val albumId: Int,
    val ownerId: Int
) {
    /** Готовое вложение для wall.post, например "photo-123_456". */
    fun getAttachment(): String = "photo$ownerId" + "_" + id
}
