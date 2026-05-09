package fi.kotinaytto.tv.ui

import fi.kotinaytto.tv.BuildConfig

fun familyPhotoPublicUrl(storagePath: String): String {
    val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    return "$base/storage/v1/object/public/family_photos/$storagePath"
}
