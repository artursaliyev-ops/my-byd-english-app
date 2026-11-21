package app.bydplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbLoopTest {
    @Test fun firstMarkSetsA_noLoopYet() {
        val ab = AbLoop()
        ab.mark(10_000)
        assertEquals(10_000L, ab.a)
        assertNull(ab.b)
        assertFalse(ab.looping)
        assertNull(ab.jumpFor(60_000))
    }

    @Test fun secondMarkSetsB_andLoopsBackToA() {
        val ab = AbLoop()
        ab.mark(10_000)
        ab.mark(20_000)
        assertTrue(ab.looping)
        assertNull(ab.jumpFor(19_999))
        assertEquals(10_000L, ab.jumpFor(20_000))
        assertEquals(10_000L, ab.jumpFor(45_000))
    }

    @Test fun markTooCloseToOrBeforeA_movesA() {
        val ab = AbLoop()
        ab.mark(10_000)
        ab.mark(10_500)
        assertEquals(10_500L, ab.a)
        assertNull(ab.b)
        ab.mark(5_000)
        assertEquals(5_000L, ab.a)
        assertNull(ab.b)
    }

    @Test fun thirdMarkClears() {
        val ab = AbLoop()
        ab.mark(10_000)
        ab.mark(20_000)
        ab.mark(15_000)
        assertNull(ab.a)
        assertNull(ab.b)
        assertNull(ab.jumpFor(30_000))
    }
}
