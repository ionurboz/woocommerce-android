package com.woocommerce.android.screenshots.orders

import android.view.View
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import com.woocommerce.android.R
import com.woocommerce.android.screenshots.util.Screen
import org.hamcrest.Matcher
import org.hamcrest.core.AllOf.allOf

class CustomerDetailsScreen : Screen(TOOLBAR) {
    companion object {
        const val ADDRESS_SWITCH = R.id.addressSwitch
        const val EDIT_TEXT = R.id.edit_text
        const val TOOLBAR = R.id.toolbar
        const val FIRST_NAME = "Mira"
        const val HINT_TEXT = "First name"
    }

    fun addCustomerDetails(): UnifiedOrderScreen {
        addFirstName(allOf(
            ViewMatchers.withId(EDIT_TEXT),
            ViewMatchers.withHint(HINT_TEXT),
            ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)
        ))

        Espresso.onView(allOf(
            ViewMatchers.withId(EDIT_TEXT),
            ViewMatchers.withText(FIRST_NAME),
            ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)
        ))
            .perform(closeSoftKeyboard())
        Espresso.onView(allOf(ViewMatchers.withId(ADDRESS_SWITCH)))
            .perform(scrollTo(), click())

        addFirstName(allOf(
            ViewMatchers.withId(EDIT_TEXT),
            ViewMatchers.withHint(HINT_TEXT),
            ViewMatchers.withText("")
        ))

        Espresso.onView(ViewMatchers.withText("DONE"))
            .check(ViewAssertions.matches(isDisplayed()))
            .perform(click())

        return UnifiedOrderScreen()
    }

    private fun addFirstName(matchers: Matcher<View>) {
        Espresso.onView(matchers)
            .perform(scrollTo(), click())
            .perform(replaceText(FIRST_NAME))
    }
}
