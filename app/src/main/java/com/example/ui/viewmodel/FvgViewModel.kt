package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.FvgAiRepository
import com.example.data.repository.FvgRepository
import com.example.domain.engine.FvgFinancialEngine
import com.example.domain.export.ReportExportPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FvgNotificationAlert(
    val id: String,
    val titleEn: String,
    val titleAr: String,
    val detailEn: String,
    val detailAr: String,
    val severity: String, // "critical", "warning", "info"
    val targetTab: Int
)

data class FvgUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val isPlatformOwner: Boolean = false,
    val userProfile: UserProfile? = null,
    val sessions: List<UserSessionRecord> = emptyList(),
    val organizations: List<Organization> = emptyList(),
    val activeOrg: Organization? = null,
    val products: List<Product> = emptyList(),
    val priceObservations: List<PriceObservation> = emptyList(),
    val inflationRecords: List<InflationRecord> = emptyList(),
    val priceBaskets: List<PriceBasket> = emptyList(),
    val fxRates: List<FxRate> = emptyList(),
    val costSnapshots: List<CostSnapshot> = emptyList(),
    val standardCosts: List<StandardCostSheet> = emptyList(),
    val bomFormulas: List<BomFormulaRecord> = emptyList(),
    val productionOrders: List<ProductionOrderRecord> = emptyList(),
    val scenarios: List<ScenarioRecord> = emptyList(),
    val accounts: List<AccountRecord> = emptyList(),
    val journalEntries: List<JournalEntryRecord> = emptyList(),
    val counterpartyItems: List<CounterpartyRecord> = emptyList(),
    val budgets: List<BudgetRecord> = emptyList(),
    val inventoryItems: List<InventoryItemRecord> = emptyList(),
    val inventoryMovements: List<InventoryMovementRecord> = emptyList(),
    val stocktakes: List<StocktakeSessionRecord> = emptyList(),
    val scrapLosses: List<ScrapLossRecord> = emptyList(),
    val ocrDrafts: List<OcrDraftRecord> = emptyList(),
    val auditLogs: List<AuditLogRecord> = emptyList(),
    val paymentRequests: List<PaymentRequestRecord> = emptyList(),
    val aiConversations: List<AiConversationRecord> = emptyList(),
    val activeConversationId: String? = null,
    val platformPlans: List<PlatformPlanRecord> = emptyList(),
    val announcements: List<AppAnnouncementRecord> = emptyList(),
    val supportTickets: List<SupportTicketRecord> = emptyList(),
    // Platform Owner Console collections
    val ownerAllPaymentRequests: List<PaymentRequestRecord> = emptyList(),
    val ownerAllOrgs: List<Organization> = emptyList(),
    val ownerAllUsers: List<UserProfile> = emptyList(),
    val ownerSupportTickets: List<SupportTicketRecord> = emptyList(),
    val statementsReport: FvgFinancialEngine.FinancialStatementsReport? = null,
    val financialRatios: List<FvgFinancialEngine.FinancialRatioResult> = emptyList(),
    val csvPreviewRows: List<FvgFinancialEngine.CsvProductRow> = emptyList(),
    val accountingCsvPreviewRows: List<FvgFinancialEngine.AccountingCsvRow> = emptyList(),
    val aiMode: String = "financial", // "general", "financial", "ocr"
    val aiResponse: String = "",
    val chatTranscript: List<Pair<String, String>> = emptyList(),
    val isAiStreaming: Boolean = false,
    val isAiOfflineFallback: Boolean = false,
    val isOcrExtracting: Boolean = false,
    val serverClockOffsetMillis: Long = 0L,
    val serverAnchorMillis: Long = System.currentTimeMillis(),
    val elapsedBaselineRealtimeMillis: Long = SystemClock.elapsedRealtime(),
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val currentUserRole: String
        get() = activeOrg?.let { org ->
            val uid = userProfile?.userId.orEmpty()
            org.roles[uid] ?: if (org.ownerId == uid) "owner" else "viewer"
        } ?: "viewer"

    val canEditFinancialData: Boolean
        get() = currentUserRole in setOf("owner", "admin", "accountant")

    val canAdminOrg: Boolean
        get() = currentUserRole in setOf("owner", "admin")

    val totalInventoryBookValue: BigDecimal
        get() = inventoryItems.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(FvgFinancialEngine.dOrZero(item.totalBookValue))
        }

    val totalReplacementCost: BigDecimal
        get() = inventoryItems.fold(BigDecimal.ZERO) { acc, item ->
            val qty = FvgFinancialEngine.dOrZero(item.quantityOnHand)
            val mkt = FvgFinancialEngine.dOrZero(item.currentMarketPrice)
            val book = FvgFinancialEngine.dOrZero(item.totalBookValue)
            acc.add(if (mkt > BigDecimal.ZERO) qty.multiply(mkt) else book)
        }

    fun effectiveTrialDurationHours(): Long =
        activeOrg?.trialDurationHours ?: userProfile?.trialDurationHours ?: FvgFinancialEngine.TRIAL_DURATION_HOURS

    fun effectiveTrialEndsMillis(): Long? =
        FvgFinancialEngine.computeServerSyncedTrialEndMillis(
            userTrialStartMillis = userProfile?.trialStartedAt?.toDate()?.time,
            orgTrialEndsAtMillis = activeOrg?.trialEndsAt?.toDate()?.time,
            durationHours = effectiveTrialDurationHours()
        )

    fun estimatedServerNowMillis(): Long =
        serverAnchorMillis + (SystemClock.elapsedRealtime() - elapsedBaselineRealtimeMillis).coerceAtLeast(0L)

    fun isTrialExpiredAndLocked(): Boolean {
        val org = activeOrg ?: return false
        if (org.subscriptionStatus == "active") return false
        if (org.subscriptionStatus == "expired" || org.subscriptionStatus == "cancelled") return true
        val endsMillis = effectiveTrialEndsMillis() ?: return false
        return estimatedServerNowMillis() >= endsMillis
    }

    fun buildNotifications(): List<FvgNotificationAlert> {
        if (userProfile?.notificationsEnabled == false) return emptyList()
        val nowMillis = estimatedServerNowMillis()
        val alerts = mutableListOf<FvgNotificationAlert>()
        val org = activeOrg
        val hours = effectiveTrialDurationHours()
        val daysLabel = (hours / 24L).coerceAtLeast(1L)

        if (org != null && (org.subscriptionStatus == "trial" || org.subscriptionStatus == "expired")) {
            val endsMillis = effectiveTrialEndsMillis()
            if (org.subscriptionStatus == "expired") {
                alerts.add(
                    FvgNotificationAlert(
                        id = "trial_expired",
                        titleEn = "$daysLabel-Day Trial Expired",
                        titleAr = "انتهت الفترة التجريبية ($daysLabel أيام / $hours ساعة)",
                        detailEn = "Your ${hours}-hour trial has ended. Submit a Bankak / O-Cash / Bank Transfer request in Reports & Billing.",
                        detailAr = "انتهت مدة التجربة ($hours ساعة). قدّم إثبات الدفع عبر بنكك / أوكاش / تحويل بنكي في المنظمة والاشتراك.",
                        severity = "critical",
                        targetTab = 5
                    )
                )
            } else if (endsMillis != null) {
                val cd = FvgFinancialEngine.computeTrialCountdown(endsMillis, nowMillis)
                if (cd.isExpired) {
                    alerts.add(
                        FvgNotificationAlert(
                            id = "trial_expired",
                            titleEn = "$daysLabel-Day Trial Expired",
                            titleAr = "انتهت الفترة التجريبية ($daysLabel أيام)",
                            detailEn = "Your ${hours}-hour trial has ended. Review plan & payment options in Reports & Billing.",
                            detailAr = "انتهت مدة التجربة ($hours ساعة). راجع خيارات الباقة والدفع في المنظمة والاشتراك.",
                            severity = "critical",
                            targetTab = 5
                        )
                    )
                } else {
                    alerts.add(
                        FvgNotificationAlert(
                            id = "trial_countdown",
                            titleEn = "Trial Active: ${cd.days}d ${cd.hours}h left",
                            titleAr = "التجربة نشطة: متبقي ${cd.days}ي ${cd.hours}س",
                            detailEn = "Full ${hours}h server-synced trial ends in ${cd.days}d ${cd.hours}h ${cd.minutes}m ${cd.seconds}s.",
                            detailAr = "تنتهي الفترة التجريبية خلال ${cd.days} يوم و ${cd.hours} ساعة و ${cd.minutes} دقيقة.",
                            severity = if (cd.totalRemainingHours < 24) "warning" else "info",
                            targetTab = 5
                        )
                    )
                }
            }
        }
        val spikes = priceObservations.filter {
            FvgFinancialEngine.isUnusualPriceMovement(runCatching { FvgFinancialEngine.d(it.pctChange) }.getOrNull())
        }
        if (spikes.isNotEmpty()) {
            val latest = spikes.last()
            alerts.add(
                FvgNotificationAlert(
                    id = "price_spike_${latest.id}",
                    titleEn = "Unusual Price Movement (${spikes.size})",
                    titleAr = "حركة سعر غير معتادة (${spikes.size})",
                    detailEn = "${latest.productName} moved ${latest.pctChange}% (${latest.previousPrice} -> ${latest.price} ${latest.currency}).",
                    detailAr = "تغير سعر ${latest.productName} بنسبة ${latest.pctChange}%.",
                    severity = "warning",
                    targetTab = 1
                )
            )
        }
        val todayDays = nowMillis / 86400_000L
        val fxIssues = fxRates.count {
            val f = FvgFinancialEngine.assessFxRateFreshness(it.effectiveDate, it.status, todayDays)
            f.isStale || f.isUnapproved
        }
        if (fxIssues > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "fx_freshness",
                    titleEn = "$fxIssues Stale or Unapproved FX Rates",
                    titleAr = "$fxIssues أسعار صرف قديمة أو غير معتمدة",
                    detailEn = "Update or approve manual FX rates older than 7 days before posting multi-currency transactions.",
                    detailAr = "حدّث أو اعتمد أسعار الصرف اليدوية الأقدم من 7 أيام قبل الترحيل.",
                    severity = "warning",
                    targetTab = 1
                )
            )
        }
        val aging = FvgFinancialEngine.computeArApAgingBuckets(
            counterpartyItems.map {
                val rem = FvgFinancialEngine.dOrZero(it.amount).subtract(FvgFinancialEngine.dOrZero(it.paidAmount))
                Triple(it.dueDate, rem, it.status)
            },
            todayDays
        )
        if (aging.overdueCount > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "overdue_arap",
                    titleEn = "${aging.overdueCount} Overdue AR/AP Invoices",
                    titleAr = "${aging.overdueCount} فواتير ذمم متأخرة",
                    detailEn = "Review counterparty aging buckets in Ledger -> AR / AP.",
                    detailAr = "راجع أعمار الديون والفواتير المستحقة في تبويب المحاسبة.",
                    severity = "critical",
                    targetTab = 3
                )
            )
        }
        val pendingScrap = scrapLosses.count { it.status == "pending_approval" }
        if (pendingScrap > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "pending_scrap",
                    titleEn = "$pendingScrap Pending Scrap / Loss Approvals",
                    titleAr = "$pendingScrap طلبات هدر وتلف بانتظار الاعتماد",
                    detailEn = "Approve or reject scrap requests to adjust stock & loss ledger.",
                    detailAr = "اعتمد أو ارفض طلبات التوالف لترحيل الأثر المخزني والمحاسبي.",
                    severity = "warning",
                    targetTab = 4
                )
            )
        }
        val expiringSoon = inventoryItems.count { it.expiryDate.isNotBlank() && it.expiryDate <= SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis + 30L * 86400_000L)) }
        if (expiringSoon > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "inv_expiring",
                    titleEn = "$expiringSoon Inventory Batches Near Expiry",
                    titleAr = "$expiringSoon دفعات مخزون قريبة الانتهاء",
                    detailEn = "Review batch expiry dates & FEFO dispatch in Stock.",
                    detailAr = "راجع تواريخ الصلاحية وصرف الوارد أولاً منتهي الصلاحية في المخزون.",
                    severity = "warning",
                    targetTab = 4
                )
            )
        }
        val pendingPayments = paymentRequests.count { it.status == "pending_verification" }
        if (pendingPayments > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "pending_payments",
                    titleEn = "$pendingPayments Payment Requests Awaiting Owner Review",
                    titleAr = "$pendingPayments طلبات دفع بانتظار مراجعة المالكة",
                    detailEn = "Submitted requests stay pending until verified by the platform owner.",
                    detailAr = "تبقى طلبات الدفع قيد المراجعة حتى تتحقق منها مالكة المنصة.",
                    severity = "info",
                    targetTab = 5
                )
            )
        }
        val pendingStocktakes = stocktakes.count { it.status == "in_review" }
        if (pendingStocktakes > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "pending_stocktakes",
                    titleEn = "$pendingStocktakes Stocktake Sessions In Review",
                    titleAr = "$pendingStocktakes جلسات جرد بانتظار الاعتماد",
                    detailEn = "Finalize physical stocktake discrepancy postings in Stock.",
                    detailAr = "قم بمراجعة واعتماد فروقات الجرد الفعلي في تبويب المخزون.",
                    severity = "info",
                    targetTab = 4
                )
            )
        }
        val pendingOcr = ocrDrafts.count { it.reviewStatus == "pending_review" }
        if (pendingOcr > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "pending_ocr",
                    titleEn = "$pendingOcr OCR Drafts Pending Review",
                    titleAr = "$pendingOcr مسودات فواتير OCR بانتظار المراجعة",
                    detailEn = "Verify extracted vendor & amount fields in Assistant & Docs.",
                    detailAr = "تحقق من حقول المورد والمبلغ المستخرجة في المساعد والوثائق.",
                    severity = "info",
                    targetTab = 5
                )
            )
        }
        val drafts = journalEntries.count { it.status == "draft" }
        if (drafts > 0) {
            alerts.add(
                FvgNotificationAlert(
                    id = "draft_jes",
                    titleEn = "$drafts Unposted Draft Journal Entries",
                    titleAr = "$drafts قيود محاسبية مسودة غير مرحّلة",
                    detailEn = "Approve and post draft journals so they reflect on Financial Statements.",
                    detailAr = "اعتمد وترحّل المسودات لتنعكس في القوائم المالية.",
                    severity = "info",
                    targetTab = 3
                )
            )
        }
        if (statementsReport != null && !statementsReport.isAccountingEquationBalanced) {
            alerts.add(
                FvgNotificationAlert(
                    id = "eq_unbalanced",
                    titleEn = "Accounting Equation Check Attention",
                    titleAr = "تنبيه توازن المعادلة المحاسبية",
                    detailEn = "Verify opening balances & posted journal credits vs debits.",
                    detailAr = "تحقق من الأرصدة الافتتاحية وتوازن القيود المرحّلة.",
                    severity = "critical",
                    targetTab = 3
                )
            )
        }
        return alerts
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FvgViewModel(
    private val repository: FvgRepository,
    private val aiRepository: FvgAiRepository,
    val currentUserId: String,
    val userEmail: String,
    val userDisplayName: String
) : ViewModel() {

    private val isOwnerAccount: Boolean =
        userEmail.trim().equals("Financialvisionguide@gmail.com", ignoreCase = true)

    private val _uiState = MutableStateFlow(FvgUiState(isPlatformOwner = isOwnerAccount))
    val uiState: StateFlow<FvgUiState> = _uiState.asStateFlow()
    private var aiStreamingJob: Job? = null
    private var workspaceJob: Job? = null

    private val authInstance: com.google.firebase.auth.FirebaseAuth? = runCatching {
        com.google.firebase.auth.FirebaseAuth.getInstance(repository.firebaseApp)
    }.getOrNull()

    private val authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (firebaseAuth.currentUser?.uid != currentUserId) {
            stopWorkspaceListeners()
        }
    }

    init {
        authInstance?.addAuthStateListener(authStateListener)
        if (currentUserId.isNotBlank() && (authInstance == null || authInstance.currentUser?.uid == currentUserId)) {
            startWorkspaceListeners()
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun stopWorkspaceListeners() {
        workspaceJob?.cancel()
        workspaceJob = null
        aiStreamingJob?.cancel()
        aiStreamingJob = null
    }

    fun restartWorkspaceIfInactive() {
        if (currentUserId.isBlank()) return
        if (authInstance != null && authInstance.currentUser?.uid != currentUserId) return
        if (workspaceJob?.isActive != true) {
            startWorkspaceListeners()
        }
    }

    override fun onCleared() {
        super.onCleared()
        authInstance?.removeAuthStateListener(authStateListener)
        stopWorkspaceListeners()
    }

    private fun handleStreamCatch(error: Throwable, stopLoading: Boolean = false) {
        val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
            .firstOrNull()
        if (fsErr == null && error is CancellationException) throw error
        if (!repository.isSignedInAs(currentUserId)) {
            if (stopLoading) _uiState.update { it.copy(isLoading = false) }
            return
        }
        val msg = (fsErr ?: error).message
        _uiState.update {
            if (stopLoading) it.copy(errorMessage = msg, isLoading = false)
            else it.copy(errorMessage = msg)
        }
    }

    private fun startWorkspaceListeners() {
        workspaceJob?.cancel()
        workspaceJob = viewModelScope.launch {
            launch {
                if (!repository.isSignedInAs(currentUserId)) return@launch
                repository.ensureUserProfile(currentUserId, userEmail, userDisplayName)
                    .onFailure { e ->
                        if (repository.isSignedInAs(currentUserId)) {
                            _uiState.update { it.copy(errorMessage = e.message) }
                        }
                    }
                if (!repository.isSignedInAs(currentUserId)) return@launch
                val offset = repository.syncServerClockOffset(currentUserId)
                val nowBaseline = SystemClock.elapsedRealtime()
                _uiState.update {
                    it.copy(
                        serverClockOffsetMillis = offset,
                        serverAnchorMillis = System.currentTimeMillis() + offset,
                        elapsedBaselineRealtimeMillis = nowBaseline
                    )
                }
            }

            val profileFlow = repository.observeUserProfile(currentUserId)
                .catch { e -> handleStreamCatch(e, stopLoading = true) }

            val orgsFlow = repository.observeOrganizations(currentUserId)
                .catch { e -> handleStreamCatch(e, stopLoading = true) }

            launch {
                combine(profileFlow, orgsFlow) { profile, orgs ->
                    val active = orgs.firstOrNull { it.id == profile?.activeOrgId } ?: orgs.firstOrNull()
                    Triple(profile, orgs, active)
                }.collect { (profile, orgs, active) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            userProfile = profile,
                            organizations = orgs,
                            activeOrg = active
                        )
                    }
                }
            }

            val activeOrgIdFlow = _uiState.map { it.activeOrg?.id.orEmpty() }.distinctUntilChanged()

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeProducts(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(products = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observePriceObservations(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(priceObservations = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeInflationRecords(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(inflationRecords = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observePriceBaskets(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(priceBaskets = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeFxRates(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(fxRates = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeCostSnapshots(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(costSnapshots = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeScenarios(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(scenarios = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(Pair(emptyList<AccountRecord>(), emptyList<JournalEntryRecord>()))
                    else combine(
                        repository.observeAccounts(orgId, currentUserId),
                        repository.observeJournalEntries(orgId, currentUserId)
                    ) { accs, jes -> accs to jes }
                }.catch { e -> handleStreamCatch(e) }
                    .collect { (accs, jes) ->
                        val report = computeStatements(accs, jes)
                        val intExp = report?.interestAndOther ?: BigDecimal.ZERO
                        val ratios = report?.let { FvgFinancialEngine.computeFinancialRatios(it, intExp) }.orEmpty()
                        _uiState.update {
                            it.copy(
                                accounts = accs,
                                journalEntries = jes,
                                statementsReport = report,
                                financialRatios = ratios
                            )
                        }
                    }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeCounterpartyItems(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(counterpartyItems = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeBudgets(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(budgets = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeInventoryItems(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(inventoryItems = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeInventoryMovements(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(inventoryMovements = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeStocktakes(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(stocktakes = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeOcrDrafts(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(ocrDrafts = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeAuditLogs(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(auditLogs = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeStandardCosts(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(standardCosts = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeProductionOrders(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(productionOrders = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeScrapLosses(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(scrapLosses = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observePaymentRequests(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(paymentRequests = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeBomFormulas(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(bomFormulas = list) } }
            }

            launch {
                activeOrgIdFlow.flatMapLatest { orgId ->
                    if (orgId.isBlank()) flowOf(emptyList())
                    else repository.observeAiConversations(orgId, currentUserId)
                }.catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(aiConversations = list) } }
            }

            launch {
                repository.observeSessions(currentUserId)
                    .catch { e -> handleStreamCatch(e) }
                    .collect { list -> _uiState.update { it.copy(sessions = list) } }
            }

            launch {
                repository.observePlatformPlans().collect { list ->
                    _uiState.update { it.copy(platformPlans = list) }
                }
            }

            launch {
                repository.observeAnnouncements().collect { list ->
                    _uiState.update { it.copy(announcements = list.filter { a -> a.active }) }
                }
            }

            launch {
                repository.observeOwnSupportTickets(currentUserId).collect { list ->
                    _uiState.update { it.copy(supportTickets = list) }
                }
            }

            if (isOwnerAccount) {
                launch {
                    repository.observeAllPaymentRequestsOwner().collect { list ->
                        _uiState.update { it.copy(ownerAllPaymentRequests = list) }
                    }
                }
                launch {
                    repository.observeAllOrganizationsOwner().collect { list ->
                        _uiState.update { it.copy(ownerAllOrgs = list) }
                    }
                }
                launch {
                    repository.observeAllUsersOwner().collect { list ->
                        _uiState.update { it.copy(ownerAllUsers = list) }
                    }
                }
                launch {
                    repository.observeSupportTicketsOwner().collect { list ->
                        _uiState.update { it.copy(ownerSupportTickets = list) }
                    }
                }
            }
        }
    }

    private fun computeStatements(
        accs: List<AccountRecord>,
        jes: List<JournalEntryRecord>
    ): FvgFinancialEngine.FinancialStatementsReport? {
        if (accs.isEmpty()) return null
        val accInputs = accs.map {
            FvgFinancialEngine.AccountInput(
                code = it.code,
                name = it.name,
                accountType = it.accountType,
                subType = it.subType,
                normalBalance = it.normalBalance,
                openingBalance = FvgFinancialEngine.dOrZero(it.openingBalance)
            )
        }
        val postedLines = jes.filter { it.status == "posted" || it.status == "reversed" }.map { je ->
            je.lines.map { l ->
                FvgFinancialEngine.JournalLineInput(
                    accountCode = l["accountCode"]?.toString().orEmpty(),
                    accountName = l["accountName"]?.toString().orEmpty(),
                    debit = FvgFinancialEngine.dOrZero(l["debit"]?.toString()),
                    credit = FvgFinancialEngine.dOrZero(l["credit"]?.toString())
                )
            }
        }
        return FvgFinancialEngine.generateFinancialStatements(accInputs, postedLines)
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    private fun runMutation(successText: String, block: suspend (Organization) -> Result<*>) {
        val org = _uiState.value.activeOrg
        if (org == null) {
            _uiState.update { it.copy(errorMessage = "Please create or select an organization first.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
            val res = block(org)
            res.onSuccess {
                _uiState.update { it.copy(isBusy = false, statusMessage = successText) }
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, errorMessage = e.message ?: "Operation failed") }
            }
        }
    }

    // --- Preferences & Organization ---

    fun createOrganization(name: String, baseCurrency: String, sectorType: String = "manufacturing") {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.createOrganization(currentUserId, userEmail, name, baseCurrency, sectorType)
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, statusMessage = "Workspace '$name' initialized.") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBusy = false, errorMessage = e.message) }
                }
        }
    }

    fun switchOrganization(orgId: String) {
        viewModelScope.launch {
            repository.updateUserPreferences(currentUserId, activeOrgId = orgId)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            repository.updateExtendedUserPreferences(currentUserId, preferredLanguage = lang)
        }
    }

    fun setDarkMode(dark: Boolean) {
        viewModelScope.launch {
            repository.updateExtendedUserPreferences(
                currentUserId,
                darkMode = dark,
                themeMode = if (dark) "dark" else "light"
            )
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.updateExtendedUserPreferences(
                currentUserId,
                darkMode = mode == "dark",
                themeMode = mode
            )
        }
    }

    fun markTourCompleted() {
        viewModelScope.launch {
            repository.updateExtendedUserPreferences(currentUserId, tourCompleted = true)
        }
    }

    fun updateExtendedProfile(
        displayName: String,
        preferredLanguage: String,
        themeMode: String,
        fontScale: Float,
        timezone: String,
        dateFormat: String,
        numberFormat: String,
        defaultCurrency: String,
        avatarLocalRef: String?,
        contactPhoneOptional: String,
        notificationsEnabled: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.updateExtendedUserPreferences(
                userId = currentUserId,
                preferredLanguage = preferredLanguage,
                darkMode = themeMode == "dark",
                themeMode = themeMode,
                fontScale = fontScale,
                displayName = displayName,
                timezone = timezone,
                dateFormat = dateFormat,
                numberFormat = numberFormat,
                defaultCurrency = defaultCurrency,
                avatarLocalRef = avatarLocalRef,
                contactPhoneOptional = contactPhoneOptional,
                notificationsEnabled = notificationsEnabled
            ).onSuccess {
                _uiState.update { it.copy(isBusy = false, statusMessage = "Profile settings saved.") }
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, errorMessage = e.message) }
            }
        }
    }

    fun registerSession(sessionId: String, deviceLabel: String) {
        viewModelScope.launch {
            repository.upsertSession(currentUserId, sessionId, deviceLabel, "Android")
        }
    }

    fun revokeSession(sessionId: String) {
        viewModelScope.launch {
            repository.revokeSession(currentUserId, sessionId)
                .onSuccess { _uiState.update { it.copy(statusMessage = "Session revoked.") } }
                .onFailure { e -> _uiState.update { it.copy(errorMessage = e.message) } }
        }
    }

    fun submitSupportTicket(senderName: String, senderEmail: String, message: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val org = _uiState.value.activeOrg
            repository.submitSupportTicket(
                actorUid = currentUserId,
                senderName = senderName.ifBlank { userDisplayName },
                senderEmail = senderEmail.ifBlank { userEmail },
                orgId = org?.id.orEmpty(),
                orgName = org?.name.orEmpty(),
                message = message
            ).onSuccess { ticketId ->
                _uiState.update { it.copy(isBusy = false, statusMessage = "Support request sent [$ticketId].") }
            }.onFailure { e ->
                _uiState.update { it.copy(isBusy = false, errorMessage = e.message) }
            }
        }
    }

    fun updateOrganizationSettings(
        name: String,
        currency: String,
        sectorType: String = _uiState.value.activeOrg?.sectorType ?: "manufacturing",
        brandTagline: String = _uiState.value.activeOrg?.brandTagline.orEmpty(),
        countryState: String = _uiState.value.activeOrg?.countryState.orEmpty(),
        fiscalYearStart: String = _uiState.value.activeOrg?.fiscalYearStart ?: "01-01",
        taxEnabled: Boolean = _uiState.value.activeOrg?.taxEnabled ?: false,
        defaultTaxRatePct: String = _uiState.value.activeOrg?.defaultTaxRatePct ?: "0.00"
    ) {
        runMutation("Organization settings saved.") { org ->
            repository.updateOrganizationSettings(
                org = org,
                newName = name,
                newCurrency = currency,
                sectorType = sectorType,
                brandTagline = brandTagline,
                countryState = countryState,
                fiscalYearStart = fiscalYearStart,
                taxEnabled = taxEnabled,
                defaultTaxRatePct = defaultTaxRatePct,
                actorUid = currentUserId,
                actorEmail = userEmail
            )
        }
    }

    fun updateMemberRole(memberUid: String, newRole: String) {
        runMutation("Member role updated to $newRole.") { org ->
            repository.updateMemberRole(org, memberUid, newRole, currentUserId, userEmail)
        }
    }

    // --- Payment Requests (Bankak / O-Cash / Bank Transfer) ---

    fun submitPaymentRequest(
        planTier: String,
        paymentMethod: String,
        amount: String,
        currency: String,
        senderName: String,
        referenceNo: String,
        transferDate: String,
        notes: String,
        last4Digits: String = "",
        attachmentNote: String = ""
    ) {
        runMutation("Payment request submitted [pending_verification].") { org ->
            repository.submitPaymentRequest(
                org, currentUserId, userEmail, planTier, paymentMethod,
                amount, currency, senderName, referenceNo, transferDate, notes, last4Digits, attachmentNote
            )
        }
    }

    // --- BOM Formulas ---

    fun saveBomFormula(
        existingId: String?,
        productName: String,
        outputUnit: String,
        batchSize: String,
        ingredients: List<Map<String, Any>>,
        unitMaterialCost: String,
        scrapAllowancePct: String,
        notes: String
    ) {
        runMutation("BOM formula saved.") { org ->
            repository.saveBomFormula(
                org, currentUserId, userEmail, existingId, productName, outputUnit,
                batchSize, ingredients, unitMaterialCost, scrapAllowancePct, notes
            )
        }
    }

    fun deleteBomFormula(bomId: String) {
        runMutation("BOM formula deleted.") { org ->
            repository.deleteBomFormula(org, currentUserId, userEmail, bomId)
        }
    }

    // --- Platform Owner Console Actions (Financialvisionguide@gmail.com only) ---

    fun reviewPaymentRequestAsOwner(
        request: PaymentRequestRecord,
        decision: String,
        reviewNotes: String
    ) {
        if (!isOwnerAccount) {
            _uiState.update { it.copy(errorMessage = "Platform owner permission required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.reviewPaymentRequestAsOwner(request, decision, reviewNotes, currentUserId, userEmail)
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, statusMessage = "Payment ${request.referenceNo} marked $decision.") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBusy = false, errorMessage = e.message) }
                }
        }
    }

    fun ownerUpdateOrgSubscription(
        org: Organization,
        planTier: String,
        subscriptionStatus: String,
        newTrialEndsAtMillis: Long?,
        reason: String
    ) {
        if (!isOwnerAccount) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.ownerUpdateOrgSubscription(org, planTier, subscriptionStatus, newTrialEndsAtMillis, reason, currentUserId, userEmail)
                .onSuccess { _uiState.update { it.copy(isBusy = false, statusMessage = "Subscription updated for ${org.name}.") } }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, errorMessage = e.message) } }
        }
    }

    fun savePlatformPlanAsOwner(plan: PlatformPlanRecord) {
        if (!isOwnerAccount) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.savePlatformPlan(plan)
                .onSuccess { _uiState.update { it.copy(isBusy = false, statusMessage = "Platform plan saved.") } }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, errorMessage = e.message) } }
        }
    }

    fun saveAnnouncementAsOwner(ann: AppAnnouncementRecord) {
        if (!isOwnerAccount) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.saveAnnouncement(ann, userEmail)
                .onSuccess { _uiState.update { it.copy(isBusy = false, statusMessage = "Announcement published.") } }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, errorMessage = e.message) } }
        }
    }

    fun replySupportTicketAsOwner(ticketId: String, status: String, ownerReply: String) {
        if (!isOwnerAccount) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.replySupportTicket(ticketId, status, ownerReply)
                .onSuccess { _uiState.update { it.copy(isBusy = false, statusMessage = "Support ticket updated.") } }
                .onFailure { e -> _uiState.update { it.copy(isBusy = false, errorMessage = e.message) } }
        }
    }

    // --- Prices & CSV ---

    fun recordPriceObservation(
        existingProduct: Product?,
        name: String,
        sku: String,
        category: String,
        description: String,
        unit: String,
        currency: String,
        location: String,
        source: String,
        newPrice: String,
        observationDate: String,
        notes: String,
        attachmentRef: String = ""
    ) {
        runMutation("Price observation saved & percentage delta computed.") { org ->
            repository.recordProductAndPriceObservation(
                org, currentUserId, userEmail, existingProduct, name, sku, category,
                description, unit, currency, location, source, newPrice, observationDate, notes, attachmentRef
            )
        }
    }

    fun previewCsvImport(csvText: String) {
        val existingSkus = _uiState.value.products.mapNotNull { it.sku.takeIf { s -> s.isNotBlank() } }.toSet()
        val parsed = FvgFinancialEngine.parseAndValidateProductCsv(csvText, existingSkus)
        _uiState.update {
            it.copy(
                csvPreviewRows = parsed,
                statusMessage = if (parsed.isEmpty()) null else "Parsed ${parsed.size} rows (${parsed.count { r -> r.error == null }} valid).",
                errorMessage = if (parsed.isEmpty()) "CSV must include a header row and at least 1 product row." else null
            )
        }
    }

    fun clearCsvPreview() {
        _uiState.update { it.copy(csvPreviewRows = emptyList()) }
    }

    fun commitCsvImport(observationDate: String) {
        val rows = _uiState.value.csvPreviewRows
        runMutation("Validated CSV rows imported to product price registry.") { org ->
            repository.importValidatedCsvProducts(org, currentUserId, userEmail, rows, observationDate).also { res ->
                if (res.isSuccess) _uiState.update { it.copy(csvPreviewRows = emptyList()) }
            }
        }
    }

    // --- Inflation, Baskets & FX ---

    fun saveInflation(
        period: String,
        category: String,
        monthlyRate: String,
        annualRate: String,
        indexValue: String,
        basePeriod: String,
        sourceType: String,
        sourceName: String,
        formulaNote: String
    ) {
        runMutation("Inflation record saved.") { org ->
            repository.saveInflationRecord(
                org, currentUserId, userEmail, period, category, monthlyRate,
                annualRate, indexValue, basePeriod, sourceType, sourceName, formulaNote
            )
        }
    }

    fun saveBasket(
        existing: PriceBasket?,
        name: String,
        basePeriod: String,
        currentPeriod: String,
        currency: String,
        items: List<BasketItemMap>
    ) {
        runMutation("Weighted price basket saved with snapshot.") { org ->
            repository.savePriceBasket(org, currentUserId, userEmail, existing, name, basePeriod, currentPeriod, currency, items)
        }
    }

    fun saveFxRate(
        baseCurrency: String,
        quoteCurrency: String,
        rate: String,
        effectiveDate: String,
        rateType: String,
        sourceName: String,
        notes: String,
        effectiveTime: String = "",
        sourceKind: String = "manual",
        status: String = "approved"
    ) {
        runMutation("Exchange rate saved ($status).") { org ->
            repository.saveFxRate(
                org, currentUserId, userEmail, baseCurrency, quoteCurrency, rate,
                effectiveDate, rateType, sourceName, notes, effectiveTime, sourceKind, status
            )
        }
    }

    fun updateFxRateStatus(fxId: String, newStatus: String) {
        runMutation("Exchange rate marked $newStatus.") { org ->
            repository.updateFxRateStatus(org, currentUserId, userEmail, fxId, newStatus)
        }
    }

    // --- Costing (8 Methods + Risk Engine) & Scenarios ---

    fun saveCosting(
        productId: String,
        productName: String,
        period: String,
        currency: String,
        method: String,
        input: FvgFinancialEngine.CostingInput,
        completedEquivUnits: BigDecimal? = null,
        targetMarginPct: BigDecimal = BigDecimal("20")
    ) {
        val latestInf = _uiState.value.inflationRecords.firstOrNull()?.monthlyRatePct?.let {
            runCatching { FvgFinancialEngine.d(it) }.getOrNull()
        }
        runMutation("Cost calculation snapshot ($method) saved with risk assessment.") { org ->
            repository.saveCostSnapshot(
                org = org,
                actorUid = currentUserId,
                actorEmail = userEmail,
                productId = productId,
                productName = productName,
                period = period,
                currency = currency,
                costingMethod = method,
                input = input,
                latestInflationPct = latestInf,
                completedEquivUnits = completedEquivUnits,
                targetMarginPct = targetMarginPct
            )
        }
    }

    fun saveScenario(
        snapshot: CostSnapshot,
        name: String,
        matPct: String,
        labPct: String,
        ovhPct: String,
        pricePct: String,
        scenarioType: String = "Custom",
        fxPct: String = "0",
        volumePct: String = "0"
    ) {
        runMutation("What-If scenario simulated and saved.") { org ->
            repository.saveScenario(
                org = org,
                actorUid = currentUserId,
                actorEmail = userEmail,
                snapshot = snapshot,
                name = name,
                matPct = FvgFinancialEngine.dOrZero(matPct),
                labPct = FvgFinancialEngine.dOrZero(labPct),
                ovhPct = FvgFinancialEngine.dOrZero(ovhPct),
                pricePct = FvgFinancialEngine.dOrZero(pricePct),
                scenarioType = scenarioType,
                fxPct = FvgFinancialEngine.dOrZero(fxPct),
                volumePct = FvgFinancialEngine.dOrZero(volumePct)
            )
        }
    }

    fun createStandardCost(
        productId: String,
        productName: String,
        stdMaterial: String,
        stdLaborHours: String,
        stdLaborRate: String,
        stdOverhead: String,
        notes: String
    ) {
        val versions = _uiState.value.standardCosts.count { it.productName.equals(productName.trim(), ignoreCase = true) }.toLong()
        runMutation("Standard Cost sheet saved (v${versions + 1}).") { org ->
            repository.createStandardCostSheet(
                org, currentUserId, userEmail, productId, productName, versions,
                stdMaterial, stdLaborHours, stdLaborRate, stdOverhead, notes
            )
        }
    }

    fun createProductionOrder(
        productId: String,
        productName: String,
        period: String,
        plannedUnits: String,
        goodUnits: String,
        scrapUnits: String,
        actualMat: String,
        actualLab: String,
        actualOvh: String
    ) {
        runMutation("Production order recorded with stage tracking.") { org ->
            repository.createProductionOrder(
                org, currentUserId, userEmail, productId, productName, period,
                plannedUnits, goodUnits, scrapUnits, actualMat, actualLab, actualOvh,
                listOf("Material Prep", "Processing", "Quality & Packaging")
            )
        }
    }

    fun completeProductionOrder(
        order: ProductionOrderRecord,
        scrapUnits: String,
        actMat: String,
        actLab: String,
        actOvh: String
    ) {
        runMutation("Production order marked completed & variance ready.") { org ->
            repository.completeProductionOrder(org, currentUserId, userEmail, order, scrapUnits, actMat, actLab, actOvh)
        }
    }

    // --- Accounting & AR/AP ---

    fun createAccount(
        code: String,
        name: String,
        nameAr: String,
        type: String,
        subType: String,
        normalBal: String,
        openingBalance: String
    ) {
        runMutation("Account $code added to Chart of Accounts.") { org ->
            repository.createAccount(org, currentUserId, userEmail, code, name, nameAr, type, subType, normalBal, openingBalance)
        }
    }

    fun postJournalEntry(
        referenceNo: String,
        entryDate: String,
        description: String,
        currency: String,
        exchangeRate: String,
        originalCurrency: String,
        originalAmount: String,
        lines: List<FvgFinancialEngine.JournalLineInput>,
        status: String = "posted"
    ) {
        val msg = if (status == "draft") "Draft journal saved (pending posting)." else "Balanced double-entry journal posted."
        runMutation(msg) { org ->
            repository.postJournalEntry(
                org, currentUserId, userEmail, referenceNo, entryDate,
                entryDate.take(7), description, currency, exchangeRate,
                originalCurrency, originalAmount, lines, sourceModule = "manual", status = status
            )
        }
    }

    fun approveAndPostDraft(entry: JournalEntryRecord) {
        runMutation("Draft ${entry.referenceNo} posted to ledger.") { org ->
            repository.approveAndPostDraftJournalEntry(org, currentUserId, userEmail, entry)
        }
    }

    fun previewAccountingCsvImport(csvText: String) {
        val codes = _uiState.value.accounts.map { it.code }.toSet()
        val parsed = FvgFinancialEngine.parseAndValidateAccountingCsv(csvText, codes)
        _uiState.update {
            it.copy(
                accountingCsvPreviewRows = parsed,
                statusMessage = if (parsed.isEmpty()) null else "Parsed ${parsed.size} accounting rows (${parsed.count { r -> r.error == null }} valid).",
                errorMessage = if (parsed.isEmpty()) "CSV requires header + at least 1 balanced row." else null
            )
        }
    }

    fun clearAccountingCsvPreview() {
        _uiState.update { it.copy(accountingCsvPreviewRows = emptyList()) }
    }

    fun commitAccountingCsvImport() {
        val rows = _uiState.value.accountingCsvPreviewRows
        val names = _uiState.value.accounts.associate { it.code to it.name }
        runMutation("Accounting CSV rows atomically posted.") { org ->
            repository.commitAccountingCsvImport(org, currentUserId, userEmail, rows, names).also { res ->
                if (res.isSuccess) _uiState.update { it.copy(accountingCsvPreviewRows = emptyList()) }
            }
        }
    }

    fun reverseJournalEntry(entry: JournalEntryRecord, reversalDate: String) {
        runMutation("Journal entry reversed cleanly.") { org ->
            repository.reverseJournalEntry(org, currentUserId, userEmail, entry, reversalDate)
        }
    }

    fun saveCounterpartyItem(
        type: String,
        partyName: String,
        invoiceNo: String,
        currency: String,
        amount: String,
        dueDate: String
    ) {
        runMutation("AR/AP item recorded.") { org ->
            repository.saveCounterpartyItem(org, currentUserId, userEmail, type, partyName, invoiceNo, currency, amount, dueDate)
        }
    }

    fun recordCounterpartyPayment(item: CounterpartyRecord, paymentAmount: String) {
        runMutation("Payment applied to ${item.invoiceNo}.") { org ->
            repository.recordCounterpartyPayment(org, currentUserId, userEmail, item, paymentAmount)
        }
    }

    // --- Budgets ---

    fun saveBudget(
        name: String,
        type: String,
        period: String,
        currency: String,
        planned: String,
        actual: String,
        explanation: String,
        approveNow: Boolean
    ) {
        runMutation("Budget saved with variance calculation.") { org ->
            repository.saveBudget(org, currentUserId, userEmail, name, type, period, currency, planned, actual, explanation, approveNow)
        }
    }

    fun updateBudgetActuals(budget: BudgetRecord, actual: String, explanation: String, status: String) {
        runMutation("Budget actuals updated.") { org ->
            repository.updateBudgetActuals(org, currentUserId, userEmail, budget, actual, explanation, status)
        }
    }

    // --- Inventory & Stocktaking ---

    fun createInventoryItem(
        sku: String,
        name: String,
        itemType: String,
        unit: String,
        warehouse: String,
        valuationMethod: String,
        openingQty: String,
        openingUnitCost: String,
        marketPrice: String,
        batchNumber: String = "",
        expiryDate: String = ""
    ) {
        runMutation("Inventory item created.") { org ->
            repository.createInventoryItem(
                org, currentUserId, userEmail, sku, name, itemType, unit,
                warehouse, valuationMethod, openingQty, openingUnitCost, marketPrice, batchNumber, expiryDate
            )
        }
    }

    fun recordInventoryMovement(
        item: InventoryItemRecord,
        movementType: String,
        qty: String,
        unitCost: String,
        warehouse: String,
        reason: String,
        referenceNo: String,
        date: String,
        postAccounting: Boolean
    ) {
        runMutation("Inventory movement ($movementType) recorded.") { org ->
            repository.recordInventoryMovement(org, currentUserId, userEmail, item, movementType, qty, unitCost, warehouse, reason, referenceNo, date, postAccounting)
        }
    }

    fun createStocktake(
        sessionName: String,
        warehouse: String,
        date: String,
        itemsInScope: List<InventoryItemRecord>,
        counts: Map<String, String>
    ) {
        runMutation("Stocktake submitted for review.") { org ->
            repository.createStocktakeSession(org, currentUserId, userEmail, sessionName, warehouse, date, itemsInScope, counts)
        }
    }

    fun processStocktakeDecision(session: StocktakeSessionRecord, decision: String) {
        val items = _uiState.value.inventoryItems
        runMutation("Stocktake marked $decision.") { org ->
            repository.processStocktakeDecision(org, currentUserId, userEmail, session, items, decision)
        }
    }

    fun submitScrapLoss(
        item: InventoryItemRecord,
        lossType: String,
        qty: String,
        reason: String,
        lossDate: String
    ) {
        runMutation("Scrap/loss request submitted for admin approval.") { org ->
            repository.submitScrapLossRequest(org, currentUserId, userEmail, item, lossType, qty, reason, lossDate)
        }
    }

    fun reviewScrapLoss(scrap: ScrapLossRecord, decision: String) {
        val item = _uiState.value.inventoryItems.firstOrNull { it.id == scrap.itemId }
        runMutation("Scrap loss request marked $decision.") { org ->
            repository.reviewScrapLossRequest(org, currentUserId, userEmail, scrap, item, decision)
        }
    }

    // --- Financial Assistant (3 Modes: General, Financial, OCR) & Invoice OCR ---

    fun setAiMode(mode: String) {
        if (mode in setOf("general", "financial", "ocr")) {
            _uiState.update { it.copy(aiMode = mode) }
        }
    }

    fun startNewAiConversation() {
        _uiState.update {
            it.copy(activeConversationId = null, chatTranscript = emptyList(), aiResponse = "", isAiOfflineFallback = false)
        }
    }

    fun selectAiConversation(conv: AiConversationRecord) {
        val pairs = conv.messages.map { m ->
            val role = m["role"]?.toString().orEmpty()
            val text = m["text"]?.toString().orEmpty()
            role to text
        }
        _uiState.update {
            it.copy(
                activeConversationId = conv.id,
                aiMode = conv.mode.ifBlank { "financial" },
                chatTranscript = pairs,
                aiResponse = pairs.lastOrNull()?.second.orEmpty(),
                isAiOfflineFallback = false
            )
        }
    }

    fun deleteAiConversation(convId: String) {
        val org = _uiState.value.activeOrg ?: return
        viewModelScope.launch {
            repository.deleteAiConversation(org, convId)
                .onSuccess {
                    if (_uiState.value.activeConversationId == convId) {
                        startNewAiConversation()
                    }
                }
        }
    }

    fun stopAiStreaming() {
        aiStreamingJob?.cancel()
        aiStreamingJob = null
        _uiState.update { it.copy(isAiStreaming = false) }
    }

    fun askAiAssistant(question: String, languageCode: String) {
        if (question.isBlank()) return
        aiStreamingJob?.cancel()
        val mode = _uiState.value.aiMode
        val context = if (mode == "general") "" else buildGroundedDataSummary()
        val org = _uiState.value.activeOrg
        aiStreamingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isAiStreaming = true, isAiOfflineFallback = false, aiResponse = "", errorMessage = null)
            }
            var finalResponse = ""
            var hadError = false
            try {
                aiRepository.streamGroundedExplanation(question, context, languageCode, mode)
                    .catch { e ->
                        if (e is CancellationException) throw e
                        hadError = true
                        val fallback = aiRepository.buildOfflineDeterministicFallback(
                            question = question,
                            systemDataContext = buildGroundedDataSummary(),
                            isArabic = languageCode == "ar",
                            errorDetail = e.localizedMessage
                        )
                        finalResponse = fallback
                        _uiState.update {
                            it.copy(
                                isAiStreaming = false,
                                isAiOfflineFallback = true,
                                aiResponse = fallback,
                                errorMessage = e.message ?: "AI assistant offline"
                            )
                        }
                    }
                    .collect { chunkText ->
                        finalResponse = chunkText
                        _uiState.update { it.copy(aiResponse = chunkText) }
                    }
            } catch (e: CancellationException) {
                throw e
            }

            val updatedTranscript = if (finalResponse.isNotBlank()) {
                _uiState.value.chatTranscript + ("User [$mode]" to question) + ("FVG [$mode]" to finalResponse)
            } else _uiState.value.chatTranscript

            _uiState.update {
                it.copy(
                    isAiStreaming = false,
                    isAiOfflineFallback = hadError,
                    chatTranscript = updatedTranscript
                )
            }
            if (org != null && finalResponse.isNotBlank()) {
                val msgMaps = updatedTranscript.map { (r, t) -> mapOf("role" to r, "text" to t) }
                val title = updatedTranscript.firstOrNull()?.second?.take(48) ?: question.take(48)
                repository.saveAiConversation(org, currentUserId, _uiState.value.activeConversationId, title, mode, msgMaps)
                    .onSuccess { convId -> _uiState.update { it.copy(activeConversationId = convId) } }
            }
        }
    }

    fun extractOcrInvoice(bitmap: Bitmap, fileName: String) {
        val org = _uiState.value.activeOrg ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrExtracting = true, errorMessage = null) }
            try {
                val res = aiRepository.extractInvoiceDocument(bitmap)
                val existingDrafts = _uiState.value.ocrDrafts
                val isDup = existingDrafts.any { d ->
                    (res.invoiceNumber.isNotBlank() && d.invoiceNumber.equals(res.invoiceNumber, ignoreCase = true)) ||
                        (res.vendorName.isNotBlank() && res.documentDate.isNotBlank() && res.totalAmount.isNotBlank() &&
                            d.vendorName.equals(res.vendorName, ignoreCase = true) &&
                            d.documentDate == res.documentDate &&
                            d.totalAmount == res.totalAmount)
                }
                val dupWarn = if (isDup) "Possible duplicate invoice detected (matching invoice #, vendor/date/total)." else ""
                val mathCheck = FvgFinancialEngine.validateOcrArithmetic(res.subtotalAmount, res.discountAmount, res.taxAmount, res.totalAmount)
                val fullNote = buildString {
                    append(res.confidenceNote)
                    if (!mathCheck.isConsistent) {
                        append(" | Math mismatch: expected ${mathCheck.expectedTotal} vs extracted ${mathCheck.extractedTotal}")
                    }
                    if (dupWarn.isNotBlank()) append(" | $dupWarn")
                }.take(190)

                repository.saveOcrDraft(org, currentUserId, userEmail, fileName, res.copy(confidenceNote = fullNote), dupWarn)
                    .onSuccess {
                        _uiState.update { it.copy(isOcrExtracting = false, statusMessage = "OCR draft saved for human review.") }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isOcrExtracting = false, errorMessage = e.message) }
                    }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isOcrExtracting = false, errorMessage = e.message ?: "OCR extraction failed") }
            }
        }
    }

    fun reviewOcrDraft(
        draft: OcrDraftRecord,
        vendorName: String,
        invoiceNumber: String,
        date: String,
        currency: String,
        subtotalAmount: String,
        discountAmount: String,
        taxAmount: String,
        amount: String,
        summary: String,
        status: String
    ) {
        runMutation("OCR draft marked $status.") { org ->
            repository.reviewOcrDraft(
                org, currentUserId, userEmail, draft, vendorName, invoiceNumber, date,
                currency, subtotalAmount, discountAmount, taxAmount, amount, summary, status
            )
        }
    }

    private fun buildGroundedDataSummary(): String {
        val st = _uiState.value
        val org = st.activeOrg
        val stmt = st.statementsReport
        val topProducts = st.products.take(12).joinToString("; ") {
            "${it.name} (${it.unit}, ${it.currency}): current=${it.currentPrice}, prev=${it.previousPrice}"
        }
        val topInflation = st.inflationRecords.take(8).joinToString("; ") {
            "${it.period} [${it.sourceType}]: monthly=${it.monthlyRatePct}%, index=${it.indexValue}"
        }
        val topFx = st.fxRates.take(6).joinToString("; ") {
            "${it.baseCurrency}/${it.quoteCurrency}=${it.rate} (${it.effectiveDate}, ${it.status})"
        }
        val topCosting = st.costSnapshots.take(8).joinToString("; ") {
            "${it.productName} (${it.period}, ${it.costingMethod}): totalCost=${it.totalCost}, unitCost=${it.unitCost}, price=${it.sellingPrice}, margin=${it.profitMarginPct}%, risk=${it.riskLevel}"
        }
        val topInv = st.inventoryItems.take(10).joinToString("; ") {
            "${it.name} (${it.valuationMethod}): qty=${it.quantityOnHand} ${it.unit}, bookValue=${it.totalBookValue}, mktPrice=${it.currentMarketPrice}"
        }
        val ratioSummary = st.financialRatios.take(10).joinToString("; ") {
            "${it.key}=${it.value?.toPlainString() ?: "MISSING (${it.missingDataWarning})"}"
        }
        return buildString {
            appendLine("Organization: ${org?.name ?: "None"} | Sector: ${org?.sectorType ?: "manufacturing"} | Base Currency: ${org?.baseCurrency ?: "SDG"} | Plan: ${org?.planTier ?: "N/A"} | Status: ${org?.subscriptionStatus ?: "N/A"}")
            appendLine("Products (${st.products.size}): $topProducts")
            appendLine("Inflation (${st.inflationRecords.size}): $topInflation")
            appendLine("FX Rates (${st.fxRates.size}): $topFx")
            appendLine("Cost Snapshots (${st.costSnapshots.size}): $topCosting")
            appendLine("BOM Formulas (${st.bomFormulas.size}) | Production Orders (${st.productionOrders.size})")
            appendLine("Inventory (${st.inventoryItems.size}), Total Book=${st.totalInventoryBookValue}, Total Replacement=${st.totalReplacementCost}: $topInv")
            if (stmt != null) {
                appendLine("Financial Statements: Revenue=${stmt.revenue}, COGS=${stmt.cogs}, GrossProfit=${stmt.grossProfit}, OpEx=${stmt.operatingExpenses}, NetProfit=${stmt.netProfit}, Cash=${stmt.cashAndBank}, Assets=${stmt.totalAssets}, Liabilities=${stmt.totalLiabilities}, Equity=${stmt.totalEquityWithNetIncome}, Balanced=${stmt.isAccountingEquationBalanced}")
                appendLine("Ratios: $ratioSummary")
            } else {
                appendLine("Financial Statements: No accounts/statements initialized yet.")
            }
        }
    }

    fun buildWorkspaceBackupJson(isArabic: Boolean = false): String {
        val st = _uiState.value
        val orgName = st.activeOrg?.name ?: if (isArabic) "مساحة عمل FVG" else "FVG Workspace"
        val curr = st.activeOrg?.baseCurrency ?: "SDG"
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val counts = mapOf(
            "products" to st.products.size,
            "priceObservations" to st.priceObservations.size,
            "inflationRecords" to st.inflationRecords.size,
            "priceBaskets" to st.priceBaskets.size,
            "fxRates" to st.fxRates.size,
            "costSnapshots" to st.costSnapshots.size,
            "standardCosts" to st.standardCosts.size,
            "bomFormulas" to st.bomFormulas.size,
            "productionOrders" to st.productionOrders.size,
            "accounts" to st.accounts.size,
            "journalEntries" to st.journalEntries.size,
            "inventoryItems" to st.inventoryItems.size,
            "stocktakes" to st.stocktakes.size,
            "scrapLosses" to st.scrapLosses.size,
            "budgets" to st.budgets.size,
            "paymentRequests" to st.paymentRequests.size
        )
        val keys = listOf("statements", "trial_balance", "ratios", "prices", "inflation", "fx", "costing", "inventory", "stocktakes", "scrap", "budgets")
        return com.example.domain.export.FvgExportService.toWorkspaceBackupJson(
            organizationName = orgName,
            baseCurrency = curr,
            generatedAtIso = nowIso,
            sections = counts,
            payloads = keys.map { buildReportPayload(it, isArabic) }
        )
    }

    fun buildReportPayload(reportType: String, isArabic: Boolean = false): ReportExportPayload {
        val st = _uiState.value
        val orgName = st.activeOrg?.name ?: if (isArabic) "مساحة عمل FVG" else "FVG Workspace"
        val curr = st.activeOrg?.baseCurrency ?: "SDG"
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        return when (reportType) {
            "prices" -> ReportExportPayload(
                title = if (isArabic) "سجل الأسعار ونسب التغير التاريخية" else "Prices & Percentage Change History",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = if (isArabic) "المعادلة: ((الجديد - القديم) / القديم) × 100 | السجلات: ${st.priceObservations.size}" else "Formula: ((New - Old) / Old) × 100 | Records: ${st.priceObservations.size}",
                headers = if (isArabic) listOf("التاريخ", "المنتج", "الفئة", "الوحدة", "العملة", "السابق", "الجديد", "التغير %") else listOf("Date", "Product", "Category", "Unit", "Currency", "Prev Price", "New Price", "% Change"),
                rows = st.priceObservations.map {
                    listOf(it.observationDate, it.productName, it.category, it.unit, it.currency, it.previousPrice, it.price, "${it.pctChange}%")
                },
                missingDataNotes = if (st.priceObservations.isEmpty()) listOf(if (isArabic) "لا توجد ملاحظات أسعار مسجلة بعد." else "No price observations recorded yet.") else emptyList(),
                isArabic = isArabic
            )
            "inflation" -> ReportExportPayload(
                title = if (isArabic) "مؤشرات التضخم والسلات المرجحة" else "Inflation Indices & Weighted Baskets",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = if (isArabic) "التضخم التراكمي Π(1 + r/100) - 1 | السلات: ${st.priceBaskets.size}" else "Compounded Inflation Π(1 + r/100) - 1 | Baskets: ${st.priceBaskets.size}",
                headers = if (isArabic) listOf("الفترة", "الفئة", "الشهري %", "السنوي %", "المؤشر", "فترة الأساس", "المصدر") else listOf("Period", "Category", "Monthly %", "Annual %", "Index", "Base Period", "Source"),
                rows = st.inflationRecords.map {
                    listOf(it.period, it.category, it.monthlyRatePct, it.annualRatePct, it.indexValue, it.basePeriod, "${it.sourceType}: ${it.sourceName}")
                },
                missingDataNotes = if (st.inflationRecords.isEmpty()) listOf(if (isArabic) "لا توجد سجلات تضخم محفوظة بعد." else "No inflation records stored yet.") else emptyList(),
                isArabic = isArabic
            )
            "fx" -> ReportExportPayload(
                title = if (isArabic) "سجل أسعار الصرف التاريخية اليدوية" else "Historical Exchange Rates Ledger",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = if (isArabic) "توثيق يدوي بدون أسعار مختلقة | السجلات: ${st.fxRates.size}" else "Zero fabricated fallback rates; all rates sourced from explicit dated manual records.",
                headers = if (isArabic) listOf("تاريخ السريان", "الزوج", "السعر", "الحالة", "النوع", "المصدر") else listOf("Effective Date", "Pair", "Rate", "Status", "Type", "Source"),
                rows = st.fxRates.map {
                    listOf(it.effectiveDate, "${it.baseCurrency}/${it.quoteCurrency}", it.rate, it.status, it.rateType, it.sourceName)
                },
                missingDataNotes = if (st.fxRates.isEmpty()) listOf(if (isArabic) "لا توجد أسعار صرف مسجلة بعد." else "No FX rates recorded yet.") else emptyList(),
                isArabic = isArabic
            )
            "costing" -> ReportExportPayload(
                title = if (isArabic) "ملخص تكاليف المنتجات والهوامش والمخاطر" else "Product Costing, Margin & Risk Summary",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = if (isArabic) "محرك FVG v${FvgFinancialEngine.MODEL_VERSION} | 8 طرق | الهامش=(السعر-التكلفة)/السعر" else "Engine v${FvgFinancialEngine.MODEL_VERSION} | 8 Methods | Margin=(Price-Cost)/Price, Markup=(Price-Cost)/Cost",
                headers = if (isArabic) listOf("الفترة", "المنتج", "الأسلوب", "التكلفة الكلية", "تكلفة الوحدة", "السعر", "صافي الربح", "الهامش %", "المخاطر") else listOf("Period", "Product", "Method", "Total Cost", "Unit Cost", "Price", "Net Profit", "Margin %", "Risk"),
                rows = st.costSnapshots.map {
                    listOf(it.period, it.productName, it.costingMethod, it.totalCost, it.unitCost, it.sellingPrice, it.netProfit, "${it.profitMarginPct}%", it.riskLevel)
                },
                missingDataNotes = if (st.costSnapshots.isEmpty()) listOf(if (isArabic) "لا توجد لقطات تكلفة محفوظة بعد." else "No cost calculation snapshots saved yet.") else emptyList(),
                isArabic = isArabic
            )
            "inventory" -> ReportExportPayload(
                title = if (isArabic) "تقرير القيمة الدفترية للمخزون مقابل الاستبدال" else "Inventory Book vs Replacement Cost Report",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = if (isArabic) "القيمة الدفترية (FIFO / المتوسط المرجح) مقابل تكلفة الاستبدال الحالية" else "Book Value (FIFO / Moving Weighted Average) vs Current Market Replacement Cost",
                headers = if (isArabic) listOf("الرمز", "الصنف", "الطريقة", "الكمية", "الوحدة", "الدفتري", "سعر السوق", "تقدير الاستبدال") else listOf("SKU", "Item", "Method", "Qty", "Unit", "Book Value", "Market Unit", "Replacement Est."),
                rows = st.inventoryItems.map { item ->
                    val qty = FvgFinancialEngine.dOrZero(item.quantityOnHand)
                    val mkt = FvgFinancialEngine.dOrZero(item.currentMarketPrice)
                    val rep = if (mkt > BigDecimal.ZERO) FvgFinancialEngine.money(qty.multiply(mkt)).toPlainString() else if (isArabic) "غير متاح" else "N/A"
                    listOf(item.sku, item.name, item.valuationMethod, item.quantityOnHand, item.unit, item.totalBookValue, item.currentMarketPrice, rep)
                },
                missingDataNotes = if (st.inventoryItems.isEmpty()) listOf(if (isArabic) "لا توجد أصناف مخزون مسجلة بعد." else "No inventory items registered yet.") else emptyList(),
                isArabic = isArabic
            )
            "stocktakes" -> ReportExportPayload(
                title = if (isArabic) "تقرير فروقات الجرد الفعلي للمخزون" else "Physical Stocktake Variance Report",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = if (isArabic) "مسار المراجعة: مسودة -> قيد المراجعة -> معتمد ومرحّل" else "Draft -> Review -> Approved & Posted Variance Workflow",
                headers = if (isArabic) listOf("التاريخ", "الجلسة", "المستودع", "الحالة", "قيمة الفروقات") else listOf("Date", "Session", "Warehouse", "Status", "Discrepancy Value"),
                rows = st.stocktakes.map {
                    listOf(it.sessionDate, it.sessionName, it.warehouse, it.status, it.totalDiscrepancyValue)
                },
                missingDataNotes = if (st.stocktakes.isEmpty()) listOf(if (isArabic) "لا توجد جلسات جرد مسجلة بعد." else "No stocktake sessions recorded yet.") else emptyList(),
                isArabic = isArabic
            )
            "scrap" -> ReportExportPayload(
                title = if (isArabic) "تقرير الهدر والتلف ومنتهي الصلاحية" else "Inventory Scrap, Damaged & Expired Losses",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = if (isArabic) "شطب مخزني معتمد إدارياً مرتبط بحساب 6210 خسائر الهدر والتلف" else "Admin-approved inventory write-off linked to 6210 Inventory Loss & Scrap.",
                headers = if (isArabic) listOf("التاريخ", "الصنف", "النوع", "الكمية", "الخسارة", "الحالة", "السبب") else listOf("Date", "Item", "Type", "Qty", "Total Loss", "Status", "Reason"),
                rows = st.scrapLosses.map {
                    listOf(it.lossDate, it.itemName, it.lossType, it.quantity, it.totalLossCost, it.status, it.reason)
                },
                missingDataNotes = if (st.scrapLosses.isEmpty()) listOf(if (isArabic) "لا توجد طلبات هدر مسجلة بعد." else "No scrap/loss requests logged yet.") else emptyList(),
                isArabic = isArabic
            )
            "budgets" -> ReportExportPayload(
                title = if (isArabic) "تقرير الموازنات وانحراف المخطط عن الفعلي" else "Budgets & Planned-vs-Actual Variance",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = if (isArabic) "الانحراف = الفعلي - المخطط | النسبة = (الانحراف / المخطط) × 100" else "Variance = Actual - Planned | Percentage = (Variance / Planned) × 100",
                headers = if (isArabic) listOf("الفترة", "الموازنة", "النوع", "المخطط", "الفعلي", "الانحراف", "النسبة %", "الحالة") else listOf("Period", "Budget", "Type", "Planned", "Actual", "Variance", "Var %", "Status"),
                rows = st.budgets.map {
                    listOf(it.period, it.name, it.budgetType, it.plannedAmount, it.actualAmount, it.variance, "${it.variancePct}%", it.status)
                },
                missingDataNotes = if (st.budgets.isEmpty()) listOf(if (isArabic) "لا توجد موازنات محفوظة بعد." else "No budgets saved yet.") else emptyList(),
                isArabic = isArabic
            )
            "trial_balance" -> {
                val stmt = st.statementsReport
                ReportExportPayload(
                    title = if (isArabic) "تقرير ميزان المراجعة" else "Trial Balance Report",
                    organizationName = orgName,
                    currency = curr,
                    generatedDate = today,
                    methodNotes = if (isArabic) "المدين: ${stmt?.totalTrialDebits ?: "0.00"} = الدائن: ${stmt?.totalTrialCredits ?: "0.00"}" else "Debits: ${stmt?.totalTrialDebits ?: "0.00"} = Credits: ${stmt?.totalTrialCredits ?: "0.00"}",
                    headers = if (isArabic) listOf("الرمز", "اسم الحساب", "النوع", "رصيد مدين", "رصيد دائن") else listOf("Code", "Account Name", "Type", "Debit Balance", "Credit Balance"),
                    rows = stmt?.trialBalance?.map {
                        listOf(it.code, it.name, it.accountType, it.debitBalance.toPlainString(), it.creditBalance.toPlainString())
                    } ?: emptyList(),
                    missingDataNotes = if (stmt == null) listOf(if (isArabic) "لم يتم تهيئة دليل الحسابات بعد." else "Chart of accounts not initialized.") else emptyList(),
                    isArabic = isArabic
                )
            }
            "ledger" -> {
                val posted = st.journalEntries.filter { it.status == "posted" || it.status == "reversed" }
                ReportExportPayload(
                    title = if (isArabic) "ملخص حركات دفتر الأستاذ العام" else "General Ledger Movements Summary",
                    organizationName = orgName,
                    currency = curr,
                    generatedDate = today,
                    methodNotes = if (isArabic) "القيود المزدوجة المرحّلة والمعكوسة (${posted.size} قيد)" else "Chronological posted & reversed double-entry ledger lines (${posted.size} entries)",
                    headers = if (isArabic) listOf("التاريخ", "المرجع", "الحالة", "الوصف", "مدين", "دائن") else listOf("Date", "Ref No", "Status", "Description", "Debit", "Credit"),
                    rows = posted.map {
                        listOf(it.entryDate, it.referenceNo, it.status, it.description, it.totalDebit, it.totalCredit)
                    },
                    missingDataNotes = if (posted.isEmpty()) listOf(if (isArabic) "لا توجد قيود مرحّلة بعد." else "No posted journal entries yet.") else emptyList(),
                    isArabic = isArabic
                )
            }
            "ratios" -> ReportExportPayload(
                title = if (isArabic) "تقرير تحليل الـ 18 نسبة مالية" else "Financial Ratios Analysis Report",
                organizationName = orgName,
                currency = curr,
                generatedDate = today,
                methodNotes = if (isArabic) "18 نسبة معيارية للسيولة والربحية والكفاءة والرفع المالي مع فحص المقام" else "18 standardized liquidity, profitability, efficiency & leverage ratios with denominator checks.",
                headers = if (isArabic) listOf("الفئة", "النسبة", "القيمة", "المعادلة", "ملاحظة") else listOf("Category", "Ratio Key", "Value", "Formula", "Note / Warning"),
                rows = st.financialRatios.map {
                    listOf(it.category, it.key, it.value?.let { v -> "${v.toPlainString()} ${it.unitSuffix}" } ?: if (isArabic) "غير متاح" else "N/A", it.formula, it.missingDataWarning ?: it.explanation)
                },
                missingDataNotes = st.financialRatios.mapNotNull { r -> r.missingDataWarning?.let { "${r.key}: $it" } },
                isArabic = isArabic
            )
            else -> {
                val stmt = st.statementsReport
                val rows = if (stmt != null) {
                    listOf(
                        listOf(if (isArabic) "قائمة الدخل" else "Income Statement", if (isArabic) "الإيرادات التشغيلية" else "Operating Revenue", stmt.revenue.toPlainString(), curr),
                        listOf(if (isArabic) "قائمة الدخل" else "Income Statement", if (isArabic) "تكلفة المبيعات" else "Cost of Goods Sold", stmt.cogs.toPlainString(), curr),
                        listOf(if (isArabic) "قائمة الدخل" else "Income Statement", if (isArabic) "مجمل الربح" else "Gross Profit", stmt.grossProfit.toPlainString(), curr),
                        listOf(if (isArabic) "قائمة الدخل" else "Income Statement", if (isArabic) "المصروفات التشغيلية" else "Operating Expenses", stmt.operatingExpenses.toPlainString(), curr),
                        listOf(if (isArabic) "قائمة الدخل" else "Income Statement", if (isArabic) "صافي الربح" else "Net Profit", stmt.netProfit.toPlainString(), curr),
                        listOf(if (isArabic) "الميزانية" else "Balance Sheet", if (isArabic) "النقدية والبنك" else "Cash & Bank", stmt.cashAndBank.toPlainString(), curr),
                        listOf(if (isArabic) "الميزانية" else "Balance Sheet", if (isArabic) "الذمم المدينة" else "Accounts Receivable", stmt.receivables.toPlainString(), curr),
                        listOf(if (isArabic) "الميزانية" else "Balance Sheet", if (isArabic) "المخزون (دفتري)" else "Inventory Book Value", stmt.inventoryBookValue.toPlainString(), curr),
                        listOf(if (isArabic) "الميزانية" else "Balance Sheet", if (isArabic) "إجمالي الأصول" else "Total Assets", stmt.totalAssets.toPlainString(), curr),
                        listOf(if (isArabic) "الميزانية" else "Balance Sheet", if (isArabic) "إجمالي الالتزامات" else "Total Liabilities", stmt.totalLiabilities.toPlainString(), curr),
                        listOf(if (isArabic) "الميزانية" else "Balance Sheet", if (isArabic) "إجمالي حقوق الملكية" else "Total Equity (incl. Net Income)", stmt.totalEquityWithNetIncome.toPlainString(), curr),
                        listOf(if (isArabic) "التدفقات" else "Cash Flow", if (isArabic) "التدفق النقدي التشغيلي" else "Operating Cash Flow", stmt.operatingCashFlow.toPlainString(), curr),
                        listOf(if (isArabic) "التدفقات" else "Cash Flow", if (isArabic) "رصيد النقدية الختامي" else "Closing Cash", stmt.closingCash.toPlainString(), curr)
                    )
                } else emptyList()
                val missing = buildList {
                    if (stmt == null) add(if (isArabic) "لم يتم تهيئة دليل الحسابات بعد." else "Chart of accounts not initialized.")
                    st.financialRatios.filter { it.missingDataWarning != null }.take(5).forEach {
                        add("${it.key}: ${it.missingDataWarning}")
                    }
                }
                ReportExportPayload(
                    title = if (isArabic) "تقرير القوائم والمركز المالي" else "Financial Statements & Position Report",
                    organizationName = orgName,
                    currency = curr,
                    generatedDate = today,
                    methodNotes = if (isArabic) "دفتر استحقاق مزدوج | توازن المعادلة: ${stmt?.isAccountingEquationBalanced ?: false}" else "Double-Entry Accrual Ledger | Equation Balanced: ${stmt?.isAccountingEquationBalanced ?: false}",
                    headers = if (isArabic) listOf("القائمة", "البند", "المبلغ", "العملة") else listOf("Statement", "Line Item", "Amount", "Currency"),
                    rows = rows,
                    missingDataNotes = missing,
                    isArabic = isArabic
                )
            }
        }
    }

    class Factory(
        private val repository: FvgRepository,
        private val aiRepository: FvgAiRepository,
        private val currentUserId: String,
        private val userEmail: String,
        private val userDisplayName: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FvgViewModel(repository, aiRepository, currentUserId, userEmail, userDisplayName) as T
        }
    }
}
