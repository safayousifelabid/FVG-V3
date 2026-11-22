package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val activeOrgId: String = "",
    val preferredLanguage: String = "ar",
    val darkMode: Boolean = false,
    val themeMode: String = "system", // "light", "dark", "system"
    val fontScale: Float = 1.0f, // 0.85f .. 1.30f
    val timezone: String = "Africa/Khartoum",
    val dateFormat: String = "yyyy-MM-dd",
    val numberFormat: String = "latin",
    val defaultCurrency: String = "SDG",
    val avatarLocalRef: String = "",
    val contactPhoneOptional: String = "",
    val tourCompleted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val trialDurationHours: Long = 168L,
    @ServerTimestamp val trialStartedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Organization(
    @DocumentId val id: String = "",
    val name: String = "",
    val baseCurrency: String = "SDG",
    val sectorType: String = "manufacturing",
    val brandTagline: String = "",
    val countryState: String = "",
    val fiscalYearStart: String = "01-01",
    val taxEnabled: Boolean = false,
    val defaultTaxRatePct: String = "0.00",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val roles: Map<String, String> = emptyMap(),
    val planTier: String = "trial",
    val subscriptionStatus: String = "trial",
    val trialDurationHours: Long = 168L,
    @ServerTimestamp val trialStartedAt: Timestamp? = null,
    val trialEndsAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val name: String = "",
    val sku: String = "",
    val category: String = "",
    val description: String = "",
    val unit: String = "kg",
    val currency: String = "SDG",
    val location: String = "HQ",
    val source: String = "Manual",
    val currentPrice: String = "0.00",
    val previousPrice: String = "0.00",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PriceObservation(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val productId: String = "",
    val productName: String = "",
    val category: String = "",
    val unit: String = "kg",
    val currency: String = "SDG",
    val price: String = "0.00",
    val previousPrice: String = "0.00",
    val absChange: String = "0.00",
    val pctChange: String = "N/A",
    val direction: String = "unchanged",
    val source: String = "Manual",
    val location: String = "HQ",
    val observationDate: String = "",
    val notes: String = "",
    val attachmentRef: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InflationRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val period: String = "",
    val category: String = "All Items",
    val monthlyRatePct: String = "0.00",
    val annualRatePct: String = "",
    val indexValue: String = "100.00",
    val basePeriod: String = "",
    val sourceType: String = "calculated",
    val sourceName: String = "",
    val formulaNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BasketItemMap(
    val name: String = "",
    val unit: String = "kg",
    val quantity: String = "1",
    val weight: String = "1",
    val basePrice: String = "0.00",
    val currentPrice: String = "0.00"
)

@IgnoreExtraProperties
data class BasketPeriodSnapshotMap(
    val period: String = "",
    val version: Long = 1L,
    val cost: String = "0.00",
    val indexValue: String = "100.00"
)

@IgnoreExtraProperties
data class PriceBasket(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val name: String = "",
    val basePeriod: String = "",
    val currentPeriod: String = "",
    val currency: String = "SDG",
    val version: Long = 1L,
    val items: List<Map<String, Any>> = emptyList(),
    val baseCost: String = "0.00",
    val currentCost: String = "0.00",
    val indexValue: String = "100.00",
    val periodSnapshots: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FxRate(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val baseCurrency: String = "USD",
    val quoteCurrency: String = "SDG",
    val rate: String = "1.0000",
    val effectiveDate: String = "",
    val effectiveTime: String = "",
    val rateType: String = "manual",
    val sourceKind: String = "manual",
    val sourceName: String = "",
    val status: String = "approved", // "draft", "approved", "cancelled"
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CostSnapshot(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val productId: String = "",
    val productName: String = "",
    val period: String = "",
    val currency: String = "SDG",
    val costingMethod: String = "actual",
    val directMaterial: String = "0.00",
    val indirectMaterial: String = "0.00",
    val directLabor: String = "0.00",
    val indirectLabor: String = "0.00",
    val overhead: String = "0.00",
    val energyFuel: String = "0.00",
    val packaging: String = "0.00",
    val transport: String = "0.00",
    val wasteScrap: String = "0.00",
    val fixedCosts: String = "0.00",
    val goodUnits: String = "1",
    val totalCost: String = "0.00",
    val unitCost: String = "0.00",
    val sellingPrice: String = "0.00",
    val revenue: String = "0.00",
    val grossProfit: String = "0.00",
    val netProfit: String = "0.00",
    val profitMarginPct: String = "0.00",
    val markupPct: String = "0.00",
    val breakEvenUnits: String = "0",
    val riskLevel: String = "low",
    val formulaVersion: String = "2.2.0",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ScenarioRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val snapshotId: String = "",
    val name: String = "",
    val scenarioType: String = "Custom",
    val materialChangePct: String = "0",
    val laborChangePct: String = "0",
    val overheadChangePct: String = "0",
    val priceChangePct: String = "0",
    val fxChangePct: String = "0",
    val volumeChangePct: String = "0",
    val baseTotalCost: String = "0.00",
    val scenarioTotalCost: String = "0.00",
    val baseProfit: String = "0.00",
    val scenarioProfit: String = "0.00",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AccountRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val code: String = "",
    val name: String = "",
    val nameAr: String = "",
    val accountType: String = "asset",
    val subType: String = "current_asset",
    val normalBalance: String = "debit",
    val openingBalance: String = "0.00",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class JournalEntryRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val referenceNo: String = "",
    val entryDate: String = "",
    val period: String = "",
    val description: String = "",
    val currency: String = "SDG",
    val exchangeRate: String = "1.0000",
    val originalCurrency: String = "SDG",
    val originalAmount: String = "0.00",
    val status: String = "posted",
    val lines: List<Map<String, Any>> = emptyList(),
    val totalDebit: String = "0.00",
    val totalCredit: String = "0.00",
    val sourceModule: String = "manual",
    val reversedEntryId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CounterpartyRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val type: String = "receivable",
    val partyName: String = "",
    val invoiceNo: String = "",
    val currency: String = "SDG",
    val amount: String = "0.00",
    val paidAmount: String = "0.00",
    val dueDate: String = "",
    val status: String = "open",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BudgetRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val name: String = "",
    val budgetType: String = "operating",
    val period: String = "",
    val version: Long = 1L,
    val currency: String = "SDG",
    val plannedAmount: String = "0.00",
    val actualAmount: String = "0.00",
    val variance: String = "0.00",
    val variancePct: String = "0.00",
    val explanation: String = "",
    val status: String = "draft",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InventoryItemRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val sku: String = "",
    val name: String = "",
    val itemType: String = "raw_material",
    val unit: String = "kg",
    val warehouse: String = "Main Warehouse",
    val currency: String = "SDG",
    val valuationMethod: String = "fifo",
    val quantityOnHand: String = "0.0000",
    val totalBookValue: String = "0.00",
    val movingAvgCost: String = "0.0000",
    val currentMarketPrice: String = "0.00",
    val batchNumber: String = "",
    val expiryDate: String = "",
    val fifoLots: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InventoryMovementRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val movementType: String = "receipt",
    val quantity: String = "0.0000",
    val unitCost: String = "0.0000",
    val totalCost: String = "0.00",
    val warehouse: String = "Main Warehouse",
    val reason: String = "",
    val referenceNo: String = "",
    val movementDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StocktakeSessionRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val sessionName: String = "",
    val warehouse: String = "Main Warehouse",
    val sessionDate: String = "",
    val status: String = "draft",
    val lines: List<Map<String, Any>> = emptyList(),
    val totalDiscrepancyValue: String = "0.00",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class OcrDraftRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val fileName: String = "",
    val invoiceNumber: String = "",
    val extractedText: String = "",
    val vendorName: String = "",
    val documentDate: String = "",
    val currency: String = "",
    val subtotalAmount: String = "",
    val discountAmount: String = "",
    val taxAmount: String = "",
    val totalAmount: String = "",
    val confidenceNote: String = "",
    val lineSummary: String = "",
    val duplicateWarning: String = "",
    val reviewStatus: String = "pending_review",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLogRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val action: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val summary: String = "",
    val actorEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StandardCostSheet(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val productId: String = "",
    val productName: String = "",
    val version: Long = 1L,
    val currency: String = "SDG",
    val standardMaterialCost: String = "0.00",
    val standardLaborHours: String = "0.00",
    val standardLaborRate: String = "0.00",
    val standardOverheadPerUnit: String = "0.00",
    val standardUnitCost: String = "0.00",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BomFormulaRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val productName: String = "",
    val outputUnit: String = "kg",
    val batchSize: String = "1",
    val ingredients: List<Map<String, Any>> = emptyList(),
    val unitMaterialCost: String = "0.00",
    val scrapAllowancePct: String = "0.00",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ProductionOrderRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val productId: String = "",
    val productName: String = "",
    val period: String = "",
    val currency: String = "SDG",
    val plannedUnits: String = "0",
    val goodUnits: String = "0",
    val scrapUnits: String = "0",
    val actualMaterialCost: String = "0.00",
    val actualLaborCost: String = "0.00",
    val actualOverheadCost: String = "0.00",
    val actualTotalCost: String = "0.00",
    val status: String = "planned",
    val stages: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ScrapLossRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val lossType: String = "scrap",
    val quantity: String = "0",
    val unitCost: String = "0.00",
    val totalLossCost: String = "0.00",
    val reason: String = "",
    val lossDate: String = "",
    val status: String = "pending_approval",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PaymentRequestRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val planTier: String = "professional",
    val paymentMethod: String = "bankak",
    val amount: String = "0.00",
    val currency: String = "SDG",
    val senderName: String = "",
    val referenceNo: String = "",
    val last4Digits: String = "",
    val transferDate: String = "",
    val attachmentNote: String = "",
    val notes: String = "",
    val status: String = "pending_verification",
    val reviewedBy: String = "",
    val decisionActorEmail: String = "",
    val reviewNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlatformPlanRecord(
    @DocumentId val id: String = "",
    val key: String = "starter",
    val nameEn: String = "",
    val nameAr: String = "",
    val priceUsd: String = "0.00",
    val priceSdg: String = "0.00",
    val billingCycle: String = "monthly",
    val maxOrgs: Long = 1L,
    val maxUsers: Long = 3L,
    val active: Boolean = true,
    val notesEn: String = "",
    val notesAr: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppAnnouncementRecord(
    @DocumentId val id: String = "",
    val titleEn: String = "",
    val titleAr: String = "",
    val bodyEn: String = "",
    val bodyAr: String = "",
    val targetAudience: String = "all", // "all", "trial", "paid"
    val active: Boolean = true,
    val createdByEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SupportTicketRecord(
    @DocumentId val id: String = "",
    val createdBy: String = "",
    val senderName: String = "",
    val senderEmail: String = "",
    val orgId: String = "",
    val orgName: String = "",
    val message: String = "",
    val status: String = "open", // "open", "reviewed", "resolved"
    val ownerReply: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AiConversationRecord(
    @DocumentId val id: String = "",
    val orgId: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    val title: String = "",
    val mode: String = "financial", // "general", "financial", "ocr"
    val messages: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserSessionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceLabel: String = "",
    val platform: String = "Android",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val lastActiveAt: Timestamp? = null
)

fun tenantEnvelope(org: Organization, createdBy: String): MutableMap<String, Any> = mutableMapOf(
    "orgId" to org.id,
    "ownerId" to org.ownerId,
    "memberUids" to org.memberUids,
    "createdBy" to createdBy,
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp()
)
