package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppAnnouncementRecord
import com.example.data.model.PlatformPlanRecord
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.ui.viewmodel.FvgViewModel

@Composable
fun OwnerAdminScreen(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val s = fvgStrings(isArabic)

    if (!state.isPlatformOwner) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            FvgCard {
                Text(
                    text = if (isArabic) "هذه اللوحة محجوزة لمالكة المنصة المعتمدة فقط." else "Restricted to verified Platform Owner account.",
                    style = MaterialTheme.typography.titleMedium,
                    color = FvgNegative
                )
                FvgPrimaryButton(text = s.backHome, onClick = onBack)
            }
        }
        return
    }

    var adminTab by remember { mutableIntStateOf(0) }

    // Plan Editor state
    var planCode by remember { mutableStateOf("professional") }
    var planNameAr by remember { mutableStateOf("الباقة الاحترافية") }
    var planNameEn by remember { mutableStateOf("Professional") }
    var planPriceSdg by remember { mutableStateOf("29000") }
    var planPriceUsd by remember { mutableStateOf("29.00") }
    var planDurationDays by remember { mutableStateOf("30") }

    // Announcement publisher state
    var annTitleAr by remember { mutableStateOf("") }
    var annTitleEn by remember { mutableStateOf("") }
    var annBodyAr by remember { mutableStateOf("") }
    var annBodyEn by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().testTag("owner_admin_screen")) {
        Surface(
            color = FvgDeepGreen,
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("owner_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.backHome, tint = FvgWarmGold)
                    }
                    Column {
                        Text(s.ownerAdminTitle, style = MaterialTheme.typography.titleLarge, color = FvgWarmIvory, fontWeight = FontWeight.Bold)
                        Text(viewModel.userEmail, style = MaterialTheme.typography.labelSmall, color = FvgWarmGold)
                    }
                }
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = FvgWarmGold)
            }
        }

        val tabs = if (isArabic) listOf("المؤشرات", "طلبات الدفع", "المنظمات والباقات", "الإعلانات والدعم")
        else listOf("Overview", "Payments", "Orgs & Plans", "Notices & Tickets")
        FvgSubTabRow(tabs = tabs, selectedIndex = adminTab, onSelect = { adminTab = it }, testTagPrefix = "owner_admin")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (adminTab) {
                0 -> {
                    item {
                        FvgCard {
                            Text(if (isArabic) "إحصائيات قاعدة البيانات الحية" else "Live Firestore Platform KPIs", style = MaterialTheme.typography.titleMedium)
                            val activeOrgs = state.ownerAllOrgs.count { it.subscriptionStatus == "active" }
                            val trialOrgs = state.ownerAllOrgs.count { it.subscriptionStatus == "trial" }
                            val expiredOrgs = state.ownerAllOrgs.count { it.subscriptionStatus == "expired" }
                            val pendingPays = state.ownerAllPaymentRequests.count { it.status == "pending_verification" }
                            val approvedPays = state.ownerAllPaymentRequests.count { it.status == "approved" }
                            Text(if (isArabic) "إجمالي المستخدمين المسجلين: ${state.ownerAllUsers.size}" else "Registered Users: ${state.ownerAllUsers.size}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(if (isArabic) "المنظمات: ${state.ownerAllOrgs.size} (نشط: $activeOrgs • تجربة: $trialOrgs • منتهي: $expiredOrgs)" else "Organizations: ${state.ownerAllOrgs.size} (Active: $activeOrgs • Trial: $trialOrgs • Expired: $expiredOrgs)", style = MaterialTheme.typography.bodyMedium)
                            Text(if (isArabic) "طلبات الدفع: ${state.ownerAllPaymentRequests.size} (قيد المراجعة: $pendingPays • معتمد: $approvedPays)" else "Payments: ${state.ownerAllPaymentRequests.size} (Pending: $pendingPays • Approved: $approvedPays)", style = MaterialTheme.typography.bodyMedium)
                            Text(if (isArabic) "تذاكر الدعم: ${state.ownerSupportTickets.size}" else "Support Tickets: ${state.ownerSupportTickets.size}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    item {
                        FvgCard {
                            Text(if (isArabic) "حالة التكاملات الخارجية (شفافية كاملة)" else "External Telemetry Status (Unfabricated)", style = MaterialTheme.typography.titleMedium)
                            Text(if (isArabic) "• تنزيلات متجر Google Play / App Store: غير متاح" else "• Store App Downloads: Not Available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (isArabic) "• زيارات الموقع العامة (Analytics): غير متاح" else "• Web Traffic / Visits: Not Available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (isArabic) "• إرسال تلقائي عبر WhatsApp / بريد SMTP: غير مهيأ (تظهر الطلبات هنا فوراً)" else "• Automatic WhatsApp / SMTP Push: Not Configured (Requests stream live here)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                1 -> {
                    if (state.ownerAllPaymentRequests.isEmpty()) {
                        item { FvgEmptyStateCard(if (isArabic) "لا توجد طلبات دفع مرسلة بعد." else "No payment verification requests yet.") }
                    } else {
                        items(state.ownerAllPaymentRequests, key = { "${it.orgId}_${it.id}" }) { req ->
                            var decisionNotes by remember(req.id) { mutableStateOf(req.reviewNotes) }
                            val orgNameLabel = state.ownerAllOrgs.firstOrNull { it.id == req.orgId }?.name ?: req.orgId
                            val submitterEmail = state.ownerAllUsers.firstOrNull { it.userId == req.createdBy }?.email ?: req.createdBy
                            FvgCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("$orgNameLabel • ${req.planTier}", style = MaterialTheme.typography.titleMedium)
                                    FvgRoleBadge(req.status)
                                }
                                Text(
                                    text = if (isArabic)
                                        "المبلغ: ${req.amount} ${req.currency} | الوسيلة: ${req.paymentMethod}\nالمرجع: ${req.referenceNo} | آخر 4 أرقام: ${req.last4Digits.ifBlank { "غير محدد" }}\nالمرسل: ${req.senderName} ($submitterEmail) | التاريخ: ${req.transferDate}"
                                    else
                                        "Amount: ${req.amount} ${req.currency} | Method: ${req.paymentMethod}\nRef: ${req.referenceNo} | Last 4 Digits: ${req.last4Digits.ifBlank { "N/A" }}\nSender: ${req.senderName} ($submitterEmail) | Date: ${req.transferDate}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (req.attachmentNote.isNotBlank() || req.notes.isNotBlank()) {
                                    Text("${req.attachmentNote} ${req.notes}".trim(), style = MaterialTheme.typography.labelSmall)
                                }
                                if (req.status == "pending_verification") {
                                    FvgField(decisionNotes, { decisionNotes = it }, if (isArabic) "ملاحظة المراجعة / سبب الرفض" else "Verification / Rejection Note")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FvgPrimaryButton(
                                            text = if (isArabic) "موافقة وتفعيل" else "Approve & Activate",
                                            icon = Icons.Filled.CheckCircle,
                                            enabled = !state.isBusy,
                                            onClick = {
                                                viewModel.reviewPaymentRequestAsOwner(
                                                    req,
                                                    "approved",
                                                    decisionNotes.ifBlank { "Verified by platform owner (${req.referenceNo})" }
                                                )
                                            },
                                            modifier = Modifier.weight(1.2f),
                                            testTag = "owner_approve_payment_button"
                                        )
                                        OutlinedButton(
                                            enabled = !state.isBusy,
                                            onClick = {
                                                viewModel.reviewPaymentRequestAsOwner(
                                                    req,
                                                    "rejected",
                                                    decisionNotes.ifBlank { "Rejected: transaction could not be verified" }
                                                )
                                            },
                                            modifier = Modifier.weight(0.8f).heightIn(min = 48.dp)
                                        ) {
                                            Text(if (isArabic) "رفض" else "Reject", color = FvgNegative)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    item {
                        FvgCard {
                            Text(if (isArabic) "إدارة الباقات والأسعار (Platform Plans)" else "Manage Platform Plans & Pricing", style = MaterialTheme.typography.titleMedium)
                            FvgChipSelector(listOf("starter", "professional", "enterprise"), planCode, { planCode = it })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FvgField(planNameAr, { planNameAr = it }, if (isArabic) "الاسم بالعربية" else "Arabic Name", modifier = Modifier.weight(1f))
                                FvgField(planNameEn, { planNameEn = it }, if (isArabic) "الاسم بالإنجليزية" else "English Name", modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FvgField(planPriceSdg, { planPriceSdg = it }, "SDG", numeric = true, modifier = Modifier.weight(1f))
                                FvgField(planPriceUsd, { planPriceUsd = it }, "USD", numeric = true, modifier = Modifier.weight(1f))
                                FvgField(planDurationDays, { planDurationDays = it }, if (isArabic) "الأيام" else "Days", numeric = true, modifier = Modifier.weight(0.7f))
                            }
                            FvgGoldButton(
                                text = if (isArabic) "حفظ / تحديث الباقة" else "Save Platform Plan",
                                enabled = !state.isBusy && planNameAr.isNotBlank(),
                                onClick = {
                                    viewModel.savePlatformPlanAsOwner(
                                        PlatformPlanRecord(
                                            id = planCode,
                                            key = planCode,
                                            nameAr = planNameAr,
                                            nameEn = planNameEn,
                                            priceSdg = planPriceSdg,
                                            priceUsd = planPriceUsd,
                                            billingCycle = "${planDurationDays}d",
                                            active = true
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    items(state.ownerAllOrgs, key = { it.id }) { org ->
                        var overridePlan by remember(org.id) { mutableStateOf(org.planTier) }
                        var overrideStatus by remember(org.id) { mutableStateOf(org.subscriptionStatus) }
                        var overrideReason by remember(org.id) { mutableStateOf("") }
                        FvgCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(org.name, style = MaterialTheme.typography.titleMedium)
                                FvgRoleBadge("${org.planTier} • ${org.subscriptionStatus}")
                            }
                            Text("ID: ${org.id} | Sector: ${org.sectorType} | Currency: ${org.baseCurrency}", style = MaterialTheme.typography.labelSmall)
                            FvgChipSelector(listOf("trial", "starter", "professional", "enterprise"), overridePlan, { overridePlan = it })
                            FvgChipSelector(listOf("trial", "active", "expired", "cancelled"), overrideStatus, { overrideStatus = it })
                            FvgField(overrideReason, { overrideReason = it }, if (isArabic) "سبب التعديل الإداري" else "Admin Override Reason")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.ownerUpdateOrgSubscription(
                                            org = org,
                                            planTier = overridePlan,
                                            subscriptionStatus = overrideStatus,
                                            newTrialEndsAtMillis = System.currentTimeMillis() + 7L * 86400_000L,
                                            reason = overrideReason.ifBlank { "Owner trial extension (+7 days)" }
                                        )
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                ) { Text(if (isArabic) "منح +7 أيام" else "+7d Trial") }
                                FvgPrimaryButton(
                                    text = if (isArabic) "تحديث الاشتراك" else "Apply Override",
                                    enabled = !state.isBusy,
                                    onClick = {
                                        viewModel.ownerUpdateOrgSubscription(
                                            org = org,
                                            planTier = overridePlan,
                                            subscriptionStatus = overrideStatus,
                                            newTrialEndsAtMillis = null,
                                            reason = overrideReason.ifBlank { "Owner status adjustment" }
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                else -> {
                    item {
                        FvgCard {
                            Text(if (isArabic) "نشر إعلان داخل التطبيق" else "Publish App Announcement", style = MaterialTheme.typography.titleMedium)
                            FvgField(annTitleAr, { annTitleAr = it }, if (isArabic) "عنوان الإعلان (عربي)" else "Arabic Title")
                            FvgField(annTitleEn, { annTitleEn = it }, if (isArabic) "عنوان الإعلان (إنجليزي)" else "English Title")
                            FvgField(annBodyAr, { annBodyAr = it }, if (isArabic) "النص (عربي)" else "Arabic Body", singleLine = false)
                            FvgField(annBodyEn, { annBodyEn = it }, if (isArabic) "النص (إنجليزي)" else "English Body", singleLine = false)
                            FvgGoldButton(
                                text = if (isArabic) "نشر الإعلان الآن" else "Publish Announcement",
                                icon = Icons.Filled.Campaign,
                                enabled = !state.isBusy && (annTitleAr.isNotBlank() || annTitleEn.isNotBlank()),
                                onClick = {
                                    viewModel.saveAnnouncementAsOwner(
                                        AppAnnouncementRecord(
                                            titleAr = annTitleAr,
                                            titleEn = annTitleEn,
                                            bodyAr = annBodyAr,
                                            bodyEn = annBodyEn,
                                            active = true
                                        )
                                    )
                                    annTitleAr = ""; annTitleEn = ""; annBodyAr = ""; annBodyEn = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    items(state.ownerSupportTickets, key = { it.id }) { tk ->
                        var replyText by remember(tk.id) { mutableStateOf(tk.ownerReply) }
                        FvgCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${tk.senderName} (${tk.senderEmail})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                FvgRoleBadge(tk.status)
                            }
                            Text(tk.message, style = MaterialTheme.typography.bodyMedium)
                            FvgField(replyText, { replyText = it }, if (isArabic) "رد المالكة" else "Owner Reply", singleLine = false)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FvgPrimaryButton(
                                    text = if (isArabic) "إرسال الرد وإغلاق" else "Reply & Resolve",
                                    enabled = replyText.isNotBlank() && !state.isBusy,
                                    onClick = { viewModel.replySupportTicketAsOwner(tk.id, "resolved", replyText) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
