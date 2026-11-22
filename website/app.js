import * as Engine from "./engine.js";
import { FVG_FIREBASE_CONFIG, isFirebaseWebConfigured } from "./firebase-config.js";

const STORAGE_KEY = "fvg_web_workspace_v3";
const SESSION_KEY = "fvg_web_session_id";
const APPROVED_ARABIC_ABOUT_US =
  "في ظل متغيّرات اقتصادية متسارعة وضغوط تضخمية حادة تهدد استمرارية المشاريع، يأتي 'دليل رؤيتك المالي' ليكون بوصلتك نحو الاستقرار المالي. نحن لا نكتفي بحساب الأرقام، بل نسهل لك اتخاذ قرارات التسعير الصحيحة بشكل تلقائي ومدروس، لنحول معادلات التكاليف المعقدة إلى أدوات عملية دقيقة تضمن لك الحفاظ على رأس المال، تعظيم الأرباح، والعبور بمشروعك نحو بر الأمان بكل ثقة واحترافية.";

const DEFAULT_COA = [
  { code: "1010", name: "Cash & Bank", nameAr: "النقدية والبنوك", accountType: "asset", subType: "cash", normalBalance: "debit", openingBalance: "0.00" },
  { code: "1110", name: "Accounts Receivable", nameAr: "الذمم المدينة", accountType: "asset", subType: "receivable", normalBalance: "debit", openingBalance: "0.00" },
  { code: "1210", name: "Inventory", nameAr: "المخزون", accountType: "asset", subType: "inventory", normalBalance: "debit", openingBalance: "0.00" },
  { code: "1510", name: "Property & Equipment", nameAr: "الأصول الثابتة", accountType: "asset", subType: "fixed_asset", normalBalance: "debit", openingBalance: "0.00" },
  { code: "2010", name: "Accounts Payable", nameAr: "الذمم الدائنة", accountType: "liability", subType: "current_liability", normalBalance: "credit", openingBalance: "0.00" },
  { code: "2510", name: "Long-Term Loans", nameAr: "قروض طويلة الأجل", accountType: "liability", subType: "long_term_liability", normalBalance: "credit", openingBalance: "0.00" },
  { code: "3010", name: "Owner Capital", nameAr: "رأس المال", accountType: "equity", subType: "equity", normalBalance: "credit", openingBalance: "0.00" },
  { code: "4010", name: "Sales Revenue", nameAr: "إيرادات المبيعات", accountType: "revenue", subType: "revenue", normalBalance: "credit", openingBalance: "0.00" },
  { code: "5010", name: "Cost of Goods Sold", nameAr: "تكلفة البضاعة المباعة", accountType: "cogs", subType: "cogs", normalBalance: "debit", openingBalance: "0.00" },
  { code: "6010", name: "Operating & Admin Expenses", nameAr: "مصروفات تشغيلية وإدارية", accountType: "expense", subType: "operating_expense", normalBalance: "debit", openingBalance: "0.00" },
  { code: "6210", name: "Inventory Loss & Scrap", nameAr: "خسائر الهدر والتلف", accountType: "expense", subType: "operating_expense", normalBalance: "debit", openingBalance: "0.00" },
  { code: "7010", name: "Finance & Interest Expense", nameAr: "تكاليف التمويل والفوائد", accountType: "expense", subType: "interest", normalBalance: "debit", openingBalance: "0.00" }
];

const DEFAULT_PLANS = [
  { code: "starter", nameAr: "الباقة الأساسية", nameEn: "Starter", priceSdg: "12000", priceUsd: "12.00", durationDays: 30 },
  { code: "professional", nameAr: "الباقة الاحترافية", nameEn: "Professional", priceSdg: "29000", priceUsd: "29.00", durationDays: 30 },
  { code: "enterprise", nameAr: "باقة المؤسسات", nameEn: "Enterprise", priceSdg: "79000", priceUsd: "79.00", durationDays: 30 }
];

function getOrCreateSessionId() {
  try {
    let sid = localStorage.getItem(SESSION_KEY);
    if (!sid) {
      sid = "sess_" + Math.random().toString(36).slice(2, 10) + "_" + Date.now();
      localStorage.setItem(SESSION_KEY, sid);
    }
    return sid;
  } catch {
    return "sess_browser";
  }
}

function loadSavedState() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw);
  } catch {}
  return null;
}

const saved = loadSavedState();

const state = {
  stage: saved?.user && saved?.activeOrg ? "app" : "splash", // "splash" | "public" | "app"
  secondaryView: null, // null | "profile" | "help" | "owner_admin"
  showCalculator: false,
  showTour: false,
  tourStep: 0,
  isArabic: saved?.isArabic ?? true, // Arabic RTL default
  themeMode: saved?.themeMode ?? "system", // "system" | "light" | "dark"
  fontScale: saved?.fontScale ?? 1.0,
  publicPage: 0,
  appTab: 0, // 0 Home, 1 Prices, 2 Costing, 3 Ledger, 4 Stock, 5 Assistant/Reports/Org
  subTab: { prices: 0, costing: 0, ledger: 0, stock: 0, reports: 1 },
  user: saved?.user ?? null,
  organizations: saved?.organizations ?? (saved?.activeOrg ? [saved.activeOrg] : []),
  activeOrg: saved?.activeOrg ?? null,
  sessions: saved?.sessions ?? [{ id: getOrCreateSessionId(), label: navigator.userAgent.slice(0, 48), platform: "Web PWA", lastActive: new Date().toISOString().slice(0, 16), revoked: false }],
  products: saved?.products ?? [],
  priceObservations: saved?.priceObservations ?? [],
  inflationRecords: saved?.inflationRecords ?? [],
  baskets: saved?.baskets ?? [],
  fxRates: saved?.fxRates ?? [],
  costSnapshots: saved?.costSnapshots ?? [],
  bomFormulas: saved?.bomFormulas ?? [],
  scenarios: saved?.scenarios ?? [],
  accounts: saved?.accounts ?? DEFAULT_COA,
  journalEntries: saved?.journalEntries ?? [],
  counterpartyItems: saved?.counterpartyItems ?? [],
  inventoryItems: saved?.inventoryItems ?? [],
  stocktakes: saved?.stocktakes ?? [],
  scrapLosses: saved?.scrapLosses ?? [],
  budgets: saved?.budgets ?? [],
  paymentRequests: saved?.paymentRequests ?? [],
  ocrDrafts: saved?.ocrDrafts ?? [],
  pendingOcrDataUrl: null,
  auditLogs: saved?.auditLogs ?? [],
  aiConversations: saved?.aiConversations ?? [],
  activeConversationId: saved?.activeConversationId ?? null,
  aiMode: "financial",
  aiResponse: "",
  chatTranscript: saved?.chatTranscript ?? [],
  platformPlans: saved?.platformPlans ?? DEFAULT_PLANS,
  announcements: saved?.announcements ?? [],
  supportTickets: saved?.supportTickets ?? [],
  costDraftMethod: "actual",
  selectedReport: "statements",
  toast: null,
  calc: { expr: "", display: "0", memory: 0, currencyFmt: true, history: [] }
};

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      isArabic: state.isArabic,
      themeMode: state.themeMode,
      fontScale: state.fontScale,
      user: state.user,
      organizations: state.organizations,
      activeOrg: state.activeOrg,
      sessions: state.sessions,
      products: state.products,
      priceObservations: state.priceObservations,
      inflationRecords: state.inflationRecords,
      baskets: state.baskets,
      fxRates: state.fxRates,
      costSnapshots: state.costSnapshots,
      bomFormulas: state.bomFormulas,
      scenarios: state.scenarios,
      accounts: state.accounts,
      journalEntries: state.journalEntries,
      counterpartyItems: state.counterpartyItems,
      inventoryItems: state.inventoryItems,
      stocktakes: state.stocktakes,
      scrapLosses: state.scrapLosses,
      budgets: state.budgets,
      paymentRequests: state.paymentRequests,
      ocrDrafts: state.ocrDrafts,
      auditLogs: state.auditLogs,
      aiConversations: state.aiConversations,
      activeConversationId: state.activeConversationId,
      chatTranscript: state.chatTranscript,
      platformPlans: state.platformPlans,
      announcements: state.announcements,
      supportTickets: state.supportTickets
    }));
  } catch {}
}

function isPlatformOwner() {
  return String(state.user?.email || "").trim().toLowerCase() === Engine.OWNER_EMAIL.toLowerCase();
}

function logAudit(action, entityType, summary) {
  state.auditLogs.unshift({
    id: "aud_" + Date.now(),
    action,
    entityType,
    summary,
    actorEmail: state.user?.email || "workspace@fvg",
    at: new Date().toISOString().slice(0, 19)
  });
}

function showToast(msg) {
  state.toast = msg;
  render();
  setTimeout(() => {
    if (state.toast === msg) {
      state.toast = null;
      render();
    }
  }, 3400);
}

function effectiveTrialDurationHours() {
  return state.activeOrg?.trialDurationHours || state.user?.trialDurationHours || Engine.TRIAL_DURATION_HOURS;
}

function effectiveTrialEndsMillis() {
  return Engine.computeServerSyncedTrialEndMillis(
    state.user?.trialStartedAtMillis,
    state.activeOrg?.trialEndsAtMillis,
    effectiveTrialDurationHours()
  );
}

function isTrialLocked() {
  const org = state.activeOrg;
  if (!org) return false;
  if (org.subscriptionStatus === "active") return false;
  if (org.subscriptionStatus === "expired" || org.subscriptionStatus === "cancelled") return true;
  const ends = effectiveTrialEndsMillis();
  return ends ? Date.now() >= ends : false;
}

function esc(str) {
  return String(str ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function t(en, ar) {
  return state.isArabic ? ar : en;
}

function localizeStatus(st) {
  if (!state.isArabic) return st;
  const m = {
    draft: "مسودة",
    approved: "معتمد",
    cancelled: "ملغى",
    posted: "مرحّل",
    reversed: "معكوس",
    pending_verification: "قيد التحقق",
    rejected: "مرفوض",
    active: "نشط",
    trial: "تجربة",
    expired: "منتهي",
    pending_review: "قيد المراجعة",
    verified: "معتمد",
    open: "مفتوح",
    in_review: "قيد المراجعة",
    replied: "تم الرد",
    resolved: "محلول"
  };
  return m[String(st).toLowerCase()] || st;
}

function localizeCostMethod(m) {
  if (!state.isArabic) return m.replace("_", " ").toUpperCase();
  const map = {
    actual: "التكلفة الفعلية",
    standard: "التكلفة المعيارية",
    target: "التكلفة المستهدفة",
    abc: "التكلفة حسب الأنشطة (ABC)",
    job_order: "تكلفة الأوامر الإنتاجية",
    process: "تكلفة المراحل",
    absorption: "التكلفة الكلية الامتصاصية",
    variable: "التكلفة المتغيرة"
  };
  return map[m] || m;
}

function renderTrialBanner(preview = false) {
  const hours = preview ? Engine.TRIAL_DURATION_HOURS : effectiveTrialDurationHours();
  const daysNum = Math.max(1, Math.round(hours / 24));
  const target = preview ? Date.now() + Engine.TRIAL_DURATION_MILLIS : effectiveTrialEndsMillis();
  if (!target) return "";
  const cd = Engine.computeTrialCountdown(target, Date.now());
  const pad = (v) => String(v).padStart(2, "0");
  return `
    <div class="card cream" data-trial-banner="${preview ? "preview" : target}">
      <div class="row-between">
        <strong>${cd.isExpired ? t(`${daysNum}-Day (${hours}h) Trial Ended`, `انتهت الفترة التجريبية (${daysNum} أيام / ${hours} ساعة)`) : (preview ? t(`Full ${daysNum}-Day (${hours}-Hour) Free Trial`, `فترة تجربة كاملة لمدة ${daysNum} أيام (${hours} ساعة)`) : t(`${daysNum}-Day (${hours}h) Active Trial`, `الفترة التجريبية النشطة (${daysNum} أيام)`))}</strong>
        <span class="badge ${cd.isExpired ? "high" : "low"}">${cd.isExpired ? t("EXPIRED", "منتهية") : `${hours}H`}</span>
      </div>
      <div class="grid-4" style="margin-top:8px;">
        <div class="timer-box"><strong data-cd="d">${pad(cd.days)}</strong><span>${t("Days", "أيام")}</span></div>
        <div class="timer-box"><strong data-cd="h">${pad(cd.hours)}</strong><span>${t("Hours", "ساعات")}</span></div>
        <div class="timer-box"><strong data-cd="m">${pad(cd.minutes)}</strong><span>${t("Mins", "دقائق")}</span></div>
        <div class="timer-box"><strong data-cd="s">${pad(cd.seconds)}</strong><span>${t("Secs", "ثواني")}</span></div>
      </div>
    </div>
  `;
}

function renderSplash() {
  return `
    <div class="fvg-splash" id="splashRoot">
      <svg class="fvg-splash-rays" viewBox="0 0 400 800" preserveAspectRatio="none">
        <polygon points="224,0 320,0 136,352 64,352" fill="#517D77" fill-opacity="0.08"/>
        <polygon points="336,0 400,0 400,112 208,400 152,400" fill="#517D77" fill-opacity="0.06"/>
        <line x1="300" y1="92" x2="300" y2="160" stroke="#517D77" stroke-opacity="0.16" stroke-width="3"/>
        <rect x="295" y="106" width="10" height="42" rx="3" fill="#517D77" fill-opacity="0.16"/>
        <line x1="324" y1="74" x2="324" y2="148" stroke="#517D77" stroke-opacity="0.16" stroke-width="3"/>
        <rect x="319" y="88" width="10" height="48" rx="3" fill="#517D77" fill-opacity="0.16"/>
        <line x1="348" y1="54" x2="348" y2="132" stroke="#517D77" stroke-opacity="0.16" stroke-width="3"/>
        <rect x="343" y="68" width="10" height="54" rx="3" fill="#517D77" fill-opacity="0.16"/>
        <line x1="372" y1="34" x2="372" y2="118" stroke="#DDB065" stroke-opacity="0.24" stroke-width="3"/>
        <rect x="367" y="48" width="10" height="58" rx="3" fill="#DDB065" fill-opacity="0.24"/>
      </svg>
      <div class="fvg-splash-center">
        <img class="fvg-splash-logo" src="./assets/fvg-icon.svg" alt="FVG Logo"/>
        <h1 class="fvg-splash-title">FVG</h1>
        <div class="fvg-splash-sub-en">Financial Vision Guide</div>
        <div class="fvg-splash-sub-ar">
          <span class="fvg-gold-line"></span>
          <span>دليل رؤيتك المالي</span>
          <span class="fvg-gold-line"></span>
        </div>
        <p class="fvg-splash-tagline">${t("Smarter Decisions.<br>Stronger Financial Future.", "قرارات أذكى.<br>مستقبل مالي أقوى.")}</p>
      </div>
      <svg class="fvg-splash-waves" viewBox="0 0 400 200" preserveAspectRatio="none">
        <path d="M0,45 C110,0 270,95 400,25 L400,200 L0,200 Z" fill="#15423A"/>
        <path d="M0,78 C120,32 280,125 400,60 L400,200 L0,200 Z" fill="#DDB065"/>
        <path d="M0,96 C120,50 280,142 400,78 L400,200 L0,200 Z" fill="#022A27"/>
      </svg>
      <div class="fvg-splash-footer">${t("Tap anywhere to continue", "انقر في أي مكان للمتابعة")}</div>
    </div>
  `;
}

function renderBanner(title, subtitle, authenticated = false) {
  const nextThemeLabel = state.themeMode === "dark" ? "☀" : (state.themeMode === "light" ? "☾" : "◐");
  const avatarHtml = authenticated
    ? (state.user?.avatarDataUrl
        ? `<img src="${state.user.avatarDataUrl}" alt="avatar" style="width:36px;height:36px;border-radius:50%;object-fit:cover;border:2px solid #DDB065;cursor:pointer;" data-action="openProfile"/>`
        : `<button class="icon-pill" data-action="openProfile" title="${t("Profile", "ملفي الشخصي")}">👤</button>`)
    : "";
  return `
    <div class="fvg-banner">
      <div style="display:flex;align-items:center;gap:10px;min-width:0;">
        ${avatarHtml}
        <div style="min-width:0;">
          <div style="font-size:1.05rem;font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(title)}</div>
          <div style="font-size:0.75rem;color:#DDB065;">${esc(subtitle)}</div>
        </div>
      </div>
      <div class="fvg-banner-actions">
        ${authenticated ? `<button class="icon-pill" data-action="openCalc" title="${t("Calculator", "الآلة الحاسبة")}">🧮</button>` : ""}
        ${authenticated ? `<button class="icon-pill" data-action="openHelp" title="${t("Help", "المساعدة")}">؟</button>` : ""}
        <button class="icon-pill" data-action="toggleLang">${state.isArabic ? "EN" : "عربي"}</button>
        <button class="icon-pill" data-action="cycleTheme" title="${t("Theme: " + state.themeMode, "المظهر: " + state.themeMode)}">${nextThemeLabel}</button>
      </div>
    </div>
  `;
}

function renderPublicSite() {
  const pages = [
    t("Home", "الرئيسية"),
    t("Features & Sectors", "المزايا والقطاعات"),
    t("Plans & Pay", "الباقات والدفع"),
    t("About FVG", "عن المنصة"),
    t("9-Step Guide", "دليل 9 خطوات"),
    t("Contact", "تواصل معنا"),
    t("Privacy", "الخصوصية"),
    t("Terms", "الشروط")
  ];
  let body = "";
  if (state.publicPage === 0) {
    body = `
      <div class="card hero">
        <div style="display:flex;align-items:center;gap:12px;">
          <img src="./assets/fvg-icon.svg" style="width:52px;height:52px;border-radius:12px;background:#FAF2E8;padding:4px;" alt="FVG"/>
          <div>
            <h2 style="margin:0;">${t("FVG — Management Accounting & Pricing", "منصة FVG للمحاسبة الإدارية والتسعير")}</h2>
            <div style="color:#DDB065;font-size:0.82rem;">${t("Installable on iPhone (Safari -> Add to Home Screen), Android & Web", "تثبيت مباشر على iPhone (Safari -> إضافة للشاشة الرئيسية) وAndroid والكمبيوتر")}</div>
          </div>
        </div>
        <p style="font-size:0.9rem;line-height:1.65;">${t("8 Costing Methods, Financial Risk Engine, Price & Inflation Monitoring, Manual FX Registry, Double-Entry Ledger, 18 Financial Ratios, FIFO/MWA Stock, Camera OCR & Financial Assistant.", "8 طرق للتكلفة والتركيبات، محرك المخاطر المالية، مراقبة الأسعار والتضخم، الصرف اليدوي، القيود والقوائم والـ 18 نسبة، المخزون FIFO/MWA، قراءة الفواتير بالكاميرا، والمساعد المالي الذكي.")}</p>
        <div class="grid-2">
          <div class="field-group">
            <label style="color:#DDB065;">${t("Workspace Email (Starts 7-Day / 168h Trial)", "البريد الإلكتروني (تجربة 7 أيام / 168 ساعة)")}</label>
            <input id="authEmailInput" type="email" placeholder="name@company.com" value="${esc(state.user?.email || "")}" />
          </div>
          <div class="field-group">
            <label style="color:#DDB065;">${t("Password (6+ characters)", "كلمة المرور (6 أحرف على الأقل)")}</label>
            <input id="authPassInput" type="password" placeholder="••••••" />
          </div>
        </div>
        <div class="grid-2">
          <div class="field-group">
            <label style="color:#DDB065;">${t("Full Name (for Registration)", "الاسم الكامل (عند إنشاء حساب جديد)")}</label>
            <input id="authNameInput" type="text" placeholder="${t("Your Name", "الاسم الكامل")}" value="${esc(state.user?.displayName || "")}" />
          </div>
          <div class="field-group">
            <label style="color:#DDB065;">${t("Organization Name (SDG Default)", "اسم المنظمة / الشركة (الافتراضي SDG)")}</label>
            <input id="authOrgInput" type="text" placeholder="${t("My Company", "شركتي")}" value="${esc(state.activeOrg?.name || "")}" />
          </div>
        </div>
        <div class="grid-2">
          <button class="btn btn-gold" data-action="launchEmailSignIn">${t("Sign In / Create Account (7-Day Trial)", "دخول / إنشاء حساب (تجربة 7 أيام)")}</button>
          <button class="btn btn-outline" style="color:#FAF2E8;border-color:#DDB065;" data-action="launchGoogle">${isFirebaseWebConfigured() ? t("Sign In with Google", "الدخول عبر حساب Google") : t("Start Local Browser Workspace", "بدء مساحة عمل محلية سريعة")}</button>
        </div>
        <div style="margin-top:8px;text-align:center;">
          <button class="btn btn-outline" style="color:#DDB065;border-color:rgba(221,176,101,0.45);min-height:40px;" data-action="resetPassword">${t("Forgot Password?", "نسيت كلمة المرور؟")}</button>
        </div>
      </div>
      ${renderTrialBanner(true)}
    `;
  } else if (state.publicPage === 1) {
    body = `
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Supported Business Sectors", "القطاعات المدعومة وخارطة التوسع")}</h3>
        ${Engine.FVG_SECTORS.map((s) => `<div class="row-between"><span>• ${esc(state.isArabic ? s.nameAr : s.nameEn)}</span><span class="badge ${s.active ? "low" : ""}">${s.active ? t("Active", "مفعّل") : t("Coming Soon", "قريباً")}</span></div>`).join("")}
      </div>
    `;
  } else if (state.publicPage === 2) {
    body = `
      ${renderTrialBanner(true)}
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Subscription Plans & Transparent Verification", "الباقات والتحقق الشفاف من الدفع")}</h3>
        <p>${t("Every workspace receives a full 7-day (168-hour) free trial. Upgrades use Bankak, O-Cash, or Bank Transfer proof submission with optional Last 4 Digits and are reviewed exclusively by the Platform Owner.", "تبدأ كل منظمة بفترة تجربة 7 أيام (168 ساعة). تتم ترقية الباقة برفع إثبات بنكك أو أوكاش أو تحويل بنكي مع آخر 4 أرقام للمطابقة وتتم مراجعتها عبر مالكة المنصة.")}</p>
        ${state.platformPlans.map((p) => `<div class="row-between"><strong>${esc(state.isArabic ? p.nameAr : p.nameEn)}</strong><span class="badge">${esc(p.priceSdg)} SDG / $${esc(p.priceUsd)} (${p.durationDays} ${t("days", "يوم")})</span></div>`).join("")}
      </div>
    `;
  } else if (state.publicPage === 3) {
    body = `
      <div class="card">
        <h3 style="margin-top:0;">${t("About Financial Vision Guide (FVG)", "رؤية دليل رؤيتك المالي (FVG)")}</h3>
        <p style="line-height:1.75;">${APPROVED_ARABIC_ABOUT_US}</p>
        <p style="color:var(--fvg-text-muted);font-size:0.88rem;">${t("Financial Vision Guide turns complex cost equations into precise, inflation-aware pricing and accounting tools that safeguard capital and protect operating margins.", "")}</p>
      </div>
    `;
  } else if (state.publicPage === 4) {
    const stepsEn = [
      "1. Sign in with Email or Google (SDG default) to activate your 7-day (168-hour) server-synced trial.",
      "2. Record product prices with append-only history, CSV/XLSX import, and >= 15% unusual spike alerts.",
      "3. Log monthly/annual inflation rates & weighted input baskets.",
      "4. Document manual FX rates (Draft / Approved / Cancelled) with stale (>7 days) reminders.",
      "5. Build product BOM recipes and compute costs across all 8 Costing Methods + Risk Engine.",
      "6. Run What-If sensitivity simulations across materials, FX, price, and volume.",
      "7. Post balanced Double-Entry Journals to render Income Statement, Balance Sheet & 18 Ratios.",
      "8. Track FIFO / Moving Average inventory, batch expiry alerts & replacement capital gap.",
      "9. Scan invoices via camera OCR, query the Financial Assistant, and export CSV/JSON/PDF."
    ];
    const stepsAr = [
      "١. سجّل بالبريد الإلكتروني أو حساب Google لتفعيل الفترة التجريبية الكاملة لمدة 7 أيام (168 ساعة).",
      "٢. وثّق أسعار المنتجات والمواد الخام دون مسح التاريخ واستورد CSV مع تنبيه القفزات >= 15%.",
      "٣. احسب التضخم المركب وسلات التكاليف الموزونة.",
      "٤. سجّل أسعار الصرف يدوياً (مسودة / معتمد / ملغى) مع تنبيه الأسعار الأقدم من 7 أيام.",
      "٥. أنشئ تركيبات المنتجات (BOM) واحسب التكلفة بالـ 8 طرق مع محرك المخاطر ونقطة التعادل.",
      "٦. اختبر سيناريوهات ماذا لو لصدمات المواد وسعر الصرف والحجم.",
      "٧. رحّل قيوداً مزدوجة متوازنة لاستخراج قائمة الدخل والميزانية و18 نسبة مالية.",
      "٨. راقب المخزون FIFO/المتوسط المرجح وتواريخ الصلاحية وفجوة تكلفة الاستبدال.",
      "٩. صوّر الفواتير بالكاميرا (OCR) واستشر المساعد المالي وصدّر التقارير."
    ];
    body = `<div class="card cream"><h3 style="margin-top:0;">${t("9-Step Operational Guide", "دليل التشغيل السريع في 9 خطوات")}</h3>${(state.isArabic ? stepsAr : stepsEn).map((st) => `<p style="margin:6px 0;">${esc(st)}</p>`).join("")}</div>`;
  } else if (state.publicPage === 5) {
    body = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Official Support Contact", "التواصل والدعم الرسمي")}</h3>
        <p><strong>${t("Official Email:", "البريد الرسمي للدعم:")}</strong> Financialvisionguide@gmail.com</p>
        <p style="font-size:0.86rem;color:var(--fvg-text-muted);">${t("Sign in to submit tracked support tickets directly to the Platform Owner console and view replies in-app.", "بعد تسجيل الدخول يمكنك إرسال تذكرة دعم ومتابعة الرد مباشرة داخل التطبيق.")}</p>
        <a class="btn btn-primary" href="mailto:Financialvisionguide@gmail.com?subject=FVG%20Support">${t("Open Email Client", "إرسال بريد إلكتروني")}</a>
      </div>
    `;
  } else if (state.publicPage === 6) {
    body = `<div class="card"><h3 style="margin-top:0;">${t("Privacy & Tenant Isolation", "الخصوصية وعزل البيانات")}</h3><p>${t("Each organization's financial records, products, inventory, and documents remain strictly isolated by membership rules.", "تُحفظ بيانات كل منظمة بعزل كامل وفق قواعد الصلاحيات ولا تتم مشاركتها مع أي منظمة أخرى.")}</p></div>`;
  } else {
    body = `<div class="card"><h3 style="margin-top:0;">${t("Terms & Methodology", "الشروط والمنهجية المحاسبية")}</h3><p>${t("What-If simulations show hypothetical scenario outcomes rather than predictions. Posted journals are immutable and corrected via reversing entries.", "نتائج المحاكاة توضح أثر السيناريوهات الافتراضية وليست تنبؤاً، وتُصحح القيود المرحّلة عبر قيود العكس فقط.")}</p></div>`;
  }

  return `
    <div class="main-content">
      ${renderBanner("FVG • دليل رؤيتك المالي", "Financial Vision Guide", false)}
      ${state.toast ? `<div class="card cream" style="border-color:var(--fvg-warm-gold);font-weight:700;">${esc(state.toast)}</div>` : ""}
      <div class="subtabs-sticky">
        <div class="subtabs">
          ${pages.map((p, i) => `<button class="subtab ${state.publicPage === i ? "active" : ""}" data-public-page="${i}">${esc(p)}</button>`).join("")}
        </div>
      </div>
      ${body}
    </div>
  `;
}

function renderStatementsAndRatios() {
  return Engine.generateFinancialStatements(state.accounts, state.journalEntries);
}

function renderTrialGateCard() {
  const hours = effectiveTrialDurationHours();
  const days = Math.max(1, Math.round(hours / 24));
  return `
    <div class="card cream" style="border-color:var(--fvg-negative);">
      <h3 style="color:var(--fvg-negative);margin-top:0;">${t(`${days}-Day (${hours}-Hour) Trial Ended`, `انتهت الفترة التجريبية (${days} أيام / ${hours} ساعة)`)}</h3>
      <p>${t("Submit a Bankak, O-Cash, or Bank Transfer proof in Assistant -> Org & Billing for Platform Owner verification.", "يرجى إرسال إثبات الدفع (بنكك / أوكاش / تحويل بنكي) في المساعد المالي -> المنظمة والدفع للمراجعة عبر مالكة المنصة.")}</p>
      <button class="btn btn-gold" data-action="goBilling">${t("Upgrade Plan & Submit Payment Proof", "ترقية الباقة وإرسال إثبات الدفع")}</button>
    </div>
  `;
}

function renderHomeTab() {
  const org = state.activeOrg;
  const curr = org?.baseCurrency || "SDG";
  const stmt = renderStatementsAndRatios();
  const invBook = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.avgCost), 0));
  const invRepl = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.marketPrice || it.avgCost), 0));
  const portfolio = Engine.money(stmt.cashAndBank + invBook);
  const latestInf = state.inflationRecords[0];
  const latestSnap = state.costSnapshots[state.costSnapshots.length - 1];
  const risk = latestSnap
    ? Engine.evaluateFinancialRisk(latestSnap.coreResult.profitMarginPct, latestInf ? latestInf.monthlyRate : null, stmt.totalLiabilities, stmt.totalEquityWithNetIncome, state.isArabic)
    : null;
  const activeAnn = state.announcements.filter((a) => a.active);

  return `
    ${isTrialLocked() ? renderTrialGateCard() : renderTrialBanner(false)}
    ${activeAnn.map((a) => `
      <div class="card cream" style="border-color:var(--fvg-warm-gold);">
        <div class="row-between"><strong>📢 ${esc(state.isArabic ? (a.titleAr || a.titleEn) : (a.titleEn || a.titleAr))}</strong><span class="badge">${t("Announcement", "إعلان")}</span></div>
        <div style="font-size:0.88rem;margin-top:4px;">${esc(state.isArabic ? (a.bodyAr || a.bodyEn) : (a.bodyEn || a.bodyAr))}</div>
      </div>
    `).join("")}
    <div class="card hero">
      <div class="row-between">
        <span style="color:#DDB065;font-weight:700;">${t("Total Cash & Inventory Book Value", "إجمالي النقدية والقيمة الدفترية للمخزون")}</span>
        <span class="badge" style="color:#DDB065;border-color:#DDB065;">${esc(curr)}</span>
      </div>
      <div style="font-size:1.9rem;font-weight:800;margin:6px 0;">${portfolio} ${esc(curr)}</div>
      <div style="font-size:0.85rem;">${t("Cash", "النقدية")}: ${stmt.cashAndBank} • ${t("Inventory Book", "المخزون الدفتري")}: ${invBook}</div>
      <div style="font-size:0.78rem;color:#DDB065;margin-top:4px;">${t("Replacement Estimate", "تكلفة الاستبدال الحالية")}: ${invRepl} ${esc(curr)}</div>
    </div>
    <div class="grid-3">
      <div class="card"><div style="font-size:0.78rem;color:var(--fvg-text-muted);">${t("Revenue", "الإيرادات")}</div><strong style="font-size:1.25rem;color:var(--fvg-positive);">${stmt.revenue}</strong></div>
      <div class="card"><div style="font-size:0.78rem;color:var(--fvg-text-muted);">${t("COGS & Expenses", "التكاليف والمصروفات")}</div><strong style="font-size:1.25rem;color:var(--fvg-warning);">${Engine.money(stmt.cogs + stmt.operatingExpenses)}</strong></div>
      <div class="card"><div style="font-size:0.78rem;color:var(--fvg-text-muted);">${t("Net Profit", "صافي الربح")}</div><strong style="font-size:1.25rem;color:${stmt.netProfit < 0 ? "var(--fvg-negative)" : "var(--fvg-positive)"};">${stmt.netProfit}</strong></div>
    </div>
    ${risk ? `
      <div class="card cream">
        <div class="row-between">
          <strong>${t("Financial Risk Engine", "محرك تقييم المخاطر المالية")} (${esc(latestSnap.productName)})</strong>
          <span class="badge ${risk.level}">${t(risk.level.toUpperCase() + " RISK", risk.level === "high" ? "خطر مرتفع" : risk.level === "medium" ? "خطر متوسط" : "منخفض")}</span>
        </div>
        <div style="font-size:0.84rem;margin-top:4px;">${esc(risk.ruleName)}: ${esc(risk.actualValue)} (${t("Threshold", "الحد")} ${esc(risk.thresholdValue)})</div>
        <div style="font-size:0.84rem;color:var(--fvg-text-muted);">${esc(risk.explanation)}</div>
      </div>
    ` : ""}
    <div class="card">
      <h3 style="margin-top:0;">${t("Quick Shortcuts & Tools", "اختصارات سريعة للأدوات")}</h3>
      <div class="grid-3">
        <button class="btn btn-outline" data-tab="1">${t("Prices", "الأسعار")}</button>
        <button class="btn btn-outline" data-tab="2">${t("Costing", "التكاليف")}</button>
        <button class="btn btn-outline" data-tab="3">${t("Ledger", "المحاسبة")}</button>
        <button class="btn btn-outline" data-tab="4">${t("Stock", "المخزون")}</button>
        <button class="btn btn-outline" data-tab="5">${t("Financial Assistant", "المساعد المالي")}</button>
        <button class="btn btn-gold" data-action="openCalc">🧮 ${t("Calculator", "الآلة الحاسبة")}</button>
        <button class="btn btn-outline" data-action="openProfile">👤 ${t("My Profile", "ملفي الشخصي")}</button>
        <button class="btn btn-outline" data-action="openHelp">❓ ${t("Help & Support", "المساعدة والدعم")}</button>
        <button class="btn btn-outline" data-action="replayTour">🧭 ${t("Start Tour", "جولة تعريفية")}</button>
        ${isPlatformOwner() ? `<button class="btn btn-primary" data-action="openOwnerAdmin">🛡 ${t("Owner Console", "لوحة مالكة المنصة")}</button>` : ""}
      </div>
    </div>
  `;
}

function renderPricesTab() {
  const curr = state.activeOrg?.baseCurrency || "SDG";
  const sub = state.subTab.prices;
  const subLabels = [t("Prices & CSV", "الأسعار وCSV"), t("Inflation & Baskets", "التضخم والسلات"), t("Manual FX", "الصرف اليدوي")];
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Record Product Price Observation", "تسجيل ملاحظة سعر جديدة")}</h3>
        <div class="grid-2">
          <div class="field-group"><label>${t("Product Name", "اسم المنتج")}</label><input id="prName" placeholder="${t("Sugar 50kg", "سكر 50 كجم")}"/></div>
          <div class="field-group"><label>${t("SKU", "الرمز")}</label><input id="prSku" placeholder="SUG-50"/></div>
          <div class="field-group"><label>${t("Category", "الفئة")}</label><input id="prCat" value="${t("Raw Material", "مادة خام")}"/></div>
          <div class="field-group"><label>${t("New Price", "السعر الجديد")} (${esc(curr)})</label><input id="prVal" type="number" step="0.01"/></div>
        </div>
        <button class="btn btn-primary" data-action="savePrice">${t("Save Price Observation", "حفظ السعر وحساب التغير %")}</button>
      </div>
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Import Product Prices via CSV", "استيراد الأسعار عبر CSV")}</h3>
        <p style="font-size:0.8rem;margin-top:0;color:var(--fvg-text-muted);">${t("Format: name,sku,category,price (one item per line)", "التنسيق: اسم المنتج,الرمز,الفئة,السعر (صنف بكل سطر)")}</p>
        <div class="field-group"><input id="csvFile" type="file" accept=".csv,text/csv"/></div>
        <div class="field-group"><textarea id="csvPaste" rows="2" placeholder="Sugar 50kg,SUG-50,Raw Material,42000"></textarea></div>
        <button class="btn btn-gold" data-action="importCsv">${t("Import CSV Rows", "استيراد صفوف CSV")}</button>
      </div>
      ${state.priceObservations.length === 0 ? `<div class="card cream">${t("No price records yet.", "لا توجد أسعار مسجلة بعد.")}</div>` : state.priceObservations.slice().reverse().map((o) => `
        <div class="card">
          <div class="row-between"><strong>${esc(o.productName)} (${esc(o.sku)})</strong><span class="badge ${o.isUnusual ? "high" : "low"}">${o.pctChange == null ? t("NEW", "جديد") : `${o.pctChange}% ${o.direction === "up" ? t("UP", "ارتفاع") : o.direction === "down" ? t("DOWN", "انخفاض") : ""}`}</span></div>
          <div style="font-size:0.85rem;">${o.previousPrice} → ${o.price} ${esc(curr)} • ${esc(o.date)}</div>
        </div>
      `).join("")}
    `;
  } else if (sub === 1) {
    const compound = Engine.calculateCompoundedInflation(state.inflationRecords.map((r) => r.monthlyRate));
    panel = `
      <div class="card cream">
        <div class="row-between"><strong>${t("Cumulative Compounded Inflation", "التضخم المركب التراكمي")}</strong><strong style="color:var(--fvg-negative);">${compound}%</strong></div>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Add Inflation Record", "تسجيل معدل التضخم")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("Period (YYYY-MM)", "الفترة (YYYY-MM)")}</label><input id="infPeriod" value="${new Date().toISOString().slice(0, 7)}"/></div>
          <div class="field-group"><label>${t("Monthly %", "المعدل الشهري %")}</label><input id="infMonthly" type="number" step="0.01"/></div>
          <div class="field-group"><label>${t("Source Type", "نوع المصدر")}</label><select id="infSource"><option value="official">${t("Official", "رسمي")}</option><option value="calculated">${t("Calculated", "محسوب")}</option><option value="manual">${t("Manual", "يدوي")}</option><option value="provisional">${t("Provisional", "تقديري")}</option></select></div>
        </div>
        <button class="btn btn-primary" data-action="saveInflation">${t("Save Inflation Record", "حفظ معدل التضخم")}</button>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Weighted Inflation Price Basket", "سلة الأسعار الموزونة لقياس التضخم")}</h3>
        <div class="grid-4">
          <div class="field-group"><label>${t("Basket Name", "اسم السلة")}</label><input id="bskName" value="${t("Factory Inputs", "مدخلات الإنتاج")}"/></div>
          <div class="field-group"><label>${t("Item", "الصنف")}</label><input id="bskItem" placeholder="${t("Diesel / Sugar", "وقود / سكر")}"/></div>
          <div class="field-group"><label>${t("Base Price", "سعر الأساس")}</label><input id="bskBase" type="number" value="1000"/></div>
          <div class="field-group"><label>${t("Current Price", "السعر الحالي")}</label><input id="bskCurr" type="number" value="1250"/></div>
        </div>
        <div class="grid-2">
          <div class="field-group"><label>${t("Quantity", "الكمية")}</label><input id="bskQty" type="number" value="10"/></div>
          <div class="field-group"><label>${t("Weight", "الوزن")}</label><input id="bskWeight" type="number" step="0.1" value="1"/></div>
        </div>
        <button class="btn btn-gold" data-action="saveBasket">${t("Calculate & Save Weighted Basket", "حساب وحفظ السلة الموزونة")}</button>
      </div>
      ${state.baskets.map((b) => `<div class="card cream"><div class="row-between"><strong>${esc(b.name)} (${esc(b.item)})</strong><span class="badge high">${t("Index", "المؤشر")} ${b.indexValue ?? t("N/A", "غير متاح")} (${b.basketInflationPct ?? 0}%)</span></div><div style="font-size:0.85rem;">${t("Base", "الأساس")}: ${b.baseCost} → ${t("Current", "الحالي")}: ${b.currentCost} ${esc(curr)}</div></div>`).join("")}
      ${state.inflationRecords.map((r) => `<div class="card"><div class="row-between"><strong>${esc(r.period)}</strong><span class="badge">${esc(localizeStatus(r.sourceType))}</span></div><div>${r.monthlyRate}% / ${t("mo", "شهرياً")}</div></div>`).join("")}
    `;
  } else {
    panel = `
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Manual FX Registry (No Electronic Auto-Fetch Errors)", "توثيق أسعار الصرف يدوياً (لتجنب الأخطاء الإلكترونية)")}</h3>
        <p style="font-size:0.82rem;color:var(--fvg-text-muted);margin-top:0;">${t("Enter exchange rates manually as Draft or Approved. Rates older than 7 days trigger a stale-rate warning.", "يُدخل سعر الصرف يدوياً بحالة مسودة أو معتمد دون سحب إلكتروني تلقائي. الأسعار الأقدم من 7 أيام تُظهر تنبيهاً.")}</p>
        <div class="grid-3">
          <div class="field-group"><label>${t("Base Currency", "العملة الأساس")}</label><input id="fxBase" value="USD"/></div>
          <div class="field-group"><label>${t("Quote Currency", "العملة المقابلة")}</label><input id="fxQuote" value="${esc(curr)}"/></div>
          <div class="field-group"><label>${t("Manual Rate", "السعر اليدوي")}</label><input id="fxRate" type="number" step="0.0001"/></div>
          <div class="field-group"><label>${t("Effective Date", "تاريخ السريان")}</label><input id="fxDate" type="date" value="${new Date().toISOString().slice(0, 10)}"/></div>
          <div class="field-group"><label>${t("Source", "المصدر")}</label><input id="fxSource" placeholder="${t("Central Bank / Market", "البنك المركزي / السوق")}"/></div>
          <div class="field-group"><label>${t("Status", "الحالة")}</label><select id="fxStatus"><option value="approved">${t("Approved", "معتمد")}</option><option value="draft">${t("Draft", "مسودة")}</option></select></div>
        </div>
        <button class="btn btn-primary" data-action="saveFx">${t("Save Manual FX Rate", "حفظ سعر الصرف اليدوي")}</button>
      </div>
      ${state.fxRates.map((f) => {
        const fresh = Engine.assessFxRateFreshness(f.date, f.status);
        return `
          <div class="card">
            <div class="row-between"><strong>${esc(f.base)}/${esc(f.quote)} = ${f.rate}</strong><span class="badge ${f.status === "approved" ? "low" : (f.status === "cancelled" ? "high" : "medium")}">${esc(localizeStatus(f.status || "approved"))}</span></div>
            <div style="font-size:0.8rem;color:var(--fvg-text-muted);">${esc(f.date)} • ${esc(f.source || t("Manual Entry", "إدخال يدوي"))}</div>
            ${fresh.isStale ? `<div style="font-size:0.78rem;color:var(--fvg-warning);margin-top:4px;">⚠ ${t("Stale rate (>7 days old) — please verify.", "سعر صرف قديم (أكثر من 7 أيام) — يُنصح بالتحديث.")}</div>` : ""}
            <div style="display:flex;gap:8px;margin-top:8px;">
              ${f.status !== "approved" ? `<button class="btn btn-outline" data-fx-status="approved:${esc(f.id)}">${t("Approve", "اعتماد")}</button>` : ""}
              ${f.status !== "cancelled" ? `<button class="btn btn-outline" data-fx-status="cancelled:${esc(f.id)}">${t("Cancel", "إلغاء")}</button>` : ""}
            </div>
          </div>
        `;
      }).join("")}
    `;
  }
  return `
    <div class="subtabs-sticky"><div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="prices:${i}">${esc(l)}</button>`).join("")}</div></div>
    ${panel}
  `;
}

function renderCostingTab() {
  const curr = state.activeOrg?.baseCurrency || "SDG";
  const sub = state.subTab.costing;
  const subLabels = [t("8 Costing & BOM", "8 طرق تكلفة وBOM"), t("CVP & Risk", "التعادل والمخاطر"), t("Variance", "الانحرافات"), t("What-If", "ماذا لو")];
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card cream">
        <h3 style="margin-top:0;">${t("BOM / Product Recipe Builder", "بناء قائمة المواد والتركيبات (BOM)")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("Product Name", "اسم المنتج")}</label><input id="bomProd" placeholder="${t("Batch Product", "منتج تشغيلة")}"/></div>
          <div class="field-group"><label>${t("Ingredient Name", "المادة الخام")}</label><input id="bomIng" placeholder="${t("Raw Flour", "دقيق خام")}"/></div>
          <div class="field-group"><label>${t("Qty Per Batch", "الكمية للتشغيلة")}</label><input id="bomQty" type="number" value="50"/></div>
          <div class="field-group"><label>${t("Ingredient Unit Cost", "تكلفة وحدة المادة")}</label><input id="bomCost" type="number" value="10"/></div>
          <div class="field-group"><label>${t("Batch Output Units", "وحدات مخرج التشغيلة")}</label><input id="bomBatch" type="number" value="100"/></div>
          <div class="field-group"><label>${t("Scrap Allowance %", "نسبة الفاقد المسموح %")}</label><input id="bomScrap" type="number" value="5"/></div>
        </div>
        <button class="btn btn-gold" data-action="saveBom">${t("Save BOM Recipe & Calculate Unit Material", "حفظ التركيبة وحساب تكلفة الوحدة")}</button>
        ${state.bomFormulas.map((b) => `<div class="row-between" style="margin-top:8px;border-top:1px solid var(--fvg-border);padding-top:6px;"><span><strong>${esc(b.productName)}</strong> (${esc(b.ingredientName)})</span><span><strong>${b.unitMaterialCost} ${esc(curr)}</strong> <button class="btn btn-outline" style="min-height:34px;padding:4px 10px;" data-del-bom="${esc(b.id)}">✕</button></span></div>`).join("")}
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("8 Management Accounting Costing Methods", "طرق التكلفة الـ 8 ومحرك المخاطر")}</h3>
        <div class="field-group">
          <label>${t("Select Method", "اختر طريقة التكلفة")}</label>
          <select id="costMethod">
            ${Engine.ALL_COSTING_METHODS.map((m) => `<option value="${m}" ${state.costDraftMethod === m ? "selected" : ""}>${esc(localizeCostMethod(m))}</option>`).join("")}
          </select>
        </div>
        <div class="grid-2">
          <div class="field-group"><label>${t("Product Name", "اسم المنتج")}</label><input id="cProd" value="${t("Industrial Batch A", "تشغيلة صناعية أ")}"/></div>
          <div class="field-group"><label>${t("Good Units", "الوحدات الصالحة")}</label><input id="cUnits" type="number" value="100"/></div>
          <div class="field-group"><label>${t("Direct Material", "مواد مباشرة")}</label><input id="cMat" type="number" value="500"/></div>
          <div class="field-group"><label>${t("Direct Labor", "عمالة مباشرة")}</label><input id="cLab" type="number" value="200"/></div>
          <div class="field-group"><label>${t("Variable Overhead + Pack", "تكاليف إضافية متغيرة")}</label><input id="cOvh" type="number" value="150"/></div>
          <div class="field-group"><label>${t("Fixed Costs", "التكاليف الثابتة")}</label><input id="cFix" type="number" value="250"/></div>
          <div class="field-group"><label>${t("Selling Price / Unit", "سعر البيع للوحدة")}</label><input id="cPrice" type="number" value="16"/></div>
          <div class="field-group"><label>${t("Target Margin % / Equiv Units", "الهامش المستهدف / المكافئة")}</label><input id="cExtra" type="number" value="20"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveCosting">${t("Compute & Save Snapshot", "حساب التكلفة والمخاطر وحفظها")}</button>
      </div>
      ${state.costSnapshots.slice().reverse().map((s) => `
        <div class="card cream">
          <div class="row-between"><strong>${esc(s.productName)} (${esc(localizeCostMethod(s.method))})</strong><span class="badge ${s.risk.level}">${t(s.risk.level.toUpperCase(), s.risk.level === "high" ? "خطر مرتفع" : s.risk.level === "medium" ? "خطر متوسط" : "آمن")}</span></div>
          <div>${t("Unit Cost", "تكلفة الوحدة")}: <strong>${s.coreResult.unitCost} ${esc(curr)}</strong> | ${t("Price", "السعر")}: ${s.sellingPrice} | ${t("Margin", "الهامش")}: ${s.coreResult.profitMarginPct ?? t("N/A", "غير متاح")}% | ${t("Markup", "العلاوة")}: ${s.coreResult.markupPct ?? t("N/A", "غير متاح")}%</div>
          <div style="font-size:0.8rem;color:var(--fvg-text-muted);">${t("Break-Even", "نقطة التعادل")}: ${s.coreResult.breakEvenUnits ?? t("N/A", "غير متاح")} ${t("units", "وحدة")} • ${esc(s.methodNote)}</div>
        </div>
      `).join("")}
    `;
  } else if (sub === 1) {
    const snap = state.costSnapshots[state.costSnapshots.length - 1];
    panel = !snap ? `<div class="card cream">${t("Save a cost snapshot in 8 Costing first.", "احفظ حساب تكلفة أولاً لعرض تحليل التعادل والمخاطر.")}</div>` : `
      <div class="card cream">
        <div class="row-between"><strong>${esc(snap.productName)} — ${t("Break-Even & Risk", "نقطة التعادل والمخاطر")}</strong><span class="badge ${snap.risk.level}">${esc(snap.risk.ruleName)}</span></div>
        <div style="font-size:1.6rem;font-weight:800;margin:8px 0;">${snap.coreResult.breakEvenUnits ?? t("N/A", "غير متاح")} ${t("units", "وحدة")}</div>
        <div>${t("Revenue", "الإيرادات")}: ${snap.coreResult.revenue} ${esc(curr)} | ${t("Total Cost", "التكلفة الكلية")}: ${snap.coreResult.totalCost} | ${t("Net Profit", "صافي الربح")}: ${snap.coreResult.netProfit}</div>
        <p style="font-size:0.85rem;color:var(--fvg-text-muted);">${esc(snap.risk.explanation)}</p>
      </div>
    `;
  } else if (sub === 2) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Detailed Material & Labor Variance Decomposition", "تحليل انحرافات المواد والعمالة التفصيلي")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("Good Units", "الوحدات الصالحة")}</label><input id="vUnits" type="number" value="100"/></div>
          <div class="field-group"><label>${t("Std Mat Qty/Unit", "كمية المادة المعيارية")}</label><input id="vSmq" type="number" value="2"/></div>
          <div class="field-group"><label>${t("Std Mat Price", "سعر المادة المعياري")}</label><input id="vSmp" type="number" value="5"/></div>
          <div class="field-group"><label>${t("Act Mat Qty", "كمية المادة الفعلية")}</label><input id="vAmq" type="number" value="215"/></div>
          <div class="field-group"><label>${t("Act Mat Price", "سعر المادة الفعلي")}</label><input id="vAmp" type="number" value="5.4"/></div>
          <div class="field-group"><label>${t("Std Labor Hrs/Unit", "ساعات العمل المعيارية")}</label><input id="vSlh" type="number" value="1.5"/></div>
          <div class="field-group"><label>${t("Std Labor Rate", "أجر الساعة المعياري")}</label><input id="vSlr" type="number" value="6"/></div>
          <div class="field-group"><label>${t("Act Labor Hrs", "ساعات العمل الفعلية")}</label><input id="vAlh" type="number" value="145"/></div>
          <div class="field-group"><label>${t("Act Labor Rate", "أجر الساعة الفعلي")}</label><input id="vAlr" type="number" value="6.2"/></div>
        </div>
        <button class="btn btn-gold" data-action="calcVariance">${t("Run Variance Decomposition", "حساب الانحرافات التفصيلية")}</button>
        <div id="varianceResultBox" style="margin-top:10px;"></div>
      </div>
    `;
  } else {
    const snap = state.costSnapshots[state.costSnapshots.length - 1];
    panel = !snap ? `<div class="card cream">${t("Save a cost snapshot first to run What-If Scenarios.", "احفظ لقطة تكلفة أولاً لتشغيل محاكي ماذا لو.")}</div>` : `
      <div class="card">
        <div class="row-between"><strong>${t("What-If Sensitivity Simulator", "محاكي ماذا لو للحساسية")}</strong><span class="badge">${t("Scenario outcome — not a prediction", "نتيجة سيناريو — ليست توقعاً")}</span></div>
        <div class="subtabs" style="margin-top:8px;">
          ${Engine.SPEC_SCENARIO_PRESETS.map((p) => `<button class="subtab" data-preset="${p.key}">${esc(state.isArabic ? p.nameAr : p.nameEn)}</button>`).join("")}
        </div>
        <div class="grid-3">
          <div class="field-group"><label>${t("Material %", "تغير المواد %")}</label><input id="scMat" type="number" value="20"/></div>
          <div class="field-group"><label>${t("FX Shock %", "صدمة الصرف %")}</label><input id="scFx" type="number" value="15"/></div>
          <div class="field-group"><label>${t("Volume %", "تغير الحجم %")}</label><input id="scVol" type="number" value="0"/></div>
          <div class="field-group"><label>${t("Price %", "تغير السعر %")}</label><input id="scPrice" type="number" value="0"/></div>
        </div>
        <button class="btn btn-primary" data-action="runScenario">${t("Simulate & Save Scenario", "محاكاة وحفظ السيناريو")}</button>
      </div>
      ${state.scenarios.slice().reverse().map((sc) => `
        <div class="card cream">
          <div class="row-between"><strong>${esc(sc.name)}</strong><span class="badge">${t("Scenario outcome — not a prediction", "نتيجة سيناريو — ليست توقعاً")}</span></div>
          <div>${t("Base Profit", "الربح الأساسي")}: ${sc.baseProfit} → ${t("Scenario Profit", "ربح السيناريو")}: <strong style="color:${sc.scenarioProfit < 0 ? "var(--fvg-negative)" : "var(--fvg-positive)"};">${sc.scenarioProfit} ${esc(curr)}</strong></div>
        </div>
      `).join("")}
    `;
  }
  return `
    <div class="subtabs-sticky"><div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="costing:${i}">${esc(l)}</button>`).join("")}</div></div>
    ${panel}
  `;
}

function renderLedgerTab() {
  const curr = state.activeOrg?.baseCurrency || "SDG";
  const sub = state.subTab.ledger;
  const subLabels = [t("Journal & COA", "القيود والدليل"), t("Statements", "القوائم المالية"), t("18 Ratios", "18 نسبة مالية"), t("AR & AP", "الذمم والعملاء")];
  const stmt = renderStatementsAndRatios();
  const ratios = Engine.computeFinancialRatios(stmt, state.isArabic);
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Post Balanced Double-Entry Journal", "ترحيل قيد يومية مزدوج متوازن")}</h3>
        <div class="grid-2">
          <div class="field-group"><label>${t("Description", "البيان")}</label><input id="jeDesc" placeholder="${t("Sales cash settlement", "تحصيل مبيعات نقدية")}"/></div>
          <div class="field-group"><label>${t("Amount", "المبلغ")} (${esc(curr)})</label><input id="jeAmt" type="number" step="0.01"/></div>
          <div class="field-group"><label>${t("Debit Account", "حساب المدين")}</label><select id="jeDr">${state.accounts.map((a) => `<option value="${a.code}">${a.code} ${esc(state.isArabic ? a.nameAr : a.name)}</option>`).join("")}</select></div>
          <div class="field-group"><label>${t("Credit Account", "حساب الدائن")}</label><select id="jeCr">${state.accounts.map((a, idx) => `<option value="${a.code}" ${idx === 7 ? "selected" : ""}>${a.code} ${esc(state.isArabic ? a.nameAr : a.name)}</option>`).join("")}</select></div>
        </div>
        <button class="btn btn-primary" data-action="postJe">${t("Post Double-Entry Journal", "ترحيل القيد")}</button>
      </div>
      ${state.journalEntries.slice().reverse().map((je) => `
        <div class="card">
          <div class="row-between"><strong>${esc(je.referenceNo)} • ${esc(je.date)}</strong><span class="badge">${esc(localizeStatus(je.status))}</span></div>
          <div>${esc(je.description)} — ${je.total} ${esc(curr)}</div>
          ${je.status === "posted" ? `<button class="btn btn-outline" style="margin-top:8px;" data-reverse-je="${esc(je.id)}">${t("Create Reversal Entry", "عكس القيد")}</button>` : ""}
        </div>
      `).join("")}
    `;
  } else if (sub === 1) {
    panel = `
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Income Statement", "قائمة الدخل")}</h3>
        <div class="row-between"><span>${t("Revenue", "الإيرادات التشغيلية")}</span><strong>${stmt.revenue} ${esc(curr)}</strong></div>
        <div class="row-between"><span>${t("COGS", "تكلفة المبيعات")}</span><strong>(${stmt.cogs}) ${esc(curr)}</strong></div>
        <div class="row-between"><span>${t("Operating Expenses", "المصروفات التشغيلية")}</span><strong>(${stmt.operatingExpenses}) ${esc(curr)}</strong></div>
        <div class="row-between"><strong>${t("Net Profit", "صافي الربح")}</strong><strong>${stmt.netProfit} ${esc(curr)}</strong></div>
      </div>
      <div class="card">
        <div class="row-between"><h3 style="margin:0;">${t("Balance Sheet", "الميزانية العمومية")}</h3><span class="badge ${stmt.isAccountingEquationBalanced ? "low" : "high"}">${stmt.isAccountingEquationBalanced ? t("A = L + E ✓", "الأصول = الالتزامات + الملكية ✓") : t("Check", "مراجعة")}</span></div>
        <div class="row-between"><span>${t("Total Assets", "إجمالي الأصول")}</span><strong>${stmt.totalAssets} ${esc(curr)}</strong></div>
        <div class="row-between"><span>${t("Total Liabilities", "إجمالي الالتزامات")}</span><strong>${stmt.totalLiabilities} ${esc(curr)}</strong></div>
        <div class="row-between"><span>${t("Total Equity (incl. Net Income)", "إجمالي حقوق الملكية")}</span><strong>${stmt.totalEquityWithNetIncome} ${esc(curr)}</strong></div>
      </div>
      <div class="card table-wrap">
        <h3 style="margin-top:0;">${t("Trial Balance", "ميزان المراجعة")} (${t("Dr", "مدين")} ${stmt.totalTrialDebits} = ${t("Cr", "دائن")} ${stmt.totalTrialCredits})</h3>
        <table><thead><tr><th>${t("Code", "الرمز")}</th><th>${t("Account", "الحساب")}</th><th>${t("Debit", "مدين")}</th><th>${t("Credit", "دائن")}</th></tr></thead>
        <tbody>${stmt.trialBalance.map((r) => `<tr><td>${r.code}</td><td>${esc(state.isArabic ? r.nameAr : r.name)}</td><td>${r.debitBalance}</td><td>${r.creditBalance}</td></tr>`).join("")}</tbody></table>
      </div>
    `;
  } else if (sub === 2) {
    panel = ratios.map((r) => `
      <div class="card">
        <div class="row-between"><strong>${esc(r.label)}</strong><strong style="color:${r.value == null ? "var(--fvg-warning)" : "var(--fvg-positive)"};">${r.value == null ? t("N/A", "غير متاح") : `${r.value} ${r.unitSuffix}`}</strong></div>
        <div style="font-size:0.78rem;color:var(--fvg-text-muted);">${esc(r.category)} • ${esc(r.formula)}</div>
        ${r.missingDataWarning ? `<div style="font-size:0.78rem;color:var(--fvg-negative);">⚠ ${esc(r.missingDataWarning)}</div>` : ""}
      </div>
    `).join("");
  } else {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Receivables & Payables Tracker (AR / AP)", "متابعة الذمم المدينة والدائنة")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("Type", "النوع")}</label><select id="cpType"><option value="receivable">${t("Receivable (AR)", "ذمم مدينة")}</option><option value="payable">${t("Payable (AP)", "ذمم دائنة")}</option></select></div>
          <div class="field-group"><label>${t("Party Name", "اسم العميل / المورد")}</label><input id="cpParty" placeholder="${t("Distributor Name", "اسم الجهة")}"/></div>
          <div class="field-group"><label>${t("Invoice #", "رقم الفاتورة")}</label><input id="cpInv" placeholder="INV-101"/></div>
          <div class="field-group"><label>${t("Amount", "المبلغ")} (${esc(curr)})</label><input id="cpAmt" type="number" step="0.01"/></div>
          <div class="field-group"><label>${t("Due Date", "تاريخ الاستحقاق")}</label><input id="cpDue" type="date" value="${new Date().toISOString().slice(0, 10)}"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveCounterparty">${t("Save AR/AP Record", "حفظ فاتورة الذمة")}</button>
      </div>
      ${state.counterpartyItems.map((c) => `<div class="card"><div class="row-between"><strong>${esc(c.partyName)} (${esc(c.invoiceNo)})</strong><span class="badge">${c.type === "receivable" ? t("AR", "مدين") : t("AP", "دائن")} • ${esc(localizeStatus(c.status))}</span></div><div>${t("Amount", "المبلغ")}: ${c.amount} | ${t("Paid", "المدفوع")}: ${c.paidAmount} ${esc(curr)} • ${t("Due", "الاستحقاق")} ${esc(c.dueDate)}</div></div>`).join("")}
    `;
  }
  return `
    <div class="subtabs-sticky"><div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="ledger:${i}">${esc(l)}</button>`).join("")}</div></div>
    ${panel}
  `;
}

function renderStockTab() {
  const curr = state.activeOrg?.baseCurrency || "SDG";
  const sub = state.subTab.stock;
  const subLabels = [t("Inventory & Batches", "المخزون والتشغيلات"), t("Scrap & Losses", "الهدر والتلف"), t("Budgets", "الموازنات")];
  const bookTotal = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.avgCost), 0));
  const replTotal = Engine.money(state.inventoryItems.reduce((s, it) => s + Engine.n(it.qty) * Engine.n(it.marketPrice), 0));
  const thirtyDaysIso = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Book Value vs Current Replacement Cost", "القيمة الدفترية مقابل تكلفة الاستبدال")}</h3>
        <div class="row-between"><span>${t("Accounting Book Value", "القيمة الدفترية")}</span><strong>${bookTotal} ${esc(curr)}</strong></div>
        <div class="row-between"><span>${t("Current Replacement Cost", "تكلفة الاستبدال الحالية")}</span><strong>${replTotal} ${esc(curr)}</strong></div>
        <div class="row-between"><span>${t("Replacement Capital Gap", "فجوة رأس المال للاستبدال")}</span><strong style="color:var(--fvg-negative);">${Engine.money(replTotal - bookTotal)} ${esc(curr)}</strong></div>
      </div>
      <div class="card">
        <h3 style="margin-top:0;">${t("Register Inventory Item & Batch", "إضافة صنف مخزني وتشغيلة")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("SKU", "الرمز")}</label><input id="invSku" placeholder="ITM-01"/></div>
          <div class="field-group"><label>${t("Item Name", "اسم الصنف")}</label><input id="invName" placeholder="${t("Raw Material", "مادة خام")}"/></div>
          <div class="field-group"><label>${t("Valuation Method", "طريقة التقييم")}</label><select id="invMethod"><option value="fifo">FIFO</option><option value="moving_avg">${t("Moving Average", "المتوسط المرجح")}</option></select></div>
          <div class="field-group"><label>${t("Quantity", "الكمية")}</label><input id="invQty" type="number" value="50"/></div>
          <div class="field-group"><label>${t("Unit Cost", "تكلفة الوحدة")}</label><input id="invCost" type="number" value="100"/></div>
          <div class="field-group"><label>${t("Market Price", "سعر السوق")}</label><input id="invMkt" type="number" value="130"/></div>
          <div class="field-group"><label>${t("Batch Number (opt.)", "رقم التشغيلة")}</label><input id="invBatch" placeholder="B-2026-01"/></div>
          <div class="field-group"><label>${t("Expiry Date (opt.)", "انتهاء الصلاحية")}</label><input id="invExpiry" type="date"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveInventory">${t("Create Inventory Item", "حفظ الصنف المخزني")}</button>
      </div>
      ${state.inventoryItems.map((it) => {
        const expSoon = it.expiryDate && it.expiryDate <= thirtyDaysIso;
        return `
          <div class="card">
            <div class="row-between"><strong>${esc(it.sku)} • ${esc(it.name)}</strong><span class="badge">${it.method === "fifo" ? "FIFO" : t("Moving Avg", "المتوسط المرجح")}</span></div>
            <div>${t("Qty", "الكمية")}: ${it.qty} | ${t("Avg Cost", "متوسط التكلفة")}: ${it.avgCost} | ${t("Market", "السوق")}: ${it.marketPrice} ${esc(curr)}</div>
            ${it.batchNumber || it.expiryDate ? `<div style="font-size:0.8rem;margin-top:4px;">${it.batchNumber ? `${t("Batch", "تشغيلة")}: ${esc(it.batchNumber)} ` : ""}${it.expiryDate ? `<span class="badge ${expSoon ? "high" : ""}">${expSoon ? "⚠ " : ""}${t("Expiry", "الصلاحية")}: ${esc(it.expiryDate)}</span>` : ""}</div>` : ""}
            <div style="display:flex;gap:8px;margin-top:8px;">
              <button class="btn btn-outline" data-stock-move="in:${esc(it.id)}">+10 ${t("Receipt", "استلام")}</button>
              <button class="btn btn-outline" data-stock-move="out:${esc(it.id)}">-10 ${t("Issue", "صرف")}</button>
            </div>
          </div>
        `;
      }).join("")}
    `;
  } else if (sub === 1) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Log Scrap / Damaged / Expired Loss", "تسجيل فاقد وتلف المخزون")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("Item", "الصنف")}</label><input id="scrItem" placeholder="${t("Packaging Film", "عبوات تغليف")}"/></div>
          <div class="field-group"><label>${t("Loss Type", "النوع")}</label><select id="scrType"><option value="scrap">${t("Scrap", "هدر")}</option><option value="damaged">${t("Damaged", "تالف")}</option><option value="expired">${t("Expired", "منتهي الصلاحية")}</option></select></div>
          <div class="field-group"><label>${t("Quantity", "الكمية")}</label><input id="scrQty" type="number" value="5"/></div>
          <div class="field-group"><label>${t("Unit Cost", "تكلفة الوحدة")}</label><input id="scrCost" type="number" value="100"/></div>
          <div class="field-group"><label>${t("Reason", "السبب")}</label><input id="scrReason" placeholder="${t("Storage damage", "تلف تخزيني")}"/></div>
        </div>
        <button class="btn btn-danger" data-action="saveScrap">${t("Log Scrap Loss", "تسجيل الفاقد")}</button>
      </div>
      ${state.scrapLosses.map((s) => `<div class="card cream"><div class="row-between"><strong>${esc(s.itemName)}</strong><strong style="color:var(--fvg-negative);">-${s.totalLossCost} ${esc(curr)}</strong></div><div>${t("Qty", "الكمية")}: ${s.quantity} • ${esc(s.reason)}</div></div>`).join("")}
    `;
  } else {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Budget & Variance", "إنشاء موازنة ومقارنتها بالفعلي")}</h3>
        <div class="grid-3">
          <div class="field-group"><label>${t("Budget Name", "اسم الموازنة")}</label><input id="bdgName" placeholder="${t("Q4 Production", "تشغيل الربع الرابع")}"/></div>
          <div class="field-group"><label>${t("Planned", "المخطط")}</label><input id="bdgPlan" type="number" value="5000"/></div>
          <div class="field-group"><label>${t("Actual", "الفعلي")}</label><input id="bdgAct" type="number" value="5400"/></div>
        </div>
        <button class="btn btn-primary" data-action="saveBudget">${t("Save Budget", "حفظ الموازنة")}</button>
      </div>
      ${state.budgets.map((b) => `<div class="card"><div class="row-between"><strong>${esc(b.name)}</strong><span class="badge">${b.variancePct}%</span></div><div>${t("Planned", "المخطط")}: ${b.planned} | ${t("Actual", "الفعلي")}: ${b.actual} | ${t("Variance", "الانحراف")}: ${b.variance} ${esc(curr)}</div></div>`).join("")}
    `;
  }
  return `
    <div class="subtabs-sticky"><div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="stock:${i}">${esc(l)}</button>`).join("")}</div></div>
    ${panel}
  `;
}

function renderReportsAiTab() {
  const curr = state.activeOrg?.baseCurrency || "SDG";
  const sub = state.subTab.reports;
  const subLabels = [t("Reports & Export", "التقارير والتصدير"), t("Financial Assistant & OCR", "المساعد المالي والفواتير"), t("Org & Billing", "المنظمة والدفع")];
  const stmt = renderStatementsAndRatios();
  let panel = "";
  if (sub === 0) {
    panel = `
      <div class="card">
        <h3 style="margin-top:0;">${t("Verified Financial Reports & Exports", "التقارير المالية والتصدير")}</h3>
        <div class="field-group">
          <label>${t("Report Dataset", "التقرير المطلوب")}</label>
          <select id="repSelect">
            <option value="statements">${t("Financial Statements", "القوائم المالية")}</option>
            <option value="prices">${t("Prices History", "سجل الأسعار")}</option>
            <option value="costing">${t("8-Method Costing Snapshots", "ملخص طرق التكلفة")}</option>
            <option value="ratios">${t("18 Financial Ratios", "النسب المالية الـ 18")}</option>
          </select>
        </div>
        <div class="grid-3">
          <button class="btn btn-primary" data-action="exportCsv">${t("Export CSV", "تصدير CSV")}</button>
          <button class="btn btn-gold" data-action="exportJson">${t("Export JSON", "تصدير JSON")}</button>
          <button class="btn btn-outline" data-action="printPdf">${t("Print / Save PDF", "طباعة / حفظ PDF")}</button>
        </div>
      </div>
      <div class="card cream">
        <div class="row-between"><strong>${t("Net Profit", "صافي الربح")}: ${stmt.netProfit} ${esc(curr)}</strong><strong>${t("Total Assets", "إجمالي الأصول")}: ${stmt.totalAssets} ${esc(curr)}</strong></div>
      </div>
    `;
  } else if (sub === 1) {
    panel = `
      <div class="card">
        <div class="row-between">
          <h3 style="margin:0;">${t("Financial Assistant (Verified Workspace Data)", "المساعد المالي الذكي")}</h3>
          <button class="btn btn-outline" style="min-height:40px;" data-action="newConversation">+ ${t("New Chat", "محادثة جديدة")}</button>
        </div>
        ${state.aiConversations.length ? `
          <div class="subtabs" style="margin-top:8px;">
            ${state.aiConversations.map((c) => `<button class="subtab ${state.activeConversationId === c.id ? "active" : ""}" data-select-conv="${esc(c.id)}">${esc(c.title)}</button>`).join("")}
          </div>
        ` : ""}
        <div class="subtabs" style="margin-top:8px;">
          <button class="subtab ${state.aiMode === "general" ? "active" : ""}" data-ai-mode="general">${t("General Guide", "إرشاد عام")}</button>
          <button class="subtab ${state.aiMode === "financial" ? "active" : ""}" data-ai-mode="financial">${t("Financial Analyst", "تحليل مالي")}</button>
          <button class="subtab ${state.aiMode === "ocr" ? "active" : ""}" data-ai-mode="ocr">${t("Invoices & OCR", "فواتير وOCR")}</button>
        </div>
        <div class="field-group"><label>${t("Question", "اسأل المساعد المالي")}</label><textarea id="aiPrompt" rows="2" placeholder="${t("Analyze margins, replacement cost gap, and inventory turnover", "حلل هوامش الربح، فجوة الاستبدال، ودوران المخزون")}"></textarea></div>
        <div class="grid-2">
          <button class="btn btn-primary" data-action="askAi">${t("Analyze Verified Records", "تحليل البيانات الموثقة")}</button>
          <button class="btn btn-outline" data-action="exportTranscript">${t("Export Transcript TXT", "تصدير المحادثة TXT")}</button>
        </div>
        ${state.aiResponse ? `
          <div class="card cream" style="margin-top:12px;white-space:pre-wrap;">
            ${esc(state.aiResponse)}
            <div style="margin-top:8px;"><button class="btn btn-outline" style="min-height:38px;" data-action="copyAi">📋 ${t("Copy Answer", "نسخ الإجابة")}</button></div>
          </div>
        ` : ""}
      </div>
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Camera Invoice Capture & OCR Review", "تصوير الفواتير بالكاميرا (OCR للمراجعة البشرية)")}</h3>
        <p style="font-size:0.82rem;color:var(--fvg-text-muted);margin-top:0;">${t("Capture directly with your phone camera, rotate 90° if needed, verify math consistency and create a review draft.", "صوّر الفاتورة بالكاميرا مباشرة، قم بتدويرها 90° عند الحاجة، ثم راجع المجاميع وتحقق من عدم التكرار.")}</p>
        <div class="grid-2">
          <label class="btn btn-gold" style="cursor:pointer;">
            📷 ${t("Capture Invoice Camera", "تصوير الفاتورة بالكاميرا")}
            <input id="ocrCameraInput" type="file" accept="image/*" capture="environment" style="display:none;" />
          </label>
          <label class="btn btn-outline" style="cursor:pointer;">
            🖼 ${t("Pick Image Fallback", "اختيار صورة بديلة")}
            <input id="ocrGalleryInput" type="file" accept="image/*" style="display:none;" />
          </label>
        </div>
        ${state.pendingOcrDataUrl ? `
          <div style="margin-top:10px;text-align:center;">
            <img src="${state.pendingOcrDataUrl}" style="max-width:100%;max-height:220px;border-radius:12px;border:1px solid var(--fvg-border);" alt="Invoice Preview"/>
            <div class="grid-2" style="margin-top:8px;">
              <button class="btn btn-outline" data-action="rotateOcr">↻ ${t("Rotate 90°", "تدوير 90°")}</button>
              <button class="btn btn-primary" data-action="createOcrDraft">${t("Create OCR Review Draft", "إنشاء مسودة مراجعة")}</button>
            </div>
          </div>
        ` : ""}
      </div>
      ${state.ocrDrafts.slice().reverse().map((d) => {
        const math = Engine.validateOcrArithmetic(d.subtotal, d.discount, d.tax, d.total);
        return `
          <div class="card">
            <div class="row-between"><strong>${esc(d.vendorName || t("Invoice Draft", "مسودة فاتورة"))} (#${esc(d.invoiceNumber || "---")})</strong><span class="badge">${esc(localizeStatus(d.reviewStatus))}</span></div>
            ${!math.isConsistent ? `<div style="color:var(--fvg-negative);font-size:0.8rem;">⚠ ${t("Math mismatch: expected", "عدم تطابق حسابي: المتوقع")} ${math.expectedTotal} ≠ ${math.extractedTotal}</div>` : ""}
            <div class="grid-3" style="margin-top:6px;">
              <div class="field-group"><label>${t("Vendor", "المورد")}</label><input data-ocr-field="vendor:${esc(d.id)}" value="${esc(d.vendorName)}"/></div>
              <div class="field-group"><label>${t("Invoice #", "رقم الفاتورة")}</label><input data-ocr-field="inv:${esc(d.id)}" value="${esc(d.invoiceNumber)}"/></div>
              <div class="field-group"><label>${t("Subtotal", "الفرعي")}</label><input type="number" step="0.01" data-ocr-field="sub:${esc(d.id)}" value="${esc(d.subtotal)}"/></div>
              <div class="field-group"><label>${t("Discount", "الخصم")}</label><input type="number" step="0.01" data-ocr-field="disc:${esc(d.id)}" value="${esc(d.discount)}"/></div>
              <div class="field-group"><label>${t("Tax", "الضريبة")}</label><input type="number" step="0.01" data-ocr-field="tax:${esc(d.id)}" value="${esc(d.tax)}"/></div>
              <div class="field-group"><label>${t("Final Total", "الإجمالي النهائي")}</label><input type="number" step="0.01" data-ocr-field="tot:${esc(d.id)}" value="${esc(d.total)}"/></div>
            </div>
            ${d.reviewStatus === "pending_review" ? `<button class="btn btn-primary" data-verify-ocr="${esc(d.id)}">${t("Verify & Save Draft", "اعتماد المسودة")}</button>` : ""}
          </div>
        `;
      }).join("")}
    `;
  } else {
    panel = `
      ${renderTrialBanner(false)}
      <div class="card cream">
        <h3 style="margin-top:0;">${t("Submit Payment Proof (Reviewed by Platform Owner)", "تقديم إثبات الدفع (يُراجع عبر مالكة المنصة)")}</h3>
        <p style="font-size:0.82rem;color:var(--fvg-text-muted);">${t("Payment proofs are queued as [Pending Verification] for review via Financialvisionguide@gmail.com. Full card numbers & CVV are never collected — enter only optional Last 4 Digits for matching.", "تُرسل طلبات الدفع بحالة [قيد التحقق] لمالكة المنصة عبر البريد الموثق للموافقة أو الرفض. لا يتم جمع أرقام بطاقات كاملة، فقط آخر 4 أرقام للمعاملة اختيارياً مع ملاحظة باسم المستخدم.")}</p>
        <div class="grid-3">
          <div class="field-group"><label>${t("Plan", "الباقة")}</label><select id="payPlan">${state.platformPlans.map((p) => `<option value="${esc(p.code)}">${esc(state.isArabic ? p.nameAr : p.nameEn)} ($${esc(p.priceUsd)})</option>`).join("")}</select></div>
          <div class="field-group"><label>${t("Method", "الوسيلة")}</label><select id="payMethod"><option value="bankak">${t("Bankak", "بنكك")}</option><option value="ocash">${t("O-Cash", "أوكاش")}</option><option value="bank_transfer">${t("Bank Transfer", "تحويل بنكي")}</option></select></div>
          <div class="field-group"><label>${t("Amount", "المبلغ")}</label><input id="payAmt" type="number" value="29"/></div>
          <div class="field-group"><label>${t("Sender Name", "اسم المحوّل")}</label><input id="paySender" placeholder="${t("Full Name", "الاسم الكامل")}"/></div>
          <div class="field-group"><label>${t("Reference No", "رقم المرجع")}</label><input id="payRef" placeholder="BNK-2026-01"/></div>
          <div class="field-group"><label>${t("Last 4 Digits (Optional)", "آخر 4 أرقام للمعاملة")}</label><input id="payLast4" maxlength="4" placeholder="1234"/></div>
        </div>
        <div class="field-group"><label>${t("User Name & Notes", "ملاحظة باسم المستخدم وتفاصيل التحويل")}</label><input id="payNotes" placeholder="${t("Name + Transfer Confirmed", "الاسم + تم التحويل")}"/></div>
        <button class="btn btn-gold" data-action="submitPayment">${t("Submit Payment Proof [Pending Verification]", "إرسال إثبات الدفع للمراجعة")}</button>
      </div>
      ${state.paymentRequests.map((p) => `
        <div class="card">
          <div class="row-between"><strong>${esc(p.paymentMethod.toUpperCase())} • ${esc(p.referenceNo)}</strong><span class="badge">${esc(localizeStatus(p.status))}</span></div>
          <div>${esc(p.planTier)} • ${p.amount} • ${esc(p.senderName)} ${p.last4Digits ? `• ****${esc(p.last4Digits)}` : ""}</div>
          ${p.reviewNotes ? `<div style="font-size:0.8rem;color:var(--fvg-warning);">${esc(p.reviewNotes)}</div>` : ""}
        </div>
      `).join("")}
      <div class="card">
        <h3 style="margin-top:0;">${t("Install Website on iPhone / Android", "تثبيت الموقع كتطبيق على iPhone أو Android")}</h3>
        <p style="font-size:0.85rem;margin:0 0 10px;">${t("On iPhone Safari: Tap Share -> 'Add to Home Screen'. On Android Chrome: Tap Menu -> 'Install app'.", "في iPhone (Safari): اضغط زر المشاركة ثم 'إضافة إلى الشاشة الرئيسية'. في Android Chrome: اختر 'تثبيت التطبيق'.")}</p>
        <div class="grid-2">
          <button class="btn btn-outline" data-action="openProfile">👤 ${t("Profile & Sessions", "الملف الشخصي والجلسات")}</button>
          <button class="btn btn-danger" data-action="signOut">${t("Sign Out", "تسجيل الخروج")}</button>
        </div>
      </div>
    `;
  }
  return `
    <div class="subtabs-sticky"><div class="subtabs">${subLabels.map((l, i) => `<button class="subtab ${sub === i ? "active" : ""}" data-sub="reports:${i}">${esc(l)}</button>`).join("")}</div></div>
    ${panel}
  `;
}

function renderProfileView() {
  const u = state.user || {};
  return `
    <div class="card">
      <div class="row-between">
        <h2 style="margin:0;">${t("My Profile & Preferences", "ملفي الشخصي والتفضيلات")}</h2>
        <button class="btn btn-outline" data-action="closeSecondary">← ${t("Back to Home", "العودة للرئيسية")}</button>
      </div>
    </div>
    <div class="card cream" style="display:flex;align-items:center;gap:14px;flex-wrap:wrap;">
      ${u.avatarDataUrl ? `<img class="avatar-circle" src="${u.avatarDataUrl}" alt="avatar"/>` : `<div class="avatar-circle">${esc((u.displayName || u.email || "FV").slice(0, 2).toUpperCase())}</div>`}
      <div style="flex:1;min-width:200px;">
        <strong>${esc(u.displayName || t("FVG User", "مستخدم FVG"))}</strong>
        <div style="font-size:0.82rem;color:var(--fvg-text-muted);">${esc(u.email || "")}</div>
        <div style="display:flex;gap:8px;margin-top:8px;flex-wrap:wrap;">
          <label class="btn btn-gold" style="min-height:40px;cursor:pointer;">
            📷 ${t("Upload / Take Photo", "رفع أو التقاط صورة")}
            <input id="avatarFileInput" type="file" accept="image/*" style="display:none;" />
          </label>
          ${u.avatarDataUrl ? `<button class="btn btn-outline" style="min-height:40px;" data-action="removeAvatar">${t("Remove Photo", "حذف الصورة")}</button>` : ""}
        </div>
      </div>
    </div>
    <div class="card">
      <div class="grid-2">
        <div class="field-group"><label>${t("Display Name", "الاسم الظاهر")}</label><input id="profName" value="${esc(u.displayName || "")}"/></div>
        <div class="field-group"><label>${t("Language", "اللغة")}</label><select id="profLang"><option value="ar" ${state.isArabic ? "selected" : ""}>العربية (RTL)</option><option value="en" ${!state.isArabic ? "selected" : ""}>English (LTR)</option></select></div>
        <div class="field-group"><label>${t("Theme Mode", "مظهر التطبيق")}</label><select id="profTheme"><option value="system" ${state.themeMode === "system" ? "selected" : ""}>${t("Follow System", "تلقائي (النظام)")}</option><option value="light" ${state.themeMode === "light" ? "selected" : ""}>${t("Light Mode", "الوضع النهاري")}</option><option value="dark" ${state.themeMode === "dark" ? "selected" : ""}>${t("Dark Mode", "الوضع الليلي")}</option></select></div>
        <div class="field-group"><label>${t("Font Size Scale", "حجم الخط")}: ${Math.round(state.fontScale * 100)}%</label><input id="profFontScale" type="range" min="0.85" max="1.30" step="0.05" value="${state.fontScale}"/></div>
      </div>
      <button class="btn btn-primary" data-action="saveProfile">${t("Save Profile Preferences", "حفظ تفضيلات الملف الشخصي")}</button>
    </div>
    <div class="card">
      <h3 style="margin-top:0;">${t("Active Signed-In Sessions", "الجلسات النشطة")}</h3>
      ${state.sessions.map((s) => `<div class="row-between"><span>${esc(s.label)} (${esc(s.platform)})</span>${s.revoked ? `<span class="badge high">${t("Revoked", "منتهية")}</span>` : `<button class="btn btn-outline" style="min-height:36px;" data-revoke-sess="${esc(s.id)}">${t("Revoke", "إنهاء")}</button>`}</div>`).join("")}
    </div>
  `;
}

function renderHelpView() {
  return `
    <div class="card">
      <div class="row-between">
        <h2 style="margin:0;">${t("Help, Methodology & Support", "المساعدة والدليل والتواصل")}</h2>
        <button class="btn btn-outline" data-action="closeSecondary">← ${t("Back to Home", "العودة للرئيسية")}</button>
      </div>
    </div>
    <div class="card cream">
      <h3 style="margin-top:0;">${t("Interactive Walkthrough Tour", "الجولة التعريفية التفاعلية")}</h3>
      <p style="font-size:0.86rem;">${t("Replay the guided spotlight walkthrough explaining every financial tool step by step.", "أعد تشغيل الجولة الإرشادية لتوضيح كل قسم ومعادلة داخل التطبيق باللغة المختارة.")}</p>
      <button class="btn btn-gold" data-action="replayTour">🧭 ${t("Start Interactive Tour", "تشغيل الجولة التعريفية")}</button>
    </div>
    <div class="card">
      <h3 style="margin-top:0;">${t("Core Accounting & Costing Formulas", "ملخص المعادلات المحاسبية المعتمدة")}</h3>
      <p style="font-size:0.85rem;">• ${t("Profit Margin % = ((Selling Price − Unit Cost) / Selling Price) × 100", "هامش الربح % = ((سعر البيع − تكلفة الوحدة) / سعر البيع) × 100")}</p>
      <p style="font-size:0.85rem;">• ${t("Markup % = ((Selling Price − Unit Cost) / Unit Cost) × 100", "نسبة الإضافة % = ((سعر البيع − تكلفة الوحدة) / تكلفة الوحدة) × 100")}</p>
      <p style="font-size:0.85rem;">• ${t("Break-Even Units = CEILING(Fixed Costs / (Price − Variable Cost/Unit))", "نقطة التعادل = تقريب للأعلى(التكاليف الثابتة / (السعر − التكلفة المتغيرة للوحدة))")}</p>
      <p style="font-size:0.85rem;">• ${t("Compounded Inflation = Π(1 + rᵢ/100) − 1", "التضخم المركب = حاصل ضرب (1 + معدل الشهر) − 1")}</p>
    </div>
    <div class="card">
      <h3 style="margin-top:0;">${t("Send Support Request", "إرسال تذكرة دعم أو استفسار")}</h3>
      <div class="field-group"><label>${t("Message to Support (Financialvisionguide@gmail.com)", "نص الرسالة للدعم الفني")}</label><textarea id="helpMsg" rows="3" placeholder="${t("Describe your question or issue...", "اكتب استفسارك هنا...")}"></textarea></div>
      <button class="btn btn-primary" data-action="submitTicket">${t("Send Support Request", "إرسال الطلب")}</button>
      ${state.supportTickets.map((tk) => `<div class="card cream" style="margin-top:8px;"><div class="row-between"><strong>${esc(tk.createdAt)}</strong><span class="badge">${esc(localizeStatus(tk.status))}</span></div><div>${esc(tk.message)}</div>${tk.ownerReply ? `<div style="color:var(--fvg-positive);margin-top:4px;">↳ ${esc(tk.ownerReply)}</div>` : ""}</div>`).join("")}
    </div>
  `;
}

function renderOwnerAdminView() {
  if (!isPlatformOwner()) return `<div class="card">${t("Restricted to Platform Owner.", "مقصورة على مالكة المنصة.")}</div>`;
  return `
    <div class="card hero">
      <div class="row-between">
        <h2 style="margin:0;">🛡 ${t("Platform Owner Console", "لوحة مالكة المنصة (الإدارة العامة)")}</h2>
        <button class="btn btn-gold" data-action="closeSecondary">← ${t("Back", "رجوع")}</button>
      </div>
      <div class="grid-3" style="margin-top:10px;">
        <div><strong>${state.organizations.length}</strong><div style="font-size:0.78rem;">${t("Organizations", "المنظمات")}</div></div>
        <div><strong>${state.paymentRequests.filter((p) => p.status === "pending_verification").length}</strong><div style="font-size:0.78rem;">${t("Pending Payments", "مدفوعات قيد المراجعة")}</div></div>
        <div><strong>${t("Not Available", "غير متاح")}</strong><div style="font-size:0.78rem;">${t("Play/Web External Metrics", "إحصاءات المتجر والزيارات الخارجية")}</div></div>
      </div>
    </div>
    <div class="card">
      <h3 style="margin-top:0;">${t("Payment Verification Queue", "طابور مراجعة إثباتات الدفع")}</h3>
      ${state.paymentRequests.length === 0 ? `<p>${t("No payment requests submitted yet.", "لا توجد طلبات دفع.")}</p>` : state.paymentRequests.map((p) => `
        <div class="card cream">
          <div class="row-between"><strong>${esc(p.senderName)} • ${esc(p.referenceNo)}</strong><span class="badge">${esc(localizeStatus(p.status))}</span></div>
          <div>${esc(p.planTier)} • ${esc(p.amount)} • ${p.last4Digits ? `****${esc(p.last4Digits)}` : ""}</div>
          ${p.status === "pending_verification" ? `
            <div style="display:flex;gap:8px;margin-top:8px;">
              <button class="btn btn-primary" data-owner-pay="approved:${esc(p.id)}">${t("Approve & Activate", "موافقة وتفعيل")}</button>
              <button class="btn btn-danger" data-owner-pay="rejected:${esc(p.id)}">${t("Reject", "رفض")}</button>
            </div>
          ` : ""}
        </div>
      `).join("")}
    </div>
    <div class="card">
      <h3 style="margin-top:0;">${t("Publish Global Announcement", "نشر إعلان أو تنبيه داخل المنصة")}</h3>
      <div class="grid-2">
        <div class="field-group"><label>${t("Title (AR)", "العنوان بالعربية")}</label><input id="annTitleAr"/></div>
        <div class="field-group"><label>${t("Title (EN)", "العنوان بالإنجليزية")}</label><input id="annTitleEn"/></div>
      </div>
      <div class="field-group"><label>${t("Body", "المحتوى")}</label><textarea id="annBody" rows="2"></textarea></div>
      <button class="btn btn-gold" data-action="publishAnnouncement">${t("Publish Announcement", "نشر الإعلان")}</button>
    </div>
    <div class="card">
      <h3 style="margin-top:0;">${t("Support Tickets Inbox", "تذاكر الدعم والرد عليها")}</h3>
      ${state.supportTickets.map((tk) => `
        <div class="card cream">
          <div class="row-between"><strong>${esc(tk.senderEmail)}</strong><span class="badge">${esc(localizeStatus(tk.status))}</span></div>
          <div>${esc(tk.message)}</div>
          <div style="display:flex;gap:8px;margin-top:6px;">
            <input id="reply_${esc(tk.id)}" placeholder="${t("Owner reply...", "رد المالكة...")}" value="${esc(tk.ownerReply || "")}"/>
            <button class="btn btn-primary" data-owner-reply="${esc(tk.id)}">${t("Send Reply", "إرسال الرد")}</button>
          </div>
        </div>
      `).join("")}
    </div>
  `;
}

function renderCalculatorModal() {
  if (!state.showCalculator) return "";
  const curr = state.activeOrg?.baseCurrency || "SDG";
  const keys = ["MC", "MR", "M+", "M-", "(", ")", "%", "⌫", "7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "-", "C", "0", ".", "+"];
  return `
    <div class="fvg-modal-overlay" id="calcOverlay">
      <div class="fvg-calc-shell">
        <div class="row-between" style="margin-bottom:8px;direction:${state.isArabic ? "rtl" : "ltr"};">
          <strong style="color:#DDB065;">🧮 ${t("FVG Financial Calculator", "آلة FVG الحاسبة المالية")} (${esc(curr)})</strong>
          <button class="icon-pill" style="min-height:36px;min-width:36px;" data-action="closeCalc">✕</button>
        </div>
        <div class="fvg-calc-display">
          <div class="fvg-calc-expr">${esc(state.calc.expr || "0")}</div>
          <div class="fvg-calc-val">${esc(state.calc.display)}</div>
        </div>
        <div class="fvg-calc-grid">
          ${keys.map((k) => {
            const cls = ["MC", "MR", "M+", "M-"].includes(k) ? "mem" : (["÷", "×", "-", "+", "(", ")", "%", "⌫", "C"].includes(k) ? "op" : "");
            return `<button class="fvg-calc-key ${cls}" data-calc-key="${esc(k)}">${esc(k)}</button>`;
          }).join("")}
          <button class="fvg-calc-key eq" style="grid-column: span 2;" data-calc-key="=">=</button>
          <button class="fvg-calc-key mem" style="grid-column: span 2;" data-action="copyCalc">📋 ${t("Copy", "نسخ")}</button>
        </div>
      </div>
    </div>
  `;
}

function renderTourModal() {
  if (!state.showTour) return "";
  const steps = [
    { tab: 0, titleAr: "مرحباً بك في دليل رؤيتك المالي", titleEn: "Welcome to FVG", descAr: "هنا لوحة القيادة لمتابعة السيولة والمخزون وصافي الربح والفترة التجريبية 7 أيام. بدّل اللغة والمظهر من الأعلى.", descEn: "Your operational financial hub tracking cash, inventory, net profit & 7-day trial." },
    { tab: 1, titleAr: "الأسعار والتضخم والصرف اليدوي", titleEn: "Prices, Inflation & Manual FX", descAr: "وثّق تغيرات الأسعار واستورد CSV وأدخل أسعار الصرف يدوياً لتفادي أخطاء التحويل الإلكتروني.", descEn: "Track historical product prices, weighted inflation baskets & verified manual FX rates." },
    { tab: 2, titleAr: "التكاليف الـ 8 وتركيبات BOM", titleEn: "8 Costing Methods & BOM", descAr: "ابنِ تركيبات المنتجات وقارن 8 أساليب محاسبية مع محرك المخاطر ونقطة التعادل وسيناريوهات ماذا لو.", descEn: "Build BOM recipes and compute unit cost across 8 methods, break-even, risk & What-If scenarios." },
    { tab: 3, titleAr: "المحاسبة المزدوجة والقوائم والـ 18 نسبة", titleEn: "Double-Entry Ledger & 18 Ratios", descAr: "رحّل قيوداً متوازنة لاستخراج قائمة الدخل والميزانية العمومية و18 نسبة مالية وأعمار الذمم.", descEn: "Post balanced journals for Income Statement, Balance Sheet, 18 ratios & AR/AP aging." },
    { tab: 4, titleAr: "المخزون والهدر والموازنات", titleEn: "Inventory, Expiry & Budgets", descAr: "تابع FIFO والمتوسط المرجح وتنبيهات الصلاحية وتكلفة الاستبدال الفعلية.", descEn: "Monitor FIFO/MWA inventory book value vs replacement cost, batch expiry & budgets." },
    { tab: 5, titleAr: "المساعد المالي وكاميرا الفواتير والفوترة", titleEn: "Financial Assistant, Camera OCR & Billing", descAr: "اسأل المساعد المالي، وصوّر الفواتير بالكاميرا مباشرة، وأرسل إثبات الدفع للمراجعة.", descEn: "Query the Financial Assistant, scan invoices via camera OCR & submit payment proofs." }
  ];
  const st = steps[Math.min(state.tourStep, steps.length - 1)];
  return `
    <div class="fvg-modal-overlay">
      <div class="fvg-tour-card">
        <div class="row-between">
          <span class="badge">${state.tourStep + 1} / ${steps.length}</span>
          <button class="btn btn-outline" style="min-height:36px;padding:4px 12px;" data-action="skipTour">${t("Skip", "تخطي")}</button>
        </div>
        <h3 style="margin:10px 0 6px;">${esc(state.isArabic ? st.titleAr : st.titleEn)}</h3>
        <p style="line-height:1.6;margin:0 0 14px;">${esc(state.isArabic ? st.descAr : st.descEn)}</p>
        <div style="display:flex;gap:8px;">
          ${state.tourStep > 0 ? `<button class="btn btn-outline" style="flex:1;" data-action="prevTour">${t("Previous", "السابق")}</button>` : ""}
          <button class="btn btn-gold" style="flex:1;" data-action="nextTour">${state.tourStep === steps.length - 1 ? t("Finish", "إنهاء") : t("Next", "التالي")}</button>
        </div>
      </div>
    </div>
  `;
}

function renderAppShell() {
  const nav = [
    { idx: 0, label: t("Home", "الرئيسية"), icon: "🏠" },
    { idx: 1, label: t("Prices", "الأسعار"), icon: "📈" },
    { idx: 2, label: t("Costing", "التكاليف"), icon: "🧮" },
    { idx: 3, label: t("Ledger", "المحاسبة"), icon: "⚖️" },
    { idx: 4, label: t("Stock", "المخزون"), icon: "📦" },
    { idx: 5, label: t("Assistant", "المساعد المالي"), icon: "✨" }
  ];

  let screenHtml = "";
  if (state.secondaryView === "profile") screenHtml = renderProfileView();
  else if (state.secondaryView === "help") screenHtml = renderHelpView();
  else if (state.secondaryView === "owner_admin") screenHtml = renderOwnerAdminView();
  else if (isTrialLocked() && state.appTab >= 1 && state.appTab <= 4) screenHtml = renderTrialGateCard();
  else if (state.appTab === 0) screenHtml = renderHomeTab();
  else if (state.appTab === 1) screenHtml = renderPricesTab();
  else if (state.appTab === 2) screenHtml = renderCostingTab();
  else if (state.appTab === 3) screenHtml = renderLedgerTab();
  else if (state.appTab === 4) screenHtml = renderStockTab();
  else screenHtml = renderReportsAiTab();

  const tourActiveTab = state.showTour ? state.tourStep : -1;

  return `
    <div class="app-shell">
      <aside class="side-rail">
        <div class="rail-brand">
          <img src="./assets/fvg-icon.svg" alt="FVG"/>
          <div><div style="font-weight:800;">FVG</div><div style="font-size:0.72rem;color:#DDB065;">${esc(state.activeOrg?.name || t("Workspace", "مساحة العمل"))}</div></div>
        </div>
        ${nav.map((n) => `<button class="nav-btn ${state.appTab === n.idx && !state.secondaryView ? "active" : ""} ${tourActiveTab === n.idx ? "fvg-tour-highlight" : ""}" data-tab="${n.idx}"><span>${n.icon}</span><span>${esc(n.label)}</span></button>`).join("")}
        <hr style="border:none;border-top:1px solid rgba(221,176,101,0.25);width:100%;margin:8px 0;"/>
        <button class="nav-btn ${state.secondaryView === "profile" ? "active" : ""}" data-action="openProfile"><span>👤</span><span>${t("My Profile", "ملفي الشخصي")}</span></button>
        <button class="nav-btn ${state.secondaryView === "help" ? "active" : ""}" data-action="openHelp"><span>❓</span><span>${t("Help & Support", "المساعدة والدعم")}</span></button>
        ${isPlatformOwner() ? `<button class="nav-btn ${state.secondaryView === "owner_admin" ? "active" : ""}" data-action="openOwnerAdmin"><span>🛡</span><span>${t("Owner Console", "لوحة مالكة المنصة")}</span></button>` : ""}
      </aside>
      <main class="main-content">
        ${renderBanner(state.activeOrg?.name || "FVG", `${state.user?.email || ""} • ${state.activeOrg?.baseCurrency || "SDG"}`, true)}
        ${state.toast ? `<div class="card cream" style="border-color:var(--fvg-warm-gold);font-weight:700;">${esc(state.toast)}</div>` : ""}
        ${screenHtml}
      </main>
      <nav class="bottom-nav">
        ${nav.map((n) => `<button class="nav-btn ${state.appTab === n.idx && !state.secondaryView ? "active" : ""} ${tourActiveTab === n.idx ? "fvg-tour-highlight" : ""}" data-tab="${n.idx}"><span>${n.icon}</span><span>${esc(n.label)}</span></button>`).join("")}
      </nav>
    </div>
    ${renderCalculatorModal()}
    ${renderTourModal()}
  `;
}

function downloadFile(filename, content, mime) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function processImageFileToDataUrl(file, maxSize, callback) {
  const reader = new FileReader();
  reader.onload = () => {
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, maxSize / Math.max(img.width, img.height));
      const canvas = document.createElement("canvas");
      canvas.width = Math.max(1, Math.round(img.width * scale));
      canvas.height = Math.max(1, Math.round(img.height * scale));
      const ctx = canvas.getContext("2d");
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      callback(canvas.toDataURL("image/jpeg", 0.82));
    };
    img.src = reader.result;
  };
  reader.readAsDataURL(file);
}

function rotateDataUrl90(dataUrl, callback) {
  const img = new Image();
  img.onload = () => {
    const canvas = document.createElement("canvas");
    canvas.width = img.height;
    canvas.height = img.width;
    const ctx = canvas.getContext("2d");
    ctx.translate(canvas.width / 2, canvas.height / 2);
    ctx.rotate(Math.PI / 2);
    ctx.drawImage(img, -img.width / 2, -img.height / 2);
    callback(canvas.toDataURL("image/jpeg", 0.85));
  };
  img.src = dataUrl;
}

function bindEvents() {
  const root = document.getElementById("app");
  if (!root) return;

  const splash = document.getElementById("splashRoot");
  if (splash) {
    splash.onclick = () => {
      state.stage = "public";
      render();
    };
  }

  root.querySelectorAll("[data-public-page]").forEach((btn) => {
    btn.onclick = () => { state.publicPage = Number(btn.dataset.publicPage); render(); };
  });

  root.querySelectorAll("[data-tab]").forEach((btn) => {
    btn.onclick = () => { state.secondaryView = null; state.appTab = Number(btn.dataset.tab); render(); };
  });

  root.querySelectorAll("[data-sub]").forEach((btn) => {
    btn.onclick = () => {
      const [group, idx] = btn.dataset.sub.split(":");
      state.subTab[group] = Number(idx);
      render();
    };
  });

  root.querySelectorAll("[data-ai-mode]").forEach((btn) => {
    btn.onclick = () => { state.aiMode = btn.dataset.aiMode; render(); };
  });

  root.querySelectorAll("[data-select-conv]").forEach((btn) => {
    btn.onclick = () => {
      const conv = state.aiConversations.find((c) => c.id === btn.dataset.selectConv);
      if (conv) {
        state.activeConversationId = conv.id;
        state.chatTranscript = conv.messages;
        state.aiResponse = conv.messages[conv.messages.length - 1]?.text || "";
        render();
      }
    };
  });

  root.querySelectorAll("[data-preset]").forEach((btn) => {
    btn.onclick = () => {
      const p = Engine.SPEC_SCENARIO_PRESETS.find((x) => x.key === btn.dataset.preset);
      if (!p) return;
      const setV = (id, v) => { const el = document.getElementById(id); if (el) el.value = v; };
      setV("scMat", p.matPct); setV("scFx", p.fxPct); setV("scVol", p.volumePct); setV("scPrice", p.pricePct);
    };
  });

  root.querySelectorAll("[data-fx-status]").forEach((btn) => {
    btn.onclick = () => {
      const [nextStatus, fxId] = btn.dataset.fxStatus.split(":");
      const item = state.fxRates.find((f) => f.id === fxId);
      if (item) { item.status = nextStatus; persist(); showToast(t("FX rate status updated.", "تم تحديث حالة سعر الصرف.")); }
    };
  });

  root.querySelectorAll("[data-del-bom]").forEach((btn) => {
    btn.onclick = () => {
      state.bomFormulas = state.bomFormulas.filter((b) => b.id !== btn.dataset.delBom);
      persist();
      showToast(t("BOM deleted.", "تم حذف التركيبة."));
    };
  });

  root.querySelectorAll("[data-verify-ocr]").forEach((btn) => {
    btn.onclick = () => {
      const id = btn.dataset.verifyOcr;
      const d = state.ocrDrafts.find((x) => x.id === id);
      if (!d) return;
      const q = (f) => root.querySelector(`[data-ocr-field="${f}:${id}"]`)?.value;
      d.vendorName = q("vendor") || d.vendorName;
      d.invoiceNumber = q("inv") || d.invoiceNumber;
      d.subtotal = Engine.money(q("sub"));
      d.discount = Engine.money(q("disc"));
      d.tax = Engine.money(q("tax"));
      d.total = Engine.money(q("tot"));
      d.reviewStatus = "verified";
      persist();
      showToast(t("Invoice draft verified.", "تم اعتماد مسودة الفاتورة."));
    };
  });

  root.querySelectorAll("[data-owner-pay]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const [decision, id] = btn.dataset.ownerPay.split(":");
      const req = state.paymentRequests.find((r) => r.id === id);
      if (!req) return;
      req.status = decision;
      req.reviewNotes = decision === "approved" ? t("Verified by Platform Owner.", "تم التحقق والموافقة من مالكة المنصة.") : t("Could not verify reference.", "تعذّر مطابقة المرجع.");
      if (decision === "approved" && state.activeOrg) {
        state.activeOrg.planTier = req.planTier;
        state.activeOrg.subscriptionStatus = "active";
      }
      persist();
      showToast(t("Payment request updated.", "تم تحديث حالة الدفع."));
    };
  });

  root.querySelectorAll("[data-owner-reply]").forEach((btn) => {
    btn.onclick = () => {
      if (!isPlatformOwner()) return;
      const id = btn.dataset.ownerReply;
      const tk = state.supportTickets.find((x) => x.id === id);
      if (!tk) return;
      tk.ownerReply = document.getElementById("reply_" + id)?.value?.trim() || "";
      tk.status = "replied";
      persist();
      showToast(t("Reply sent.", "تم إرسال الرد."));
    };
  });

  root.querySelectorAll("[data-revoke-sess]").forEach((btn) => {
    btn.onclick = () => {
      const s = state.sessions.find((x) => x.id === btn.dataset.revokeSess);
      if (s) { s.revoked = true; persist(); showToast(t("Session revoked.", "تم إنهاء الجلسة.")); }
    };
  });

  root.querySelectorAll("[data-calc-key]").forEach((btn) => {
    btn.onclick = () => {
      const k = btn.dataset.calcKey;
      if (k === "C") { state.calc.expr = ""; state.calc.display = "0"; }
      else if (k === "⌫") { state.calc.expr = state.calc.expr.slice(0, -1); }
      else if (k === "MC") { state.calc.memory = 0; }
      else if (k === "MR") { state.calc.expr += String(state.calc.memory); }
      else if (k === "M+" || k === "M-") {
        const res = Engine.evaluateCalculatorExpression(state.calc.expr || state.calc.display);
        if (res.value != null) state.calc.memory += (k === "M+" ? res.value : -res.value);
      } else if (k === "=") {
        const res = Engine.evaluateCalculatorExpression(state.calc.expr);
        state.calc.display = res.error ? res.formatted : (state.calc.currencyFmt ? Engine.money(res.value).toLocaleString("en-US") : res.formatted);
        if (!res.error) state.calc.expr = String(res.value);
      } else {
        state.calc.expr += k;
        const live = Engine.evaluateCalculatorExpression(state.calc.expr);
        if (!live.error) state.calc.display = state.calc.currencyFmt ? Engine.money(live.value).toLocaleString("en-US") : live.formatted;
      }
      render();
    };
  });

  const avatarEl = document.getElementById("avatarFileInput");
  if (avatarEl) {
    avatarEl.onchange = () => {
      const file = avatarEl.files?.[0];
      if (!file) return;
      processImageFileToDataUrl(file, 256, (dataUrl) => {
        if (state.user) state.user.avatarDataUrl = dataUrl;
        persist();
        showToast(t("Profile photo updated.", "تم تحديث صورة الملف الشخصي."));
      });
    };
  }

  ["ocrCameraInput", "ocrGalleryInput"].forEach((id) => {
    const el = document.getElementById(id);
    if (el) {
      el.onchange = () => {
        const file = el.files?.[0];
        if (!file) return;
        processImageFileToDataUrl(file, 1024, (dataUrl) => {
          state.pendingOcrDataUrl = dataUrl;
          render();
        });
      };
    }
  });

  root.querySelectorAll("[data-action]").forEach((btn) => {
    btn.onclick = () => {
      const act = btn.dataset.action;
      if (act === "toggleLang") {
        state.isArabic = !state.isArabic; persist(); render();
      } else if (act === "cycleTheme") {
        state.themeMode = state.themeMode === "system" ? "light" : (state.themeMode === "light" ? "dark" : "system");
        persist(); render();
      } else if (act === "openCalc") { state.showCalculator = true; render();
      } else if (act === "closeCalc") { state.showCalculator = false; render();
      } else if (act === "copyCalc") {
        navigator.clipboard?.writeText(state.calc.display).catch(() => {});
        showToast(t("Result copied.", "تم نسخ النتيجة."));
      } else if (act === "openProfile") { state.secondaryView = "profile"; render();
      } else if (act === "openHelp") { state.secondaryView = "help"; render();
      } else if (act === "openOwnerAdmin") { if (isPlatformOwner()) { state.secondaryView = "owner_admin"; render(); }
      } else if (act === "closeSecondary") { state.secondaryView = null; render();
      } else if (act === "replayTour") { state.secondaryView = null; state.appTab = 0; state.tourStep = 0; state.showTour = true; render();
      } else if (act === "nextTour") {
        if (state.tourStep < 5) { state.tourStep++; state.appTab = state.tourStep; render(); }
        else { state.showTour = false; if (state.user) state.user.tourCompleted = true; persist(); render(); }
      } else if (act === "prevTour") {
        if (state.tourStep > 0) { state.tourStep--; state.appTab = state.tourStep; render(); }
      } else if (act === "skipTour") {
        state.showTour = false; if (state.user) state.user.tourCompleted = true; persist(); render();
      } else if (act === "goBilling") {
        state.secondaryView = null; state.appTab = 5; state.subTab.reports = 2; render();
      } else if (act === "signOut") {
        state.secondaryView = null; state.stage = "public"; render();
      } else if (act === "resetPassword") {
        const email = document.getElementById("authEmailInput")?.value?.trim();
        if (!email || !email.includes("@")) return showToast(t("Enter a valid email first.", "أدخل بريدك الإلكتروني أولاً."));
        showToast(t(`Password reset requested for ${email}. Configure Firebase Auth Web API key for live email delivery.`, `تم طلب استعادة كلمة المرور للبريد ${email}.`));
      } else if (act === "launchGoogle") {
        const orgName = document.getElementById("authOrgInput")?.value?.trim() || t("FVG Workspace", "مساحة عمل FVG");
        if (isFirebaseWebConfigured()) { launchWithFirebaseWeb(orgName); return; }
        initLocalWorkspace("workspace@fvg.local", t("FVG User", "مستخدم FVG"), orgName);
      } else if (act === "launchEmailSignIn") {
        const email = document.getElementById("authEmailInput")?.value?.trim();
        const pass = document.getElementById("authPassInput")?.value || "";
        const name = document.getElementById("authNameInput")?.value?.trim() || email?.split("@")[0] || "FVG User";
        const orgName = document.getElementById("authOrgInput")?.value?.trim() || t("FVG Workspace", "مساحة عمل FVG");
        if (!email || !email.includes("@")) return showToast(t("Please enter a valid email address.", "يرجى إدخال بريد إلكتروني صحيح."));
        if (pass.length < 6) return showToast(t("Password must be at least 6 characters.", "كلمة المرور يجب أن تكون 6 أحرف على الأقل."));
        initLocalWorkspace(email, name, orgName);
      } else if (act === "saveProfile") {
        if (state.user) state.user.displayName = document.getElementById("profName")?.value?.trim() || state.user.displayName;
        state.isArabic = (document.getElementById("profLang")?.value || "ar") === "ar";
        state.themeMode = document.getElementById("profTheme")?.value || "system";
        state.fontScale = Number(document.getElementById("profFontScale")?.value || 1.0);
        persist(); showToast(t("Profile saved.", "تم حفظ التفضيلات."));
      } else if (act === "removeAvatar") {
        if (state.user) state.user.avatarDataUrl = null;
        persist(); showToast(t("Avatar removed.", "تم حذف الصورة."));
      } else if (act === "rotateOcr" && state.pendingOcrDataUrl) {
        rotateDataUrl90(state.pendingOcrDataUrl, (rotated) => { state.pendingOcrDataUrl = rotated; render(); });
      } else if (act === "createOcrDraft") {
        state.ocrDrafts.push({
          id: "ocr_" + Date.now(),
          vendorName: "",
          invoiceNumber: "",
          subtotal: 0,
          discount: 0,
          tax: 0,
          total: 0,
          reviewStatus: "pending_review"
        });
        state.pendingOcrDataUrl = null;
        persist();
        showToast(t("OCR draft ready for human verification.", "تم إنشاء مسودة الفاتورة للمراجعة والتحقق البشري."));
      } else if (act === "savePrice") {
        const name = document.getElementById("prName").value.trim();
        const sku = document.getElementById("prSku").value.trim() || "SKU";
        const price = Engine.money(document.getElementById("prVal").value);
        if (!name || price <= 0) return showToast(t("Product name & positive price required.", "يرجى إدخال اسم المنتج وسعر موجب."));
        recordPriceObservationItem(name, sku, price);
        persist(); showToast(t("Price observation saved.", "تم حفظ السعر بنجاح."));
      } else if (act === "importCsv") {
        const fileEl = document.getElementById("csvFile");
        const pasteText = document.getElementById("csvPaste")?.value?.trim() || "";
        const handleCsvText = (txt) => {
          const lines = txt.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
          let count = 0;
          lines.forEach((line, idx) => {
            const cols = line.split(",").map((c) => c.trim());
            if (idx === 0 && cols[0].toLowerCase() === "name") return;
            const name = cols[0], sku = cols[1] || "CSV", price = Engine.money(cols[3] || cols[2]);
            if (name && price > 0) { recordPriceObservationItem(name, sku, price); count++; }
          });
          persist(); showToast(count ? t(`Imported ${count} rows.`, `تم استيراد ${count} صفوف بنجاح.`) : t("No valid CSV rows found.", "لم يتم العثور على صفوف صالحة."));
        };
        if (fileEl?.files?.[0]) fileEl.files[0].text().then(handleCsvText);
        else handleCsvText(pasteText);
      } else if (act === "saveInflation") {
        const period = document.getElementById("infPeriod").value.trim();
        const monthlyRate = Engine.pct4(document.getElementById("infMonthly").value);
        const sourceType = document.getElementById("infSource").value;
        if (!period) return;
        state.inflationRecords.unshift({ id: "inf_" + Date.now(), period, monthlyRate, sourceType });
        persist(); showToast(t("Inflation record saved.", "تم حفظ سجل التضخم."));
      } else if (act === "saveBasket") {
        const name = document.getElementById("bskName").value.trim() || "Basket";
        const item = document.getElementById("bskItem").value.trim() || "Input";
        const res = Engine.calculateWeightedBasket([{
          quantity: Engine.n(document.getElementById("bskQty").value),
          weight: Engine.n(document.getElementById("bskWeight").value),
          basePrice: Engine.n(document.getElementById("bskBase").value),
          currentPrice: Engine.n(document.getElementById("bskCurr").value)
        }]);
        state.baskets.unshift({ id: "bsk_" + Date.now(), name, item, ...res });
        persist(); showToast(t("Weighted basket saved.", "تم حفظ السلة الموزونة."));
      } else if (act === "saveFx") {
        const base = document.getElementById("fxBase").value.trim().toUpperCase();
        const quote = document.getElementById("fxQuote").value.trim().toUpperCase();
        const rate = Engine.pct4(document.getElementById("fxRate").value);
        const date = document.getElementById("fxDate").value || new Date().toISOString().slice(0, 10);
        const source = document.getElementById("fxSource").value.trim();
        const status = document.getElementById("fxStatus").value;
        if (rate <= 0) return showToast(t("Positive FX rate required.", "يرجى إدخال سعر صرف موجب."));
        state.fxRates.unshift({ id: "fx_" + Date.now(), base, quote, rate, date, source, status });
        persist(); showToast(t("Manual FX rate saved.", "تم حفظ سعر الصرف اليدوي."));
      } else if (act === "saveBom") {
        const productName = document.getElementById("bomProd").value.trim();
        const ingredientName = document.getElementById("bomIng").value.trim();
        const qty = Engine.n(document.getElementById("bomQty").value);
        const cost = Engine.n(document.getElementById("bomCost").value);
        const batch = Math.max(1, Engine.n(document.getElementById("bomBatch").value));
        const scrap = Engine.n(document.getElementById("bomScrap").value);
        if (!productName || !ingredientName) return showToast(t("Product & ingredient required.", "يرجى إدخال المنتج والمادة."));
        const unitMaterialCost = Engine.money(((qty * cost) * (1 + scrap / 100)) / batch);
        state.bomFormulas.push({ id: "bom_" + Date.now(), productName, ingredientName, qty, cost, batch, scrap, unitMaterialCost });
        persist(); showToast(t("BOM recipe saved.", "تم حفظ التركيبة بنجاح."));
      } else if (act === "saveCosting") {
        const method = document.getElementById("costMethod").value;
        const productName = document.getElementById("cProd").value.trim() || "Product";
        const extra = Engine.n(document.getElementById("cExtra").value);
        const input = {
          directMaterial: Engine.n(document.getElementById("cMat").value), indirectMaterial: 0,
          directLabor: Engine.n(document.getElementById("cLab").value), indirectLabor: 0,
          overhead: Engine.n(document.getElementById("cOvh").value),
          energyFuel: 0, packaging: 0, transport: 0, wasteScrap: 0,
          fixedCosts: Engine.n(document.getElementById("cFix").value),
          goodUnits: Engine.n(document.getElementById("cUnits").value),
          sellingPrice: Engine.n(document.getElementById("cPrice").value)
        };
        const out = Engine.calculateCostingByMethod(method, input, extra, extra, state.isArabic);
        const latestInf = state.inflationRecords[0]?.monthlyRate ?? null;
        const stmt = renderStatementsAndRatios();
        const risk = Engine.evaluateFinancialRisk(out.coreResult.profitMarginPct, latestInf, stmt.totalLiabilities, stmt.totalEquityWithNetIncome, state.isArabic);
        state.costSnapshots.push({ id: "cs_" + Date.now(), productName, method, input, sellingPrice: input.sellingPrice, coreResult: out.coreResult, methodNote: out.methodNote, risk });
        persist(); showToast(t("Costing snapshot saved.", "تم حفظ لقطة التكلفة والمخاطر."));
      } else if (act === "calcVariance") {
        const d = Engine.computeDetailedVariance({
          goodUnits: document.getElementById("vUnits").value,
          stdMaterialQtyPerUnit: document.getElementById("vSmq").value,
          stdMaterialPrice: document.getElementById("vSmp").value,
          actualMaterialQty: document.getElementById("vAmq").value,
          actualMaterialPrice: document.getElementById("vAmp").value,
          stdLaborHoursPerUnit: document.getElementById("vSlh").value,
          stdLaborRate: document.getElementById("vSlr").value,
          actualLaborHours: document.getElementById("vAlh").value,
          actualLaborRate: document.getElementById("vAlr").value,
          stdOverheadPerUnit: 0, actualOverheadCost: 0
        });
        const box = document.getElementById("varianceResultBox");
        if (box) {
          box.innerHTML = `<div class="card cream"><div>${t("Mat Price Var", "انحراف السعر")}: <strong>${d.materialPriceVariance}</strong> | ${t("Mat Usage Var", "انحراف الكمية")}: <strong>${d.materialUsageVariance}</strong></div><div>${t("Labor Rate Var", "انحراف الأجر")}: <strong>${d.laborRateVariance}</strong> | ${t("Labor Eff Var", "انحراف الكفاءة")}: <strong>${d.laborEfficiencyVariance}</strong></div><div>${t("Total Variance", "إجمالي الانحراف")}: <strong>${d.totalVariance}</strong></div></div>`;
        }
      } else if (act === "runScenario") {
        const snap = state.costSnapshots[state.costSnapshots.length - 1];
        if (!snap) return;
        const sim = Engine.runExtendedScenario({
          baseMaterial: snap.input.directMaterial, baseLabor: snap.input.directLabor,
          baseOverheadAndOther: snap.input.overhead + snap.input.fixedCosts, baseRevenue: snap.coreResult.revenue,
          materialChangePct: document.getElementById("scMat").value, fxChangePct: document.getElementById("scFx").value,
          volumeChangePct: document.getElementById("scVol").value, priceChangePct: document.getElementById("scPrice").value
        });
        state.scenarios.push({ id: "sc_" + Date.now(), name: snap.productName, ...sim });
        persist(); showToast(t("Scenario saved.", "تم حفظ السيناريو."));
      } else if (act === "postJe") {
        const desc = document.getElementById("jeDesc").value.trim();
        const amt = Engine.money(document.getElementById("jeAmt").value);
        const dr = document.getElementById("jeDr").value;
        const cr = document.getElementById("jeCr").value;
        if (!desc || amt <= 0 || dr === cr) return showToast(t("Valid description, positive amount & distinct accounts required.", "يرجى إدخال وصف ومبلغ موجب وحسابين مختلفين."));
        state.journalEntries.push({
          id: "je_" + Date.now(),
          referenceNo: "JE-" + String(state.journalEntries.length + 1).padStart(3, "0"),
          date: new Date().toISOString().slice(0, 10),
          description: desc,
          status: "posted",
          total: amt,
          lines: [{ accountCode: dr, debit: amt, credit: 0 }, { accountCode: cr, debit: 0, credit: amt }]
        });
        persist(); showToast(t("Balanced journal posted.", "تم ترحيل القيد المزدوج."));
      } else if (act === "saveInventory") {
        const sku = document.getElementById("invSku").value.trim();
        const name = document.getElementById("invName").value.trim();
        if (!sku || !name) return showToast(t("SKU & Name required.", "الرمز والاسم مطلوبان."));
        state.inventoryItems.push({
          id: "inv_" + Date.now(), sku, name,
          method: document.getElementById("invMethod").value,
          qty: Engine.n(document.getElementById("invQty").value),
          avgCost: Engine.n(document.getElementById("invCost").value),
          marketPrice: Engine.n(document.getElementById("invMkt").value),
          batchNumber: document.getElementById("invBatch").value.trim(),
          expiryDate: document.getElementById("invExpiry").value
        });
        persist(); showToast(t("Inventory item saved.", "تم حفظ الصنف المخزني."));
      } else if (act === "saveBudget") {
        const name = document.getElementById("bdgName").value.trim();
        const planned = Engine.money(document.getElementById("bdgPlan").value);
        const actual = Engine.money(document.getElementById("bdgAct").value);
        if (!name || planned <= 0) return showToast(t("Budget name & planned amount required.", "اسم الموازنة والمبلغ المخطط مطلوبان."));
        const variance = Engine.money(actual - planned);
        const variancePct = Engine.pct4((variance / planned) * 100);
        state.budgets.push({ id: "bdg_" + Date.now(), name, planned, actual, variance, variancePct });
        persist(); showToast(t("Budget saved.", "تم حفظ الموازنة."));
      } else if (act === "newConversation") {
        state.activeConversationId = null; state.chatTranscript = []; state.aiResponse = ""; render();
      } else if (act === "askAi") {
        const q = document.getElementById("aiPrompt").value.trim();
        if (!q) return;
        const stmt = renderStatementsAndRatios();
        const ans = state.isArabic ? [
          `[المساعد المالي FVG — تحليل حتمي موثق من سجلاتك]`,
          `المنظمة: ${state.activeOrg?.name || "غير محددة"} (${state.activeOrg?.baseCurrency || "SDG"})`,
          `المنتجات المسجلة: ${state.products.length} | حسابات التكلفة: ${state.costSnapshots.length} | القيود المرحّلة: ${state.journalEntries.length}`,
          `الإيرادات: ${stmt.revenue} | مجمل الربح: ${stmt.grossProfit} | صافي الربح: ${stmt.netProfit}`,
          `ملخص السؤال: "${q}"`
        ].join("\n") : [
          `[FVG Financial Assistant — Verified Workspace Analysis]`,
          `Organization: ${state.activeOrg?.name || "None"} (${state.activeOrg?.baseCurrency || "SDG"})`,
          `Products: ${state.products.length} | Cost Snapshots: ${state.costSnapshots.length} | Journals: ${state.journalEntries.length}`,
          `Revenue: ${stmt.revenue} | Gross Profit: ${stmt.grossProfit} | Net Profit: ${stmt.netProfit}`,
          `Question: "${q}"`
        ].join("\n");
        state.aiResponse = ans;
        state.chatTranscript.push({ role: `User [${state.aiMode}]`, text: q }, { role: `FVG [${state.aiMode}]`, text: ans });
        if (!state.activeConversationId) {
          const id = "conv_" + Date.now();
          state.activeConversationId = id;
          state.aiConversations.unshift({ id, title: q.slice(0, 28), messages: [...state.chatTranscript] });
        } else {
          const c = state.aiConversations.find((x) => x.id === state.activeConversationId);
          if (c) c.messages = [...state.chatTranscript];
        }
        persist(); render();
      } else if (act === "copyAi") {
        navigator.clipboard?.writeText(state.aiResponse).catch(() => {});
        showToast(t("Answer copied.", "تم نسخ الإجابة."));
      } else if (act === "exportTranscript") {
        const txt = state.chatTranscript.map((m, i) => `[${i + 1}] ${m.role}:\n${m.text}\n`).join("\n") || t("No transcript yet.", "لا توجد محادثات.");
        downloadFile("fvg_assistant_transcript.txt", txt, "text/plain");
      } else if (act === "exportCsv") {
        const stmt = renderStatementsAndRatios();
        const csv = `Metric,Value\nRevenue,${stmt.revenue}\nCOGS,${stmt.cogs}\nNet Profit,${stmt.netProfit}\nTotal Assets,${stmt.totalAssets}\nTotal Liabilities,${stmt.totalLiabilities}\nEquity,${stmt.totalEquityWithNetIncome}\n`;
        downloadFile("fvg_report.csv", csv, "text/csv");
      } else if (act === "exportJson") {
        downloadFile("fvg_workspace_backup.json", JSON.stringify(state, null, 2), "application/json");
      } else if (act === "printPdf") {
        window.print();
      } else if (act === "submitPayment") {
        const senderName = document.getElementById("paySender").value.trim();
        const referenceNo = document.getElementById("payRef").value.trim();
        const last4Digits = document.getElementById("payLast4").value.trim();
        const notes = document.getElementById("payNotes").value.trim();
        if (!senderName || referenceNo.length < 2) return showToast(t("Sender name & reference required.", "اسم المحوّل ورقم المرجع مطلوبان."));
        state.paymentRequests.unshift({
          id: "pay_" + Date.now(),
          planTier: document.getElementById("payPlan").value,
          paymentMethod: document.getElementById("payMethod").value,
          amount: document.getElementById("payAmt").value,
          senderName,
          referenceNo,
          last4Digits,
          notes,
          status: "pending_verification"
        });
        persist(); showToast(t("Payment proof submitted [pending_verification].", "تم إرسال إثبات الدفع للمراجعة."));
      } else if (act === "saveCounterparty") {
        const partyName = document.getElementById("cpParty").value.trim();
        const invoiceNo = document.getElementById("cpInv").value.trim() || "INV";
        const amount = Engine.money(document.getElementById("cpAmt").value);
        if (!partyName || amount <= 0) return showToast(t("Party name & positive amount required.", "اسم الجهة ومبلغ موجب مطلوبان."));
        state.counterpartyItems.unshift({
          id: "cp_" + Date.now(), type: document.getElementById("cpType").value,
          partyName, invoiceNo, amount, paidAmount: 0, dueDate: document.getElementById("cpDue").value, status: "open"
        });
        persist(); showToast(t("AR/AP record saved.", "تم حفظ الذمة."));
      } else if (act === "saveScrap") {
        const itemName = document.getElementById("scrItem").value.trim();
        const qty = Engine.n(document.getElementById("scrQty").value);
        const cost = Engine.n(document.getElementById("scrCost").value);
        const reason = document.getElementById("scrReason").value.trim() || t("Waste", "تلف");
        if (!itemName || qty <= 0) return showToast(t("Item & positive qty required.", "الصنف والكمية مطلوبان."));
        state.scrapLosses.unshift({
          id: "scr_" + Date.now(), itemName, lossType: document.getElementById("scrType").value,
          quantity: qty, unitCost: cost, totalLossCost: Engine.money(qty * cost), reason
        });
        persist(); showToast(t("Scrap loss logged.", "تم تسجيل الفاقد."));
      } else if (act === "submitTicket") {
        const msg = document.getElementById("helpMsg")?.value?.trim();
        if (!msg) return showToast(t("Enter your message first.", "يرجى كتابة الرسالة."));
        state.supportTickets.unshift({
          id: "tk_" + Date.now(),
          senderEmail: state.user?.email || "user@fvg",
          message: msg,
          status: "open",
          ownerReply: "",
          createdAt: new Date().toISOString().slice(0, 10)
        });
        persist(); showToast(t("Support request sent.", "تم إرسال تذكرة الدعم."));
      } else if (act === "publishAnnouncement" && isPlatformOwner()) {
        const titleAr = document.getElementById("annTitleAr")?.value?.trim() || "";
        const titleEn = document.getElementById("annTitleEn")?.value?.trim() || "";
        const body = document.getElementById("annBody")?.value?.trim() || "";
        if (!titleAr && !titleEn) return;
        state.announcements.unshift({ id: "ann_" + Date.now(), titleAr, titleEn, bodyAr: body, bodyEn: body, active: true });
        persist(); showToast(t("Announcement published.", "تم نشر الإعلان."));
      }
    };
  });

  root.querySelectorAll("[data-reverse-je]").forEach((btn) => {
    btn.onclick = () => {
      const orig = state.journalEntries.find((j) => j.id === btn.dataset.reverseJe);
      if (!orig || orig.status !== "posted") return;
      orig.status = "reversed";
      state.journalEntries.push({
        id: "je_rev_" + Date.now(), referenceNo: orig.referenceNo + "-REV",
        date: new Date().toISOString().slice(0, 10),
        description: "Reversal of " + orig.referenceNo + ": " + orig.description,
        status: "posted", total: orig.total,
        lines: orig.lines.map((l) => ({ accountCode: l.accountCode, debit: l.credit, credit: l.debit }))
      });
      persist(); showToast(t("Journal reversed.", "تم إنشاء القيد العكسي."));
    };
  });

  root.querySelectorAll("[data-stock-move]").forEach((btn) => {
    btn.onclick = () => {
      const [dir, itemId] = btn.dataset.stockMove.split(":");
      const item = state.inventoryItems.find((i) => i.id === itemId);
      if (!item) return;
      item.qty = dir === "in" ? Engine.money(Engine.n(item.qty) + 10) : Math.max(0, Engine.money(Engine.n(item.qty) - 10));
      persist(); showToast(t(`${item.sku} stock: ${item.qty}`, `رصيد ${item.sku}: ${item.qty}`));
    };
  });
}

function initLocalWorkspace(email, displayName, orgName) {
  if (!state.user || state.user.email !== email) {
    state.user = {
      email,
      displayName,
      trialDurationHours: Engine.TRIAL_DURATION_HOURS,
      trialStartedAtMillis: Date.now(),
      tourCompleted: false
    };
    const org = {
      id: "org_" + Date.now(),
      name: orgName,
      baseCurrency: "SDG",
      planTier: "trial",
      subscriptionStatus: "trial",
      trialDurationHours: Engine.TRIAL_DURATION_HOURS,
      trialEndsAtMillis: Date.now() + Engine.TRIAL_DURATION_MILLIS
    };
    state.organizations.push(org);
    state.activeOrg = org;
    logAudit("INIT_WORKSPACE", "organization", `Initialized ${orgName} (168h / 7-day trial)`);
  }
  state.stage = "app";
  if (!state.user.tourCompleted) state.showTour = true;
  persist();
  render();
}

function recordPriceObservationItem(name, sku, price) {
  const existing = state.products.find((p) => p.name.toLowerCase() === name.toLowerCase());
  const prev = existing ? existing.currentPrice : 0;
  const delta = Engine.calculatePercentageDelta(prev, price);
  if (existing) existing.currentPrice = price;
  else state.products.push({ id: "p_" + Date.now() + "_" + Math.random().toString(36).slice(2, 6), name, sku, currentPrice: price });
  state.priceObservations.push({
    id: "obs_" + Date.now() + "_" + Math.random().toString(36).slice(2, 6),
    productName: name, sku, previousPrice: prev, price,
    pctChange: delta.pctChange, direction: delta.direction, isUnusual: delta.isUnusual,
    date: new Date().toISOString().slice(0, 10)
  });
  logAudit("SAVE_PRICE", "priceObservation", `${name}: ${price}`);
}

async function launchWithFirebaseWeb(orgName) {
  try {
    const [{ initializeApp }, { getAuth, GoogleAuthProvider, signInWithPopup }, { getFirestore, doc, getDoc, setDoc, serverTimestamp, Timestamp }] = await Promise.all([
      import("https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js"),
      import("https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js"),
      import("https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js")
    ]);
    const app = initializeApp(FVG_FIREBASE_CONFIG);
    const auth = getAuth(app);
    const cred = await signInWithPopup(auth, new GoogleAuthProvider());
    const db = getFirestore(app, FVG_FIREBASE_CONFIG.databaseId);
    const uid = cred.user.uid;
    const email = cred.user.email || "";
    const uRef = doc(db, "users", uid);
    const uSnap = await getDoc(uRef);
    let orgId = uSnap.exists() ? uSnap.data().activeOrgId : null;
    if (!orgId) {
      orgId = "org_" + uid.slice(0, 12);
      const trialEnd = Timestamp.fromMillis(Date.now() + Engine.TRIAL_DURATION_MILLIS);
      await setDoc(doc(db, "organizations", orgId), {
        name: orgName, baseCurrency: "SDG", sectorType: "manufacturing",
        ownerId: uid, memberUids: [uid], roles: { [uid]: "owner" },
        planTier: "trial", subscriptionStatus: "trial",
        trialDurationHours: Engine.TRIAL_DURATION_HOURS,
        trialStartedAt: serverTimestamp(), trialEndsAt: trialEnd,
        createdAt: serverTimestamp(), updatedAt: serverTimestamp()
      });
      await setDoc(uRef, {
        userId: uid, email, displayName: cred.user.displayName || email,
        activeOrgId: orgId, preferredLanguage: state.isArabic ? "ar" : "en",
        themeMode: state.themeMode, darkMode: state.themeMode === "dark",
        fontScale: state.fontScale, tourCompleted: false,
        trialDurationHours: Engine.TRIAL_DURATION_HOURS,
        trialStartedAt: serverTimestamp(),
        createdAt: serverTimestamp(), updatedAt: serverTimestamp()
      });
    }
    const oSnap = await getDoc(doc(db, "organizations", orgId));
    const od = oSnap.data();
    const uData = uSnap.exists() ? uSnap.data() : {};
    const durHours = od?.trialDurationHours || uData?.trialDurationHours || Engine.TRIAL_DURATION_HOURS;
    const startMs = uData?.trialStartedAt?.toMillis ? uData.trialStartedAt.toMillis() : Date.now();
    const orgEndMs = od?.trialEndsAt?.toMillis ? od.trialEndsAt.toMillis() : (startMs + durHours * 3600000);
    state.user = { uid, email, displayName: uData?.displayName || cred.user.displayName || email, trialDurationHours: durHours, trialStartedAtMillis: startMs, tourCompleted: Boolean(uData?.tourCompleted) };
    state.activeOrg = { id: orgId, name: od?.name || orgName, baseCurrency: od?.baseCurrency || "SDG", planTier: od?.planTier || "trial", subscriptionStatus: od?.subscriptionStatus || "trial", trialDurationHours: durHours, trialEndsAtMillis: orgEndMs };
    state.stage = "app";
    if (!state.user.tourCompleted) state.showTour = true;
    persist();
    render();
    showToast(t("Signed in via Google & synced with Firestore.", "تم تسجيل الدخول والمزامنة مع الخادم."));
  } catch (err) {
    showToast(t("Firebase Auth failed: ", "تعذّر الدخول السحابي: ") + (err?.message || "Check firebase-config.js"));
  }
}

function render() {
  document.documentElement.dir = state.isArabic ? "rtl" : "ltr";
  document.documentElement.lang = state.isArabic ? "ar" : "en";
  document.documentElement.setAttribute("data-theme", state.themeMode);
  document.documentElement.style.setProperty("--fvg-font-scale", String(state.fontScale || 1));

  const root = document.getElementById("app");
  if (!root) return;

  if (state.stage === "splash") root.innerHTML = renderSplash();
  else if (state.stage === "public") root.innerHTML = renderPublicSite();
  else root.innerHTML = renderAppShell();

  bindEvents();
}

setInterval(() => {
  document.querySelectorAll("[data-trial-banner]").forEach((el) => {
    const raw = el.dataset.trialBanner;
    const target = raw === "preview" ? Date.now() + Engine.TRIAL_DURATION_MILLIS : Number(raw);
    if (!Number.isFinite(target)) return;
    const cd = Engine.computeTrialCountdown(target, Date.now());
    const pad = (v) => String(v).padStart(2, "0");
    const setT = (k, val) => { const node = el.querySelector(`[data-cd="${k}"]`); if (node) node.textContent = pad(val); };
    setT("d", cd.days); setT("h", cd.hours); setT("m", cd.minutes); setT("s", cd.seconds);
  });
}, 1000);

render();
