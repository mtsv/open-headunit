package com.andrerinas.openheadunit.main

import java.util.Locale

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

    /**
     * `title [m:ss]`, or `title [h:mm:ss]` once the track runs an hour or longer.
     *
     * Android Auto sends the duration as a separate field, and the media session already carries it
     * as `METADATA_KEY_DURATION` for anything that lays out its own transport bar. This is for the
     * readers that show a title and nothing else - a plain notification row, a head unit's
     * single-line now-playing - where the length is otherwise invisible.
     *
     * A non-positive duration appends nothing: proto2 hands back 0 for an unset optional field, so
     * "no duration" and "zero seconds" arrive identically and neither is worth a `[00:00]`.
     */
    fun titleWithDuration(title: String, durationSeconds: Long): String {
        if (durationSeconds <= 0L) return title
        return "$title [${formatDuration(durationSeconds)}]"
    }

    /**
     * `m:ss` under an hour, `h:mm:ss` at or above it.
     *
     * The leading field is never padded and every field after it always is, which is how a track
     * length is written everywhere else a listener sees one - `3:45`, not `03:45`. Only the leading
     * field changes width, so the colons stay where the eye expects them.
     *
     * [Locale.US] rather than the default: the digits here are a timestamp, and a device set to a
     * locale with its own numerals would otherwise render them in a script the rest of the title is
     * not written in.
     */
    fun formatDuration(durationSeconds: Long): String {
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
