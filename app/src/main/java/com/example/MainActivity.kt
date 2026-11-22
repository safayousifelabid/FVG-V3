package com.example

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.configureAppCheck
import com.example.data.repository.FvgAiRepository
import com.example.data.repository.FvgRepository
import com.example.ui.auth.AuthHelper
import com.example.ui.components.FvgCalculatorSheet
import com.example.ui.components.FvgTrialExpiredGateCard
import com.example.ui.components.FvgWalkthroughOverlay
import com.example.ui.i18n.fvgStrings
import com.example.ui.screens.*
import com.example.ui.theme.FvgDeepGreen
import com.example.ui.theme.FvgTheme
import com.example.ui.theme.FvgWarmGold
import com.example.ui.theme.FvgWarmIvory
import com.example.ui.viewmodel.FvgViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            FvgRootApp()
        }
    }
}

private data class FvgNavItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

private fun getOrCreateSessionId(context: Context): String {
    val prefs = context.getSharedPreferences("fvg_session_prefs", Context.MODE_PRIVATE)
    val existing = prefs.getString("fvg_session_id", null)
    if (!existing.isNullOrBlank()) return existing
    val created = UUID.randomUUID().toString()
    prefs.edit().putString("fvg_session_id", created).apply()
    return created
}

@Composable
fun FvgRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }

    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var isArabic by rememberSaveable { mutableStateOf(true) }
    var themeMode by rememberSaveable { mutableStateOf("system") }
    var fontScale by rememberSaveable { mutableFloatStateOf(1.0f) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        delay(400L)
        if (auth.currentUser == null) {
            AuthHelper.attemptSilentGoogleSignIn(context, auth)
        }
    }

    val resolvedDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    FvgTheme(themeMode = themeMode, isArabic = isArabic, fontScale = fontScale) {
        if (showSplash) {
            FvgSplashScreen(isArabic = isArabic) {
                showSplash = false
            }
        } else if (currentUser == null) {
            PublicWebsiteAndAuthScreen(
                isArabic = isArabic,
                isDark = resolvedDark,
                isSigningIn = isSigningIn,
                authError = authError,
                onToggleLanguage = { isArabic = !isArabic },
                onToggleTheme = {
                    themeMode = if (resolvedDark) "light" else "dark"
                },
                onGoogleSignInClick = {
                    if (!isSigningIn) {
                        scope.launch {
                            isSigningIn = true
                            authError = null
                            AuthHelper.signInWithGoogleExplicit(context, auth)
                                .onFailure { e ->
                                    if (e !is AuthHelper.CancelledAuthException) {
                                        authError = e.message ?: if (isArabic) "فشل تسجيل الدخول عبر Google" else "Google Sign-In failed"
                                    }
                                }
                            isSigningIn = false
                        }
                    }
                },
                onEmailSignInClick = { email, password, isSignUp, displayName ->
                    if (!isSigningIn) {
                        scope.launch {
                            isSigningIn = true
                            authError = null
                            runCatching {
                                val cleanEmail = email.trim()
                                require(cleanEmail.contains("@")) {
                                    if (isArabic) "يرجى إدخال بريد إلكتروني صحيح" else "Valid email address required"
                                }
                                require(password.length >= 6) {
                                    if (isArabic) "كلمة المرور يجب أن تكون 6 أحرف على الأقل" else "Password must be at least 6 characters"
                                }
                                if (isSignUp) {
                                    val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                                    val u = result.user
                                    if (u != null) {
                                        if (displayName.isNotBlank()) {
                                            u.updateProfile(
                                                UserProfileChangeRequest.Builder()
                                                    .setDisplayName(displayName.trim())
                                                    .build()
                                            ).await()
                                        }
                                        runCatching { u.sendEmailVerification().await() }
                                    }
                                } else {
                                    auth.signInWithEmailAndPassword(cleanEmail, password).await()
                                }
                            }.onFailure { e ->
                                authError = e.message ?: if (isArabic) "فشل الدخول بالبريد الإلكتروني" else "Email authentication failed"
                            }
                            isSigningIn = false
                        }
                    }
                },
                onPasswordResetClick = { email ->
                    scope.launch {
                        authError = null
                        AuthHelper.sendPasswordReset(auth, email)
                            .onSuccess {
                                authError = if (isArabic) "تم إرسال رابط استعادة كلمة المرور إلى $email" else "Password reset email sent to $email"
                            }
                            .onFailure { e ->
                                authError = e.message
                            }
                    }
                }
            )
        } else {
            val user = currentUser!!
            val repository = remember(context) { FvgRepository(context.applicationContext) }
            val aiRepository = remember { FvgAiRepository() }
            key(user.uid) {
                val vm: FvgViewModel = viewModel(
                    key = user.uid,
                    factory = FvgViewModel.Factory(
                        repository = repository,
                        aiRepository = aiRepository,
                        currentUserId = user.uid,
                        userEmail = user.email.orEmpty(),
                        userDisplayName = user.displayName ?: "FVG User"
                    )
                )
                val uiState by vm.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(user.uid) {
                    vm.restartWorkspaceIfInactive()
                    val sid = withContext(Dispatchers.IO) { getOrCreateSessionId(context.applicationContext) }
                    vm.registerSession(sid, AuthHelper.currentDeviceLabel())
                }

                DisposableEffect(user.uid) {
                    onDispose {
                        if ((context as? Activity)?.isChangingConfigurations != true) {
                            vm.stopWorkspaceListeners()
                        }
                    }
                }

                LaunchedEffect(uiState.userProfile?.preferredLanguage) {
                    uiState.userProfile?.preferredLanguage?.let { lang ->
                        isArabic = (lang == "ar")
                    }
                }
                LaunchedEffect(uiState.userProfile?.themeMode, uiState.userProfile?.darkMode) {
                    uiState.userProfile?.let { prof ->
                        themeMode = prof.themeMode.ifBlank { if (prof.darkMode) "dark" else "light" }
                    }
                }
                LaunchedEffect(uiState.userProfile?.fontScale) {
                    uiState.userProfile?.fontScale?.let { scale ->
                        fontScale = scale.toFloat().coerceIn(0.85f, 1.30f)
                    }
                }

                FvgAuthenticatedShell(
                    vm = vm,
                    uiState = uiState,
                    userDisplayName = uiState.userProfile?.displayName?.ifBlank { null }
                        ?: user.displayName
                        ?: user.email
                        ?: "FVG User",
                    isArabic = isArabic,
                    isDark = resolvedDark,
                    onToggleLanguage = {
                        val next = !isArabic
                        isArabic = next
                        vm.setLanguage(if (next) "ar" else "en")
                    },
                    onToggleTheme = {
                        val nextMode = if (resolvedDark) "light" else "dark"
                        themeMode = nextMode
                        vm.setThemeMode(nextMode)
                    },
                    onSignOut = {
                        scope.launch {
                            vm.stopWorkspaceListeners()
                            yield()
                            AuthHelper.signOut(context, auth)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FvgAuthenticatedShell(
    vm: FvgViewModel,
    uiState: com.example.ui.viewmodel.FvgUiState,
    userDisplayName: String,
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit
) {
    val s = fvgStrings(isArabic)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var activeSecondaryScreen by rememberSaveable { mutableStateOf<String?>(null) } // "profile", "help", "owner_admin"
    var showCalculatorSheet by rememberSaveable { mutableStateOf(false) }
    var showNotificationsDialog by rememberSaveable { mutableStateOf(false) }
    var showWalkthrough by rememberSaveable { mutableStateOf(false) }
    var autoTourEvaluated by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val notifications = remember(
        uiState.activeOrg,
        uiState.priceObservations,
        uiState.fxRates,
        uiState.counterpartyItems,
        uiState.scrapLosses,
        uiState.stocktakes,
        uiState.ocrDrafts,
        uiState.journalEntries,
        uiState.statementsReport,
        uiState.paymentRequests,
        uiState.inventoryItems,
        uiState.userProfile?.notificationsEnabled
    ) { uiState.buildNotifications() }

    LaunchedEffect(uiState.userProfile?.tourCompleted) {
        val profile = uiState.userProfile
        if (profile != null && !autoTourEvaluated) {
            autoTourEvaluated = true
            if (!profile.tourCompleted) {
                showWalkthrough = true
            }
        }
    }

    BackHandler(enabled = activeSecondaryScreen != null || selectedTab != 0) {
        if (activeSecondaryScreen != null) {
            activeSecondaryScreen = null
        } else {
            selectedTab = 0
        }
    }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearMessages()
        }
    }

    val navItems = listOf(
        FvgNavItem(0, s.navDashboard, Icons.Filled.Dashboard, "nav_dashboard"),
        FvgNavItem(1, s.navPrices, Icons.AutoMirrored.Filled.TrendingUp, "nav_prices"),
        FvgNavItem(2, s.navCosting, Icons.Filled.Calculate, "nav_costing"),
        FvgNavItem(3, s.navAccounting, Icons.Filled.AccountBalance, "nav_accounting"),
        FvgNavItem(4, s.navInventory, Icons.Filled.Inventory2, "nav_inventory"),
        FvgNavItem(5, s.navAssistantReports, Icons.Filled.AutoAwesome, "nav_reports")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        if (activeSecondaryScreen == "profile") {
            ProfileScreen(
                state = uiState,
                viewModel = vm,
                isArabic = isArabic,
                onBack = { activeSecondaryScreen = null },
                onSignOutClick = onSignOut
            )
        } else if (activeSecondaryScreen == "help") {
            HelpAndSupportScreen(
                state = uiState,
                viewModel = vm,
                isArabic = isArabic,
                onReplayTour = {
                    activeSecondaryScreen = null
                    selectedTab = 0
                    showWalkthrough = true
                },
                onBack = { activeSecondaryScreen = null }
            )
        } else if (activeSecondaryScreen == "owner_admin" && uiState.isPlatformOwner) {
            OwnerAdminScreen(
                state = uiState,
                viewModel = vm,
                isArabic = isArabic,
                onBack = { activeSecondaryScreen = null }
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = FvgDeepGreen,
                            contentColor = FvgWarmIvory
                        ) {
                            navItems.forEach { item ->
                                val selected = selectedTab == item.index
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectedTab = item.index },
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = item.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = FvgDeepGreen,
                                        selectedTextColor = FvgWarmGold,
                                        indicatorColor = FvgWarmGold,
                                        unselectedIconColor = FvgWarmIvory.copy(alpha = 0.72f),
                                        unselectedTextColor = FvgWarmIvory.copy(alpha = 0.72f)
                                    ),
                                    modifier = Modifier.testTag(item.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = FvgDeepGreen,
                            contentColor = FvgWarmIvory
                        ) {
                            navItems.forEach { item ->
                                val selected = selectedTab == item.index
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { selectedTab = item.index },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = FvgDeepGreen,
                                        selectedTextColor = FvgWarmGold,
                                        indicatorColor = FvgWarmGold,
                                        unselectedIconColor = FvgWarmIvory.copy(alpha = 0.72f),
                                        unselectedTextColor = FvgWarmIvory.copy(alpha = 0.72f)
                                    ),
                                    modifier = Modifier.testTag(item.tag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val trialLocked = uiState.isTrialExpiredAndLocked()
                        if (trialLocked && selectedTab in 1..4) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                FvgTrialExpiredGateCard(
                                    isArabic = isArabic,
                                    orgName = uiState.activeOrg?.name ?: "FVG Workspace",
                                    onUpgradeClick = { selectedTab = 5 },
                                    onSignOutClick = onSignOut
                                )
                            }
                        } else {
                            when (selectedTab) {
                                0 -> DashboardScreen(
                                    state = uiState,
                                    userDisplayName = userDisplayName,
                                    isArabic = isArabic,
                                    isDark = isDark,
                                    unreadNotificationsCount = notifications.size,
                                    onNotificationsClick = { showNotificationsDialog = true },
                                    onOpenCalculator = { showCalculatorSheet = true },
                                    onOpenProfile = { activeSecondaryScreen = "profile" },
                                    onOpenHelp = { activeSecondaryScreen = "help" },
                                    onOpenOwnerAdmin = { if (uiState.isPlatformOwner) activeSecondaryScreen = "owner_admin" },
                                    onReplayTour = { showWalkthrough = true },
                                    onToggleLanguage = onToggleLanguage,
                                    onToggleTheme = onToggleTheme,
                                    onCreateOrganization = { name, curr, sector -> vm.createOrganization(name, curr, sector) },
                                    onNavigateTab = { selectedTab = it }
                                )
                                1 -> PricesInflationScreen(
                                    state = uiState,
                                    viewModel = vm,
                                    isArabic = isArabic,
                                    isDark = isDark,
                                    onToggleLanguage = onToggleLanguage,
                                    onToggleTheme = onToggleTheme,
                                    onOpenCalculator = { showCalculatorSheet = true },
                                    onOpenHelp = { activeSecondaryScreen = "help" },
                                    onOpenProfile = { activeSecondaryScreen = "profile" }
                                )
                                2 -> CostingScenariosScreen(
                                    state = uiState,
                                    viewModel = vm,
                                    isArabic = isArabic,
                                    isDark = isDark,
                                    onToggleLanguage = onToggleLanguage,
                                    onToggleTheme = onToggleTheme,
                                    onOpenCalculator = { showCalculatorSheet = true },
                                    onOpenHelp = { activeSecondaryScreen = "help" },
                                    onOpenProfile = { activeSecondaryScreen = "profile" }
                                )
                                3 -> AccountingStatementsScreen(
                                    state = uiState,
                                    viewModel = vm,
                                    isArabic = isArabic,
                                    isDark = isDark,
                                    onToggleLanguage = onToggleLanguage,
                                    onToggleTheme = onToggleTheme,
                                    onOpenCalculator = { showCalculatorSheet = true },
                                    onOpenHelp = { activeSecondaryScreen = "help" },
                                    onOpenProfile = { activeSecondaryScreen = "profile" }
                                )
                                4 -> InventoryBudgetsScreen(
                                    state = uiState,
                                    viewModel = vm,
                                    isArabic = isArabic,
                                    isDark = isDark,
                                    onToggleLanguage = onToggleLanguage,
                                    onToggleTheme = onToggleTheme,
                                    onOpenCalculator = { showCalculatorSheet = true },
                                    onOpenHelp = { activeSecondaryScreen = "help" },
                                    onOpenProfile = { activeSecondaryScreen = "profile" }
                                )
                                else -> ReportsAiSettingsScreen(
                                    state = uiState,
                                    viewModel = vm,
                                    isArabic = isArabic,
                                    isDark = isDark,
                                    onToggleLanguage = onToggleLanguage,
                                    onToggleTheme = onToggleTheme,
                                    onOpenCalculator = { showCalculatorSheet = true },
                                    onOpenProfile = { activeSecondaryScreen = "profile" },
                                    onOpenHelp = { activeSecondaryScreen = "help" },
                                    onOpenOwnerAdmin = { if (uiState.isPlatformOwner) activeSecondaryScreen = "owner_admin" },
                                    onSignOutClick = onSignOut
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCalculatorSheet) {
        FvgCalculatorSheet(
            isArabic = isArabic,
            defaultCurrency = uiState.activeOrg?.baseCurrency ?: "SDG",
            onDismiss = { showCalculatorSheet = false }
        )
    }

    if (showWalkthrough) {
        FvgWalkthroughOverlay(
            isArabic = isArabic,
            onHighlightTab = { tabIdx ->
                activeSecondaryScreen = null
                selectedTab = tabIdx.coerceIn(0, 5)
            },
            onDismissComplete = {
                showWalkthrough = false
                vm.markTourCompleted()
            }
        )
    }

    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = {
                Text(s.notificationsTitle, fontWeight = FontWeight.Bold)
            },
            text = {
                if (notifications.isEmpty()) {
                    Text(
                        text = if (isArabic) "لا توجد تنبيهات نشطة حالياً." else "All financial checks look clear — no active alerts.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifications, key = { it.id }) { alert ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTab = alert.targetTab.coerceIn(0, 5)
                                        activeSecondaryScreen = null
                                        showNotificationsDialog = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isArabic) alert.titleAr else alert.titleEn,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (alert.severity == "critical") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(alert.severity.uppercase(), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(
                                        text = if (isArabic) alert.detailAr else alert.detailEn,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text(if (isArabic) "إغلاق" else "Close")
                }
            }
        )
    }
}
