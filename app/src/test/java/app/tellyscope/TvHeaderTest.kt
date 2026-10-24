// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.ui.collapsedFraction
import app.tellyscope.ui.detailAlpha
import app.tellyscope.ui.titleAlpha
import org.junit.Assert.assertEquals
import org.junit.Test

class TvHeaderTest {
    @Test fun theHeroCollapsesWithTheScrollAndStaysCollapsedPastIt() {
        assertEquals(0f, collapsedFraction(0, 0, 600))
        assertEquals(0.5f, collapsedFraction(0, 300, 600))
        assertEquals(1f, collapsedFraction(0, 900, 600)) // the chips are still in view
        assertEquals(1f, collapsedFraction(1, 0, 600)) // the hero is gone
        // Not measured yet: drawn expanded, never divided by zero.
        assertEquals(0f, collapsedFraction(0, 120, 0))
    }

    @Test fun theDetailsGoFirstAndTheBarTitleComesLast() {
        assertEquals(1f, detailAlpha(0f), 0.001f)
        assertEquals(0f, detailAlpha(0.5f), 0.001f)
        assertEquals(0f, titleAlpha(0.5f), 0.001f)
        assertEquals(1f, titleAlpha(1f), 0.001f)
        // Never both out of sight with nothing in the bar: the title starts once the details are gone.
        assertEquals(0f, detailAlpha(0.67f), 0.001f)
    }
}
