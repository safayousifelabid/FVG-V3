package com.example.domain.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object FvgFinancialEngine {
    const val MODEL_VERSION = "2.2.0"
    const val TRIAL_DURATION_HOURS = 168L
    const val TRIAL_DURATION_MILLIS = 168L * 3600_000L
    val MC = MathContext(16, RoundingMode.HALF_UP)

    fun d(value: String?): BigDecimal {
        val cleaned = value?.trim().orEmpty()
        require(cleaned.isNotEmpty()) { "Numeric input cannot be blank" }
        return BigDecimal(cleaned)
    }

    fun dOrZero(value: String?): BigDecimal {
        val cleaned = value?.trim().orEmpty()
        if (cleaned.isEmpty()) return BigDecimal.ZERO
        return BigDecimal(cleaned)
    }

    fun money(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)
    fun pct4(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)
    fun rate4(value: BigDecimal): BigDecimal = value.setScale(4, RoundingMode.HALF_UP)

    // --- 1. Prices & Unit Conversions ---

    data class PriceComparisonResult(
        val newPrice: BigDecimal,
        val oldPrice: BigDecimal,
        val absChange: BigDecimal,
        val pctChange: BigDecimal?,
        val direction: String,
        val formula: String
    )

    fun calculatePriceChange(newPrice: BigDecimal, oldPrice: BigDecimal): PriceComparisonResult {
        require(newPrice >= BigDecimal.ZERO) { "New price cannot be negative" }
        require(oldPrice >= BigDecimal.ZERO) { "Previous price cannot be negative" }
        val abs = money(newPrice.subtract(oldPrice))
        val pct = if (oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            null
        } else {
            pct4(abs.divide(oldPrice, MC).multiply(BigDecimal(100)))
        }
        val direction = when {
            abs > BigDecimal.ZERO -> "increased"
            abs < BigDecimal.ZERO -> "decreased"
            else -> "unchanged"
        }
        val formula = "((New Price - Old Price) / Old Price) × 100"
        return PriceComparisonResult(
            newPrice = money(newPrice),
            oldPrice = money(oldPrice),
            absChange = abs,
            pctChange = pct,
            direction = direction,
            formula = formula
        )
    }

    private val UNIT_CONVERSIONS: Map<Pair<String, String>, BigDecimal> = mapOf(
        ("kg" to "g") to BigDecimal("1000"),
        ("ton" to "kg") to BigDecimal("1000"),
        ("l" to "ml") to BigDecimal("1000"),
        ("dozen" to "unit") to BigDecimal("12"),
        ("box" to "unit") to BigDecimal("24")
    )

    fun convertUnits(quantity: BigDecimal, fromUnit: String, toUnit: String): BigDecimal {
        require(quantity >= BigDecimal.ZERO) { "Quantity cannot be negative" }
        val f = fromUnit.trim().lowercase()
        val t = toUnit.trim().lowercase()
        if (f == t) return quantity.setScale(4, RoundingMode.HALF_UP)
        UNIT_CONVERSIONS[f to t]?.let { return quantity.multiply(it, MC).setScale(4, RoundingMode.HALF_UP) }
        UNIT_CONVERSIONS[t to f]?.let { return quantity.divide(it, MC).setScale(4, RoundingMode.HALF_UP) }
        throw IllegalArgumentException("Incompatible units: $fromUnit -> $toUnit")
    }

    // --- 2. Inflation & Weighted Price Baskets ---

    fun calculatePeriodInflationPct(currentIndex: BigDecimal, previousIndex: BigDecimal): BigDecimal? {
        if (previousIndex <= BigDecimal.ZERO) return null
        return pct4(
            currentIndex.subtract(previousIndex)
                .divide(previousIndex, MC)
                .multiply(BigDecimal(100))
        )
    }

    fun calculateCumulativeInflationPct(monthlyRatesPct: List<BigDecimal>): BigDecimal {
        var factor = BigDecimal.ONE
        for (rate in monthlyRatesPct) {
            val multiplier = BigDecimal.ONE.add(rate.divide(BigDecimal(100), MC))
            factor = factor.multiply(multiplier, MC)
        }
        return pct4(factor.subtract(BigDecimal.ONE).multiply(BigDecimal(100)))
    }

    data class BasketLineInput(
        val name: String,
        val quantity: BigDecimal,
        val weight: BigDecimal,
        val basePrice: BigDecimal,
        val currentPrice: BigDecimal
    )

    data class BasketLineBreakdown(
        val name: String,
        val baseContribution: BigDecimal,
        val currentContribution: BigDecimal,
        val changePct: BigDecimal?,
        val shareOfTotalIncreasePct: BigDecimal?
    )

    data class BasketCalculationResult(
        val baseCost: BigDecimal,
        val currentCost: BigDecimal,
        val indexValue: BigDecimal?,
        val inflationPct: BigDecimal?,
        val breakdown: List<BasketLineBreakdown>
    )

    fun calculateWeightedBasket(lines: List<BasketLineInput>): BasketCalculationResult {
        require(lines.isNotEmpty()) { "Price basket must contain at least one item" }
        var baseTotal = BigDecimal.ZERO
        var currTotal = BigDecimal.ZERO
        val raw = lines.map { item ->
            require(item.quantity >= BigDecimal.ZERO && item.weight >= BigDecimal.ZERO) {
                "Basket quantity and weight must be non-negative"
            }
            require(item.basePrice >= BigDecimal.ZERO && item.currentPrice >= BigDecimal.ZERO) {
                "Basket prices must be non-negative"
            }
            val factor = item.quantity.multiply(item.weight, MC)
            val baseContrib = factor.multiply(item.basePrice, MC)
            val currContrib = factor.multiply(item.currentPrice, MC)
            baseTotal = baseTotal.add(baseContrib)
            currTotal = currTotal.add(currContrib)
            Triple(item, baseContrib, currContrib)
        }
        val totalDelta = currTotal.subtract(baseTotal)
        val index = if (baseTotal > BigDecimal.ZERO) {
            pct4(currTotal.divide(baseTotal, MC).multiply(BigDecimal(100)))
        } else null
        val infl = if (baseTotal > BigDecimal.ZERO) {
            pct4(totalDelta.divide(baseTotal, MC).multiply(BigDecimal(100)))
        } else null

        val breakdown = raw.map { (item, baseC, currC) ->
            val delta = currC.subtract(baseC)
            val itemPct = if (baseC > BigDecimal.ZERO) pct4(delta.divide(baseC, MC).multiply(BigDecimal(100))) else null
            val share = if (totalDelta.compareTo(BigDecimal.ZERO) != 0) {
                pct4(delta.divide(totalDelta, MC).multiply(BigDecimal(100)))
            } else null
            BasketLineBreakdown(
                name = item.name,
                baseContribution = money(baseC),
                currentContribution = money(currC),
                changePct = itemPct,
                shareOfTotalIncreasePct = share
            )
        }
        return BasketCalculationResult(
            baseCost = money(baseTotal),
            currentCost = money(currTotal),
            indexValue = index,
            inflationPct = infl,
            breakdown = breakdown
        )
    }

    // --- 3. Costing (All 8 Management Accounting Methods), Pricing, CVP, ABC, Risk & Spec #45 Scenarios ---

    val ALL_COSTING_METHODS = listOf(
        "actual", "standard", "target", "abc",
        "job_order", "process", "absorption", "variable"
    )

    data class CostingInput(
        val directMaterial: BigDecimal,
        val indirectMaterial: BigDecimal = BigDecimal.ZERO,
        val directLabor: BigDecimal,
        val indirectLabor: BigDecimal = BigDecimal.ZERO,
        val overhead: BigDecimal,
        val energyFuel: BigDecimal = BigDecimal.ZERO,
        val packaging: BigDecimal = BigDecimal.ZERO,
        val transport: BigDecimal = BigDecimal.ZERO,
        val wasteScrap: BigDecimal = BigDecimal.ZERO,
        val fixedCosts: BigDecimal = BigDecimal.ZERO,
        val goodUnits: BigDecimal,
        val sellingPrice: BigDecimal
    )

    data class CostingResult(
        val totalCost: BigDecimal,
        val unitCost: BigDecimal,
        val revenue: BigDecimal,
        val grossProfit: BigDecimal,
        val netProfit: BigDecimal,
        val profitMarginPct: BigDecimal?,
        val markupPct: BigDecimal?,
        val contributionPerUnit: BigDecimal,
        val breakEvenUnits: BigDecimal?,
        val breakEvenRevenue: BigDecimal?,
        val marginOfSafetyPct: BigDecimal?
    )

    data class MethodCostingOutcome(
        val method: String,
        val coreResult: CostingResult,
        val targetAllowableUnitCost: BigDecimal?,
        val targetCostGapPerUnit: BigDecimal?,
        val fixedPerUnit: BigDecimal,
        val variableUnitCost: BigDecimal,
        val effectiveUnitsUsed: BigDecimal,
        val methodNote: String
    )

    fun calculateCosting(input: CostingInput): CostingResult {
        val components = listOf(
            input.directMaterial, input.indirectMaterial, input.directLabor,
            input.indirectLabor, input.overhead, input.energyFuel,
            input.packaging, input.transport, input.wasteScrap
        )
        require(components.all { it >= BigDecimal.ZERO }) { "Cost components cannot be negative" }
        require(input.goodUnits > BigDecimal.ZERO) { "Good saleable units must be greater than zero" }
        require(input.sellingPrice >= BigDecimal.ZERO) { "Selling price cannot be negative" }
        require(input.fixedCosts >= BigDecimal.ZERO) { "Fixed costs cannot be negative" }

        val totalCost = money(components.fold(BigDecimal.ZERO, BigDecimal::add))
        val unitCost = money(totalCost.divide(input.goodUnits, MC))
        val revenue = money(input.sellingPrice.multiply(input.goodUnits, MC))
        val grossProfit = money(revenue.subtract(totalCost))
        val netProfit = money(grossProfit.subtract(input.fixedCosts))

        val marginPct = if (input.sellingPrice > BigDecimal.ZERO) {
            pct4(input.sellingPrice.subtract(unitCost).divide(input.sellingPrice, MC).multiply(BigDecimal(100)))
        } else null

        val markupPct = if (unitCost > BigDecimal.ZERO) {
            pct4(input.sellingPrice.subtract(unitCost).divide(unitCost, MC).multiply(BigDecimal(100)))
        } else null

        val contributionPerUnit = money(input.sellingPrice.subtract(unitCost))
        val totalFixed = if (input.fixedCosts > BigDecimal.ZERO) input.fixedCosts else input.overhead
        val beUnits = if (contributionPerUnit > BigDecimal.ZERO && totalFixed >= BigDecimal.ZERO) {
            totalFixed.divide(contributionPerUnit, 0, RoundingMode.CEILING)
        } else null
        val beRevenue = beUnits?.let { money(it.multiply(input.sellingPrice, MC)) }
        val mosPct = if (beUnits != null && input.goodUnits > BigDecimal.ZERO) {
            pct4(input.goodUnits.subtract(beUnits).divide(input.goodUnits, MC).multiply(BigDecimal(100)))
        } else null

        return CostingResult(
            totalCost = totalCost,
            unitCost = unitCost,
            revenue = revenue,
            grossProfit = grossProfit,
            netProfit = netProfit,
            profitMarginPct = marginPct,
            markupPct = markupPct,
            contributionPerUnit = contributionPerUnit,
            breakEvenUnits = beUnits,
            breakEvenRevenue = beRevenue,
            marginOfSafetyPct = mosPct
        )
    }

    /**
     * Evaluates all 8 management accounting methods deterministically:
     * - actual: historical incurred variable + production overhead
     * - standard: benchmark budget unit allowance compared against actual
     * - target: computes allowable target cost = SellingPrice * (1 - targetMarginPct) and cost gap
     * - abc: activity-based driver pool allocation via overhead
     * - job_order: specific job/batch total and job-batch unit cost
     * - process: continuous process equivalent units costing (uses completedEquivUnits if > 0)
     * - absorption: includes fixed manufacturing overhead in inventoriable total/unit cost
     * - variable: excludes fixed costs from inventoriable unit cost, computing true contribution margin
     */
    fun calculateCostingByMethod(
        method: String,
        input: CostingInput,
        targetMarginPct: BigDecimal = BigDecimal("20"),
        completedEquivUnits: BigDecimal? = null
    ): MethodCostingOutcome {
        val m = method.trim().lowercase()
        require(m in ALL_COSTING_METHODS) { "Unsupported costing method: $method" }

        val variableComponents = listOf(
            input.directMaterial, input.indirectMaterial, input.directLabor,
            input.indirectLabor, input.overhead, input.energyFuel,
            input.packaging, input.transport, input.wasteScrap
        ).fold(BigDecimal.ZERO, BigDecimal::add)

        val effectiveUnits = if (m == "process" && completedEquivUnits != null && completedEquivUnits > BigDecimal.ZERO) {
            completedEquivUnits
        } else {
            input.goodUnits
        }

        val variableUnit = money(variableComponents.divide(effectiveUnits, MC))
        val fixedPerUnit = money(input.fixedCosts.divide(effectiveUnits, MC))

        val adjustedInput = when (m) {
            "absorption" -> input.copy(
                overhead = input.overhead.add(input.fixedCosts),
                fixedCosts = BigDecimal.ZERO,
                goodUnits = effectiveUnits
            )
            "process" -> input.copy(goodUnits = effectiveUnits)
            else -> input
        }

        val core = calculateCosting(adjustedInput)

        val targetAllowable = if (m == "target") {
            val tm = targetMarginPct.divide(BigDecimal(100), MC).coerceIn(BigDecimal.ZERO, BigDecimal("0.99"))
            money(input.sellingPrice.multiply(BigDecimal.ONE.subtract(tm), MC))
        } else null
        val targetGap = targetAllowable?.let { money(core.unitCost.subtract(it)) }

        val finalCore = when (m) {
            "variable" -> {
                val cmUnit = money(input.sellingPrice.subtract(variableUnit))
                val beUnits = if (cmUnit > BigDecimal.ZERO && input.fixedCosts >= BigDecimal.ZERO) {
                    input.fixedCosts.divide(cmUnit, 0, RoundingMode.CEILING)
                } else null
                val beRev = beUnits?.let { money(it.multiply(input.sellingPrice, MC)) }
                val mos = if (beUnits != null && effectiveUnits > BigDecimal.ZERO) {
                    pct4(effectiveUnits.subtract(beUnits).divide(effectiveUnits, MC).multiply(BigDecimal(100)))
                } else null
                core.copy(
                    contributionPerUnit = cmUnit,
                    breakEvenUnits = beUnits,
                    breakEvenRevenue = beRev,
                    marginOfSafetyPct = mos
                )
            }
            "absorption" -> {
                // Re-derive netProfit preserving fixed costs absorbed into unit cost
                val beUnits = if (core.contributionPerUnit > BigDecimal.ZERO && input.fixedCosts >= BigDecimal.ZERO) {
                    input.fixedCosts.divide(core.contributionPerUnit, 0, RoundingMode.CEILING)
                } else null
                core.copy(breakEvenUnits = beUnits)
            }
            else -> core
        }

        val note = when (m) {
            "actual" -> "Actual Costing: Sum of all incurred direct/indirect variable costs ÷ good units."
            "standard" -> "Standard Costing: Compare unit cost against established Standard Cost Sheet."
            "target" -> "Target Costing: Allowable unit cost = Price × (1 - ${pct4(targetMarginPct)}%); Cost gap = Unit Cost - Allowable."
            "abc" -> "Activity-Based Costing (ABC): Allocates indirect activity pools via measurable drivers."
            "job_order" -> "Job Order Costing: Accumulates direct material, labor, and overhead per batch/order."
            "process" -> "Process Costing: Distributes conversion & material costs across $effectiveUnits equivalent units."
            "absorption" -> "Absorption Costing: Absorbs fixed overhead ($fixedPerUnit/unit) into inventoriable unit cost."
            "variable" -> "Variable (Direct) Costing: Unit cost excludes fixed period costs ($fixedPerUnit/unit) for clear CVP contribution."
            else -> "Standard calculation"
        }

        return MethodCostingOutcome(
            method = m,
            coreResult = finalCore,
            targetAllowableUnitCost = targetAllowable,
            targetCostGapPerUnit = targetGap,
            fixedPerUnit = fixedPerUnit,
            variableUnitCost = variableUnit,
            effectiveUnitsUsed = effectiveUnits,
            methodNote = note
        )
    }

    fun targetPriceByMargin(unitCost: BigDecimal, targetMarginPct: BigDecimal): BigDecimal {
        require(unitCost >= BigDecimal.ZERO) { "Unit cost cannot be negative" }
        val m = targetMarginPct.divide(BigDecimal(100), MC)
        require(m >= BigDecimal.ZERO && m < BigDecimal.ONE) { "Target margin must be in [0%, 100%)" }
        return money(unitCost.divide(BigDecimal.ONE.subtract(m), MC))
    }

    fun targetPriceByMarkup(unitCost: BigDecimal, targetMarkupPct: BigDecimal): BigDecimal {
        require(unitCost >= BigDecimal.ZERO && targetMarkupPct >= BigDecimal.ZERO) {
            "Cost and markup must be non-negative"
        }
        val m = targetMarkupPct.divide(BigDecimal(100), MC)
        return money(unitCost.multiply(BigDecimal.ONE.add(m), MC))
    }

    fun allocateAbcCost(poolCost: BigDecimal, driverQuantities: List<BigDecimal>, selectedDriver: BigDecimal): BigDecimal {
        require(poolCost >= BigDecimal.ZERO && selectedDriver >= BigDecimal.ZERO) { "Values cannot be negative" }
        val totalDriver = driverQuantities.fold(BigDecimal.ZERO, BigDecimal::add)
        require(totalDriver > BigDecimal.ZERO) { "Total allocation driver must be greater than zero" }
        require(selectedDriver <= totalDriver) { "Selected driver cannot exceed total driver" }
        return money(poolCost.multiply(selectedDriver, MC).divide(totalDriver, MC))
    }

    data class FinancialRiskAssessment(
        val level: String, // low, medium, high
        val ruleName: String,
        val actualValue: String,
        val thresholdValue: String,
        val explanation: String,
        val modelVersion: String = MODEL_VERSION
    )

    fun evaluateFinancialRisk(
        profitMarginPct: BigDecimal?,
        inflationRatePct: BigDecimal? = null,
        totalDebt: BigDecimal = BigDecimal.ZERO,
        totalEquity: BigDecimal = BigDecimal.ZERO
    ): FinancialRiskAssessment {
        val margin = profitMarginPct ?: BigDecimal.ZERO
        val inflation = inflationRatePct ?: BigDecimal.ZERO
        val leverageRatio = if (totalEquity > BigDecimal.ZERO) {
            totalDebt.divide(totalEquity, MC).setScale(2, RoundingMode.HALF_UP)
        } else null

        return when {
            margin < BigDecimal.ZERO -> FinancialRiskAssessment(
                level = "high",
                ruleName = "NEGATIVE_MARGIN_RULE",
                actualValue = "${pct4(margin)}%",
                thresholdValue = ">= 0.00%",
                explanation = "Selling price is below unit cost; each unit sold erodes equity."
            )
            margin < BigDecimal("10") -> FinancialRiskAssessment(
                level = "high",
                ruleName = "THIN_MARGIN_UNDER_10_PCT",
                actualValue = "${pct4(margin)}%",
                thresholdValue = ">= 10.00%",
                explanation = "Profit margin is below 10%; vulnerable to raw material or FX shocks."
            )
            inflation >= BigDecimal("15") -> FinancialRiskAssessment(
                level = "high",
                ruleName = "INFLATION_SURGE_RISK",
                actualValue = "${pct4(inflation)}%",
                thresholdValue = "< 15.00%",
                explanation = "High period inflation (>= 15%) risks replacement-cost capital erosion."
            )
            leverageRatio != null && leverageRatio > BigDecimal("2.0") -> FinancialRiskAssessment(
                level = "medium",
                ruleName = "ELEVATED_LEVERAGE_RULE",
                actualValue = "${leverageRatio}x",
                thresholdValue = "<= 2.00x",
                explanation = "Debt-to-equity exceeds 2.0x; monitor financing costs closely."
            )
            margin < BigDecimal("20") || inflation >= BigDecimal("7") -> FinancialRiskAssessment(
                level = "medium",
                ruleName = "MODERATE_MARGIN_OR_COST_PRESSURE",
                actualValue = "Margin ${pct4(margin)}%",
                thresholdValue = ">= 20.00% & Inflation < 7%",
                explanation = "Margin buffer is moderate; review supplier prices and basket index."
            )
            else -> FinancialRiskAssessment(
                level = "low",
                ruleName = "HEALTHY_MARGIN_AND_STABILITY",
                actualValue = "Margin ${pct4(margin)}%",
                thresholdValue = ">= 20.00%",
                explanation = "Margin comfortably covers operating costs and modest cost fluctuations."
            )
        }
    }

    data class SpecScenarioPreset(
        val id: String,
        val labelEn: String,
        val labelAr: String,
        val materialPct: String,
        val laborPct: String,
        val overheadPct: String,
        val pricePct: String,
        val fxPct: String,
        val volumePct: String
    )

    val SPEC_SCENARIO_PRESETS = listOf(
        SpecScenarioPreset("base", "Base Scenario", "السيناريو الأساسي", "0", "0", "0", "0", "0", "0"),
        SpecScenarioPreset("inflation_10", "Inflation +10%", "تضخم +10%", "10", "10", "10", "0", "0", "0"),
        SpecScenarioPreset("raw_mat_15", "Raw Material +15%", "المواد الخام +15%", "15", "0", "0", "0", "0", "0"),
        SpecScenarioPreset("raw_mat_20", "Raw Material +20%", "المواد الخام +20%", "20", "0", "0", "0", "0", "0"),
        SpecScenarioPreset("fx_10", "FX Shock +10%", "صدمة صرف +10%", "0", "0", "0", "0", "10", "0"),
        SpecScenarioPreset("fx_15", "FX Shock +15%", "صدمة صرف +15%", "0", "0", "0", "0", "15", "0"),
        SpecScenarioPreset("price_cut_10", "Price Reduction -10%", "خفض السعر -10%", "0", "0", "0", "-10", "0", "0"),
        SpecScenarioPreset("volume_up_20", "Volume Increase +20%", "زيادة الحجم +20%", "0", "0", "0", "0", "0", "20")
    )

    data class ScenarioResult(
        val baseTotalCost: BigDecimal,
        val scenarioTotalCost: BigDecimal,
        val baseProfit: BigDecimal,
        val scenarioProfit: BigDecimal,
        val costDelta: BigDecimal,
        val profitDelta: BigDecimal,
        val disclaimer: String = "Scenario outcome — not a prediction"
    )

    fun runScenario(
        baseMaterial: BigDecimal,
        baseLabor: BigDecimal,
        baseOverheadAndOther: BigDecimal,
        baseRevenue: BigDecimal,
        materialChangePct: BigDecimal,
        laborChangePct: BigDecimal,
        overheadChangePct: BigDecimal,
        priceChangePct: BigDecimal
    ): ScenarioResult = runExtendedScenario(
        baseMaterial = baseMaterial,
        baseLabor = baseLabor,
        baseOverheadAndOther = baseOverheadAndOther,
        baseRevenue = baseRevenue,
        materialChangePct = materialChangePct,
        laborChangePct = laborChangePct,
        overheadChangePct = overheadChangePct,
        priceChangePct = priceChangePct,
        fxChangePct = BigDecimal.ZERO,
        volumeChangePct = BigDecimal.ZERO
    )

    fun runExtendedScenario(
        baseMaterial: BigDecimal,
        baseLabor: BigDecimal,
        baseOverheadAndOther: BigDecimal,
        baseRevenue: BigDecimal,
        materialChangePct: BigDecimal,
        laborChangePct: BigDecimal,
        overheadChangePct: BigDecimal,
        priceChangePct: BigDecimal,
        fxChangePct: BigDecimal = BigDecimal.ZERO,
        volumeChangePct: BigDecimal = BigDecimal.ZERO
    ): ScenarioResult {
        val pctMin = BigDecimal("-100")
        require(
            materialChangePct >= pctMin &&
                laborChangePct >= pctMin &&
                overheadChangePct >= pctMin &&
                priceChangePct >= pctMin &&
                fxChangePct >= pctMin &&
                volumeChangePct >= pctMin
        ) {
            "Percentage changes cannot be below -100%"
        }
        fun applyPct(base: BigDecimal, pct: BigDecimal): BigDecimal =
            base.multiply(BigDecimal.ONE.add(pct.divide(BigDecimal(100), MC)), MC)

        val volFactor = BigDecimal.ONE.add(volumeChangePct.divide(BigDecimal(100), MC))
        val fxFactor = BigDecimal.ONE.add(fxChangePct.divide(BigDecimal(100), MC))

        val baseTotal = money(baseMaterial.add(baseLabor).add(baseOverheadAndOther))
        val scenMat = applyPct(baseMaterial, materialChangePct).multiply(volFactor, MC).multiply(fxFactor, MC)
        val scenLab = applyPct(baseLabor, laborChangePct).multiply(volFactor, MC)
        val scenOvh = applyPct(baseOverheadAndOther, overheadChangePct).multiply(fxFactor, MC)
        val scenTotal = money(scenMat.add(scenLab).add(scenOvh))
        val scenRevenue = money(applyPct(baseRevenue, priceChangePct).multiply(volFactor, MC))
        val baseProfit = money(baseRevenue.subtract(baseTotal))
        val scenProfit = money(scenRevenue.subtract(scenTotal))
        return ScenarioResult(
            baseTotalCost = baseTotal,
            scenarioTotalCost = scenTotal,
            baseProfit = baseProfit,
            scenarioProfit = scenProfit,
            costDelta = money(scenTotal.subtract(baseTotal)),
            profitDelta = money(scenProfit.subtract(baseProfit)),
            disclaimer = "Scenario outcome — not a prediction"
        )
    }

    // --- 4. Historical Foreign Exchange ---

    fun convertCurrency(amount: BigDecimal, historicalRate: BigDecimal): BigDecimal {
        require(historicalRate > BigDecimal.ZERO) { "Exchange rate must be positive" }
        return money(amount.multiply(historicalRate, MC))
    }

    // --- 5. Double-Entry Accounting, Trial Balance & Financial Statements ---

    data class JournalLineInput(
        val accountCode: String,
        val accountName: String,
        val debit: BigDecimal,
        val credit: BigDecimal
    )

    fun validateJournalBalance(lines: List<JournalLineInput>): Pair<BigDecimal, BigDecimal> {
        require(lines.size >= 2) { "Journal entry requires at least 2 lines" }
        var totalDebit = BigDecimal.ZERO
        var totalCredit = BigDecimal.ZERO
        for (line in lines) {
            require(line.debit >= BigDecimal.ZERO && line.credit >= BigDecimal.ZERO) {
                "Debit and credit amounts cannot be negative"
            }
            require((line.debit > BigDecimal.ZERO) xor (line.credit > BigDecimal.ZERO)) {
                "Each line must have either a positive debit or a positive credit"
            }
            totalDebit = totalDebit.add(line.debit)
            totalCredit = totalCredit.add(line.credit)
        }
        val d = money(totalDebit)
        val c = money(totalCredit)
        require(d.compareTo(c) == 0) { "Journal entry is out of balance: Debits ($d) != Credits ($c)" }
        return d to c
    }

    data class AccountInput(
        val code: String,
        val name: String,
        val accountType: String,
        val subType: String,
        val normalBalance: String,
        val openingBalance: BigDecimal
    )

    data class TrialBalanceRow(
        val code: String,
        val name: String,
        val accountType: String,
        val debitBalance: BigDecimal,
        val creditBalance: BigDecimal,
        val netSignedBalance: BigDecimal
    )

    data class FinancialStatementsReport(
        val trialBalance: List<TrialBalanceRow>,
        val totalTrialDebits: BigDecimal,
        val totalTrialCredits: BigDecimal,
        val isTrialBalanced: Boolean,
        val revenue: BigDecimal,
        val cogs: BigDecimal,
        val grossProfit: BigDecimal,
        val operatingExpenses: BigDecimal,
        val operatingProfit: BigDecimal,
        val interestAndOther: BigDecimal,
        val netProfit: BigDecimal,
        val cashAndBank: BigDecimal,
        val receivables: BigDecimal,
        val inventoryBookValue: BigDecimal,
        val currentAssets: BigDecimal,
        val nonCurrentAssets: BigDecimal,
        val totalAssets: BigDecimal,
        val payables: BigDecimal,
        val currentLiabilities: BigDecimal,
        val nonCurrentLiabilities: BigDecimal,
        val totalLiabilities: BigDecimal,
        val totalEquityWithNetIncome: BigDecimal,
        val totalLiabilitiesAndEquity: BigDecimal,
        val isAccountingEquationBalanced: Boolean,
        val operatingCashFlow: BigDecimal,
        val investingCashFlow: BigDecimal,
        val financingCashFlow: BigDecimal,
        val netCashChange: BigDecimal,
        val openingCash: BigDecimal,
        val closingCash: BigDecimal
    )

    fun generateFinancialStatements(
        accounts: List<AccountInput>,
        postedEntries: List<List<JournalLineInput>>
    ): FinancialStatementsReport {
        val debitMap = mutableMapOf<String, BigDecimal>()
        val creditMap = mutableMapOf<String, BigDecimal>()

        for (lines in postedEntries) {
            for (l in lines) {
                debitMap[l.accountCode] = (debitMap[l.accountCode] ?: BigDecimal.ZERO).add(l.debit)
                creditMap[l.accountCode] = (creditMap[l.accountCode] ?: BigDecimal.ZERO).add(l.credit)
            }
        }

        val tbRows = accounts.map { acc ->
            val movDr = debitMap[acc.code] ?: BigDecimal.ZERO
            val movCr = creditMap[acc.code] ?: BigDecimal.ZERO
            val signed = if (acc.normalBalance == "debit") {
                acc.openingBalance.add(movDr).subtract(movCr)
            } else {
                acc.openingBalance.add(movCr).subtract(movDr)
            }
            val (drBal, crBal) = if (acc.normalBalance == "debit") {
                if (signed >= BigDecimal.ZERO) money(signed) to BigDecimal.ZERO.setScale(2)
                else BigDecimal.ZERO.setScale(2) to money(signed.abs())
            } else {
                if (signed >= BigDecimal.ZERO) BigDecimal.ZERO.setScale(2) to money(signed)
                else money(signed.abs()) to BigDecimal.ZERO.setScale(2)
            }
            TrialBalanceRow(acc.code, acc.name, acc.accountType, drBal, crBal, money(signed))
        }

        val totDr = money(tbRows.fold(BigDecimal.ZERO) { a, r -> a.add(r.debitBalance) })
        val totCr = money(tbRows.fold(BigDecimal.ZERO) { a, r -> a.add(r.creditBalance) })

        fun sumBySubType(subType: String): BigDecimal =
            accounts.filter { it.subType == subType }.fold(BigDecimal.ZERO) { acc, a ->
                val row = tbRows.first { it.code == a.code }
                acc.add(row.netSignedBalance)
            }

        fun sumByType(type: String): BigDecimal =
            accounts.filter { it.accountType == type }.fold(BigDecimal.ZERO) { acc, a ->
                val row = tbRows.first { it.code == a.code }
                acc.add(row.netSignedBalance)
            }

        val rev = money(sumByType("revenue"))
        val cogs = money(sumByType("cogs"))
        val grossProfit = money(rev.subtract(cogs))
        val opEx = money(accounts.filter { it.accountType == "expense" && it.subType == "operating_expense" }
            .fold(BigDecimal.ZERO) { acc, a -> acc.add(tbRows.first { it.code == a.code }.netSignedBalance) })
        val opProfit = money(grossProfit.subtract(opEx))
        val interestExp = money(accounts.filter { it.accountType == "expense" && it.subType == "interest_expense" }
            .fold(BigDecimal.ZERO) { acc, a -> acc.add(tbRows.first { it.code == a.code }.netSignedBalance) })
        val otherExp = money(accounts.filter { it.accountType == "expense" && it.subType == "other_income_expense" }
            .fold(BigDecimal.ZERO) { acc, a -> acc.add(tbRows.first { it.code == a.code }.netSignedBalance) })
        val interestAndOther = money(interestExp.add(otherExp))
        val netProfit = money(opProfit.subtract(interestAndOther))

        val cash = money(sumBySubType("cash_bank"))
        val ar = money(sumBySubType("receivable"))
        val inv = money(sumBySubType("inventory"))
        val otherCurrAsset = money(sumBySubType("current_asset"))
        val currAssets = money(cash.add(ar).add(inv).add(otherCurrAsset))
        val nonCurrAssets = money(sumBySubType("non_current_asset"))
        val totalAssets = money(currAssets.add(nonCurrAssets))

        val ap = money(sumBySubType("payable"))
        val otherCurrLiab = money(sumBySubType("current_liability"))
        val currLiab = money(ap.add(otherCurrLiab))
        val nonCurrLiab = money(sumBySubType("non_current_liability"))
        val totalLiab = money(currLiab.add(nonCurrLiab))

        val baseEquity = money(sumByType("equity"))
        val totalEquity = money(baseEquity.add(netProfit))
        val totLiabEq = money(totalLiab.add(totalEquity))

        val openingCash = money(accounts.filter { it.subType == "cash_bank" }.fold(BigDecimal.ZERO) { a, acc -> a.add(acc.openingBalance) })
        val netCashChange = money(cash.subtract(openingCash))
        val cashCodes = accounts.filter { it.subType == "cash_bank" }.map { it.code }.toSet()
        val nonCurrCodes = accounts.filter { it.subType == "non_current_asset" }.map { it.code }.toSet()
        val finCodes = accounts.filter { it.subType == "non_current_liability" || it.accountType == "equity" }.map { it.code }.toSet()

        var invCf = BigDecimal.ZERO
        var finCf = BigDecimal.ZERO
        for (lines in postedEntries) {
            val cashDelta = lines.filter { it.accountCode in cashCodes }
                .fold(BigDecimal.ZERO) { a, l -> a.add(l.debit).subtract(l.credit) }
            if (cashDelta.compareTo(BigDecimal.ZERO) != 0) {
                when {
                    lines.any { it.accountCode in nonCurrCodes } -> invCf = invCf.add(cashDelta)
                    lines.any { it.accountCode in finCodes } -> finCf = finCf.add(cashDelta)
                }
            }
        }
        val investingCash = money(invCf)
        val financingCash = money(finCf)
        val operatingCash = money(netCashChange.subtract(investingCash).subtract(financingCash))

        return FinancialStatementsReport(
            trialBalance = tbRows,
            totalTrialDebits = totDr,
            totalTrialCredits = totCr,
            isTrialBalanced = totDr.compareTo(totCr) == 0,
            revenue = rev,
            cogs = cogs,
            grossProfit = grossProfit,
            operatingExpenses = opEx,
            operatingProfit = opProfit,
            interestAndOther = interestAndOther,
            netProfit = netProfit,
            cashAndBank = cash,
            receivables = ar,
            inventoryBookValue = inv,
            currentAssets = currAssets,
            nonCurrentAssets = nonCurrAssets,
            totalAssets = totalAssets,
            payables = ap,
            currentLiabilities = currLiab,
            nonCurrentLiabilities = nonCurrLiab,
            totalLiabilities = totalLiab,
            totalEquityWithNetIncome = totalEquity,
            totalLiabilitiesAndEquity = totLiabEq,
            isAccountingEquationBalanced = totalAssets.compareTo(totLiabEq) == 0,
            operatingCashFlow = operatingCash,
            investingCashFlow = investingCash,
            financingCashFlow = financingCash,
            netCashChange = netCashChange,
            openingCash = openingCash,
            closingCash = cash
        )
    }

    // --- 6. Financial Ratios (With Missing-Data Guards) ---

    data class FinancialRatioResult(
        val key: String,
        val category: String,
        val formula: String,
        val inputsSummary: String,
        val value: BigDecimal?,
        val unitSuffix: String,
        val explanation: String,
        val missingDataWarning: String?
    )

    fun computeFinancialRatios(
        statements: FinancialStatementsReport,
        interestExpense: BigDecimal = BigDecimal.ZERO
    ): List<FinancialRatioResult> {
        fun safeDiv(num: BigDecimal, den: BigDecimal, multiply100: Boolean = false, label: String): Pair<BigDecimal?, String?> {
            if (den <= BigDecimal.ZERO) return null to "Missing or non-positive denominator ($label = $den)"
            val raw = num.divide(den, MC)
            val finalVal = if (multiply100) pct4(raw.multiply(BigDecimal(100))) else raw.setScale(2, RoundingMode.HALF_UP)
            return finalVal to null
        }

        val quickAssets = statements.cashAndBank.add(statements.receivables)
        val (curRatio, curWarn) = safeDiv(statements.currentAssets, statements.currentLiabilities, false, "Current Liabilities")
        val (qkRatio, qkWarn) = safeDiv(quickAssets, statements.currentLiabilities, false, "Current Liabilities")
        val (cshRatio, cshWarn) = safeDiv(statements.cashAndBank, statements.currentLiabilities, false, "Current Liabilities")

        val (gm, gmWarn) = safeDiv(statements.grossProfit, statements.revenue, true, "Revenue")
        val (om, omWarn) = safeDiv(statements.operatingProfit, statements.revenue, true, "Revenue")
        val (nm, nmWarn) = safeDiv(statements.netProfit, statements.revenue, true, "Revenue")
        val (roa, roaWarn) = safeDiv(statements.netProfit, statements.totalAssets, true, "Total Assets")
        val (roe, roeWarn) = safeDiv(statements.netProfit, statements.totalEquityWithNetIncome, true, "Equity")

        val (invTurn, invTurnWarn) = safeDiv(statements.cogs, statements.inventoryBookValue, false, "Inventory")
        val invDays = if (invTurn != null && invTurn > BigDecimal.ZERO) BigDecimal(365).divide(invTurn, 1, RoundingMode.HALF_UP) else null
        val (arTurn, arTurnWarn) = safeDiv(statements.revenue, statements.receivables, false, "Receivables")
        val arDays = if (arTurn != null && arTurn > BigDecimal.ZERO) BigDecimal(365).divide(arTurn, 1, RoundingMode.HALF_UP) else null
        val (apTurn, apTurnWarn) = safeDiv(statements.cogs, statements.payables, false, "Payables")
        val apDays = if (apTurn != null && apTurn > BigDecimal.ZERO) BigDecimal(365).divide(apTurn, 1, RoundingMode.HALF_UP) else null
        val (astTurn, astWarn) = safeDiv(statements.revenue, statements.totalAssets, false, "Total Assets")

        val (debtRatio, debtWarn) = safeDiv(statements.totalLiabilities, statements.totalAssets, true, "Total Assets")
        val (dte, dteWarn) = safeDiv(statements.totalLiabilities, statements.totalEquityWithNetIncome, false, "Equity")
        val (intCov, intWarn) = safeDiv(statements.operatingProfit, interestExpense, false, "Interest Expense")

        return listOf(
            FinancialRatioResult("current_ratio", "Liquidity", "Current Assets / Current Liabilities", "${statements.currentAssets} / ${statements.currentLiabilities}", curRatio, "x", "Measures short-term obligations coverage.", curWarn),
            FinancialRatioResult("quick_ratio", "Liquidity", "(Cash + Receivables) / Current Liabilities", "$quickAssets / ${statements.currentLiabilities}", qkRatio, "x", "Measures immediate liquidity excluding inventory.", qkWarn),
            FinancialRatioResult("cash_ratio", "Liquidity", "Cash / Current Liabilities", "${statements.cashAndBank} / ${statements.currentLiabilities}", cshRatio, "x", "Measures pure cash coverage of current debt.", cshWarn),

            FinancialRatioResult("gross_margin", "Profitability", "(Gross Profit / Revenue) × 100", "${statements.grossProfit} / ${statements.revenue}", gm, "%", "Share of revenue retained after direct COGS.", gmWarn),
            FinancialRatioResult("operating_margin", "Profitability", "(Operating Profit / Revenue) × 100", "${statements.operatingProfit} / ${statements.revenue}", om, "%", "Core operating profitability.", omWarn),
            FinancialRatioResult("net_margin", "Profitability", "(Net Profit / Revenue) × 100", "${statements.netProfit} / ${statements.revenue}", nm, "%", "Bottom-line profit per unit of revenue.", nmWarn),
            FinancialRatioResult("roa", "Profitability", "(Net Profit / Total Assets) × 100", "${statements.netProfit} / ${statements.totalAssets}", roa, "%", "Return generated on total organizational assets.", roaWarn),
            FinancialRatioResult("roe", "Profitability", "(Net Profit / Equity) × 100", "${statements.netProfit} / ${statements.totalEquityWithNetIncome}", roe, "%", "Return earned on shareholders' equity.", roeWarn),

            FinancialRatioResult("inventory_turnover", "Efficiency", "COGS / Inventory", "${statements.cogs} / ${statements.inventoryBookValue}", invTurn, "x", "Inventory cycles sold during the period.", invTurnWarn),
            FinancialRatioResult("inventory_days", "Efficiency", "365 / Inventory Turnover", "365 / ${invTurn ?: "N/A"}", invDays, "days", "Average days stock is held before sale.", invTurnWarn),
            FinancialRatioResult("receivables_turnover", "Efficiency", "Revenue / Receivables", "${statements.revenue} / ${statements.receivables}", arTurn, "x", "Collection frequency of customer credit.", arTurnWarn),
            FinancialRatioResult("receivables_days", "Efficiency", "365 / Receivables Turnover", "365 / ${arTurn ?: "N/A"}", arDays, "days", "Average days to collect receivables.", arTurnWarn),
            FinancialRatioResult("payables_turnover", "Efficiency", "COGS / Payables", "${statements.cogs} / ${statements.payables}", apTurn, "x", "Supplier payment frequency.", apTurnWarn),
            FinancialRatioResult("payables_days", "Efficiency", "365 / Payables Turnover", "365 / ${apTurn ?: "N/A"}", apDays, "days", "Average days taken to pay suppliers.", apTurnWarn),
            FinancialRatioResult("asset_turnover", "Efficiency", "Revenue / Total Assets", "${statements.revenue} / ${statements.totalAssets}", astTurn, "x", "Revenue generated per currency unit of assets.", astWarn),

            FinancialRatioResult("debt_ratio", "Leverage", "(Total Liabilities / Total Assets) × 100", "${statements.totalLiabilities} / ${statements.totalAssets}", debtRatio, "%", "Proportion of assets financed by liabilities.", debtWarn),
            FinancialRatioResult("debt_to_equity", "Leverage", "Total Liabilities / Total Equity", "${statements.totalLiabilities} / ${statements.totalEquityWithNetIncome}", dte, "x", "Financial leverage relative to equity.", dteWarn),
            FinancialRatioResult("interest_coverage", "Leverage", "Operating Profit / Interest Expense", "${statements.operatingProfit} / $interestExpense", intCov, "x", "Ability to service interest obligations.", intWarn)
        )
    }

    // --- 7. Budgets & Variances ---

    fun calculateBudgetVariance(planned: BigDecimal, actual: BigDecimal): Pair<BigDecimal, BigDecimal?> {
        val variance = money(actual.subtract(planned))
        val pct = if (planned.compareTo(BigDecimal.ZERO) != 0) {
            pct4(variance.divide(planned, MC).multiply(BigDecimal(100)))
        } else null
        return variance to pct
    }

    // --- 8. Inventory Valuation (FIFO & Moving Weighted Average + Replacement Cost) ---

    data class FifoLot(
        val lotDate: String,
        val quantity: BigDecimal,
        val unitCost: BigDecimal
    )

    data class InventoryValuationOutcome(
        val newQuantityOnHand: BigDecimal,
        val newTotalBookValue: BigDecimal,
        val newMovingAvgCost: BigDecimal,
        val movementTotalCost: BigDecimal,
        val movementUnitCost: BigDecimal,
        val remainingLots: List<FifoLot>
    )

    fun applyInventoryReceipt(
        currentQty: BigDecimal,
        currentBookValue: BigDecimal,
        currentLots: List<FifoLot>,
        receiptQty: BigDecimal,
        receiptUnitCost: BigDecimal,
        receiptDate: String
    ): InventoryValuationOutcome {
        require(receiptQty > BigDecimal.ZERO && receiptUnitCost >= BigDecimal.ZERO) {
            "Receipt quantity must be positive and unit cost non-negative"
        }
        val moveCost = money(receiptQty.multiply(receiptUnitCost, MC))
        val newQty = currentQty.add(receiptQty).setScale(4, RoundingMode.HALF_UP)
        val newBook = money(currentBookValue.add(moveCost))
        val newAvg = if (newQty > BigDecimal.ZERO) newBook.divide(newQty, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
        val updatedLots = currentLots + FifoLot(receiptDate, receiptQty.setScale(4, RoundingMode.HALF_UP), receiptUnitCost.setScale(4, RoundingMode.HALF_UP))
        return InventoryValuationOutcome(
            newQuantityOnHand = newQty,
            newTotalBookValue = newBook,
            newMovingAvgCost = newAvg,
            movementTotalCost = moveCost,
            movementUnitCost = receiptUnitCost.setScale(4, RoundingMode.HALF_UP),
            remainingLots = updatedLots
        )
    }

    fun applyInventoryIssue(
        valuationMethod: String,
        currentQty: BigDecimal,
        currentBookValue: BigDecimal,
        currentMovingAvg: BigDecimal,
        currentLots: List<FifoLot>,
        issueQty: BigDecimal
    ): InventoryValuationOutcome {
        require(issueQty > BigDecimal.ZERO) { "Issue quantity must be positive" }
        require(issueQty <= currentQty) { "Insufficient stock on hand ($currentQty) for issue ($issueQty)" }

        if (valuationMethod == "fifo") {
            var remainingToIssue = issueQty
            var costOfIssue = BigDecimal.ZERO
            val newLots = mutableListOf<FifoLot>()
            for (lot in currentLots) {
                if (remainingToIssue <= BigDecimal.ZERO) {
                    newLots.add(lot)
                    continue
                }
                if (lot.quantity <= remainingToIssue) {
                    costOfIssue = costOfIssue.add(lot.quantity.multiply(lot.unitCost, MC))
                    remainingToIssue = remainingToIssue.subtract(lot.quantity)
                } else {
                    costOfIssue = costOfIssue.add(remainingToIssue.multiply(lot.unitCost, MC))
                    newLots.add(lot.copy(quantity = lot.quantity.subtract(remainingToIssue).setScale(4, RoundingMode.HALF_UP)))
                    remainingToIssue = BigDecimal.ZERO
                }
            }
            val moveCost = money(costOfIssue)
            val newQty = currentQty.subtract(issueQty).setScale(4, RoundingMode.HALF_UP)
            val newBook = money(newLots.fold(BigDecimal.ZERO) { a, l -> a.add(l.quantity.multiply(l.unitCost, MC)) })
            val newAvg = if (newQty > BigDecimal.ZERO) newBook.divide(newQty, 4, RoundingMode.HALF_UP) else BigDecimal.ZERO
            val unitCost = money(moveCost.divide(issueQty, MC)).setScale(4, RoundingMode.HALF_UP)
            return InventoryValuationOutcome(newQty, newBook, newAvg, moveCost, unitCost, newLots)
        } else {
            val moveCost = money(issueQty.multiply(currentMovingAvg, MC))
            val newQty = currentQty.subtract(issueQty).setScale(4, RoundingMode.HALF_UP)
            val newBook = money(currentBookValue.subtract(moveCost).max(BigDecimal.ZERO))
            val newAvg = if (newQty > BigDecimal.ZERO) currentMovingAvg.setScale(4, RoundingMode.HALF_UP) else BigDecimal.ZERO
            return InventoryValuationOutcome(newQty, newBook, newAvg, moveCost, currentMovingAvg.setScale(4, RoundingMode.HALF_UP), currentLots)
        }
    }

    data class ReplacementCostAnalysis(
        val bookValue: BigDecimal,
        val currentReplacementCost: BigDecimal,
        val inflationAdjustedCost: BigDecimal,
        val replacementGap: BigDecimal,
        val capitalTiedUpPctIncrease: BigDecimal?
    )

    fun analyzeReplacementCost(
        quantityOnHand: BigDecimal,
        bookValue: BigDecimal,
        currentMarketPrice: BigDecimal,
        inflationFactor: BigDecimal = BigDecimal.ONE
    ): ReplacementCostAnalysis {
        val replacement = money(quantityOnHand.multiply(currentMarketPrice, MC))
        val inflationAdj = money(bookValue.multiply(inflationFactor, MC))
        val gap = money(replacement.subtract(bookValue))
        val pct = if (bookValue > BigDecimal.ZERO) pct4(gap.divide(bookValue, MC).multiply(BigDecimal(100))) else null
        return ReplacementCostAnalysis(
            bookValue = money(bookValue),
            currentReplacementCost = replacement,
            inflationAdjustedCost = inflationAdj,
            replacementGap = gap,
            capitalTiedUpPctIncrease = pct
        )
    }

    // --- 9. Safe CSV Import Parsing & Formula Injection Guard ---

    data class CsvProductRow(
        val rowNumber: Int,
        val name: String,
        val sku: String,
        val category: String,
        val unit: String,
        val currency: String,
        val currentPrice: BigDecimal,
        val location: String,
        val error: String? = null
    )

    fun isFormulaInjection(value: String): Boolean {
        val trimmed = value.trimStart()
        return trimmed.startsWith("=") || trimmed.startsWith("+") ||
            trimmed.startsWith("-") || trimmed.startsWith("@")
    }

    fun parseAndValidateProductCsv(csvContent: String, existingSkus: Set<String>): List<CsvProductRow> {
        val lines = csvContent.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return emptyList()
        val seenSkus = existingSkus.map { it.uppercase() }.toMutableSet()
        return lines.drop(1).take(500).mapIndexed { idx, line ->
            val cols = line.split(",").map { it.trim().trim('"') }
            val rowNum = idx + 2
            if (cols.size < 6) {
                CsvProductRow(rowNum, "", "", "", "", "", BigDecimal.ZERO, "", "Row $rowNum: Expected at least 6 columns (name,sku,category,unit,currency,price[,location])")
            } else {
                val name = cols[0]
                val sku = cols[1]
                val category = cols[2]
                val unit = cols[3]
                val currency = cols[4].uppercase()
                val priceStr = cols[5]
                val location = cols.getOrElse(6) { "HQ" }
                val err = when {
                    listOf(name, sku, category, unit, currency, location).any { isFormulaInjection(it) } ->
                        "Row $rowNum: Rejected formula-like prefix (=,+,-,@)"
                    name.isBlank() || category.isBlank() || unit.isBlank() ->
                        "Row $rowNum: Name, category, and unit are required"
                    currency.length !in 3..5 ->
                        "Row $rowNum: Invalid currency code '$currency'"
                    sku.isNotBlank() && !seenSkus.add(sku.uppercase()) ->
                        "Row $rowNum: Duplicate SKU '$sku'"
                    else -> try {
                        val p = d(priceStr)
                        if (p < BigDecimal.ZERO) "Row $rowNum: Negative price" else null
                    } catch (_: Exception) {
                        "Row $rowNum: Invalid numeric price '$priceStr'"
                    }
                }
                val parsedPrice = if (err == null) money(d(priceStr)) else BigDecimal.ZERO
                CsvProductRow(rowNum, name, sku, category, unit, currency, parsedPrice, location, err)
            }
        }
    }

    // --- 10. Server-Synced 3-Day (72h) Trial Countdown, Detailed Variance, Yield/Scrap, Volatility, GL & Aging ---

    data class TrialCountdownBreakdown(
        val days: Long,
        val hours: Long,
        val minutes: Long,
        val seconds: Long,
        val totalRemainingHours: Long,
        val remainingMillis: Long,
        val isExpired: Boolean
    )

    fun computeServerSyncedTrialEndMillis(
        userTrialStartMillis: Long?,
        orgTrialEndsAtMillis: Long?,
        durationHours: Long = TRIAL_DURATION_HOURS
    ): Long? {
        val durationMs = durationHours * 3600_000L
        val userAnchoredEnd = userTrialStartMillis?.let { it + durationMs }
        return when {
            userAnchoredEnd != null && orgTrialEndsAtMillis != null -> minOf(userAnchoredEnd, orgTrialEndsAtMillis)
            orgTrialEndsAtMillis != null -> orgTrialEndsAtMillis
            else -> userAnchoredEnd
        }
    }

    fun computeTrialCountdown(trialEndsAtMillis: Long, nowMillis: Long): TrialCountdownBreakdown {
        val diff = (trialEndsAtMillis - nowMillis).coerceAtLeast(0L)
        if (diff <= 0L) {
            return TrialCountdownBreakdown(0L, 0L, 0L, 0L, 0L, 0L, isExpired = true)
        }
        val totalSeconds = diff / 1000L
        val days = totalSeconds / 86400L
        val hours = (totalSeconds % 86400L) / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        val totalHours = totalSeconds / 3600L
        return TrialCountdownBreakdown(
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            totalRemainingHours = totalHours,
            remainingMillis = diff,
            isExpired = false
        )
    }

    fun computeMonotonicTrialCountdown(
        trialEndsAtMillis: Long,
        anchorServerNowMillis: Long,
        elapsedDeltaMillis: Long
    ): TrialCountdownBreakdown {
        val monotonicNow = anchorServerNowMillis + elapsedDeltaMillis.coerceAtLeast(0L)
        return computeTrialCountdown(trialEndsAtMillis, monotonicNow)
    }

    fun isUnusualPriceMovement(
        pctChange: BigDecimal?,
        thresholdPct: BigDecimal = BigDecimal("15")
    ): Boolean {
        if (pctChange == null) return false
        return pctChange.abs() >= thresholdPct
    }

    data class CostVarianceResult(
        val standardTotalAllowed: BigDecimal,
        val actualMaterialCost: BigDecimal?,
        val actualLaborCost: BigDecimal?,
        val actualOverheadCost: BigDecimal?,
        val materialVariance: BigDecimal?,
        val laborVariance: BigDecimal?,
        val overheadVariance: BigDecimal?,
        val totalVariance: BigDecimal?,
        val completenessNote: String
    )

    data class DetailedVarianceResult(
        val summary: CostVarianceResult,
        val materialPriceVariance: BigDecimal?,
        val materialUsageVariance: BigDecimal?,
        val laborRateVariance: BigDecimal?,
        val laborEfficiencyVariance: BigDecimal?,
        val decompositionNote: String
    )

    fun computeCostVariance(
        stdMaterialPerUnit: BigDecimal,
        stdLaborHoursPerUnit: BigDecimal,
        stdLaborRate: BigDecimal,
        stdOverheadPerUnit: BigDecimal,
        goodUnits: BigDecimal,
        actualMaterialCost: BigDecimal?,
        actualLaborCost: BigDecimal?,
        actualOverheadCost: BigDecimal?
    ): CostVarianceResult {
        require(goodUnits > BigDecimal.ZERO) { "Good units must be positive" }
        val stdMatTotal = money(stdMaterialPerUnit.multiply(goodUnits, MC))
        val stdLabTotal = money(stdLaborHoursPerUnit.multiply(stdLaborRate, MC).multiply(goodUnits, MC))
        val stdOvhTotal = money(stdOverheadPerUnit.multiply(goodUnits, MC))
        val stdTotal = money(stdMatTotal.add(stdLabTotal).add(stdOvhTotal))

        val matVar = actualMaterialCost?.let { money(it.subtract(stdMatTotal)) }
        val labVar = actualLaborCost?.let { money(it.subtract(stdLabTotal)) }
        val ovhVar = actualOverheadCost?.let { money(it.subtract(stdOvhTotal)) }

        val complete = actualMaterialCost != null && actualLaborCost != null
        val totalVar = if (complete) {
            money((matVar ?: BigDecimal.ZERO).add(labVar ?: BigDecimal.ZERO).add(ovhVar ?: BigDecimal.ZERO))
        } else null
        val note = if (complete) {
            "Positive = Unfavorable (actual > standard); Negative = Favorable"
        } else {
            "Missing actual material or labor costs — total variance withheld (never treated as zero)."
        }

        return CostVarianceResult(
            standardTotalAllowed = stdTotal,
            actualMaterialCost = actualMaterialCost?.let { money(it) },
            actualLaborCost = actualLaborCost?.let { money(it) },
            actualOverheadCost = actualOverheadCost?.let { money(it) },
            materialVariance = matVar,
            laborVariance = labVar,
            overheadVariance = ovhVar,
            totalVariance = totalVar,
            completenessNote = note
        )
    }

    fun computeDetailedVariance(
        stdMaterialQtyPerUnit: BigDecimal?,
        stdMaterialPrice: BigDecimal?,
        stdLaborHoursPerUnit: BigDecimal,
        stdLaborRate: BigDecimal,
        stdOverheadPerUnit: BigDecimal,
        goodUnits: BigDecimal,
        actualMaterialQty: BigDecimal?,
        actualMaterialPrice: BigDecimal?,
        actualLaborHours: BigDecimal?,
        actualLaborRate: BigDecimal?,
        actualOverheadCost: BigDecimal?
    ): DetailedVarianceResult {
        val stdMatPerUnit = if (stdMaterialQtyPerUnit != null && stdMaterialPrice != null) {
            stdMaterialQtyPerUnit.multiply(stdMaterialPrice, MC)
        } else BigDecimal.ZERO

        val actualMatTotal = if (actualMaterialQty != null && actualMaterialPrice != null) {
            actualMaterialQty.multiply(actualMaterialPrice, MC)
        } else null

        val actualLabTotal = if (actualLaborHours != null && actualLaborRate != null) {
            actualLaborHours.multiply(actualLaborRate, MC)
        } else null

        val summary = computeCostVariance(
            stdMaterialPerUnit = stdMatPerUnit,
            stdLaborHoursPerUnit = stdLaborHoursPerUnit,
            stdLaborRate = stdLaborRate,
            stdOverheadPerUnit = stdOverheadPerUnit,
            goodUnits = goodUnits,
            actualMaterialCost = actualMatTotal,
            actualLaborCost = actualLabTotal,
            actualOverheadCost = actualOverheadCost
        )

        val matPriceVar = if (actualMaterialQty != null && actualMaterialPrice != null && stdMaterialPrice != null) {
            money(actualMaterialPrice.subtract(stdMaterialPrice).multiply(actualMaterialQty, MC))
        } else null

        val matUsageVar = if (actualMaterialQty != null && stdMaterialQtyPerUnit != null && stdMaterialPrice != null) {
            val stdAllowedQty = stdMaterialQtyPerUnit.multiply(goodUnits, MC)
            money(actualMaterialQty.subtract(stdAllowedQty).multiply(stdMaterialPrice, MC))
        } else null

        val labRateVar = if (actualLaborHours != null && actualLaborRate != null) {
            money(actualLaborRate.subtract(stdLaborRate).multiply(actualLaborHours, MC))
        } else null

        val labEffVar = if (actualLaborHours != null) {
            val stdAllowedHours = stdLaborHoursPerUnit.multiply(goodUnits, MC)
            money(actualLaborHours.subtract(stdAllowedHours).multiply(stdLaborRate, MC))
        } else null

        val decompNote = if (matPriceVar != null && matUsageVar != null && labRateVar != null && labEffVar != null) {
            "Material Variance = Price Variance + Usage Variance; Labor Variance = Rate Variance + Efficiency Variance."
        } else {
            "Provide actual quantity and hours to decompose Material Price/Usage and Labor Rate/Efficiency variances."
        }

        return DetailedVarianceResult(
            summary = summary,
            materialPriceVariance = matPriceVar,
            materialUsageVariance = matUsageVar,
            laborRateVariance = labRateVar,
            laborEfficiencyVariance = labEffVar,
            decompositionNote = decompNote
        )
    }

    data class ScrapYieldAnalysisResult(
        val plannedUnits: BigDecimal,
        val recordedScrapUnits: BigDecimal,
        val indicativeGoodUnits: BigDecimal,
        val scrapRatePct: BigDecimal,
        val yieldPct: BigDecimal,
        val currentCostPerGoodUnit: BigDecimal?,
        val indicativeCostAfterScrap: BigDecimal?
    )

    fun computeScrapYieldAnalysis(
        plannedUnits: BigDecimal,
        goodUnits: BigDecimal,
        recordedScrapUnits: BigDecimal,
        totalCost: BigDecimal
    ): ScrapYieldAnalysisResult {
        require(plannedUnits > BigDecimal.ZERO) { "Planned units must be positive" }
        require(recordedScrapUnits >= BigDecimal.ZERO) { "Scrap units cannot be negative" }
        require(recordedScrapUnits <= plannedUnits) { "Recorded scrap exceeds planned production" }
        val indicativeGood = plannedUnits.subtract(recordedScrapUnits).setScale(2, RoundingMode.HALF_UP)
        val scrapRate = pct4(recordedScrapUnits.divide(plannedUnits, MC).multiply(BigDecimal(100)))
        val yieldPct = pct4(BigDecimal(100).subtract(scrapRate))
        val currCpu = if (goodUnits > BigDecimal.ZERO) money(totalCost.divide(goodUnits, MC)) else null
        val indCpu = if (indicativeGood > BigDecimal.ZERO) money(totalCost.divide(indicativeGood, MC)) else null
        return ScrapYieldAnalysisResult(
            plannedUnits = plannedUnits.setScale(2, RoundingMode.HALF_UP),
            recordedScrapUnits = recordedScrapUnits.setScale(2, RoundingMode.HALF_UP),
            indicativeGoodUnits = indicativeGood,
            scrapRatePct = scrapRate,
            yieldPct = yieldPct,
            currentCostPerGoodUnit = currCpu,
            indicativeCostAfterScrap = indCpu
        )
    }

    data class CostVolatilityResult(
        val count: Int,
        val minCost: BigDecimal,
        val maxCost: BigDecimal,
        val meanCost: BigDecimal,
        val spreadPct: BigDecimal,
        val stdDev: BigDecimal
    )

    fun computeCostVolatility(unitCosts: List<BigDecimal>): CostVolatilityResult? {
        if (unitCosts.isEmpty()) return null
        val n = BigDecimal(unitCosts.size)
        val minV = unitCosts.minOrNull()!!
        val maxV = unitCosts.maxOrNull()!!
        val sum = unitCosts.fold(BigDecimal.ZERO, BigDecimal::add)
        val mean = money(sum.divide(n, MC))
        val variance = unitCosts.fold(BigDecimal.ZERO) { acc, c ->
            val diff = c.subtract(mean)
            acc.add(diff.multiply(diff, MC))
        }.divide(n, MC)
        val std = money(BigDecimal(kotlin.math.sqrt(variance.toDouble().coerceAtLeast(0.0))))
        val spread = if (minV > BigDecimal.ZERO) {
            pct4(maxV.subtract(minV).divide(minV, MC).multiply(BigDecimal(100)))
        } else BigDecimal.ZERO
        return CostVolatilityResult(unitCosts.size, money(minV), money(maxV), mean, spread, std)
    }

    data class CvpPoint(val units: Float, val totalCost: Float, val totalRevenue: Float)

    fun generateCvpCurvePoints(
        fixedCosts: BigDecimal,
        variableUnitCost: BigDecimal,
        sellingPrice: BigDecimal,
        maxUnits: BigDecimal
    ): List<CvpPoint> {
        val steps = 5
        val cap = maxUnits.max(BigDecimal(10))
        return (0..steps).map { idx ->
            val u = cap.multiply(BigDecimal(idx), MC).divide(BigDecimal(steps), MC)
            val cost = fixedCosts.add(variableUnitCost.multiply(u, MC))
            val rev = sellingPrice.multiply(u, MC)
            CvpPoint(u.toFloat(), cost.toFloat(), rev.toFloat())
        }
    }

    data class LedgerMovementLine(
        val entryDate: String,
        val referenceNo: String,
        val description: String,
        val debit: BigDecimal,
        val credit: BigDecimal,
        val runningBalance: BigDecimal
    )

    data class AccountLedgerStatement(
        val accountCode: String,
        val accountName: String,
        val openingBalance: BigDecimal,
        val movements: List<LedgerMovementLine>,
        val totalDebits: BigDecimal,
        val totalCredits: BigDecimal,
        val closingBalance: BigDecimal
    )

    fun computeAccountLedgerStatement(
        account: AccountInput,
        entries: List<Triple<String, String, Pair<String, List<JournalLineInput>>>>,
        periodFilter: String = ""
    ): AccountLedgerStatement {
        var running = money(account.openingBalance)
        var totDr = BigDecimal.ZERO
        var totCr = BigDecimal.ZERO
        val rows = mutableListOf<LedgerMovementLine>()
        val filtered = entries
            .filter { periodFilter.isBlank() || it.first.startsWith(periodFilter.trim()) }
            .sortedBy { it.first }
        for ((date, refNo, descAndLines) in filtered) {
            val (desc, lines) = descAndLines
            for (l in lines) {
                if (l.accountCode == account.code) {
                    val dr = money(l.debit)
                    val cr = money(l.credit)
                    totDr = totDr.add(dr)
                    totCr = totCr.add(cr)
                    running = if (account.normalBalance == "debit") {
                        money(running.add(dr).subtract(cr))
                    } else {
                        money(running.add(cr).subtract(dr))
                    }
                    rows.add(LedgerMovementLine(date, refNo, desc, dr, cr, running))
                }
            }
        }
        return AccountLedgerStatement(
            accountCode = account.code,
            accountName = account.name,
            openingBalance = money(account.openingBalance),
            movements = rows,
            totalDebits = money(totDr),
            totalCredits = money(totCr),
            closingBalance = running
        )
    }

    data class ArApAgingSummary(
        val currentAmount: BigDecimal,
        val days1To30Amount: BigDecimal,
        val days31To60Amount: BigDecimal,
        val days61To90Amount: BigDecimal,
        val days90PlusAmount: BigDecimal,
        val overdueCount: Int
    )

    fun computeArApAgingBuckets(
        items: List<Triple<String, BigDecimal, String>>,
        asOfEpochDays: Long
    ): ArApAgingSummary {
        var current = BigDecimal.ZERO
        var d1_30 = BigDecimal.ZERO
        var d31_60 = BigDecimal.ZERO
        var d61_90 = BigDecimal.ZERO
        var d90p = BigDecimal.ZERO
        var overdue = 0

        for ((dueDateStr, rem, status) in items) {
            if (status == "paid" || rem <= BigDecimal.ZERO) continue
            val dueEpochDays = runCatching {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                (fmt.parse(dueDateStr.trim())?.time ?: (asOfEpochDays * 86400_000L)) / 86400_000L
            }.getOrDefault(asOfEpochDays)
            val daysPastDue = asOfEpochDays - dueEpochDays
            if (daysPastDue <= 0) {
                current = current.add(rem)
            } else {
                overdue++
                when {
                    daysPastDue <= 30 -> d1_30 = d1_30.add(rem)
                    daysPastDue <= 60 -> d31_60 = d31_60.add(rem)
                    daysPastDue <= 90 -> d61_90 = d61_90.add(rem)
                    else -> d90p = d90p.add(rem)
                }
            }
        }
        return ArApAgingSummary(
            currentAmount = money(current),
            days1To30Amount = money(d1_30),
            days31To60Amount = money(d31_60),
            days61To90Amount = money(d61_90),
            days90PlusAmount = money(d90p),
            overdueCount = overdue
        )
    }

    data class PeriodComparisonBridge(
        val periodA: String,
        val periodB: String,
        val revenueDelta: BigDecimal,
        val cogsDelta: BigDecimal,
        val opExDelta: BigDecimal,
        val netProfitPeriodA: BigDecimal,
        val netProfitPeriodB: BigDecimal,
        val netProfitDelta: BigDecimal,
        val explanation: String
    )

    fun comparePeriodsProfitBridge(
        accounts: List<AccountInput>,
        datedEntries: List<Pair<String, List<JournalLineInput>>>,
        periodA: String,
        periodB: String
    ): PeriodComparisonBridge {
        val zeroOpAccounts = accounts.map { it.copy(openingBalance = BigDecimal.ZERO) }
        val repA = generateFinancialStatements(zeroOpAccounts, datedEntries.filter { it.first == periodA }.map { it.second })
        val repB = generateFinancialStatements(zeroOpAccounts, datedEntries.filter { it.first == periodB }.map { it.second })
        val revD = money(repB.revenue.subtract(repA.revenue))
        val cogsD = money(repB.cogs.subtract(repA.cogs))
        val opExD = money(repB.operatingExpenses.subtract(repA.operatingExpenses))
        val npD = money(repB.netProfit.subtract(repA.netProfit))
        val driver = when {
            revD.abs() >= cogsD.abs() && revD.abs() >= opExD.abs() ->
                "Revenue change (${if (revD >= BigDecimal.ZERO) "+" else ""}$revD) is the primary driver of net profit shift."
            cogsD.abs() >= opExD.abs() ->
                "COGS movement (${if (cogsD >= BigDecimal.ZERO) "+" else ""}$cogsD) is the largest cost factor impacting gross margin."
            else ->
                "Operating expense shift (${if (opExD >= BigDecimal.ZERO) "+" else ""}$opExD) drove net profit change."
        }
        return PeriodComparisonBridge(
            periodA = periodA,
            periodB = periodB,
            revenueDelta = revD,
            cogsDelta = cogsD,
            opExDelta = opExD,
            netProfitPeriodA = repA.netProfit,
            netProfitPeriodB = repB.netProfit,
            netProfitDelta = npD,
            explanation = driver
        )
    }

    data class AccountingCsvRow(
        val rowNumber: Int,
        val entryDate: String,
        val referenceNo: String,
        val description: String,
        val debitAccountCode: String,
        val creditAccountCode: String,
        val amount: BigDecimal,
        val error: String? = null
    )

    fun parseAndValidateAccountingCsv(csvContent: String, validAccountCodes: Set<String>): List<AccountingCsvRow> {
        val lines = csvContent.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return emptyList()
        return lines.drop(1).take(300).mapIndexed { idx, line ->
            val cols = line.split(",").map { it.trim().trim('"') }
            val rowNum = idx + 2
            if (cols.size < 6) {
                AccountingCsvRow(rowNum, "", "", "", "", "", BigDecimal.ZERO, "Row $rowNum: Expected 6 columns (date,refNo,description,debitCode,creditCode,amount)")
            } else {
                val date = cols[0]
                val refNo = cols[1]
                val desc = cols[2]
                val dr = cols[3]
                val cr = cols[4]
                val amtStr = cols[5]
                val err = when {
                    listOf(date, refNo, desc, dr, cr).any { isFormulaInjection(it) } ->
                        "Row $rowNum: Rejected formula-like prefix (=,+,-,@)"
                    date.length < 7 || refNo.isBlank() || desc.isBlank() ->
                        "Row $rowNum: Date, refNo, and description are required"
                    dr !in validAccountCodes || cr !in validAccountCodes ->
                        "Row $rowNum: Unknown account code ($dr or $cr)"
                    dr == cr ->
                        "Row $rowNum: Debit and credit accounts must differ"
                    else -> try {
                        val amt = d(amtStr)
                        if (amt <= BigDecimal.ZERO) "Row $rowNum: Amount must be positive" else null
                    } catch (_: Exception) {
                        "Row $rowNum: Invalid amount '$amtStr'"
                    }
                }
                val parsedAmt = if (err == null) money(d(amtStr)) else BigDecimal.ZERO
                AccountingCsvRow(rowNum, date, refNo, desc, dr, cr, parsedAmt, err)
            }
        }
    }

    // --- 11. Deterministic 3D Calculator Expression Evaluator ---

    private class CalcParser(private val cleaned: String) {
        private var pos = 0

        private fun peek(): Char? = if (pos < cleaned.length) cleaned[pos] else null

        fun parse(): BigDecimal {
            val result = parseExpression()
            require(pos == cleaned.length) { "Invalid syntax near '${cleaned.substring(pos)}'" }
            return result.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
        }

        private fun parseExpression(): BigDecimal {
            var left = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; left = left.add(parseTerm(), MC) }
                    '-' -> { pos++; left = left.subtract(parseTerm(), MC) }
                    else -> return left
                }
            }
        }

        private fun parseTerm(): BigDecimal {
            var left = parsePostfix()
            while (true) {
                when (peek()) {
                    '*' -> { pos++; left = left.multiply(parsePostfix(), MC) }
                    '/' -> {
                        pos++
                        val divisor = parsePostfix()
                        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                            throw ArithmeticException("Cannot divide by zero")
                        }
                        left = left.divide(divisor, MC)
                    }
                    else -> return left
                }
            }
        }

        private fun parsePostfix(): BigDecimal {
            var value = parsePrimary()
            while (peek() == '%') {
                pos++
                value = value.divide(BigDecimal(100), MC)
            }
            return value
        }

        private fun parsePrimary(): BigDecimal {
            return when (peek()) {
                '+' -> { pos++; parsePostfix() }
                '-' -> { pos++; parsePostfix().negate() }
                '(' -> {
                    pos++
                    val inside = parseExpression()
                    require(peek() == ')') { "Missing closing parenthesis" }
                    pos++
                    inside
                }
                else -> {
                    val start = pos
                    var sawDot = false
                    while (true) {
                        val ch = peek() ?: break
                        if (ch.isDigit()) {
                            pos++
                        } else if (ch == '.' && !sawDot) {
                            sawDot = true
                            pos++
                        } else break
                    }
                    require(pos > start) { "Unexpected character in expression" }
                    BigDecimal(cleaned.substring(start, pos))
                }
            }
        }
    }

    fun evaluateCalculatorExpression(rawExpr: String): BigDecimal {
        val cleaned = rawExpr
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace(",", "")
            .replace(" ", "")
        require(cleaned.isNotEmpty()) { "Expression is empty" }
        require(cleaned.length <= 160) { "Expression is too long" }
        return CalcParser(cleaned).parse()
    }

    fun formatCalculatorResult(value: BigDecimal): String {
        val scaled = if (value.scale() > 6) value.setScale(6, RoundingMode.HALF_UP) else value
        return scaled.stripTrailingZeros().toPlainString()
    }

    // --- 12. Business Sector Registry, Manufacturing BOM & Food Batch FEFO ---

    data class FvgSectorDefinition(
        val key: String,
        val nameEn: String,
        val nameAr: String,
        val isActive: Boolean
    )

    val FVG_SECTORS = listOf(
        FvgSectorDefinition("manufacturing", "General Manufacturing", "التصنيع العام", true),
        FvgSectorDefinition("food_beverage", "Food & Beverage Processing", "الأغذية والمشروبات", true),
        FvgSectorDefinition("retail_wholesale", "Retail & Wholesale Trade", "تجارة التجزئة والجملة", false),
        FvgSectorDefinition("services_hospitality", "Services & Hospitality", "الخدمات والضيافة", false),
        FvgSectorDefinition("construction_projects", "Construction & Projects", "المقاولات والمشاريع", false)
    )

    data class BomLineInput(
        val materialName: String,
        val qtyPerUnit: BigDecimal,
        val unitCost: BigDecimal,
        val scrapAllowancePct: BigDecimal = BigDecimal.ZERO
    )

    data class BomCostBreakdown(
        val netMaterialCostPerUnit: BigDecimal,
        val scrapAllowanceCostPerUnit: BigDecimal,
        val effectiveMaterialCostPerUnit: BigDecimal
    )

    fun calculateBomUnitCost(lines: List<BomLineInput>): BomCostBreakdown {
        var netTotal = BigDecimal.ZERO
        var scrapTotal = BigDecimal.ZERO
        for (line in lines) {
            require(line.qtyPerUnit >= BigDecimal.ZERO && line.unitCost >= BigDecimal.ZERO) {
                "BOM quantity and unit cost cannot be negative"
            }
            val lineCost = line.qtyPerUnit.multiply(line.unitCost, MC)
            val scrapPct = line.scrapAllowancePct.coerceAtLeast(BigDecimal.ZERO)
            val lineScrap = lineCost.multiply(scrapPct.divide(BigDecimal(100), MC), MC)
            netTotal = netTotal.add(lineCost)
            scrapTotal = scrapTotal.add(lineScrap)
        }
        return BomCostBreakdown(
            netMaterialCostPerUnit = money(netTotal),
            scrapAllowanceCostPerUnit = money(scrapTotal),
            effectiveMaterialCostPerUnit = money(netTotal.add(scrapTotal))
        )
    }

    data class FxFreshnessStatus(
        val isStale: Boolean,
        val isUnapproved: Boolean,
        val daysOld: Long?
    )

    fun assessFxRateFreshness(
        effectiveDate: String,
        status: String,
        todayEpochDays: Long = System.currentTimeMillis() / 86400_000L
    ): FxFreshnessStatus {
        val effDays = runCatching {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            (fmt.parse(effectiveDate.trim())?.time ?: (todayEpochDays * 86400_000L)) / 86400_000L
        }.getOrNull()
        val age = effDays?.let { (todayEpochDays - it).coerceAtLeast(0L) }
        return FxFreshnessStatus(
            isStale = age != null && age > 7L,
            isUnapproved = status != "approved",
            daysOld = age
        )
    }

    data class OcrMathValidationResult(
        val isConsistent: Boolean,
        val expectedTotal: BigDecimal?,
        val extractedTotal: BigDecimal?,
        val discrepancy: BigDecimal?
    )

    fun validateOcrArithmetic(
        subtotalStr: String?,
        discountStr: String?,
        taxStr: String?,
        totalStr: String?
    ): OcrMathValidationResult {
        val total = runCatching { d(totalStr) }.getOrNull()
        val sub = runCatching { d(subtotalStr) }.getOrNull()
        if (total == null || sub == null) {
            return OcrMathValidationResult(true, null, total, null)
        }
        val disc = runCatching { dOrZero(discountStr) }.getOrDefault(BigDecimal.ZERO)
        val tax = runCatching { dOrZero(taxStr) }.getOrDefault(BigDecimal.ZERO)
        val expected = money(sub.subtract(disc).add(tax))
        val extracted = money(total)
        val diff = money(extracted.subtract(expected).abs())
        return OcrMathValidationResult(
            isConsistent = diff <= BigDecimal("0.05"),
            expectedTotal = expected,
            extractedTotal = extracted,
            discrepancy = diff
        )
    }
}
