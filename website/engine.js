export const MODEL_VERSION = "3.0.0";
export const TRIAL_DURATION_HOURS = 168;
export const LEGACY_TRIAL_DURATION_HOURS = 72;
export const TRIAL_DURATION_MILLIS = TRIAL_DURATION_HOURS * 3600 * 1000;
export const OWNER_EMAIL = "Financialvisionguide@gmail.com";

export const ALL_COSTING_METHODS = [
  "actual",
  "standard",
  "target",
  "abc",
  "job_order",
  "process",
  "absorption",
  "variable"
];

export const FVG_SECTORS = [
  { key: "manufacturing", nameEn: "Manufacturing & Industrial Production", nameAr: "التصنيع والإنتاج الصناعي", active: true },
  { key: "food_beverage", nameEn: "Food, Beverage & Packaging", nameAr: "الأغذية والمشروبات والتعبئة", active: true },
  { key: "retail_wholesale", nameEn: "Retail & Wholesale Trade", nameAr: "تجارة التجزئة والجملة", active: false },
  { key: "pharmacy_healthcare", nameEn: "Pharmacy & Healthcare", nameAr: "الصيدلة والرعاية الصحية", active: false },
  { key: "agriculture", nameEn: "Agriculture & Livestock", nameAr: "الزراعة والثروة الحيوانية", active: false },
  { key: "construction", nameEn: "Construction & Contracting", nameAr: "البناء والمقاولات", active: false },
  { key: "logistics", nameEn: "Logistics & Transport", nameAr: "اللوجستيات والنقل", active: false },
  { key: "hospitality", nameEn: "Hospitality & Catering", nameAr: "الضيافة والتموين", active: false },
  { key: "services", nameEn: "Professional Services & Consulting", nameAr: "الخدمات المهنية والاستشارات", active: false },
  { key: "ecommerce", nameEn: "E-Commerce & Digital Supply", nameAr: "التجارة الإلكترونية والإمداد الرقمي", active: false }
];

export const SPEC_SCENARIO_PRESETS = [
  { key: "material_plus_20", nameEn: "Raw Materials +20%", nameAr: "المواد الخام +20%", matPct: 20, labPct: 0, ovhPct: 0, pricePct: 0, fxPct: 0, volumePct: 0 },
  { key: "fx_plus_15", nameEn: "Exchange Rate +15%", nameAr: "سعر الصرف +15%", matPct: 15, labPct: 0, ovhPct: 5, pricePct: 0, fxPct: 15, volumePct: 0 },
  { key: "price_minus_10", nameEn: "Selling Price -10%", nameAr: "سعر البيع -10%", matPct: 0, labPct: 0, ovhPct: 0, pricePct: -10, fxPct: 0, volumePct: 0 },
  { key: "volume_minus_25", nameEn: "Volume -25%", nameAr: "حجم المبيعات -25%", matPct: 0, labPct: 0, ovhPct: 0, pricePct: 0, fxPct: 0, volumePct: -25 }
];

export function n(v) {
  const num = Number(v);
  return Number.isFinite(num) ? num : 0;
}

export function money(v) {
  return Math.round((n(v) + Number.EPSILON) * 100) / 100;
}

export function pct4(v) {
  return Math.round((n(v) + Number.EPSILON) * 10000) / 10000;
}

export function computeServerSyncedTrialEndMillis(userTrialStartMillis, orgTrialEndsAtMillis, durationHours = TRIAL_DURATION_HOURS) {
  const expected = userTrialStartMillis ? userTrialStartMillis + Number(durationHours) * 3600 * 1000 : null;
  if (expected && orgTrialEndsAtMillis) return Math.min(expected, orgTrialEndsAtMillis);
  return orgTrialEndsAtMillis || expected || null;
}

export function assessFxRateFreshness(effectiveDate, status, todayDays = Math.floor(Date.now() / 86400000)) {
  const parsed = Date.parse(effectiveDate);
  const ageDays = Number.isFinite(parsed) ? Math.max(0, todayDays - Math.floor(parsed / 86400000)) : 999;
  const isStale = ageDays > 7;
  const isUnapproved = String(status || "").toLowerCase() !== "approved";
  return { ageDays, isStale, isUnapproved };
}

export function validateOcrArithmetic(subtotal, discount, tax, total) {
  const expectedTotal = money(n(subtotal) - n(discount) + n(tax));
  const extractedTotal = money(total);
  const diff = money(Math.abs(expectedTotal - extractedTotal));
  return {
    isConsistent: diff <= 0.05,
    expectedTotal,
    extractedTotal,
    discrepancy: diff
  };
}

export function evaluateCalculatorExpression(expr) {
  const clean = String(expr || "").replace(/\s+/g, "").replace(/×/g, "*").replace(/÷/g, "/");
  if (!clean) return { value: 0, formatted: "0", error: null };
  let pos = 0;

  function peek() { return clean[pos]; }
  function parseNumber() {
    let start = pos;
    let seenDot = false;
    while (pos < clean.length && (/[0-9]/.test(clean[pos]) || (clean[pos] === "." && !seenDot))) {
      if (clean[pos] === ".") seenDot = true;
      pos++;
    }
    if (pos === start) throw new Error("SYNTAX");
    let num = Number(clean.slice(start, pos));
    if (!Number.isFinite(num)) throw new Error("SYNTAX");
    if (peek() === "%") {
      pos++;
      num = num / 100;
    }
    return num;
  }

  function parseFactor() {
    if (peek() === "+") { pos++; return parseFactor(); }
    if (peek() === "-") { pos++; return -parseFactor(); }
    if (peek() === "(") {
      pos++;
      const val = parseExpr();
      if (peek() !== ")") throw new Error("PAREN");
      pos++;
      if (peek() === "%") { pos++; return val / 100; }
      return val;
    }
    return parseNumber();
  }

  function parseTerm() {
    let val = parseFactor();
    while (peek() === "*" || peek() === "/") {
      const op = clean[pos++];
      const rhs = parseFactor();
      if (op === "*") val = val * rhs;
      else {
        if (rhs === 0) throw new Error("DIV0");
        val = val / rhs;
      }
    }
    return val;
  }

  function parseExpr() {
    let val = parseTerm();
    while (peek() === "+" || peek() === "-") {
      const op = clean[pos++];
      const rhs = parseTerm();
      val = op === "+" ? val + rhs : val - rhs;
    }
    return val;
  }

  try {
    const res = parseExpr();
    if (pos < clean.length || !Number.isFinite(res)) return { value: null, formatted: "Error", error: "SYNTAX" };
    const rounded = Math.round((res + Number.EPSILON) * 1000000) / 1000000;
    return { value: rounded, formatted: String(rounded), error: null };
  } catch (e) {
    return { value: null, formatted: e.message === "DIV0" ? "÷0!" : "Error", error: e.message };
  }
}

export function calculatePercentageDelta(previousPrice, newPrice) {
  const absChange = money(n(newPrice) - n(previousPrice));
  const direction = absChange > 0 ? "up" : absChange < 0 ? "down" : "unchanged";
  if (n(previousPrice) <= 0) {
    return { absChange, pctChange: null, direction, isUnusual: false };
  }
  const pctChange = pct4((absChange / n(previousPrice)) * 100);
  return { absChange, pctChange, direction, isUnusual: Math.abs(pctChange) >= 15 };
}

export function calculateCompoundedInflation(monthlyRates) {
  if (!monthlyRates.length) return 0;
  let factor = 1;
  for (const r of monthlyRates) {
    factor *= 1 + n(r) / 100;
  }
  return pct4((factor - 1) * 100);
}

export function calculateWeightedBasket(items) {
  let baseTotal = 0;
  let currTotal = 0;
  for (const it of items) {
    const qtyWeight = n(it.quantity) * n(it.weight || 1);
    baseTotal += qtyWeight * n(it.basePrice);
    currTotal += qtyWeight * n(it.currentPrice);
  }
  const baseCost = money(baseTotal);
  const currentCost = money(currTotal);
  const indexValue = baseCost > 0 ? pct4((currentCost / baseCost) * 100) : null;
  const basketInflationPct = baseCost > 0 ? pct4(((currentCost - baseCost) / baseCost) * 100) : null;
  return { baseCost, currentCost, indexValue, basketInflationPct };
}

export function calculateCosting(input) {
  const units = n(input.goodUnits);
  if (units <= 0) throw new Error("Good units must be greater than zero");

  const variableTotal =
    n(input.directMaterial) +
    n(input.indirectMaterial) +
    n(input.directLabor) +
    n(input.indirectLabor) +
    n(input.overhead) +
    n(input.energyFuel) +
    n(input.packaging) +
    n(input.transport) +
    n(input.wasteScrap);

  const fixed = n(input.fixedCosts);
  const totalCost = money(variableTotal + fixed);
  const unitCost = money(totalCost / units);
  const unitVarCost = variableTotal / units;
  const price = money(input.sellingPrice);
  const revenue = money(price * units);
  const grossProfit = money(revenue - variableTotal);
  const netProfit = money(revenue - totalCost);

  const profitMarginPct = price > 0 ? pct4(((price - unitCost) / price) * 100) : null;
  const markupPct = unitCost > 0 ? pct4(((price - unitCost) / unitCost) * 100) : null;

  const unitContribution = price - unitVarCost;
  const breakEvenUnits = fixed > 0 && unitContribution > 0 ? Math.ceil(fixed / unitContribution) : null;

  return {
    totalCost,
    unitCost,
    unitVarCost: money(unitVarCost),
    revenue,
    grossProfit,
    netProfit,
    profitMarginPct,
    markupPct,
    breakEvenUnits
  };
}

export function calculateCostingByMethod(method, input, targetMarginPct = 20, completedEquivUnits = null, isArabic = true) {
  const norm = ALL_COSTING_METHODS.includes(method) ? method : "actual";
  let effectiveInput = { ...input };
  let effectiveUnits = n(input.goodUnits);
  let targetAllowableUnitCost = null;
  let targetCostGapPerUnit = null;
  let note = "";

  if (norm === "variable") {
    effectiveInput.fixedCosts = 0;
    note = isArabic
      ? "التكلفة المتغيرة: تستبعد التكاليف الثابتة من تكلفة الوحدة وتعاملها كمصروف فترة."
      : "Variable Costing excludes Fixed Overhead from unit product cost (period expense).";
  } else if (norm === "process") {
    const eq = completedEquivUnits && n(completedEquivUnits) > 0 ? n(completedEquivUnits) : effectiveUnits;
    effectiveInput.goodUnits = eq;
    effectiveUnits = eq;
    note = isArabic
      ? `تكلفة المراحل: توزّع تكلفة التشغيل على ${eq} وحدة مكافئة.`
      : `Process Costing allocates total batch cost across ${eq} equivalent units.`;
  } else if (norm === "target") {
    const res = calculateCosting(effectiveInput);
    const allowable = money(n(input.sellingPrice) * (1 - n(targetMarginPct) / 100));
    targetAllowableUnitCost = allowable;
    targetCostGapPerUnit = money(res.unitCost - allowable);
    note = isArabic
      ? `التكلفة المستهدفة: التكلفة المسموحة = ${allowable} عند هامش ${targetMarginPct}%`
      : `Target Costing: Allowable Unit Cost = ${allowable} at ${targetMarginPct}% margin.`;
    return { method: norm, coreResult: res, effectiveUnitsUsed: effectiveUnits, targetAllowableUnitCost, targetCostGapPerUnit, methodNote: note };
  } else if (norm === "absorption") {
    note = isArabic ? "التكلفة الكلية الامتصاصية: تشمل المواد المباشرة والتكاليف المتغيرة والثابتة." : "Absorption Costing includes Direct, Variable Overhead & Fixed Manufacturing Overhead.";
  } else if (norm === "standard") {
    note = isArabic ? "التكلفة المعيارية: تقارن الوصفة المعيارية بالإنفاق الفعلي." : "Standard Costing benchmarks budgeted unit recipes against actual factory spend.";
  } else if (norm === "abc") {
    note = isArabic ? "التكلفة على أساس الأنشطة (ABC): توزّع غير المباشر حسب مسببات التكلفة." : "Activity-Based Costing assigns overhead pools via measured cost drivers.";
  } else if (norm === "job_order") {
    note = isArabic ? "تكلفة الأوامر الإنتاجية: تحصر مواد وعمالة وأعباء كل أمر تشغيل." : "Job Order Costing accumulates materials, labor, and overhead per custom batch.";
  } else {
    note = isArabic ? "التكلفة الفعلية: تسجل إجمالي التكاليف الفعلية لكل وحدة صالحة." : "Actual Costing records full historical incurred factory costs per good unit.";
  }

  const coreResult = calculateCosting(effectiveInput);
  return { method: norm, coreResult, effectiveUnitsUsed: effectiveUnits, targetAllowableUnitCost, targetCostGapPerUnit, methodNote: note };
}

export function evaluateFinancialRisk(profitMarginPct, inflationRatePct = null, totalDebt = 0, totalEquity = 0, isArabic = true) {
  const margin = profitMarginPct == null ? null : n(profitMarginPct);
  const inf = inflationRatePct == null ? null : n(inflationRatePct);
  const deRatio = n(totalEquity) > 0 ? money(n(totalDebt) / n(totalEquity)) : null;

  if (margin != null && margin < 10) {
    return {
      level: "high",
      ruleName: isArabic ? "انخفاض حاد في الهامش" : "LOW_MARGIN",
      actualValue: `${margin}%`,
      thresholdValue: "10.00%",
      explanation: isArabic ? "هامش الربح أقل من حد الأمان 10% — خطر مرتفع لتآكل رأس المال." : "Profit margin is below the 10% safety floor — high risk of cost-erosion loss."
    };
  }
  if (inf != null && inf > 20 && margin != null && margin < inf) {
    return {
      level: "high",
      ruleName: isArabic ? "ضغط التضخم على الهامش" : "INFLATION_SQUEEZE",
      actualValue: `${margin}% / ${inf}%`,
      thresholdValue: ">= Inflation",
      explanation: isArabic ? "التضخم الشهري يفوق هامش الوحدة؛ يلزم تعديل التسعير لحماية الاستبدال." : "Inflation exceeds unit margin; capital replacement erodes without repricing."
    };
  }
  if (deRatio != null && deRatio > 2) {
    return {
      level: "high",
      ruleName: isArabic ? "ارتفاع المديونية" : "HIGH_LEVERAGE",
      actualValue: `${deRatio}x`,
      thresholdValue: "2.00x",
      explanation: isArabic ? "نسبة الديون إلى حقوق الملكية تتجاوز 2.0 ضعف — ضغط سيولة." : "Debt-to-Equity exceeds 2.0x — elevated financial solvency stress."
    };
  }
  if (margin != null && margin < 18) {
    return {
      level: "medium",
      ruleName: isArabic ? "هامش ربح ضيق" : "MODERATE_MARGIN",
      actualValue: `${margin}%`,
      thresholdValue: "18.00%",
      explanation: isArabic ? "الهامش موجب لكنه ضيق (بين 10% و 18%) في بيئة تضخمية." : "Margin is positive but tight under inflationary supply volatility (10%–18%)."
    };
  }
  if (inf != null && inf > 10) {
    return {
      level: "medium",
      ruleName: isArabic ? "تضخم مرتفع" : "ELEVATED_INFLATION",
      actualValue: `${inf}%`,
      thresholdValue: "10.00%",
      explanation: isArabic ? "التضخم يتجاوز 10%؛ راقب تكلفة الاستبدال باستمرار." : "Inflation exceeds 10%; maintain dynamic replacement-cost monitoring."
    };
  }
  return {
    level: "low",
    ruleName: isArabic ? "نطاق أمان صحي" : "HEALTHY_BUFFER",
    actualValue: margin != null ? `${margin}%` : (isArabic ? "بانتظار البيانات" : "Pending"),
    thresholdValue: ">= 18%",
    explanation: isArabic ? "الربحية والرافعة المالية وهامش الأمان التضخمي ضمن الحدود الصحية." : "Unit profitability, leverage, and inflation buffers stay within healthy tolerance."
  };
}

export function computeDetailedVariance(params) {
  const stdMatAllowed = n(params.stdMaterialQtyPerUnit) * n(params.goodUnits) * n(params.stdMaterialPrice);
  const actMatCost = n(params.actualMaterialQty) * n(params.actualMaterialPrice);
  const matPriceVar = money((n(params.actualMaterialPrice) - n(params.stdMaterialPrice)) * n(params.actualMaterialQty));
  const matUsageVar = money((n(params.actualMaterialQty) - n(params.stdMaterialQtyPerUnit) * n(params.goodUnits)) * n(params.stdMaterialPrice));

  const stdLabHrsAllowed = n(params.stdLaborHoursPerUnit) * n(params.goodUnits);
  const stdLabAllowed = stdLabHrsAllowed * n(params.stdLaborRate);
  const actLabCost = n(params.actualLaborHours) * n(params.actualLaborRate);
  const labRateVar = money((n(params.actualLaborRate) - n(params.stdLaborRate)) * n(params.actualLaborHours));
  const labEffVar = money((n(params.actualLaborHours) - stdLabHrsAllowed) * n(params.stdLaborRate));

  const stdOvhAllowed = n(params.stdOverheadPerUnit) * n(params.goodUnits);
  const ovhVar = money(n(params.actualOverheadCost) - stdOvhAllowed);

  const stdTotal = money(stdMatAllowed + stdLabAllowed + stdOvhAllowed);
  const actTotal = money(actMatCost + actLabCost + n(params.actualOverheadCost));
  const totalVar = money(actTotal - stdTotal);

  return {
    materialPriceVariance: matPriceVar,
    materialUsageVariance: matUsageVar,
    laborRateVariance: labRateVar,
    laborEfficiencyVariance: labEffVar,
    overheadVariance: ovhVar,
    standardTotalAllowed: stdTotal,
    actualTotal: actTotal,
    totalVariance: totalVar
  };
}

export function runExtendedScenario({ baseMaterial, baseLabor, baseOverheadAndOther, baseRevenue, materialChangePct = 0, laborChangePct = 0, overheadChangePct = 0, priceChangePct = 0, fxChangePct = 0, volumeChangePct = 0 }) {
  const baseTotalCost = money(n(baseMaterial) + n(baseLabor) + n(baseOverheadAndOther));
  const baseProfit = money(n(baseRevenue) - baseTotalCost);
  const volFactor = 1 + n(volumeChangePct) / 100;
  const fxFactor = 1 + n(fxChangePct) / 100;

  const newMat = n(baseMaterial) * (1 + n(materialChangePct) / 100) * fxFactor * volFactor;
  const newLab = n(baseLabor) * (1 + n(laborChangePct) / 100) * volFactor;
  const newOvh = n(baseOverheadAndOther) * (1 + n(overheadChangePct) / 100);
  const newRev = n(baseRevenue) * (1 + n(priceChangePct) / 100) * volFactor;

  const scenarioTotalCost = money(newMat + newLab + newOvh);
  const scenarioProfit = money(newRev - scenarioTotalCost);
  return {
    baseTotalCost,
    scenarioTotalCost,
    baseProfit,
    scenarioProfit,
    profitDelta: money(scenarioProfit - baseProfit)
  };
}

export function generateFinancialStatements(accounts, postedEntries) {
  const drMap = new Map();
  const crMap = new Map();
  for (const je of postedEntries) {
    if (je.status !== "posted" && je.status !== "reversed") continue;
    for (const l of je.lines) {
      drMap.set(l.accountCode, (drMap.get(l.accountCode) || 0) + n(l.debit));
      crMap.set(l.accountCode, (crMap.get(l.accountCode) || 0) + n(l.credit));
    }
  }

  let totalTrialDebits = 0;
  let totalTrialCredits = 0;
  let revenue = 0, cogs = 0, operatingExpenses = 0, interestAndOther = 0;
  let cashAndBank = 0, receivables = 0, inventoryBookValue = 0, nonCurrentAssets = 0;
  let currentLiabilities = 0, nonCurrentLiabilities = 0, equityBase = 0;

  const trialBalance = accounts.map((acc) => {
    const dr = drMap.get(acc.code) || 0;
    const cr = crMap.get(acc.code) || 0;
    const open = n(acc.openingBalance);
    const net = acc.normalBalance === "debit" ? open + dr - cr : open + cr - dr;
    const debitBal = acc.normalBalance === "debit" ? (net >= 0 ? money(net) : 0) : (net < 0 ? money(Math.abs(net)) : 0);
    const creditBal = acc.normalBalance === "credit" ? (net >= 0 ? money(net) : 0) : (net < 0 ? money(Math.abs(net)) : 0);
    totalTrialDebits += debitBal;
    totalTrialCredits += creditBal;

    if (acc.accountType === "revenue") revenue += net;
    else if (acc.accountType === "cogs") cogs += net;
    else if (acc.accountType === "expense") {
      if (acc.subType === "interest" || acc.subType === "tax") interestAndOther += net;
      else operatingExpenses += net;
    } else if (acc.accountType === "asset") {
      if (acc.subType === "cash") cashAndBank += net;
      else if (acc.subType === "receivable") receivables += net;
      else if (acc.subType === "inventory") inventoryBookValue += net;
      else if (acc.subType === "fixed_asset") nonCurrentAssets += net;
    } else if (acc.accountType === "liability") {
      if (acc.subType === "long_term_liability") nonCurrentLiabilities += net;
      else currentLiabilities += net;
    } else if (acc.accountType === "equity") {
      equityBase += net;
    }
    return { code: acc.code, name: acc.name, nameAr: acc.nameAr || acc.name, accountType: acc.accountType, debitBalance: debitBal, creditBalance: creditBal };
  });

  const grossProfit = money(revenue - cogs);
  const operatingProfit = money(grossProfit - operatingExpenses);
  const netProfit = money(operatingProfit - interestAndOther);
  const currentAssets = money(cashAndBank + receivables + inventoryBookValue);
  const totalAssets = money(currentAssets + nonCurrentAssets);
  const totalLiabilities = money(currentLiabilities + nonCurrentLiabilities);
  const totalEquityWithNetIncome = money(equityBase + netProfit);
  const totalLiabilitiesAndEquity = money(totalLiabilities + totalEquityWithNetIncome);
  const isBalanced = Math.abs(totalAssets - totalLiabilitiesAndEquity) <= 0.05;

  return {
    trialBalance,
    totalTrialDebits: money(totalTrialDebits),
    totalTrialCredits: money(totalTrialCredits),
    revenue: money(revenue),
    cogs: money(cogs),
    grossProfit,
    operatingExpenses: money(operatingExpenses),
    operatingProfit,
    interestAndOther: money(interestAndOther),
    netProfit,
    cashAndBank: money(cashAndBank),
    receivables: money(receivables),
    inventoryBookValue: money(inventoryBookValue),
    currentAssets,
    nonCurrentAssets: money(nonCurrentAssets),
    totalAssets,
    currentLiabilities: money(currentLiabilities),
    nonCurrentLiabilities: money(nonCurrentLiabilities),
    totalLiabilities,
    totalEquityWithNetIncome,
    totalLiabilitiesAndEquity,
    isAccountingEquationBalanced: isBalanced,
    operatingCashFlow: netProfit,
    investingCashFlow: money(-nonCurrentAssets),
    financingCashFlow: money(nonCurrentLiabilities + equityBase),
    closingCash: money(cashAndBank)
  };
}

export function computeFinancialRatios(stmt, isArabic = true) {
  function ratio(key, nameAr, nameEn, catAr, catEn, num, den, mult = 1, unit = "x", unitAr = "مرة", formula = "", missingAr = "المقام صفر أو غير مكتمل", missingEn = "Denominator is zero") {
    const valid = den > 0;
    const val = valid ? pct4((num / den) * mult) : null;
    return {
      key,
      label: isArabic ? nameAr : nameEn,
      category: isArabic ? catAr : catEn,
      value: val,
      unitSuffix: isArabic ? unitAr : unit,
      formula,
      missingDataWarning: valid ? null : (isArabic ? missingAr : missingEn)
    };
  }
  const quickAssets = stmt.cashAndBank + stmt.receivables;
  const ebit = stmt.operatingProfit;
  return [
    ratio("current_ratio", "نسبة التداول الجارية", "Current Ratio", "السيولة", "Liquidity", stmt.currentAssets, stmt.currentLiabilities, 1, "x", "مرة", "Current Assets / Current Liabilities"),
    ratio("quick_ratio", "نسبة السيولة السريعة", "Quick Ratio", "السيولة", "Liquidity", quickAssets, stmt.currentLiabilities, 1, "x", "مرة", "(Cash + AR) / Current Liabilities"),
    ratio("cash_ratio", "نسبة النقدية", "Cash Ratio", "السيولة", "Liquidity", stmt.cashAndBank, stmt.currentLiabilities, 1, "x", "مرة", "Cash / Current Liabilities"),
    ratio("working_capital_ratio", "رأس المال العامل للأصول", "Working Capital Ratio", "السيولة", "Liquidity", stmt.currentAssets - stmt.currentLiabilities, stmt.totalAssets, 100, "%", "%", "Working Capital / Total Assets"),
    ratio("gross_profit_margin", "هامش مجمل الربح", "Gross Profit Margin", "الربحية", "Profitability", stmt.grossProfit, stmt.revenue, 100, "%", "%", "Gross Profit / Revenue"),
    ratio("operating_profit_margin", "هامش الربح التشغيلي", "Operating Margin", "الربحية", "Profitability", stmt.operatingProfit, stmt.revenue, 100, "%", "%", "Operating Profit / Revenue"),
    ratio("net_profit_margin", "هامش صافي الربح", "Net Profit Margin", "الربحية", "Profitability", stmt.netProfit, stmt.revenue, 100, "%", "%", "Net Profit / Revenue"),
    ratio("return_on_assets_roa", "العائد على الأصول", "Return on Assets (ROA)", "الربحية", "Profitability", stmt.netProfit, stmt.totalAssets, 100, "%", "%", "Net Profit / Total Assets"),
    ratio("return_on_equity_roe", "العائد على الملكية", "Return on Equity (ROE)", "الربحية", "Profitability", stmt.netProfit, stmt.totalEquityWithNetIncome, 100, "%", "%", "Net Profit / Total Equity"),
    ratio("return_on_capital_employed", "العائد على رأس المال المستثمر", "ROCE", "الربحية", "Profitability", ebit, stmt.totalAssets - stmt.currentLiabilities, 100, "%", "%", "EBIT / Capital Employed"),
    ratio("inventory_turnover", "معدل دوران المخزون", "Inventory Turnover", "الكفاءة", "Efficiency", stmt.cogs, stmt.inventoryBookValue, 1, "x", "مرة", "COGS / Inventory Book Value"),
    ratio("dio_days_inventory", "أيام بقاء المخزون", "Days Inventory (DIO)", "الكفاءة", "Efficiency", stmt.inventoryBookValue * 365, stmt.cogs, 1, "days", "يوم", "(Inventory / COGS) * 365"),
    ratio("receivables_turnover", "معدل دوران الذمم", "Receivables Turnover", "الكفاءة", "Efficiency", stmt.revenue, stmt.receivables, 1, "x", "مرة", "Revenue / Receivables"),
    ratio("dso_days_sales_outstanding", "أيام تحصيل الذمم", "Days Sales (DSO)", "الكفاءة", "Efficiency", stmt.receivables * 365, stmt.revenue, 1, "days", "يوم", "(Receivables / Revenue) * 365"),
    ratio("asset_turnover", "معدل دوران الأصول", "Asset Turnover", "الكفاءة", "Efficiency", stmt.revenue, stmt.totalAssets, 1, "x", "مرة", "Revenue / Total Assets"),
    ratio("debt_to_equity", "الدين إلى حقوق الملكية", "Debt to Equity", "الرفع المالي", "Leverage", stmt.totalLiabilities, stmt.totalEquityWithNetIncome, 1, "x", "مرة", "Total Liabilities / Equity"),
    ratio("debt_ratio", "نسبة الديون إلى الأصول", "Debt Ratio", "الرفع المالي", "Leverage", stmt.totalLiabilities, stmt.totalAssets, 100, "%", "%", "Total Liabilities / Total Assets"),
    ratio("interest_coverage", "تغطية الفوائد", "Interest Coverage", "الرفع المالي", "Leverage", ebit, stmt.interestAndOther, 1, "x", "مرة", "EBIT / Interest & Finance Cost")
  ];
}

export function computeTrialCountdown(trialEndsAtMillis, nowMillis) {
  const diff = Math.max(0, trialEndsAtMillis - nowMillis);
  const totalSecs = Math.floor(diff / 1000);
  const days = Math.floor(totalSecs / 86400);
  const hours = Math.floor((totalSecs % 86400) / 3600);
  const minutes = Math.floor((totalSecs % 3600) / 60);
  const seconds = totalSecs % 60;
  return { days, hours, minutes, seconds, isExpired: diff <= 0 };
}
