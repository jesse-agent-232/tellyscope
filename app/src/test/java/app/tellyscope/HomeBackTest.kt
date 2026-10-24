// SPDX-License-Identifier: GPL-3.0-only

package app.tellyscope

import app.tellyscope.data.Action
import app.tellyscope.data.HistoryEntry
import app.tellyscope.data.HomeBackTarget
import app.tellyscope.data.homeBackTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeBackTest {
    private val stock = "com.google.android.tvlauncher"
    private val added = "com.spocky.projengmenu"

    private fun switch(id: Long, from: String, to: String, undoneAt: Long? = null) = HistoryEntry(
        id, "tv", id, Action.Home, from.substringBefore('/'), "Projectivy", Action.Enable, undoneAt,
        previous = mapOf("from" to from, "to" to to),
    )

    @Test fun goesBackToWhatTheSwitchReplaced() {
        val history = listOf(
            switch(1, "com.xgimi.home/.Main", "$added/.Home", undoneAt = 5),
            switch(7, "$stock/.MainActivity", "$added/.Home"),
        )
        assertEquals(HomeBackTarget(stock, "$stock/.MainActivity", 7), homeBackTarget(added, history, listOf("com.xgimi.home")))
    }

    @Test fun withoutASwitchGoesToTheTvsOwnEvenWhenOff() {
        // Projectivy was made Home on the TV itself: the stock launcher, now off, is the way back.
        assertEquals(HomeBackTarget(stock, null, null), homeBackTarget(added, emptyList(), listOf(stock)))
        // A switch that was undone, or one to another launcher, doesn't count.
        val other = listOf(switch(3, "$stock/.MainActivity", "com.other.home/.Main"), switch(2, "$stock/.MainActivity", "$added/.Home", undoneAt = 4))
        assertEquals(HomeBackTarget(stock, null, null), homeBackTarget(added, other, listOf(stock)))
    }

    @Test fun nothingToGoBackTo() {
        assertNull(homeBackTarget(added, emptyList(), emptyList()))
        assertNull(homeBackTarget(added, emptyList(), listOf(added)))
    }
}
