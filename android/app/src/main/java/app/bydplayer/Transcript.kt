package app.bydplayer

import android.content.Context
import org.json.JSONObject
import java.io.FileNotFoundException

/** One Whisper segment of a lesson transcript. Times are in milliseconds. */
data class Segment(val startMs: Long, val endMs: Long, val text: String, val english: Boolean)

/**
 * The asset name of a lesson's transcript: course number 1 → "I_01", 31 → "II_01", 90 → "III_30".
 * Matches the files `tools/transcribe.py` writes into `transcripts/`.
 */
fun transcriptId(lessonNumber: Int): String? {
    val level = Level.entries.firstOrNull { lessonNumber in it.first..it.last } ?: return null
    return "%s_%02d".format(level.roman, lessonNumber - level.first + 1)
}

/**
 * Index of the segment to highlight at [positionMs]: the last one that has started.
 * In a pause between two segments the earlier one stays lit. Before the first segment: -1.
 * [segments] must be sorted by start.
 */
fun activeSegmentIndex(segments: List<Segment>, positionMs: Long): Int {
    var lo = 0
    var hi = segments.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (segments[mid].startMs <= positionMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found
}

/** Parses a transcript JSON (`{"segments":[{start,end,text,lang}]}`, seconds). */
fun parseTranscript(json: String): List<Segment> {
    val arr = JSONObject(json).getJSONArray("segments")
    return List(arr.length()) { i ->
        val s = arr.getJSONObject(i)
        Segment(
            startMs = (s.getDouble("start") * 1000).toLong(),
            endMs = (s.getDouble("end") * 1000).toLong(),
            text = s.getString("text").trim(),
            english = s.optString("lang") == "en",
        )
    }.sortedBy { it.startMs }
}

/** Loads the bundled transcript for a lesson, or null if the APK has none for it yet. */
fun loadTranscript(context: Context, lessonNumber: Int): List<Segment>? {
    val id = transcriptId(lessonNumber) ?: return null
    return try {
        context.assets.open("transcripts/$id.json").bufferedReader().use { parseTranscript(it.readText()) }
    } catch (_: FileNotFoundException) {
        null
    }
}
