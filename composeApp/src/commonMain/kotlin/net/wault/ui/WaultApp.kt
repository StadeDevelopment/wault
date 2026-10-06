package net.wault.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import net.wault.AppContainer
import net.wault.item.ItemType
import net.wault.security.UnlockOutcome
import net.wault.ui.components.HomeActionBar
import net.wault.ui.components.HomeDestination
import net.wault.ui.components.LocalHomeBarClearance
import net.wault.ui.components.backdropSource
import net.wault.ui.components.rememberBackdropState
import net.wault.ui.i18n.LocalStrings
import net.wault.ui.i18n.localeToLayoutDirection
import net.wault.ui.i18n.localeToStrings
import net.wault.ui.screens.AuthenticatorScreen
import net.wault.ui.screens.DevicesScreen
import net.wault.ui.screens.GeneratorScreen
import net.wault.ui.screens.HealthScreen
import net.wault.ui.screens.ItemDetailScreen
import net.wault.ui.screens.ItemEditScreen
import net.wault.ui.screens.LockScreen
import net.wault.ui.screens.OnboardingScreen
import net.wault.ui.screens.PairDeviceScreen
import net.wault.ui.screens.RecoveryKeyScreen
import net.wault.ui.screens.SettingsDetailScreen
import net.wault.ui.screens.SettingsScreen
import net.wault.ui.screens.SplashScreen
import net.wault.ui.screens.VaultScreen
import net.wault.ui.theme.WaultTheme

private const val NAV_SLIDE_MS = 300
private const val NAV_FADE_MS = 140

private val NavEnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val NavExitEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

val LocalAppReady = compositionLocalOf { true }

enum class SettingsSection {
    Appearance,
    Security,
    Autofill,
    Sync,
    Data,
    Language,
    About
}

sealed interface Screen {
    data object Onboarding : Screen
    data class Recovery(val recoveryKey: String) : Screen
    data object Lock : Screen
    data object Vault : Screen
    data object Authenticator : Screen
    data object Generator : Screen
    data object Health : Screen
    data object Settings : Screen
    data class SettingsDetail(val section: SettingsSection) : Screen
    data object Devices : Screen
    data class PairDevice(val masterPassword: String?) : Screen
    data class ItemDetail(val itemId: String) : Screen
    data class ItemEdit(val itemId: String?, val type: ItemType) : Screen
}

internal fun screenKey(screen: Screen): String = when (screen) {
    is Screen.ItemDetail -> "item:" + screen.itemId
    is Screen.ItemEdit -> "edit:" + (screen.itemId ?: screen.type.name)
    is Screen.Recovery -> "recovery"
    is Screen.PairDevice -> "pair"
    is Screen.SettingsDetail -> "settings:" + screen.section.name
    else -> screen.toString()
}

internal fun navPosition(screen: Screen): Int = when (screen) {
    Screen.Onboarding -> 0
    Screen.Lock -> 0
    is Screen.Recovery -> 1
    Screen.Vault -> 10
    Screen.Authenticator -> 11
    Screen.Generator -> 12
    Screen.Health -> 13
    Screen.Settings -> 14
    is Screen.SettingsDetail -> 20
    is Screen.ItemDetail -> 20
    Screen.Devices -> 21
    is Screen.PairDevice -> 22
    is Screen.ItemEdit -> 22
}

private fun homeDestination(screen: Screen): HomeDestination? = when (screen) {
    Screen.Vault -> HomeDestination.Vault
    Screen.Authenticator -> HomeDestination.Authenticator
    Screen.Generator -> HomeDestination.Generator
    Screen.Health -> HomeDestination.Health
    Screen.Settings -> HomeDestination.Settings
    else -> null
}

@Composable
fun WaultApp(container: AppContainer) {
    remember(container) { UiSettings.bind(container.preferences) }

    val locale by UiSettings.locale
    val strings = localeToStrings(locale)

    var splashDone by remember { mutableStateOf(false) }

    WaultTheme {
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalLayoutDirection provides localeToLayoutDirection(locale),
            LocalAppReady provides splashDone
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                    WaultNavHost(container)

                    if (!splashDone) {
                        SplashScreen(onFinished = { splashDone = true })
                    }
                }
            }
        }
    }
}

@Composable
private fun WaultNavHost(container: AppContainer) {
    val density = LocalDensity.current

    val nav = remember {
        WaultNavigator(if (container.vault.isInitialized()) Screen.Lock else Screen.Onboarding)
    }
    val screen = nav.current
    var unlocked by remember { mutableStateOf(container.vault.isUnlocked()) }
    var barIntroPlayed by remember { mutableStateOf(false) }
    var measuredBarHeight by remember { mutableStateOf(0.dp) }

    val lockRequested by container.lockRequested.collectAsState()

    SystemBackHandler(enabled = nav.canGoBack) { nav.back() }

    LaunchedEffect(lockRequested) {
        if (lockRequested && unlocked) {
            unlocked = false
            barIntroPlayed = false
            nav.resetTo(if (container.vault.isInitialized()) Screen.Lock else Screen.Onboarding)
        }
    }

    LaunchedEffect(unlocked) {
        if (unlocked) container.onUnlocked() else container.onLocked()
    }

    val barDestination = homeDestination(screen)
    val backdrop = rememberBackdropState()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            modifier = Modifier.backdropSource(backdrop, enabled = barDestination != null),
            targetState = screen,
            transitionSpec = {
                val forward = navPosition(targetState) >= navPosition(initialState)
                val enterSlide = tween<IntOffset>(NAV_SLIDE_MS, easing = NavEnterEasing)
                val exitSlide = tween<IntOffset>(NAV_SLIDE_MS, easing = NavExitEasing)
                val enterFade = tween<Float>(NAV_FADE_MS, easing = LinearEasing)
                val exitFade = tween<Float>(NAV_SLIDE_MS, easing = LinearEasing)
                val transition = if (forward) {
                    (slideInHorizontally(enterSlide) { it } + fadeIn(enterFade)) togetherWith
                        (slideOutHorizontally(exitSlide) { -it / 4 } + fadeOut(exitFade, targetAlpha = 0.85f))
                } else {
                    (slideInHorizontally(enterSlide) { -it / 4 } + fadeIn(enterFade)) togetherWith
                        (slideOutHorizontally(exitSlide) { it } + fadeOut(exitFade, targetAlpha = 0.85f))
                }
                transition.using(SizeTransform(clip = false))
            },
            contentKey = { screenKey(it) },
            label = "wault-nav"
        ) { current ->
            val clearance = if (homeDestination(current) != null) measuredBarHeight else 0.dp

            CompositionLocalProvider(LocalHomeBarClearance provides clearance) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                ) {
                    when (current) {
                        Screen.Onboarding -> OnboardingScreen(
                            container = container,
                            onVaultCreated = { recoveryKey -> nav.resetTo(Screen.Recovery(recoveryKey)) },
                            onJoinVault = { password ->
                                unlocked = true
                                container.onUnlockedByUser()
                                nav.resetTo(Screen.PairDevice(password))
                            }
                        )

                        is Screen.Recovery -> RecoveryKeyScreen(
                            recoveryKey = current.recoveryKey,
                            onAcknowledged = {
                                unlocked = true
                                container.onUnlockedByUser()
                                nav.resetTo(Screen.Vault)
                            }
                        )

                        Screen.Lock -> LockScreen(
                            container = container,
                            onUnlocked = { outcome ->
                                if (outcome is UnlockOutcome.Success) {
                                    unlocked = true
                                    container.onUnlockedByUser()
                                    nav.resetTo(Screen.Vault)
                                }
                            },
                            onWiped = {
                                unlocked = false
                                container.onUnlockedByUser()
                                nav.resetTo(Screen.Onboarding)
                            }
                        )

                        Screen.Vault -> VaultScreen(
                            container = container,
                            onOpenItem = { id -> nav.push(Screen.ItemDetail(id)) },
                            onCreate = { type -> nav.push(Screen.ItemEdit(null, type)) },
                            onLock = { container.lockNow() }
                        )

                        Screen.Authenticator -> AuthenticatorScreen(
                            container = container,
                            onOpenItem = { id -> nav.push(Screen.ItemDetail(id)) },
                            onAdd = { nav.push(Screen.ItemEdit(null, ItemType.Authenticator)) }
                        )

                        Screen.Generator -> GeneratorScreen(container = container)

                        Screen.Health -> HealthScreen(
                            container = container,
                            onOpenItem = { id -> nav.push(Screen.ItemDetail(id)) }
                        )

                        Screen.Settings -> SettingsScreen(
                            onOpenSection = { section -> nav.push(Screen.SettingsDetail(section)) },
                            onLock = { container.lockNow() }
                        )

                        is Screen.SettingsDetail -> SettingsDetailScreen(
                            container = container,
                            section = current.section,
                            onBack = { nav.back() },
                            onOpenDevices = { nav.push(Screen.Devices) },
                            onWiped = {
                                unlocked = false
                                container.onUnlockedByUser()
                                nav.resetTo(Screen.Onboarding)
                            }
                        )

                        Screen.Devices -> DevicesScreen(
                            container = container,
                            onBack = { nav.back() },
                            onPair = { nav.push(Screen.PairDevice(null)) }
                        )

                        is Screen.PairDevice -> PairDeviceScreen(
                            container = container,
                            masterPassword = current.masterPassword,
                            onBack = {
                                if (current.masterPassword == null) nav.back() else nav.resetTo(Screen.Vault)
                            },
                            onPaired = { nav.resetTo(Screen.Vault) }
                        )

                        is Screen.ItemDetail -> ItemDetailScreen(
                            container = container,
                            itemId = current.itemId,
                            onBack = { nav.back() },
                            onEdit = { id ->
                                nav.push(Screen.ItemEdit(id, container.items.byId(id)?.type ?: ItemType.Login))
                            }
                        )

                        is Screen.ItemEdit -> ItemEditScreen(
                            container = container,
                            itemId = current.itemId,
                            type = current.type,
                            onDone = { savedId ->
                                if (savedId != null) {
                                    nav.backTo(Screen.ItemDetail(savedId))
                                } else {
                                    nav.back()
                                }
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = barDestination != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(NAV_SLIDE_MS, easing = NavEnterEasing)) { it },
            exit = slideOutVertically(tween(NAV_SLIDE_MS, easing = NavExitEasing)) { it }
        ) {
            HomeActionBar(
                selected = barDestination ?: HomeDestination.Vault,
                onSelect = { target ->
                    nav.selectTab(
                        when (target) {
                            HomeDestination.Vault -> Screen.Vault
                            HomeDestination.Authenticator -> Screen.Authenticator
                            HomeDestination.Generator -> Screen.Generator
                            HomeDestination.Health -> Screen.Health
                            HomeDestination.Settings -> Screen.Settings
                        }
                    )
                },
                playIntro = !barIntroPlayed,
                onIntroFinished = { barIntroPlayed = true },
                backdrop = backdrop,
                modifier = Modifier
                    .onSizeChanged { size ->
                        val height = with(density) { size.height.toDp() }
                        if (height > 0.dp) measuredBarHeight = height
                    }
                    .navigationBarsPadding()
            )
        }
    }
}
