package com.woocommerce.android.ui.payments.cardreader.hub

import androidx.annotation.DrawableRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.AppPrefsWrapper
import com.woocommerce.android.AppUrls
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTrackerWrapper
import com.woocommerce.android.model.UiString
import com.woocommerce.android.model.UiString.UiStringRes
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.payments.cardreader.CardReaderTracker
import com.woocommerce.android.ui.payments.cardreader.CashOnDeliverySettingsRepository
import com.woocommerce.android.ui.payments.cardreader.InPersonPaymentsCanadaFeatureFlag
import com.woocommerce.android.ui.payments.cardreader.hub.CardReaderHubViewModel.CardReaderHubViewState.ListItem.NonToggleableListItem
import com.woocommerce.android.ui.payments.cardreader.hub.CardReaderHubViewModel.CardReaderHubViewState.ListItem.ToggleableListItem
import com.woocommerce.android.ui.payments.cardreader.hub.CardReaderHubViewModel.CardReaderHubViewState.OnboardingErrorAction
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderFlowParam
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderOnboardingChecker
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderOnboardingState
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderOnboardingState.OnboardingCompleted
import com.woocommerce.android.ui.payments.cardreader.onboarding.CardReaderOnboardingState.StripeAccountPendingRequirement
import com.woocommerce.android.ui.payments.cardreader.onboarding.PluginType.STRIPE_EXTENSION_GATEWAY
import com.woocommerce.android.ui.payments.cardreader.onboarding.PluginType.WOOCOMMERCE_PAYMENTS
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.WooLog.T.CARD_READER
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.wordpress.android.fluxc.store.WooCommerceStore
import javax.inject.Inject

@HiltViewModel
class CardReaderHubViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val inPersonPaymentsCanadaFeatureFlag: InPersonPaymentsCanadaFeatureFlag,
    private val appPrefsWrapper: AppPrefsWrapper,
    private val selectedSite: SelectedSite,
    private val analyticsTrackerWrapper: AnalyticsTrackerWrapper,
    private val wooStore: WooCommerceStore,
    private val cardReaderChecker: CardReaderOnboardingChecker,
    private val cashOnDeliveryToggler: CashOnDeliverySettingsRepository,
    private val cardReaderTracker: CardReaderTracker,
) : ScopedViewModel(savedState) {
    private val arguments: CardReaderHubFragmentArgs by savedState.navArgs()

    private val cashOnDeliveryState = MutableLiveData(
        ToggleableListItem(
            icon = R.drawable.ic_manage_card_reader,
            label = UiStringRes(R.string.card_reader_enable_pay_in_person),
            description = UiStringRes(R.string.card_reader_enable_pay_in_person_description),
            index = 2,
            isChecked = false,
            onToggled = { (::onCashOnDeliveryToggled)(it) }
        )
    )

    private val viewState = MutableLiveData(
        CardReaderHubViewState(
            rows = (
                createHubListWhenSinglePluginInstalled(
                    isOnboardingComplete = false,
                    cashOnDeliveryItem = cashOnDeliveryState.value!!
                )
                ).sortedBy {
                it.index
            },
            isLoading = true,
            onboardingErrorAction = null
        )
    )

    private suspend fun checkAndUpdateCashOnDeliveryOptionState() {
        val isCashOnDeliveryEnabled = cashOnDeliveryToggler.isCashOnDeliveryEnabled()
        updateCashOnDeliveryOptionState(
            cashOnDeliveryState.value?.copy(
                isChecked = isCashOnDeliveryEnabled
            )!!
        )
    }

    fun onViewVisible() {
        launch {
            checkAndUpdateCashOnDeliveryOptionState()
        }
        launch {
            viewState.value = when (val state = cardReaderChecker.getOnboardingState()) {
                is OnboardingCompleted -> createOnboardingCompleteState()
                is StripeAccountPendingRequirement -> createOnboardingWithPendingRequirementsState(state)
                else -> createOnboardingFailedState(state)
            }
        }
    }

    private val cardReaderPurchaseUrl: String by lazy {
        if (inPersonPaymentsCanadaFeatureFlag.isEnabled()) {
            val storeCountryCode = wooStore.getStoreCountryCode(selectedSite.get()) ?: null.also {
                WooLog.e(CARD_READER, "Store's country code not found.")
            }
            "${AppUrls.WOOCOMMERCE_PURCHASE_CARD_READER_IN_COUNTRY}$storeCountryCode"
        } else {
            val preferredPlugin = appPrefsWrapper.getCardReaderPreferredPlugin(
                selectedSite.get().id,
                selectedSite.get().siteId,
                selectedSite.get().selfHostedSiteId
            )
            when (preferredPlugin) {
                STRIPE_EXTENSION_GATEWAY -> AppUrls.STRIPE_M2_PURCHASE_CARD_READER
                WOOCOMMERCE_PAYMENTS, null -> AppUrls.WOOCOMMERCE_M2_PURCHASE_CARD_READER
            }
        }
    }

    private fun createHubListWhenSinglePluginInstalled(
        isOnboardingComplete: Boolean,
        cashOnDeliveryItem: ToggleableListItem
    ) =
        listOf(
            CardReaderHubViewState.ListItem.HeaderItem(
                label = UiStringRes(R.string.card_reader_payment_options_header),
                index = 0
            ),
            NonToggleableListItem(
                icon = R.drawable.ic_gridicons_money_on_surface,
                label = UiStringRes(R.string.card_reader_collect_payment),
                index = 1,
                onClick = ::onCollectPaymentClicked
            ),
            cashOnDeliveryItem,
            CardReaderHubViewState.ListItem.HeaderItem(
                label = UiStringRes(R.string.card_reader_card_readers_header),
                index = 4,
            ),
            NonToggleableListItem(
                icon = R.drawable.ic_shopping_cart,
                label = UiStringRes(R.string.card_reader_purchase_card_reader),
                index = 5,
                onClick = ::onPurchaseCardReaderClicked
            ),
            NonToggleableListItem(
                icon = R.drawable.ic_manage_card_reader,
                label = UiStringRes(R.string.card_reader_manage_card_reader),
                isEnabled = isOnboardingComplete,
                index = 6,
                onClick = ::onManageCardReaderClicked
            ),
            NonToggleableListItem(
                icon = R.drawable.ic_card_reader_manual,
                label = UiStringRes(R.string.settings_card_reader_manuals),
                index = 7,
                onClick = ::onCardReaderManualsClicked
            )
        )

    private fun createAdditionalItemWhenMultiplePluginsInstalled() =
        NonToggleableListItem(
            icon = R.drawable.ic_payment_provider,
            label = UiStringRes(R.string.card_reader_manage_payment_provider),
            index = 3,
            onClick = ::onCardReaderPaymentProviderClicked
        )

    private fun updateCashOnDeliveryOptionState(toggleableListItem: ToggleableListItem) {
        cashOnDeliveryState.value = toggleableListItem
        viewState.value = viewState.value?.copy(
            rows = (getNonTogggleableItems()!! + toggleableListItem).sortedBy {
                it.index
            }
        )
    }

    private fun getNonTogggleableItems(): List<CardReaderHubViewState.ListItem>? {
        return viewState.value?.rows?.filter {
            it !is ToggleableListItem
        }
    }

    private fun createOnboardingCompleteState(): CardReaderHubViewState {
        return CardReaderHubViewState(
            rows = if (isCardReaderPluginExplicitlySelected()) {
                (
                    createHubListWhenSinglePluginInstalled(true, cashOnDeliveryState.value!!) +
                        createAdditionalItemWhenMultiplePluginsInstalled()
                    ).sortedBy {
                    it.index
                }
            } else {
                createHubListWhenSinglePluginInstalled(true, cashOnDeliveryState.value!!)
            },
            isLoading = false,
            onboardingErrorAction = null,
        )
    }

    private fun createOnboardingWithPendingRequirementsState(state: CardReaderOnboardingState) =
        createOnboardingCompleteState().copy(
            onboardingErrorAction = OnboardingErrorAction(
                text = UiStringRes(R.string.card_reader_onboarding_with_pending_requirements, containsHtml = true),
                onClick = { onOnboardingErrorClicked(state) }
            )
        )

    private fun createOnboardingFailedState(state: CardReaderOnboardingState): CardReaderHubViewState {
        return CardReaderHubViewState(
            rows = (
                createHubListWhenSinglePluginInstalled(false, cashOnDeliveryState.value!!)
                ).sortedBy {
                it.index
            },
            isLoading = false,
            onboardingErrorAction = OnboardingErrorAction(
                text = UiStringRes(R.string.card_reader_onboarding_not_finished, containsHtml = true),
                onClick = { onOnboardingErrorClicked(state) }
            )
        )
    }

    val viewStateData: LiveData<CardReaderHubViewState> = viewState

    private fun onCollectPaymentClicked() {
        trackEvent(AnalyticsEvent.PAYMENTS_HUB_COLLECT_PAYMENT_TAPPED)
        triggerEvent(CardReaderHubEvents.NavigateToPaymentCollectionScreen)
    }

    private fun onManageCardReaderClicked() {
        trackEvent(AnalyticsEvent.PAYMENTS_HUB_MANAGE_CARD_READERS_TAPPED)
        triggerEvent(CardReaderHubEvents.NavigateToCardReaderDetail(arguments.cardReaderFlowParam))
    }

    private fun onPurchaseCardReaderClicked() {
        trackEvent(AnalyticsEvent.PAYMENTS_HUB_ORDER_CARD_READER_TAPPED)
        triggerEvent(CardReaderHubEvents.NavigateToPurchaseCardReaderFlow(cardReaderPurchaseUrl))
    }

    private fun onCardReaderManualsClicked() {
        trackEvent(AnalyticsEvent.PAYMENTS_HUB_CARD_READER_MANUALS_TAPPED)
        triggerEvent(CardReaderHubEvents.NavigateToCardReaderManualsScreen)
    }

    private fun onCardReaderPaymentProviderClicked() {
        trackEvent(AnalyticsEvent.SETTINGS_CARD_PRESENT_SELECT_PAYMENT_GATEWAY_TAPPED)
        clearPluginExplicitlySelectedFlag()
        triggerEvent(
            CardReaderHubEvents.NavigateToCardReaderOnboardingScreen(
                CardReaderOnboardingState.ChoosePaymentGatewayProvider
            )
        )
    }

    private fun onCashOnDeliveryToggled(isChecked: Boolean) {
        launch {
            updateCashOnDeliveryOptionState(
                cashOnDeliveryState.value?.copy(isEnabled = false, isChecked = isChecked)!!
            )
            val result = cashOnDeliveryToggler.toggleCashOnDeliveryOption(isChecked)
            result.model?.let {
                cardReaderTracker.trackCashOnDeliveryEnabledSuccess()
                updateCashOnDeliveryOptionState(
                    cashOnDeliveryState.value?.copy(isEnabled = true, isChecked = isChecked)!!
                )
            } ?: run {
                cardReaderTracker.trackCashOnDeliveryEnabledFailure(
                    result.error.message
                )
                updateCashOnDeliveryOptionState(
                    cashOnDeliveryState.value?.copy(isEnabled = true, isChecked = !isChecked)!!
                )
            }
        }
    }

    private fun onOnboardingErrorClicked(state: CardReaderOnboardingState) {
        trackEvent(AnalyticsEvent.PAYMENTS_HUB_ONBOARDING_ERROR_TAPPED)
        triggerEvent(CardReaderHubEvents.NavigateToCardReaderOnboardingScreen(state))
    }

    private fun trackEvent(event: AnalyticsEvent) {
        analyticsTrackerWrapper.track(event)
    }

    private fun clearPluginExplicitlySelectedFlag() {
        val site = selectedSite.get()
        appPrefsWrapper.setIsCardReaderPluginExplicitlySelectedFlag(
            site.id,
            site.siteId,
            site.selfHostedSiteId,
            false
        )
    }

    private fun isCardReaderPluginExplicitlySelected() =
        appPrefsWrapper.isCardReaderPluginExplicitlySelected(
            localSiteId = selectedSite.get().id,
            remoteSiteId = selectedSite.get().siteId,
            selfHostedSiteId = selectedSite.get().selfHostedSiteId,
        )

    sealed class CardReaderHubEvents : MultiLiveEvent.Event() {
        data class NavigateToCardReaderDetail(val cardReaderFlowParam: CardReaderFlowParam) : CardReaderHubEvents()
        data class NavigateToPurchaseCardReaderFlow(val url: String) : CardReaderHubEvents()
        object NavigateToPaymentCollectionScreen : CardReaderHubEvents()
        object NavigateToCardReaderManualsScreen : CardReaderHubEvents()
        data class NavigateToCardReaderOnboardingScreen(
            val onboardingState: CardReaderOnboardingState
        ) : CardReaderHubEvents()
    }

    data class CardReaderHubViewState(
        val rows: List<ListItem>,
        val isLoading: Boolean,
        val onboardingErrorAction: OnboardingErrorAction?,
    ) {
        sealed class ListItem {
            abstract val label: UiString
            abstract val icon: Int?
            abstract val onClick: (() -> Unit)?
            abstract val index: Int
            abstract var isEnabled: Boolean

            data class NonToggleableListItem(
                @DrawableRes override val icon: Int,
                override val label: UiString,
                override var isEnabled: Boolean = true,
                override val index: Int,
                override val onClick: () -> Unit
            ) : ListItem()

            data class ToggleableListItem(
                @DrawableRes override val icon: Int,
                override val label: UiString,
                val description: UiString,
                override var isEnabled: Boolean = true,
                val isChecked: Boolean,
                override val index: Int,
                override val onClick: (() -> Unit)? = null,
                val onToggled: (Boolean) -> Unit
            ) : ListItem()

            data class HeaderItem(
                @DrawableRes override val icon: Int? = null,
                override val label: UiString,
                override val index: Int,
                override var isEnabled: Boolean = false,
                override val onClick: (() -> Unit)? = null
            ) : ListItem()
        }

        data class OnboardingErrorAction(
            val text: UiString?,
            val onClick: () -> Unit,
        )
    }
}
