package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.engine.FvgFinancialEngine
import com.example.domain.export.FvgExportService
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.ui.viewmodel.FvgViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun rotateBitmap90(bitmap: Bitmap): Bitmap {
    val matrix = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun localizePayStatus(status: String, isArabic: Boolean): String = if (!isArabic) status else when (status.lowercase()) {
    "pending_verification" -> "قيد التحقق"
    "approved" -> "مقبول"
    "rejected" -> "مرفوض"
    "active" -> "نشط"
    "trial" -> "تجربة"
    "expired" -> "منتهي"
    "cancelled" -> "ملغى"
    "pending_review" -> "قيد المراجعة"
    "verified" -> "معتمد"
    else -> status
}

@Composable
fun ReportsAiSettingsScreen(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenCalculator: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenOwnerAdmin: () -> Unit = {},
    onSignOutClick: () -> Unit
) {
    val s = fvgStrings(isArabic)
    var subTab by remember { mutableIntStateOf(1) } // Default to Financial Assistant / OCR when entering Assistant tab

    Column(modifier = Modifier.fillMaxSize().testTag("reports_ai_settings_screen")) {
        FvgTopHeaderBanner(
            title = if (isArabic) "المساعد المالي والتقارير والاشتراك" else "Financial Assistant, Reports & Billing",
            subtitle = s.brandTaglineEn,
            icon = Icons.Filled.AutoAwesome,
            isArabic = isArabic,
            isDark = isDark,
            onToggleLanguage = onToggleLanguage,
            onToggleTheme = onToggleTheme,
            orgName = state.activeOrg?.name,
            roleBadge = state.currentUserRole,
            onCalculatorClick = onOpenCalculator,
            onHelpClick = onOpenHelp,
            onProfileClick = onOpenProfile
        )
        FvgSubTabRow(
            tabs = listOf(s.tabReportsExports, s.tabAssistantOcr, s.tabOrgSettings),
            selectedIndex = subTab,
            onSelect = { subTab = it },
            testTagPrefix = "reports"
        )

        when (subTab) {
            0 -> ReportsExportTab(state, viewModel, isArabic)
            1 -> AiAssistantAndOcrTab(state, viewModel, isArabic)
            2 -> OrgRbacBillingSettingsTab(
                state = state,
                viewModel = viewModel,
                isArabic = isArabic,
                onOpenProfile = onOpenProfile,
                onOpenHelp = onOpenHelp,
                onOpenOwnerAdmin = onOpenOwnerAdmin,
                onSignOutClick = onSignOutClick
            )
        }
    }
}

@Composable
private fun ReportsExportTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean
) {
    val context = LocalContext.current
    val s = fvgStrings(isArabic)
    var selectedReport by remember { mutableStateOf("statements") }
    var exportNotice by remember { mutableStateOf<String?>(null) }

    val payload = remember(selectedReport, state, isArabic) {
        viewModel.buildReportPayload(selectedReport, isArabic)
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FvgExportService.writePdf(out, payload)
                }
                exportNotice = if (isArabic) "تم تصدير ملف PDF بنجاح (${payload.rows.size} صف)." else "PDF exported (${payload.rows.size} rows)."
            }.onFailure { exportNotice = if (isArabic) "فشل تصدير PDF: ${it.message}" else "PDF export failed: ${it.message}" }
        }
    }
    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FvgExportService.writeXlsx(out, payload)
                }
                exportNotice = if (isArabic) "تم تصدير ملف XLSX بنجاح (${payload.rows.size} صف)." else "XLSX exported (${payload.rows.size} rows)."
            }.onFailure { exportNotice = if (isArabic) "فشل تصدير XLSX: ${it.message}" else "XLSX export failed: ${it.message}" }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                FvgExportService.writeTextToUri(context, uri, FvgExportService.toCsvString(payload))
                exportNotice = if (isArabic) "تم تصدير ملف CSV بنجاح." else "CSV exported."
            }.onFailure { exportNotice = if (isArabic) "فشل تصدير CSV: ${it.message}" else "CSV export failed: ${it.message}" }
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                FvgExportService.writeTextToUri(context, uri, FvgExportService.toJsonString(payload))
                exportNotice = if (isArabic) "تم تصدير ملف JSON بنجاح." else "JSON exported."
            }.onFailure { exportNotice = if (isArabic) "فشل تصدير JSON: ${it.message}" else "JSON export failed: ${it.message}" }
        }
    }
    val backupJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                FvgExportService.writeTextToUri(context, uri, viewModel.buildWorkspaceBackupJson(isArabic))
                exportNotice = if (isArabic) "تم تصدير النسخة الاحتياطية الكاملة (JSON) بنجاح." else "Full Workspace Backup (JSON) exported cleanly."
            }.onFailure { exportNotice = if (isArabic) "فشل النسخ الاحتياطي: ${it.message}" else "Backup failed: ${it.message}" }
        }
    }

    val stamp = remember { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) }
    val stmt = state.statementsReport

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (stmt != null) {
            item {
                FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = if (isArabic) "ملخص الأداء السنوي / التراكمي" else "Financial Performance Summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FvgBarComparisonCanvas(
                        bars = listOf(
                            (if (isArabic) "إيرادات" else "Rev") to stmt.revenue.toFloat(),
                            (if (isArabic) "تكلفة" else "COGS") to stmt.cogs.toFloat(),
                            (if (isArabic) "مجمل" else "Gross") to stmt.grossProfit.toFloat(),
                            (if (isArabic) "تشغيل" else "OpEx") to stmt.operatingExpenses.toFloat(),
                            (if (isArabic) "صافي" else "Net") to stmt.netProfit.toFloat()
                        )
                    )
                }
            }
        }

        item {
            FvgCard {
                Text(
                    text = if (isArabic) "التقارير المالية والتصديرات (12 تقريراً موثقاً)" else "Financial Reports & Verified Exports (12 Reports)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FvgChipSelector(
                    options = listOf(
                        "statements", "trial_balance", "ledger", "ratios",
                        "prices", "inflation", "fx", "costing",
                        "inventory", "stocktakes", "scrap", "budgets"
                    ),
                    selected = selectedReport,
                    onSelect = { selectedReport = it },
                    labelResolver = {
                        when (it) {
                            "statements" -> if (isArabic) "القوائم" else "Statements"
                            "trial_balance" -> if (isArabic) "ميزان المراجعة" else "Trial Balance"
                            "ledger" -> if (isArabic) "الأستاذ" else "Ledger"
                            "ratios" -> if (isArabic) "النسب" else "Ratios"
                            "prices" -> if (isArabic) "الأسعار" else "Prices"
                            "inflation" -> if (isArabic) "التضخم" else "Inflation"
                            "fx" -> if (isArabic) "الصرف" else "FX Rates"
                            "costing" -> if (isArabic) "التكاليف" else "Costing"
                            "inventory" -> if (isArabic) "المخزون" else "Inventory"
                            "stocktakes" -> if (isArabic) "الجرد" else "Stocktakes"
                            "scrap" -> if (isArabic) "الهدر" else "Scrap"
                            else -> if (isArabic) "الموازنات" else "Budgets"
                        }
                    }
                )
                Text(payload.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text("${payload.organizationName} • ${payload.currency} • ${payload.generatedDate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(payload.methodNotes, style = MaterialTheme.typography.bodyMedium)

                if (payload.missingDataNotes.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = payload.missingDataNotes.joinToString("\n"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (exportNotice != null) {
                    Text(exportNotice!!, style = MaterialTheme.typography.bodyMedium, color = FvgPositive, fontWeight = FontWeight.SemiBold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgPrimaryButton(
                        text = s.exportPdf,
                        icon = Icons.Filled.PictureAsPdf,
                        onClick = { pdfLauncher.launch("fvg_${selectedReport}_$stamp.pdf") },
                        modifier = Modifier.weight(1f),
                        testTag = "export_pdf_button"
                    )
                    FvgGoldButton(
                        text = s.exportXlsx,
                        icon = Icons.Filled.TableChart,
                        onClick = { xlsxLauncher.launch("fvg_${selectedReport}_$stamp.xlsx") },
                        modifier = Modifier.weight(1f),
                        testTag = "export_xlsx_button"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { csvLauncher.launch("fvg_${selectedReport}_$stamp.csv") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(s.exportCsv) }
                    OutlinedButton(
                        onClick = { jsonLauncher.launch("fvg_${selectedReport}_$stamp.json") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(s.exportJson) }
                }
                OutlinedButton(
                    onClick = { backupJsonLauncher.launch("fvg_workspace_backup_$stamp.json") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isArabic) "تصدير نسخة احتياطية شاملة JSON" else "Export Full Workspace Backup (JSON)")
                }
            }
        }

        items(payload.rows.take(25)) { row ->
            FvgCard {
                Text(row.joinToString("  |  "), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AiAssistantAndOcrTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var question by remember { mutableStateOf("") }
    var transcriptNotice by remember { mutableStateOf<String?>(null) }
    var pendingOcrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingOcrLabel by remember { mutableStateOf("camera_invoice.jpg") }
    var cameraNotice by remember { mutableStateOf<String?>(null) }
    val stamp = remember { SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    val transcriptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            runCatching {
                val txt = FvgExportService.toChatTranscriptText(
                    organizationName = state.activeOrg?.name ?: "FVG Workspace",
                    aiMode = state.aiMode,
                    generatedDate = today,
                    messages = state.chatTranscript
                )
                FvgExportService.writeTextToUri(context, uri, txt)
                transcriptNotice = if (isArabic) "تم تصدير سجل محادثة المساعد المالي بنجاح." else "Assistant transcript exported (.txt)."
            }.onFailure { transcriptNotice = if (isArabic) "فشل التصدير: ${it.message}" else "Export failed: ${it.message}" }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            pendingOcrBitmap = bmp
            pendingOcrLabel = "camera_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            cameraNotice = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            cameraNotice = if (isArabic)
                "تم رفض إذن الكاميرا — يمكنك فتح اختيار صورة بديل أدناه."
            else
                "Camera permission denied — use the gallery fallback button below."
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                @Suppress("DEPRECATION")
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                pendingOcrBitmap = bitmap
                pendingOcrLabel = uri.lastPathSegment ?: "invoice_doc.jpg"
                cameraNotice = null
            }.onFailure {
                cameraNotice = if (isArabic) "تعذّر فتح الصورة: ${it.message}" else "Unable to decode image: ${it.message}"
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isArabic) "المساعد المالي الذكي (Firebase AI Logic)" else "Financial Assistant (Firebase AI Logic)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { viewModel.startNewAiConversation() },
                        modifier = Modifier.heightIn(min = 42.dp).testTag("new_conversation_button")
                    ) {
                        Icon(Icons.Filled.AddComment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isArabic) "محادثة جديدة" else "New Chat")
                    }
                }

                if (state.aiConversations.isNotEmpty()) {
                    Text(if (isArabic) "المحادثات المحفوظة:" else "Saved Conversations:", style = MaterialTheme.typography.labelLarge)
                    FvgChipSelector(
                        options = state.aiConversations.map { it.id },
                        selected = state.activeConversationId ?: "",
                        onSelect = { id ->
                            state.aiConversations.firstOrNull { it.id == id }?.let { viewModel.selectAiConversation(it) }
                        },
                        labelResolver = { id ->
                            state.aiConversations.firstOrNull { it.id == id }?.title?.ifBlank { id.take(8) } ?: id
                        }
                    )
                    if (state.activeConversationId != null) {
                        TextButton(onClick = { viewModel.deleteAiConversation(state.activeConversationId) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "حذف المحادثة الحالية" else "Delete Current Conversation", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Text(
                    text = if (isArabic) "وضع المساعد المالي:" else "Select Assistant Mode:",
                    style = MaterialTheme.typography.labelLarge
                )
                Box(modifier = Modifier.testTag("ai_mode_selector")) {
                    FvgChipSelector(
                        options = listOf("general", "financial", "ocr"),
                        selected = state.aiMode,
                        onSelect = { viewModel.setAiMode(it) },
                        labelResolver = { mode ->
                            when (mode) {
                                "general" -> if (isArabic) "إرشاد عام ومنهجي" else "General Methodology"
                                "financial" -> if (isArabic) "تحليل مالي حي" else "Financial Analyst"
                                else -> if (isArabic) "مساعدة الفواتير (OCR)" else "Invoice & Docs Helper"
                            }
                        }
                    )
                }
                Text(
                    text = if (isArabic)
                        "يجيب المساعد من سجلات منظمتك الموثقة ومعادلات الدليل المالي عبر Firebase AI Logic + App Check دون اختلاق أرقام."
                    else
                        "Answers strictly from verified workspace records & accounting formulas via Firebase AI Logic + App Check without fabricating numbers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FvgField(
                    value = question,
                    onValueChange = { question = it },
                    label = if (isArabic) "اسأل عن الهوامش، التضخم، طرق التكلفة الـ 8، أو المخزون" else "Ask about margins, inflation, 8 costing methods, or inventory",
                    singleLine = false,
                    testTag = "ai_question_input"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgPrimaryButton(
                        text = if (state.isAiStreaming) (if (isArabic) "جاري التحليل..." else "Analyzing...")
                        else if (isArabic) "تحليل البيانات" else "Analyze System Data",
                        icon = Icons.Filled.AutoAwesome,
                        enabled = !state.isAiStreaming && question.isNotBlank() && state.activeOrg != null,
                        onClick = { viewModel.askAiAssistant(question, if (isArabic) "ar" else "en") },
                        modifier = Modifier.weight(1f),
                        testTag = "ask_ai_button"
                    )
                    if (state.isAiStreaming) {
                        OutlinedButton(
                            onClick = { viewModel.stopAiStreaming() },
                            modifier = Modifier.heightIn(min = 50.dp).testTag("stop_ai_button")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "إيقاف" else "Stop")
                        }
                    }
                }

                if (state.isAiOfflineFallback) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isArabic) "تنبيه: تم تشغيل التحليل الحتمي المحلي غير المتصل لعرض الأرقام الحقيقية المخزنة." else "Notice: Offline deterministic analysis rendered from stored verified records.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (state.aiResponse.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = state.aiResponse,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(state.aiResponse))
                                    transcriptNotice = if (isArabic) "تم نسخ الإجابة." else "Answer copied."
                                }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isArabic) "نسخ الإجابة" else "Copy Answer")
                                }
                            }
                        }
                    }
                }

                if (state.chatTranscript.isNotEmpty()) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "سجل الجلسة (${state.chatTranscript.size})" else "Session Transcript (${state.chatTranscript.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = { transcriptLauncher.launch("fvg_assistant_transcript_$stamp.txt") },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("export_transcript_button")
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "تصدير TXT" else "Export TXT")
                        }
                    }
                    state.chatTranscript.takeLast(6).forEach { (role, msg) ->
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(role, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(msg.take(320), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (transcriptNotice != null) {
                    Text(transcriptNotice!!, style = MaterialTheme.typography.labelLarge, color = FvgPositive)
                }
            }
        }

        // Camera-first Invoice OCR Card
        item {
            FvgCard {
                Text(
                    text = if (isArabic) "تصوير الفواتير والوثائق بالكاميرا (OCR للمراجعة البشرية)" else "Camera Invoice Capture & OCR (Human Review Required)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isArabic) "صوّر الفاتورة بالكاميرا مباشرة أو اختر ملفاً احتياطياً ثم راجع الحقول المستخرجة وتأكد من المعادلة قبل الحفظ." else "Capture invoice directly with camera (recommended) or pick image fallback, verify extracted totals & duplicates before approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (cameraNotice != null) {
                    Text(cameraNotice!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgGoldButton(
                        text = if (state.isOcrExtracting) (if (isArabic) "جاري الاستخراج..." else "Extracting...")
                        else if (isArabic) "تصوير الفاتورة بالكاميرا" else "Capture with Camera",
                        icon = Icons.Filled.CameraAlt,
                        enabled = state.canEditFinancialData && !state.isOcrExtracting && state.activeOrg != null,
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                cameraLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        testTag = "capture_ocr_camera_button"
                    )
                    OutlinedButton(
                        enabled = state.canEditFinancialData && !state.isOcrExtracting && state.activeOrg != null,
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(0.8f).heightIn(min = 50.dp).testTag("pick_ocr_photo_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isArabic) "صورة" else "Gallery")
                    }
                }

                if (pendingOcrBitmap != null) {
                    Spacer(Modifier.height(6.dp))
                    Image(
                        bitmap = pendingOcrBitmap!!.asImageBitmap(),
                        contentDescription = if (isArabic) "معاينة الفاتورة" else "Invoice preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pendingOcrBitmap = rotateBitmap90(pendingOcrBitmap!!) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "تدوير 90°" else "Rotate 90°")
                        }
                        FvgPrimaryButton(
                            text = if (isArabic) "استخراج حقول الفاتورة" else "Extract Invoice Fields",
                            enabled = !state.isOcrExtracting,
                            onClick = {
                                val bmp = pendingOcrBitmap
                                if (bmp != null) {
                                    viewModel.extractOcrInvoice(bmp, pendingOcrLabel)
                                    pendingOcrBitmap = null
                                }
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "run_ocr_extract_button"
                        )
                    }
                }
            }
        }

        items(state.ocrDrafts.reversed(), key = { it.id }) { draft ->
            var vName by remember(draft.id) { mutableStateOf(draft.vendorName) }
            var invNo by remember(draft.id) { mutableStateOf(draft.invoiceNumber) }
            var dDate by remember(draft.id) { mutableStateOf(draft.documentDate) }
            var dCurr by remember(draft.id) { mutableStateOf(draft.currency) }
            var dSub by remember(draft.id) { mutableStateOf(draft.subtotalAmount) }
            var dDisc by remember(draft.id) { mutableStateOf(draft.discountAmount) }
            var dTax by remember(draft.id) { mutableStateOf(draft.taxAmount) }
            var dAmt by remember(draft.id) { mutableStateOf(draft.totalAmount) }
            var dSum by remember(draft.id) { mutableStateOf(draft.lineSummary) }

            val mathCheck = remember(dSub, dDisc, dTax, dAmt) {
                FvgFinancialEngine.validateOcrArithmetic(dSub, dDisc, dTax, dAmt)
            }

            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(draft.fileName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    FvgRoleBadge(localizePayStatus(draft.reviewStatus, isArabic))
                }
                if (draft.confidenceNote.isNotBlank() || !mathCheck.isConsistent || draft.duplicateWarning.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (!mathCheck.isConsistent || draft.duplicateWarning.isNotBlank()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (draft.confidenceNote.isNotBlank()) {
                                Text(
                                    text = if (isArabic) "ملاحظة الاستخراج: ${draft.confidenceNote}" else "OCR Confidence: ${draft.confidenceNote}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (!mathCheck.isConsistent) {
                                Text(
                                    text = if (isArabic) "⚠ عدم تطابق حسابي: المتوقع ${mathCheck.expectedTotal} مقابل الإجمالي ${mathCheck.extractedTotal}"
                                    else "⚠ Math discrepancy: expected ${mathCheck.expectedTotal} vs entered ${mathCheck.extractedTotal}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (draft.duplicateWarning.isNotBlank()) {
                                Text(
                                    text = "⚠ ${draft.duplicateWarning}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(vName, { vName = it }, if (isArabic) "اسم المورد" else "Vendor Name", modifier = Modifier.weight(1.3f))
                    FvgField(invNo, { invNo = it }, if (isArabic) "رقم الفاتورة" else "Invoice #", modifier = Modifier.weight(0.9f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(dDate, { dDate = it }, if (isArabic) "التاريخ" else "Date", modifier = Modifier.weight(1f))
                    FvgField(dCurr, { dCurr = it }, if (isArabic) "العملة" else "Currency", modifier = Modifier.weight(0.7f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(dSub, { dSub = it }, if (isArabic) "المجموع الفرعي" else "Subtotal", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(dDisc, { dDisc = it }, if (isArabic) "الخصم" else "Discount", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(dTax, { dTax = it }, if (isArabic) "الضريبة" else "Tax", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(dAmt, { dAmt = it }, if (isArabic) "الإجمالي النهائي" else "Final Total", numeric = true, modifier = Modifier.weight(1f))
                }
                FvgField(dSum, { dSum = it }, if (isArabic) "ملخص البنود المستخرجة" else "Extracted Line Summary", singleLine = false)
                if (draft.reviewStatus == "pending_review" && state.canEditFinancialData) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgPrimaryButton(
                            text = if (isArabic) "اعتماد المسودة" else "Verify Draft",
                            onClick = { viewModel.reviewOcrDraft(draft, vName, invNo, dDate, dCurr, dSub, dDisc, dTax, dAmt, dSum, "verified") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.reviewOcrDraft(draft, vName, invNo, dDate, dCurr, dSub, dDisc, dTax, dAmt, dSum, "rejected") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(if (isArabic) "رفض" else "Reject") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrgRbacBillingSettingsTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    onOpenProfile: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenOwnerAdmin: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val org = state.activeOrg
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var editOrgName by remember(org?.id) { mutableStateOf(org?.name ?: "") }
    var editCurrency by remember(org?.id) { mutableStateOf(org?.baseCurrency ?: "SDG") }
    var editSector by remember(org?.id) { mutableStateOf(org?.sectorType ?: "manufacturing") }
    var editTagline by remember(org?.id) { mutableStateOf(org?.brandTagline.orEmpty()) }
    var editCountry by remember(org?.id) { mutableStateOf(org?.countryState.orEmpty()) }
    var editFiscalStart by remember(org?.id) { mutableStateOf(org?.fiscalYearStart ?: "01-01") }
    var editTaxEnabled by remember(org?.id) { mutableStateOf(org?.taxEnabled ?: false) }
    var editTaxRate by remember(org?.id) { mutableStateOf(org?.defaultTaxRatePct ?: "0.00") }

    var inviteUid by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf("accountant") }
    var newWorkspaceName by remember { mutableStateOf("") }
    var newWorkspaceSector by remember { mutableStateOf("manufacturing") }

    // Manual Sudan Payment Proof submission state (Customer side ONLY submits pending_verification)
    var payPlanTier by remember { mutableStateOf("professional") }
    var payMethod by remember { mutableStateOf("bankak") }
    var payAmount by remember { mutableStateOf("29.00") }
    var payCurrency by remember { mutableStateOf("USD") }
    var senderName by remember { mutableStateOf("") }
    var referenceNo by remember { mutableStateOf("") }
    var last4Digits by remember { mutableStateOf("") }
    var transferDate by remember { mutableStateOf(today) }
    var attachmentNote by remember { mutableStateOf("") }
    var payNotes by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.organizations.size > 1) {
            item {
                FvgCard {
                    Text(if (isArabic) "تبديل المنظمة النشطة" else "Switch Active Workspace", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    FvgChipSelector(
                        options = state.organizations.map { it.id },
                        selected = org?.id ?: "",
                        onSelect = { viewModel.switchOrganization(it) },
                        labelResolver = { id -> state.organizations.firstOrNull { it.id == id }?.name ?: id }
                    )
                }
            }
        }

        if (org != null) {
            item {
                FvgTrialCountdownBanner(
                    trialEndsAtMillis = state.effectiveTrialEndsMillis(),
                    isArabic = isArabic,
                    durationHours = state.effectiveTrialDurationHours(),
                    serverAnchorMillis = state.serverAnchorMillis,
                    elapsedBaselineMillis = state.elapsedBaselineRealtimeMillis
                )
            }

            // Payment Proof Submission Card (Zero customer self-approval)
            item {
                FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = if (isArabic) "رفع إثبات الدفع للتفعيل (بنكك / أوكاش / تحويل بنكي)" else "Submit Payment Proof (Bankak / O-Cash / Bank Transfer)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isArabic)
                                "ملاحظة التوثيق: يتم إرسال طلب الدفع بحالة [قيد التحقق] إلى مالكة المنصة للمراجعة والاعتماد عبر البريد الموثق Financialvisionguide@gmail.com. لا يتم جمع أرقام بطاقات بنكية كاملة أو CVV — يمكنك إدخال آخر 4 أرقام للمطابقة فقط."
                            else
                                "Verification Notice: Payment proofs are queued as [Pending Verification] for the Platform Owner (Financialvisionguide@gmail.com). Full card numbers & CVV are never collected — optional Last 4 Digits is for transfer verification only.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    FvgChipSelector(
                        options = listOf("starter", "professional", "enterprise"),
                        selected = payPlanTier,
                        onSelect = {
                            payPlanTier = it
                            payAmount = when (it) {
                                "starter" -> "12.00"
                                "professional" -> "29.00"
                                else -> "79.00"
                            }
                        },
                        labelResolver = { tier ->
                            when (tier) {
                                "starter" -> if (isArabic) "الباقة الأساسية (Starter)" else "Starter ($12)"
                                "professional" -> if (isArabic) "الاحترافية (Professional)" else "Professional ($29)"
                                else -> if (isArabic) "المؤسسات (Enterprise)" else "Enterprise ($79)"
                            }
                        }
                    )
                    FvgChipSelector(
                        options = listOf("bankak", "ocash", "bank_transfer"),
                        selected = payMethod,
                        onSelect = { payMethod = it },
                        labelResolver = { m ->
                            when (m) {
                                "bankak" -> if (isArabic) "بنكك" else "Bankak"
                                "ocash" -> if (isArabic) "أوكاش" else "O-Cash"
                                else -> if (isArabic) "تحويل بنكي" else "Bank Transfer"
                            }
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(payAmount, { payAmount = it }, if (isArabic) "المبلغ" else "Amount", numeric = true, modifier = Modifier.weight(1f), testTag = "payment_amount_input")
                        FvgField(payCurrency, { payCurrency = it }, if (isArabic) "العملة" else "Currency", modifier = Modifier.weight(0.7f))
                        FvgField(transferDate, { transferDate = it }, if (isArabic) "التاريخ" else "Date", modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(senderName, { senderName = it }, if (isArabic) "اسم المحوّل" else "Sender Name", modifier = Modifier.weight(1f), testTag = "payment_sender_input")
                        FvgField(referenceNo, { referenceNo = it }, if (isArabic) "رقم المرجع" else "Reference No", modifier = Modifier.weight(1f), testTag = "payment_ref_input")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(
                            value = last4Digits,
                            onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) last4Digits = it },
                            label = if (isArabic) "آخر 4 أرقام للمعاملة/الحساب" else "Last 4 Digits",
                            numeric = true,
                            modifier = Modifier.weight(1f),
                            testTag = "payment_last4_input"
                        )
                        FvgField(
                            value = attachmentNote,
                            onValueChange = { attachmentNote = it },
                            label = if (isArabic) "بيان الإيصال / الحساب" else "Receipt / Account Note",
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                    FvgField(payNotes, { payNotes = it }, if (isArabic) "ملاحظة باسم المستخدم والمبلغ" else "User Name & Transfer Notes")
                    FvgGoldButton(
                        text = if (isArabic) "إرسال إثبات الدفع للمراجعة" else "Submit Payment Proof [Pending Verification]",
                        icon = Icons.Filled.ReceiptLong,
                        enabled = state.canAdminOrg && senderName.isNotBlank() && referenceNo.trim().length >= 2 && !state.isBusy,
                        onClick = {
                            viewModel.submitPaymentRequest(
                                payPlanTier, payMethod, payAmount, payCurrency,
                                senderName, referenceNo, transferDate, payNotes,
                                last4Digits, attachmentNote
                            )
                            senderName = ""; referenceNo = ""; payNotes = ""; last4Digits = ""; attachmentNote = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "submit_payment_proof_button"
                    )
                }
            }

            // Read-Only Payment Requests History on Customer Screen
            if (state.paymentRequests.isNotEmpty()) {
                items(state.paymentRequests.reversed(), key = { it.id }) { req ->
                    FvgCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${req.paymentMethod.uppercase()} • ${req.referenceNo}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            FvgRoleBadge(localizePayStatus(req.status, isArabic))
                        }
                        Text(
                            text = if (isArabic) "الباقة: ${req.planTier} | المبلغ: ${req.amount} ${req.currency} | المحوّل: ${req.senderName} (${req.transferDate})"
                            else "Plan: ${req.planTier} | Amount: ${req.amount} ${req.currency} | Sender: ${req.senderName} (${req.transferDate})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (req.last4Digits.isNotBlank()) {
                            Text(
                                text = if (isArabic) "آخر 4 أرقام: ${req.last4Digits}" else "Last 4 Digits: ${req.last4Digits}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (req.reviewNotes.isNotBlank()) {
                            Text(
                                text = if (isArabic) "ملاحظة المراجعة: ${req.reviewNotes}" else "Owner Review Note: ${req.reviewNotes}",
                                style = MaterialTheme.typography.labelSmall,
                                color = FvgAntiqueGold
                            )
                        }
                    }
                }
            }

            // Organization Metadata Settings (NO self-service planTier or subscriptionStatus editing)
            item {
                FvgCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isArabic) "إعدادات المنظمة والضرائب والهوية" else "Organization & Fiscal Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        FvgRoleBadge("${org.planTier} • ${localizePayStatus(org.subscriptionStatus, isArabic)}")
                    }
                    FvgField(editOrgName, { editOrgName = it }, if (isArabic) "اسم المنظمة" else "Organization Name")
                    FvgField(editTagline, { editTagline = it }, if (isArabic) "الشعار الفرعي للمنظمة" else "Organization Tagline")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(editCountry, { editCountry = it }, if (isArabic) "الدولة / الولاية" else "Country / State", modifier = Modifier.weight(1f))
                        FvgField(editFiscalStart, { editFiscalStart = it }, if (isArabic) "بداية السنة (MM-DD)" else "Fiscal Start", modifier = Modifier.weight(1f))
                    }
                    Text(if (isArabic) "القطاع:" else "Business Sector:", style = MaterialTheme.typography.labelLarge)
                    FvgChipSelector(
                        options = FvgFinancialEngine.FVG_SECTORS.map { it.key },
                        selected = editSector,
                        onSelect = { editSector = it },
                        labelResolver = { key ->
                            val sec = FvgFinancialEngine.FVG_SECTORS.firstOrNull { it.key == key }
                            if (sec == null) key else if (isArabic) sec.nameAr else sec.nameEn
                        }
                    )
                    Text(if (isArabic) "العملة الأساسية:" else "Base Currency:", style = MaterialTheme.typography.labelLarge)
                    FvgChipSelector(listOf("SDG", "USD", "SAR", "AED", "EUR", "EGP"), editCurrency, { editCurrency = it })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = editTaxEnabled, onCheckedChange = { editTaxEnabled = it })
                        Text(if (isArabic) "تفعيل الضريبة الافتراضية" else "Enable Default Tax", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        FvgField(editTaxRate, { editTaxRate = it }, if (isArabic) "النسبة %" else "Tax %", numeric = true, modifier = Modifier.weight(0.8f))
                    }
                    FvgPrimaryButton(
                        text = if (isArabic) "حفظ إعدادات المنظمة" else "Save Organization Settings",
                        enabled = state.canAdminOrg && editOrgName.isNotBlank() && !state.isBusy,
                        onClick = {
                            viewModel.updateOrganizationSettings(
                                name = editOrgName,
                                currency = editCurrency,
                                sectorType = editSector,
                                brandTagline = editTagline,
                                countryState = editCountry,
                                fiscalYearStart = editFiscalStart,
                                taxEnabled = editTaxEnabled,
                                defaultTaxRatePct = editTaxRate
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (state.canAdminOrg) {
                item {
                    FvgCard {
                        Text(if (isArabic) "إدارة أعضاء المنظمة ومصفوفة الصلاحيات" else "Team & Role Access Matrix (RBAC)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (isArabic) "المالك/المدير: تحكم كامل والاعتمادات | المحاسب: القيود والتكاليف والمخزون | المشاهد: عرض فقط"
                            else "Owner/Admin: Full write, Approvals & Roles | Accountant: Journals, Costing, Prices, Inventory | Viewer: Read-only",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FvgField(inviteUid, { inviteUid = it }, if (isArabic) "معرّف العضو (Firebase UID)" else "Member Firebase UID")
                        FvgChipSelector(listOf("admin", "accountant", "viewer"), inviteRole, { inviteRole = it })
                        FvgGoldButton(
                            text = if (isArabic) "تحديث صلاحية العضو" else "Grant / Update Member Role",
                            enabled = inviteUid.isNotBlank() && !state.isBusy,
                            onClick = {
                                viewModel.updateMemberRole(inviteUid, inviteRole)
                                inviteUid = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "إنشاء مساحة عمل إضافية" else "Create Additional Organization", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                FvgField(newWorkspaceName, { newWorkspaceName = it }, if (isArabic) "اسم المنظمة الجديدة" else "New Organization Name")
                FvgChipSelector(
                    options = FvgFinancialEngine.FVG_SECTORS.map { it.key },
                    selected = newWorkspaceSector,
                    onSelect = { newWorkspaceSector = it },
                    labelResolver = { key ->
                        val sec = FvgFinancialEngine.FVG_SECTORS.firstOrNull { it.key == key }
                        if (sec == null) key else if (isArabic) sec.nameAr else sec.nameEn
                    }
                )
                FvgGoldButton(
                    text = if (isArabic) "إنشاء منظمة جديدة (تجربة 7 أيام / SDG)" else "Create Workspace (7-Day Trial / SDG)",
                    enabled = newWorkspaceName.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.createOrganization(newWorkspaceName, "SDG", newWorkspaceSector)
                        newWorkspaceName = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "الحساب والدعم ولوحة التحكم" else "Profile, Help & Account", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenProfile, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isArabic) "ملفي الشخصي" else "My Profile")
                    }
                    OutlinedButton(onClick = onOpenHelp, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isArabic) "المساعدة" else "Help & Support")
                    }
                }
                if (state.isPlatformOwner) {
                    FvgGoldButton(
                        text = if (isArabic) "فتح لوحة مالكة المنصة (الإدارة العامة)" else "Open Platform Owner Console",
                        icon = Icons.Filled.AdminPanelSettings,
                        onClick = onOpenOwnerAdmin,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = onSignOutClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(fvgStrings(isArabic).signOut)
                }
            }
        }

        item {
            Text(
                text = if (isArabic) "سجل التدقيق غير القابل للتعديل (${state.auditLogs.size})" else "Immutable Audit Trail (${state.auditLogs.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(state.auditLogs.reversed().take(30), key = { it.id }) { log ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(log.action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(log.entityType, style = MaterialTheme.typography.labelSmall, color = FvgAntiqueGold)
                }
                Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (isArabic) "بواسطة: ${log.actorEmail}" else "By: ${log.actorEmail}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
