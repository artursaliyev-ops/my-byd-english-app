package app.bydplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscriptTest {
    @Test fun transcriptIdMapsCourseNumberToLevelFile() {
        assertEquals("I_01", transcriptId(1))
        assertEquals("I_30", transcriptId(30))
        assertEquals("II_01", transcriptId(31))
        assertEquals("III_30", transcriptId(90))
        assertNull(transcriptId(0))
        assertNull(transcriptId(91))
    }

    private val segs = listOf(
        Segment(4_000, 11_000, "a", false),
        Segment(11_000, 14_000, "b", true),
        Segment(15_000, 17_000, "c", true),
    )

    @Test fun beforeFirstSegmentNothingIsActive() {
        assertEquals(-1, activeSegmentIndex(segs, 0))
        assertEquals(-1, activeSegmentIndex(segs, 3_999))
        assertEquals(-1, activeSegmentIndex(emptyList(), 5_000))
    }

    @Test fun activeIsLastStartedSegment() {
        assertEquals(0, activeSegmentIndex(segs, 4_000))
        assertEquals(0, activeSegmentIndex(segs, 10_999))
        assertEquals(1, activeSegmentIndex(segs, 11_000))
        // In the pause between b and c, b stays lit.
        assertEquals(1, activeSegmentIndex(segs, 14_500))
        assertEquals(2, activeSegmentIndex(segs, 15_000))
        assertEquals(2, activeSegmentIndex(segs, 999_000))
    }
}
