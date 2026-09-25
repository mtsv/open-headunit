package com.andrerinas.openheadunit.main

/**
 * The line under the track title in the media notification.
 *
 * Android Auto sends the artist and the album as separate fields and either can be missing, so the
 * separator has to be decided from what actually arrived rather than baked into a format string.
 * The three cases that matter are the lopsided ones: an artist with no album must not trail a
 * dangling " - ", and an album with no artist must not lead with one.
 *
 * Only the notification is joined like this. The media session keeps
 * [android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST] and `METADATA_KEY_ALBUM`
 * apart, because everything reading it - the car's own UI included - lays them out itself, and a
 * pre-joined string would strip it of that choice.
 */
object MediaNotificationTextPolicy {

    const val SEPARATOR = " - "

    /**
     * `artist - album`, or whichever of the two is present on its own, or an empty string.
     *
     * Blank is treated as absent throughout: Android Auto sends an empty field rather than omitting
     * it, and a whitespace-only value would otherwise produce a line that is a separator and
     * nothing else.
     */
    fun artistLine(artist: String?, album: String?): String {
        val trimmedArtist = artist?.trim().orEmpty()
        val trimmedAlbum = album?.trim().orEmpty()
        return when {
            trimmedArtist.isNotEmpty() && trimmedAlbum.isNotEmpty() ->
                trimmedArtist + SEPARATOR + trimmedAlbum
            trimmedArtist.isNotEmpty() -> trimmedArtist
            else -> trimmedAlbum
        }
    }
}
