package uz.robutpit.znatoki.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uz.robutpit.znatoki.R
import uz.robutpit.znatoki.data.local.SettingsRepository
import uz.robutpit.znatoki.data.repo.AuthState
import uz.robutpit.znatoki.data.repo.SessionRepository
import uz.robutpit.znatoki.design.Club
import uz.robutpit.znatoki.design.components.ClubBackground
import uz.robutpit.znatoki.domain.GameGateway
import uz.robutpit.znatoki.feature.auth.*
import uz.robutpit.znatoki.feature.game.GameScreen
import uz.robutpit.znatoki.feature.home.HomeScreen
import uz.robutpit.znatoki.feature.lobby.CreateRoomScreen
import uz.robutpit.znatoki.feature.lobby.OpenGamesScreen
import uz.robutpit.znatoki.feature.profile.LeaderboardScreen
import uz.robutpit.znatoki.feature.profile.ProfileScreen
import uz.robutpit.znatoki.feature.questions.QuestionsScreen
import uz.robutpit.znatoki.feature.settings.SettingsScreen
import uz.robutpit.znatoki.feature.social.FriendsScreen
import uz.robutpit.znatoki.feature.social.TeamsScreen
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    val session: SessionRepository,
    val gateway: GameGateway,
    private val settings: SettingsRepository,
) : ViewModel() {
    var start by mutableStateOf<String?>(null); private set

    init {
        viewModelScope.launch {
            session.restore()
            val onboarded = settings.settings.first().onboardingDone
            start = when {
                session.auth.value is AuthState.LoggedIn -> Routes.MAIN
                !onboarded -> Routes.ONBOARDING
                else -> Routes.LOGIN
            }
        }
    }
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val VERIFY = "verify"
    const val FORGOT = "forgot"
    const val LEGAL = "legal/{kind}"
    const val MAIN = "main"
    const val HOME = "home"
    const val FRIENDS = "friends"
    const val QUESTIONS = "questions"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val LEADERBOARD = "leaderboard"
    const val TEAMS = "teams"
    const val CREATE_ROOM = "create_room"
    const val OPEN_GAMES = "open_games"
    const val ROOM = "room/{roomId}?role={role}"
    fun room(id: String, spectator: Boolean = false) = "room/$id?role=${if (spectator) "spectator" else "player"}"
    fun legal(kind: String) = "legal/$kind"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Filled.Home),
    Tab(Routes.FRIENDS, R.string.tab_friends, Icons.Filled.People),
    Tab(Routes.QUESTIONS, R.string.tab_questions, Icons.Filled.Quiz),
    Tab(Routes.PROFILE, R.string.tab_profile, Icons.Filled.Person),
)

@Composable
fun AppNavHost(deepLinkCode: String?, onDeepLinkConsumed: () -> Unit, root: RootViewModel = hiltViewModel()) {
    val start = root.start
    if (start == null) {
        ClubBackground {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(160.dp))
                CircularProgressIndicator(color = Club.colors.brass)
            }
        }
        return
    }
    val nav = rememberNavController()
    val auth by root.session.auth.collectAsState()
    // выход из аккаунта из любого места → экран входа
    LaunchedEffect(auth) {
        if (auth is AuthState.LoggedOut && nav.currentDestination?.route !in setOf(Routes.LOGIN, Routes.REGISTER, Routes.FORGOT, Routes.ONBOARDING, Routes.LEGAL)) {
            nav.navigate(Routes.LOGIN) { popUpTo(0) }
        }
    }
    val enter = fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 }
    val exit = fadeOut(tween(160))
    NavHost(nav, startDestination = start, enterTransition = { enter }, exitTransition = { exit }, popEnterTransition = { fadeIn(tween(220)) }, popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 8 } }) {
        composable(Routes.ONBOARDING) { OnboardingScreen(onDone = { nav.navigate(Routes.LOGIN) { popUpTo(0) } }) }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = { nav.navigate(Routes.MAIN) { popUpTo(0) } },
                onRegister = { nav.navigate(Routes.REGISTER) }, onForgot = { nav.navigate(Routes.FORGOT) }, isMock = root.gateway.isMock,
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(onRegistered = { nav.navigate(Routes.VERIFY) { popUpTo(0) } }, onBack = { nav.popBackStack() }, onOpenLegal = { nav.navigate(Routes.legal(it)) })
        }
        composable(Routes.VERIFY) { VerifyEmailScreen(onDone = { nav.navigate(Routes.MAIN) { popUpTo(0) } }) }
        composable(Routes.FORGOT) { ForgotScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.LEGAL, arguments = listOf(navArgument("kind") { type = NavType.StringType })) { e ->
            LegalScreen(e.arguments?.getString("kind") ?: "privacy", onBack = { nav.popBackStack() }, gateway = root.gateway)
        }
        composable(Routes.MAIN) { MainShell(nav, root, deepLinkCode, onDeepLinkConsumed) }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() }, onLoggedOut = { nav.navigate(Routes.LOGIN) { popUpTo(0) } }, onLegal = { nav.navigate(Routes.legal(it)) })
        }
        composable(Routes.LEADERBOARD) { LeaderboardScreen(onBack = { nav.popBackStack() }, myId = root.session.profile?.id ?: "me") }
        composable(Routes.TEAMS) { TeamsScreen(onBack = { nav.popBackStack() }, onRoom = { nav.navigate(Routes.room(it)) }, myId = root.session.profile?.id ?: "me") }
        composable(Routes.CREATE_ROOM) { CreateRoomScreen(onBack = { nav.popBackStack() }, onCreated = { nav.navigate(Routes.room(it)) { popUpTo(Routes.MAIN) } }) }
        composable(Routes.OPEN_GAMES) {
            OpenGamesScreen(onBack = { nav.popBackStack() }, onWatch = { nav.navigate(Routes.room(it, spectator = true)) }, onJoin = { nav.navigate(Routes.room(it)) })
        }
        composable(
            Routes.ROOM,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType }, navArgument("role") { type = NavType.StringType; defaultValue = "player" }),
        ) {
            GameScreen(onExit = { nav.popBackStack(Routes.MAIN, inclusive = false) })
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun MainShell(rootNav: NavHostController, root: RootViewModel, deepLinkCode: String?, onDeepLinkConsumed: () -> Unit) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val go: (String) -> Unit = { r ->
        nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
    }
    val content: @Composable (Modifier) -> Unit = { m ->
        NavHost(nav, startDestination = Routes.HOME, modifier = m, enterTransition = { fadeIn(tween(200)) }, exitTransition = { fadeOut(tween(150)) }) {
            composable(Routes.HOME) {
                HomeScreen(
                    onRoom = { rootNav.navigate(Routes.room(it)) },
                    onCreateRoom = { rootNav.navigate(Routes.CREATE_ROOM) },
                    onOpenGames = { rootNav.navigate(Routes.OPEN_GAMES) },
                    onTeams = { rootNav.navigate(Routes.TEAMS) },
                    onLeaderboard = { rootNav.navigate(Routes.LEADERBOARD) },
                    onSettings = { rootNav.navigate(Routes.SETTINGS) },
                    deepLinkCode = deepLinkCode, onDeepLinkConsumed = onDeepLinkConsumed,
                )
            }
            composable(Routes.FRIENDS) { FriendsScreen(onRoom = { rootNav.navigate(Routes.room(it)) }) }
            composable(Routes.QUESTIONS) { QuestionsScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(onLeaderboard = { rootNav.navigate(Routes.LEADERBOARD) }, onSettings = { rootNav.navigate(Routes.SETTINGS) }, onTeams = { rootNav.navigate(Routes.TEAMS) })
            }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(Modifier.fillMaxHeight().systemBarsPadding()) {
                Spacer(Modifier.height(12.dp))
                tabs.forEach { t ->
                    NavigationRailItem(selected = route == t.route, onClick = { go(t.route) }, icon = { Icon(t.icon, null) }, label = { Text(stringResource(t.label)) })
                }
            }
            content(Modifier.weight(1f))
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(selected = route == t.route, onClick = { go(t.route) }, icon = { Icon(t.icon, null) }, label = { Text(stringResource(t.label)) })
                    }
                }
            },
            contentWindowInsets = WindowInsets(0),
        ) { pad -> content(Modifier.padding(pad)) }
    }
}
