package com.example.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class FvgFinancialEngineTest {

    @Test
    fun `all 8 costing methods compute deterministic results`() {
        val baseInput = FvgFinancialEngine.CostingInput(
            directMaterial = BigDecimal("500"),
            directLabor = BigDecimal("200"),
            overhead = BigDecimal("100"),
            fixedCosts = BigDecimal("200"),
            goodUnits = BigDecimal("100"),
            sellingPrice = BigDecimal("15")
        )
        for (method in FvgFinancialEngine.ALL_COSTING_METHODS) {
            val outcome = FvgFinancialEngine.calculateCostingByMethod(
                method = method,
                input = baseInput,
                targetMarginPct = BigDecimal("25"),
                completedEquivUnits = BigDecimal("80")
            )
            assertEquals(method, outcome.method)
            assertTrue(outcome.coreResult.unitCost > BigDecimal.ZERO)
            assertTrue(outcome.methodNote.isNotBlank())
        }

        val absorption = FvgFinancialEngine.calculateCostingByMethod("absorption", baseInput)
        val variable = FvgFinancialEngine.calculateCostingByMethod("variable", baseInput)
        // Absorption includes fixed costs (1000 / 100 = 10.00); Variable excludes fixed costs (800 / 100 = 8.00)
        assertEquals(BigDecimal("10.00"), absorption.coreResult.unitCost)
        assertEquals(BigDecimal("8.00"), variable.coreResult.unitCost)

        val target = FvgFinancialEngine.calculateCostingByMethod("target", baseInput, targetMarginPct = BigDecimal("40"))
        // Price 15 * (1 - 0.40) = 9.00 allowable unit cost; actual unit cost 8.00 -> -1.00 gap
        assertEquals(BigDecimal("9.00"), target.targetAllowableUnitCost)
        assertEquals(BigDecimal("-1.00"), target.targetCostGapPerUnit)
    }

    @Test
    fun `detailed variance decomposes material price usage and labor rate efficiency`() {
        val res = FvgFinancialEngine.computeDetailedVariance(
            stdMaterialQtyPerUnit = BigDecimal("2"),
            stdMaterialPrice = BigDecimal("10"),
            stdLaborHoursPerUnit = BigDecimal("1"),
            stdLaborRate = BigDecimal("20"),
            stdOverheadPerUnit = BigDecimal("5"),
            goodUnits = BigDecimal("10"),
            actualMaterialQty = BigDecimal("22"),
            actualMaterialPrice = BigDecimal("11"),
            actualLaborHours = BigDecimal("9"),
            actualLaborRate = BigDecimal("22"),
            actualOverheadCost = BigDecimal("50")
        )
        // Mat Price Var = (11 - 10) * 22 = 22.00; Mat Usage Var = (22 - 20) * 10 = 20.00
        assertEquals(BigDecimal("22.00"), res.materialPriceVariance)
        assertEquals(BigDecimal("20.00"), res.materialUsageVariance)
        // Lab Rate Var = (22 - 20) * 9 = 18.00; Lab Eff Var = (9 - 10) * 20 = -20.00
        assertEquals(BigDecimal("18.00"), res.laborRateVariance)
        assertEquals(BigDecimal("-20.00"), res.laborEfficiencyVariance)
        assertEquals(BigDecimal("40.00"), res.summary.totalVariance)
    }

    @Test
    fun `financial risk engine evaluates rules explicitly without hallucination`() {
        val highNeg = FvgFinancialEngine.evaluateFinancialRisk(BigDecimal("-5"))
        assertEquals("high", highNeg.level)
        assertEquals("NEGATIVE_MARGIN_RULE", highNeg.ruleName)

        val medInf = FvgFinancialEngine.evaluateFinancialRisk(BigDecimal("18"), BigDecimal("5"))
        assertEquals("medium", medInf.level)

        val lowHealthy = FvgFinancialEngine.evaluateFinancialRisk(BigDecimal("28"), BigDecimal("4"))
        assertEquals("low", lowHealthy.level)
    }

    @Test
    fun `extended scenario runs FX and volume shocks with disclaimer`() {
        val scen = FvgFinancialEngine.runExtendedScenario(
            baseMaterial = BigDecimal("1000"),
            baseLabor = BigDecimal("500"),
            baseOverheadAndOther = BigDecimal("200"),
            baseRevenue = BigDecimal("2500"),
            materialChangePct = BigDecimal("15"),
            laborChangePct = BigDecimal.ZERO,
            overheadChangePct = BigDecimal.ZERO,
            priceChangePct = BigDecimal.ZERO,
            fxChangePct = BigDecimal("10"),
            volumeChangePct = BigDecimal("20")
        )
        assertEquals("Scenario outcome — not a prediction", scen.disclaimer)
        assertTrue(scen.scenarioTotalCost > scen.baseTotalCost)
    }

    @Test
    fun `strict 168h (7-day) trial countdown preserves legacy 72h and ticks monotonically`() {
        assertEquals(168L, FvgFinancialEngine.TRIAL_DURATION_HOURS)
        val baseStart = 1_700_000_000_000L
        val firstOrgEnd = baseStart + FvgFinancialEngine.TRIAL_DURATION_MILLIS
        val secondOrgClaimedEnd = baseStart + 240L * 3600_000L
        val capped = FvgFinancialEngine.computeServerSyncedTrialEndMillis(baseStart, secondOrgClaimedEnd)
        assertNotNull(capped)
        assertEquals(firstOrgEnd, capped!!)

        val active = FvgFinancialEngine.computeMonotonicTrialCountdown(
            trialEndsAtMillis = firstOrgEnd,
            anchorServerNowMillis = baseStart,
            elapsedDeltaMillis = 3600_000L // +1 hour elapsed -> 167 hours left (6d 23h)
        )
        assertFalse(active.isExpired)
        assertEquals(6L, active.days)
        assertEquals(23L, active.hours)
        assertEquals(167L, active.totalRemainingHours)

        // Legacy 72h account preservation without auto-extension
        val legacyEnd = FvgFinancialEngine.computeServerSyncedTrialEndMillis(
            userTrialStartMillis = baseStart,
            orgTrialEndsAtMillis = baseStart + 72L * 3600_000L,
            durationHours = 72L
        )
        assertEquals(baseStart + 72L * 3600_000L, legacyEnd)

        val expired = FvgFinancialEngine.computeMonotonicTrialCountdown(
            trialEndsAtMillis = firstOrgEnd,
            anchorServerNowMillis = baseStart,
            elapsedDeltaMillis = FvgFinancialEngine.TRIAL_DURATION_MILLIS
        )
        assertTrue(expired.isExpired)
        assertEquals(0L, expired.remainingMillis)

        // Missing denominators return null ratio rather than fake zero
        val zeroStatements = FvgFinancialEngine.generateFinancialStatements(emptyList(), emptyList())
        val ratios = FvgFinancialEngine.computeFinancialRatios(zeroStatements)
        assertNull(ratios.first { it.key == "current_ratio" }.value)
    }

    @Test
    fun `3D calculator evaluates precedence parentheses percentages and rejects divide by zero`() {
        assertEquals("14", FvgFinancialEngine.formatCalculatorResult(FvgFinancialEngine.evaluateCalculatorExpression("2 + 3 × 4")))
        assertEquals("20", FvgFinancialEngine.formatCalculatorResult(FvgFinancialEngine.evaluateCalculatorExpression("(2 + 3) × 4")))
        assertEquals("250", FvgFinancialEngine.formatCalculatorResult(FvgFinancialEngine.evaluateCalculatorExpression("1000 × 25%")))
        assertEquals("5", FvgFinancialEngine.formatCalculatorResult(FvgFinancialEngine.evaluateCalculatorExpression("15 ÷ 3")))
        assertThrows(ArithmeticException::class.java) {
            FvgFinancialEngine.evaluateCalculatorExpression("10 ÷ 0")
        }
    }

    @Test
    fun `BOM recipe builder computes net and scrap allowance unit material cost`() {
        val bom = FvgFinancialEngine.calculateBomUnitCost(
            listOf(
                FvgFinancialEngine.BomLineInput("Raw Flour", BigDecimal("0.5"), BigDecimal("100"), BigDecimal("10")),
                FvgFinancialEngine.BomLineInput("Sugar", BigDecimal("0.2"), BigDecimal("150"), BigDecimal.ZERO)
            )
        )
        // Net = 50 + 30 = 80.00; Scrap = 10% of 50 = 5.00; Total = 85.00
        assertEquals(BigDecimal("80.00"), bom.netMaterialCostPerUnit)
        assertEquals(BigDecimal("5.00"), bom.scrapAllowanceCostPerUnit)
        assertEquals(BigDecimal("85.00"), bom.effectiveMaterialCostPerUnit)
    }

    @Test
    fun `FX freshness and OCR arithmetic validation detect stale rates and mismatches`() {
        val fresh = FvgFinancialEngine.assessFxRateFreshness("2026-11-18", "approved", todayEpochDays = 20776L)
        assertFalse(fresh.isStale)
        assertFalse(fresh.isUnapproved)

        val staleDraft = FvgFinancialEngine.assessFxRateFreshness("2026-11-01", "draft", todayEpochDays = 20776L)
        assertTrue(staleDraft.isStale)
        assertTrue(staleDraft.isUnapproved)

        val ocrOk = FvgFinancialEngine.validateOcrArithmetic("1000", "50", "150", "1100.00")
        assertTrue(ocrOk.isConsistent)
        assertEquals(BigDecimal("1100.00"), ocrOk.expectedTotal)

        val ocrBad = FvgFinancialEngine.validateOcrArithmetic("1000", "50", "150", "1250.00")
        assertFalse(ocrBad.isConsistent)
        assertEquals(BigDecimal("150.00"), ocrBad.discrepancy)
    }
}
