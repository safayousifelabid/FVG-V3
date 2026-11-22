package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.FvgFinancialEngine
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import java.math.BigDecimal

@Composable
fun DashboardScreen(
    state: FvgUiState,
    userDisplayName: String,
    isArabic: Boolean,
    isDark: Boolean,
    unreadNotificationsCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    onOpenCalculator: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenOwnerAdmin: () -> Unit = {},
    onReplayTour: () -> Unit = {},
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onCreateOrganization: (String, String, String) -> Unit,
    onNavigateTab: (Int) -> Unit
) {
    val s = fvgStrings(isArabic)
    val org = state.activeOrg
    val stmt = state.statementsReport
    val currency = org?.baseCurrency ?: "SDG"
    var chartWindow by remember { mutableStateOf("last6") }

    val totalCash = stmt?.cashAndBank ?: BigDecimal.ZERO
    val invBook = state.totalInventoryBookValue
    val invRepl = state.totalReplacementCost
    val combinedBookPortfolio = FvgFinancialEngine.money(totalCash.add(invBook))

    val revenue = stmt?.revenue ?: BigDecimal.ZERO
    val expenses = FvgFinancialEngine.money((stmt?.cogs ?: BigDecimal.ZERO).add(stmt?.operatingExpenses ?: BigDecimal.ZERO))
    val netProfit = stmt?.netProfit ?: BigDecimal.ZERO

    val latestInflation = state.inflationRecords.firstOrNull()
    val latestInfPct = latestInflation?.monthlyRatePct?.let { runCatching { FvgFinancialEngine.d(it) }.getOrNull() }
    val latestSnap = state.costSnapshots.lastOrNull()
    val riskAssessment = remember(latestSnap, latestInfPct, stmt) {
        latestSnap?.let { snap ->
            FvgFinancialEngine.evaluateFinancialRisk(
                profitMarginPct = runCatching { FvgFinancialEngine.d(snap.profitMarginPct) }.getOrNull(),
                inflationRatePct = latestInfPct,
                totalDebt = stmt?.totalLiabilities ?: BigDecimal.ZERO,
                totalEquity = stmt?.totalEquityWithNetIncome ?: BigDecimal.ZERO
            )
        }
    }

    val priceTrendPoints = remember(state.priceObservations, chartWindow) {
        val list = if (chartWindow == "last6") state.priceObservations.takeLast(6) else state.priceObservations
        list.mapNotNull { obs ->
            obs.price.toFloatOrNull()?.let { obs.observationDate.takeLast(5) to it }
        }
    }
    val resolvedName = state.userProfile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName
    val userInitials = remember(resolvedName) {
        resolvedName.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().toString() }
            .ifBlank { "FV" }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgTopHeaderBanner(
                title = if (isArabic) "مرحباً، $resolvedName" else "Welcome, $resolvedName",
                subtitle = if (isArabic) "رؤيتك المالية، دليلنا" else "Your Financial Vision, Our Guide",
                icon = Icons.Filled.AccountBalanceWallet,
                isArabic = isArabic,
                isDark = isDark,
                onToggleLanguage = onToggleLanguage,
                onToggleTheme = onToggleTheme,
                orgName = org?.name,
                roleBadge = org?.let { state.currentUserRole },
                userInitials = userInitials,
                unreadNotificationsCount = unreadNotificationsCount,
                onNotificationsClick = onNotificationsClick,
                onCalculatorClick = onOpenCalculator,
                onHelpClick = onOpenHelp,
                onProfileClick = onOpenProfile
            )
        }

        // Announcements from Owner Console
        if (state.announcements.isNotEmpty()) {
            item {
                val ann = state.announcements.first()
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    FvgCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (isArabic) ann.titleAr.ifBlank { ann.titleEn } else ann.titleEn.ifBlank { ann.titleAr },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (isArabic) ann.bodyAr.ifBlank { ann.bodyEn } else ann.bodyEn.ifBlank { ann.bodyAr },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Platform Owner Console Exclusive Entry
        if (state.isPlatformOwner) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    FvgGoldButton(
                        text = if (isArabic) "فتح لوحة مالكة المنصة (الإدارة والمدفوعات)" else "Open Platform Owner Admin Console",
                        icon = Icons.Filled.AdminPanelSettings,
                        onClick = onOpenOwnerAdmin,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "open_owner_admin_button"
                    )
                }
            }
        }

        if (org != null && state.isTrialExpiredAndLocked()) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    FvgTrialExpiredGateCard(
                        isArabic = isArabic,
                        orgName = org.name,
                        durationHours = state.effectiveTrialDurationHours(),
                        onUpgradeClick = { onNavigateTab(5) }
                    )
                }
            }
        } else if (org != null && (org.subscriptionStatus == "trial" || org.trialEndsAt != null)) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    FvgTrialCountdownBanner(
                        trialEndsAtMillis = state.effectiveTrialEndsMillis(),
                        isArabic = isArabic,
                        isPreviewTrial = false,
                        durationHours = state.effectiveTrialDurationHours(),
                        serverAnchorMillis = state.serverAnchorMillis,
                        elapsedBaselineMillis = state.elapsedBaselineRealtimeMillis,
                        onActionClick = { onNavigateTab(5) }
                    )
                }
            }
        }

        if (org == null && !state.isLoading) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    OrganizationSetupCard(
                        isArabic = isArabic,
                        isBusy = state.isBusy,
                        onCreate = onCreateOrganization
                    )
                }
            }
        }

        // Hero Card: Total Cash & Inventory Book Value + Sparkline
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(FvgDeepGreen, FvgForestGreen)))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = s.totalPortfolioValue,
                                style = MaterialTheme.typography.labelLarge,
                                color = FvgWarmGold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = FvgWarmGold.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = currency,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = FvgWarmGold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "${combinedBookPortfolio.toPlainString()} $currency",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = FvgWarmIvory
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isArabic)
                                        "النقدية: $totalCash  |  المخزون الدفتري: $invBook"
                                    else
                                        "Cash: $totalCash  •  Inventory Book: $invBook",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FvgWarmIvory.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = if (isArabic)
                                        "تكلفة الاستبدال الحالية (تقدير): ${FvgFinancialEngine.money(invRepl)} $currency"
                                    else
                                        "Current Replacement Estimate: ${FvgFinancialEngine.money(invRepl)} $currency",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FvgWarmGold
                                )
                            }
                            FvgSparklineCanvas(
                                values = if (priceTrendPoints.size >= 2) priceTrendPoints.map { it.second } else listOf(10f, 12f, 11.5f, 14f),
                                modifier = Modifier
                                    .width(92.dp)
                                    .height(42.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3 KPI Summary Cards
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiMiniCard(
                    title = s.revenueLabel,
                    value = "$revenue",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    title = s.expensesLabel,
                    value = "$expenses",
                    accentColor = FvgAntiqueGold,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    title = s.netProfitLabel,
                    value = "$netProfit",
                    accentColor = if (netProfit < BigDecimal.ZERO) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Financial Risk Summary Card
        if (riskAssessment != null) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "تقييم المخاطر المالية (${latestSnap?.productName})" else "Financial Risk Engine (${latestSnap?.productName})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            FvgRiskBadge(level = riskAssessment.level, isArabic = isArabic)
                        }
                        Text(
                            text = if (isArabic) "${riskAssessment.ruleName}: ${riskAssessment.actualValue} (الحد ${riskAssessment.thresholdValue})"
                            else "${riskAssessment.ruleName}: ${riskAssessment.actualValue} (Threshold ${riskAssessment.thresholdValue})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(riskAssessment.explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // Financial Overview Chart
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FvgCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "نظرة عامة على الأداء المالي والأسعار" else "Financial Overview & Price Trajectory",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        FvgChipSelector(
                            options = listOf("last6", "all"),
                            selected = chartWindow,
                            onSelect = { chartWindow = it },
                            labelResolver = { if (it == "last6") (if (isArabic) "آخر 6" else "Last 6") else (if (isArabic) "الكل" else "All") }
                        )
                    }
                    if (priceTrendPoints.size >= 2) {
                        FvgSplineLineChartCanvas(points = priceTrendPoints)
                    }
                    if (stmt == null && state.journalEntries.isEmpty()) {
                        Text(
                            text = if (isArabic)
                                "لم يتم تسجيل قيود محاسبية بعد. ابدأ بتسجيل قيد أو فاتورة في تبويب المحاسبة."
                            else
                                "No posted accounting entries yet. Post balanced entries in Ledger to populate financial overview bars.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FvgBarComparisonCanvas(
                            bars = listOf(
                                (if (isArabic) "إيرادات" else "Revenue") to revenue.toFloat(),
                                (if (isArabic) "تكلفة" else "COGS/Exp") to expenses.toFloat(),
                                (if (isArabic) "ربح" else "Net Profit") to netProfit.toFloat(),
                                (if (isArabic) "مخزون" else "Inv.Book") to invBook.toFloat()
                            )
                        )
                    }
                }
            }
        }

        // Inflation & Price Watch Summary
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FvgCard {
                    Text(
                        text = if (isArabic) "مراقبة التضخم وتغير الأسعار" else "Inflation & Price Movement Monitor",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.inflationIndexLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = latestInflation?.let { "${it.indexValue} (${it.monthlyRatePct}% / ${it.sourceType})" }
                                    ?: if (isArabic) "غير مسجل بعد" else "Not recorded yet",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isArabic) "المنتجات المراقبة" else "Tracked Products",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isArabic) "${state.products.size} (${state.priceObservations.size} سجل)" else "${state.products.size} (${state.priceObservations.size} obs)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (state.priceObservations.isNotEmpty()) {
                        val latestObs = state.priceObservations.last()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${latestObs.productName}: ${latestObs.previousPrice} -> ${latestObs.price} ${latestObs.currency} (${latestObs.pctChange}% ${latestObs.direction})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions Grid (6 operational modules + Calculator + Profile + Help/Tour)
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FvgCard {
                    Text(
                        text = if (isArabic) "وصول سريع للوحدات والأدوات" else "Quick Access to Modules & Tools",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionTile(s.navPrices, Icons.AutoMirrored.Filled.TrendingUp, { onNavigateTab(1) }, Modifier.weight(1f), "quick_prices")
                        QuickActionTile(s.navCosting, Icons.Filled.Calculate, { onNavigateTab(2) }, Modifier.weight(1f), "quick_costing")
                        QuickActionTile(s.navAccounting, Icons.Filled.AccountBalance, { onNavigateTab(3) }, Modifier.weight(1f), "quick_accounting")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionTile(s.navInventory, Icons.Filled.Inventory2, { onNavigateTab(4) }, Modifier.weight(1f), "quick_inventory")
                        QuickActionTile(s.navAssistantReports, Icons.Filled.AutoAwesome, { onNavigateTab(5) }, Modifier.weight(1f), "quick_assistant")
                        QuickActionTile(if (isArabic) "الآلة الحاسبة" else "Calculator", Icons.Filled.Dialpad, onOpenCalculator, Modifier.weight(1f), "quick_calculator")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionTile(if (isArabic) "ملفي" else "Profile", Icons.Filled.Person, onOpenProfile, Modifier.weight(1f), "quick_profile")
                        QuickActionTile(if (isArabic) "المساعدة" else "Help", Icons.Filled.HelpOutline, onOpenHelp, Modifier.weight(1f), "quick_help")
                        QuickActionTile(if (isArabic) "الجولة" else "Tour", Icons.Filled.Explore, onReplayTour, Modifier.weight(1f), "quick_tour")
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganizationSetupCard(
    isArabic: Boolean,
    isBusy: Boolean,
    onCreate: (String, String, String) -> Unit
) {
    val s = fvgStrings(isArabic)
    var orgName by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SDG") }
    var sectorType by remember { mutableStateOf("manufacturing") }

    FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(s.createOrgPromptTitle, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(s.createOrgPromptBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        FvgField(
            value = orgName,
            onValueChange = { orgName = it },
            label = if (isArabic) "اسم المنظمة / الشركة" else "Organization Name",
            testTag = "setup_org_name_input"
        )
        Text(if (isArabic) "القطاع:" else "Business Sector:", style = MaterialTheme.typography.labelLarge)
        FvgChipSelector(
            options = FvgFinancialEngine.FVG_SECTORS.map { it.key },
            selected = sectorType,
            onSelect = { sectorType = it },
            labelResolver = { key ->
                val sec = FvgFinancialEngine.FVG_SECTORS.firstOrNull { it.key == key }
                if (sec == null) key else if (isArabic) sec.nameAr else sec.nameEn
            }
        )
        Text(if (isArabic) "العملة الأساسية:" else "Base Currency:", style = MaterialTheme.typography.labelLarge)
        FvgChipSelector(
            options = listOf("SDG", "USD", "SAR", "AED", "EUR", "EGP"),
            selected = currency,
            onSelect = { currency = it }
        )
        FvgPrimaryButton(
            text = if (isBusy) (if (isArabic) "جاري التهيئة..." else "Initializing...")
            else if (isArabic) "إنشاء المنظمة وتفعيل الدليل المحاسبي (تجربة 7 أيام)" else "Initialize Organization (7-Day Trial)",
            enabled = !isBusy && orgName.isNotBlank(),
            icon = Icons.Filled.Business,
            onClick = { onCreate(orgName, currency, sectorType) },
            testTag = "create_org_button"
        )
    }
}

@Composable
private fun KpiMiniCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
