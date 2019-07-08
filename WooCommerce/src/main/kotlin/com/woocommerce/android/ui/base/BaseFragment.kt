package com.woocommerce.android.ui.base

/**
 * All top level fragments and child fragments should extend this class to provide a consistent method
 * of setting the activity title
 */
abstract class BaseFragment : androidx.fragment.app.Fragment(), BaseFragmentView {
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            updateActivityTitle()
        }
    }

    override fun onResume() {
        super.onResume()
        updateActivityTitle()
    }

    fun updateActivityTitle() {
        (this as? TopLevelFragment)?.let {
            if (!it.isActive) {
                return
            }
        }
        if (isAdded && !isHidden) {
            activity?.title = getFragmentTitle()
        }
    }
}
