const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-fvg-963bcf06-9233-4f62-aa0d-cdebb701a866";
const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr || "8085", 10);
const ALICE_UID = "alice_owner_1";
const BOB_UID = "bob_outsider_2";
const VIEWER_UID = "charlie_viewer_3";
const ACCOUNTANT_UID = "accountant_4";
const ORG_ID = "org_fvg_alpha";

const PLATFORM_OWNER_UID = "zahra_platform_owner";
const PLATFORM_OWNER_EMAIL = "Financialvisionguide@gmail.com";

function dbFor(context) {
  return context.firestore();
}

function platformOwnerDb() {
  return dbFor(
    testEnv.authenticatedContext(PLATFORM_OWNER_UID, {
      email: PLATFORM_OWNER_EMAIL,
      email_verified: true,
    })
  );
}

async function seedOrgAndMembership(trialHours = 168) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = dbFor(context);
    const now = new Date(Date.now() - 1000);
    await adminDb.collection("organizations").doc(ORG_ID).set({
      name: "Alpha Trading Co",
      baseCurrency: "USD",
      sectorType: "manufacturing",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      roles: {
        [ALICE_UID]: "owner",
        [ACCOUNTANT_UID]: "accountant",
        [VIEWER_UID]: "viewer",
      },
      planTier: "trial",
      subscriptionStatus: "trial",
      trialDurationHours: trialHours,
      trialStartedAt: now,
      trialEndsAt: new Date(now.getTime() + trialHours * 3600000),
      createdAt: now,
      updatedAt: now,
    });
  });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or list organizations or products", async () => {
  await seedOrgAndMembership();
  const unauthDb = dbFor(testEnv.unauthenticatedContext());
  await assertFails(unauthDb.collection("organizations").doc(ORG_ID).get());
  await assertFails(unauthDb.collection("organizations").doc(ORG_ID).collection("products").get());
});

test("Owner creates profile and org with 168h (7-day) immutable trial and cannot extend trial or self-upgrade plan", async () => {
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const now = new Date(Date.now() - 2000);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@fvg.test",
      displayName: "Alice",
      preferredLanguage: "ar",
      themeMode: "system",
      fontScale: 1.0,
      tourCompleted: false,
      trialDurationHours: 168,
      trialStartedAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject tampering with UserProfile.trialStartedAt
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      trialStartedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject creating org with arbitrary >168h duration or non-trial tier
  await assertFails(
    aliceDb.collection("organizations").doc("org_fake").set({
      name: "Tampered Co",
      baseCurrency: "SDG",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      roles: { [ALICE_UID]: "owner" },
      planTier: "enterprise",
      subscriptionStatus: "active",
      trialDurationHours: 168,
      trialStartedAt: now,
      trialEndsAt: new Date(now.getTime() + 7 * 86400000),
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).set({
      name: "Alpha Co",
      baseCurrency: "SDG",
      sectorType: "manufacturing",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      roles: { [ALICE_UID]: "owner" },
      planTier: "trial",
      subscriptionStatus: "trial",
      trialDurationHours: 168,
      trialStartedAt: now,
      trialEndsAt: new Date(now.getTime() + 7 * 86400000),
      createdAt: now,
      updatedAt: now,
    })
  );

  // Org owner cannot self-extend trialEndsAt or self-activate planTier
  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).update({
      trialEndsAt: new Date(now.getTime() + 30 * 86400000),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).update({
      planTier: "professional",
      subscriptionStatus: "active",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Org owner CAN update permitted org profile settings
  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).update({
      name: "Alpha Manufacturing Ltd",
      sectorType: "food_beverage",
      updatedAt: new Date(Date.now() - 400),
    })
  );
});

test("Legacy 72h org remains valid for reads/settings updates without extending trial", async () => {
  await seedOrgAndMembership(72);
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).update({
      name: "Legacy Co Renamed",
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).update({
      trialDurationHours: 168,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Cross-organization isolation: outsider cannot read, list unfiltered, or write to another org", async () => {
  await seedOrgAndMembership();
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = dbFor(testEnv.authenticatedContext(BOB_UID));
  const now = new Date(Date.now() - 1000);
  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).collection("products").doc("prod_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ALICE_UID,
      name: "Cement",
      category: "Materials",
      unit: "ton",
      currency: "USD",
      currentPrice: "120.00",
      previousPrice: "110.00",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("organizations").doc(ORG_ID).get());
  await assertFails(bobDb.collection("organizations").doc(ORG_ID).collection("products").get());
  await assertFails(bobDb.collection("organizations").doc(ORG_ID).collection("products").doc("prod_1").get());
  await assertFails(
    bobDb.collection("organizations").doc(ORG_ID).collection("products").doc("prod_2").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      createdBy: BOB_UID,
      name: "Spoofed Item",
      category: "Grain",
      unit: "kg",
      currency: "USD",
      currentPrice: "10.00",
      previousPrice: "9.00",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Viewer role can read with memberUids filter but cannot write financial records", async () => {
  await seedOrgAndMembership();
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const viewerDb = dbFor(testEnv.authenticatedContext(VIEWER_UID));
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).collection("products").doc("prod_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ALICE_UID,
      name: "Wheat Flour",
      category: "Commodities",
      unit: "kg",
      currency: "USD",
      currentPrice: "1.80",
      previousPrice: "1.50",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    viewerDb.collection("organizations").doc(ORG_ID).collection("products").where("memberUids", "array-contains", VIEWER_UID).get()
  );

  await assertFails(
    viewerDb.collection("organizations").doc(ORG_ID).collection("products").doc("prod_2").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: VIEWER_UID,
      name: "Unauthorized Item",
      category: "Commodities",
      unit: "kg",
      currency: "USD",
      currentPrice: "2.00",
      previousPrice: "2.00",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Journal entry enforces balanced debits/credits and terminal lock on posted entries", async () => {
  await seedOrgAndMembership();
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const now = new Date(Date.now() - 1000);

  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).collection("journalEntries").doc("je_unbalanced").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ALICE_UID,
      referenceNo: "JE-001",
      entryDate: "2026-11-15",
      period: "2026-11",
      description: "Unbalanced",
      currency: "USD",
      exchangeRate: "1.0000",
      status: "posted",
      lines: [{ accountCode: "1010", debit: "100.00", credit: "0.00" }, { accountCode: "4010", debit: "0.00", credit: "90.00" }],
      totalDebit: "100.00",
      totalCredit: "90.00",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).collection("journalEntries").doc("je_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ALICE_UID,
      referenceNo: "JE-002",
      entryDate: "2026-11-15",
      period: "2026-11",
      description: "Cash Sale",
      currency: "USD",
      exchangeRate: "1.0000",
      status: "posted",
      lines: [{ accountCode: "1010", debit: "100.00", credit: "0.00" }, { accountCode: "4010", debit: "0.00", credit: "100.00" }],
      totalDebit: "100.00",
      totalCredit: "100.00",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).collection("journalEntries").doc("je_1").update({
      totalDebit: "500.00",
      totalCredit: "500.00",
      updatedAt: now,
    })
  );
});

test("Audit logs and price observations protect history from tampering or deletion", async () => {
  await seedOrgAndMembership();
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).collection("auditLogs").doc("log_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ALICE_UID,
      action: "CREATE_PRODUCT",
      entityType: "product",
      entityId: "prod_1",
      summary: "Created Wheat Flour",
      actorEmail: "alice@fvg.test",
      createdAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).collection("auditLogs").doc("log_1").update({
      summary: "Tampered",
    })
  );
  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).collection("auditLogs").doc("log_1").delete()
  );
});

test("CostSnapshot supports all 8 costing methods and rejects unknown costing methods", async () => {
  await seedOrgAndMembership();
  const accountantDb = dbFor(testEnv.authenticatedContext(ACCOUNTANT_UID));
  const now = new Date(Date.now() - 1000);
  const methods = ["standard", "actual", "target", "abc", "job_order", "process", "absorption", "variable"];

  for (const m of methods) {
    await assertSucceeds(
      accountantDb.collection("organizations").doc(ORG_ID).collection("costSnapshots").doc(`cs_${m}`).set({
        orgId: ORG_ID,
        ownerId: ALICE_UID,
        memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
        createdBy: ACCOUNTANT_UID,
        productId: "prod_1",
        productName: "Item 1kg",
        period: "2026-11",
        currency: "USD",
        costingMethod: m,
        directMaterial: "500.00",
        directLabor: "200.00",
        overhead: "100.00",
        goodUnits: "100",
        totalCost: "800.00",
        unitCost: "8.00",
        sellingPrice: "12.00",
        profitMarginPct: "33.33",
        markupPct: "50.00",
        riskLevel: "low",
        formulaVersion: "2.2.0",
        createdAt: now,
        updatedAt: now,
      })
    );
  }

  await assertFails(
    accountantDb.collection("organizations").doc(ORG_ID).collection("costSnapshots").doc("cs_invalid").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ACCOUNTANT_UID,
      productId: "prod_1",
      productName: "Item 1kg",
      period: "2026-11",
      currency: "USD",
      costingMethod: "invented_method",
      directMaterial: "500.00",
      directLabor: "200.00",
      overhead: "100.00",
      goodUnits: "100",
      totalCost: "800.00",
      unitCost: "8.00",
      sellingPrice: "12.00",
      profitMarginPct: "33.33",
      markupPct: "50.00",
      formulaVersion: "2.2.0",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Payment requests block org self-approval and allow ONLY verified Platform Owner (Zahra) idempotent approval", async () => {
  await seedOrgAndMembership();
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID, { email: "alice@fvg.test", email_verified: true }));
  const accountantDb = dbFor(testEnv.authenticatedContext(ACCOUNTANT_UID));
  const ownerDb = platformOwnerDb();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    accountantDb.collection("organizations").doc(ORG_ID).collection("paymentRequests").doc("pay_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ACCOUNTANT_UID,
      planTier: "professional",
      paymentMethod: "bankak",
      amount: "29.00",
      currency: "USD",
      senderName: "Ahmed Ali",
      referenceNo: "BNK-991822",
      last4Digits: "1822",
      transferDate: "2026-11-15",
      notes: "Last 4 digits: 1822",
      status: "pending_verification",
      reviewedBy: "",
      decisionActorEmail: "",
      reviewNotes: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Even the organization owner CANNOT self-approve their own payment request!
  await assertFails(
    aliceDb.collection("organizations").doc(ORG_ID).collection("paymentRequests").doc("pay_1").update({
      status: "approved",
      reviewedBy: ALICE_UID,
      reviewNotes: "Self approval must fail",
      updatedAt: now,
    })
  );

  // Unverified spoofed owner email must also fail
  const spoofDb = dbFor(
    testEnv.authenticatedContext("spoof_1", {
      email: PLATFORM_OWNER_EMAIL,
      email_verified: false,
    })
  );
  await assertFails(
    spoofDb.collection("organizations").doc(ORG_ID).collection("paymentRequests").doc("pay_1").update({
      status: "approved",
      reviewedBy: "spoof_1",
      updatedAt: now,
    })
  );

  // Verified Platform Owner approves idempotently and activates org subscription
  await assertSucceeds(
    ownerDb.collection("organizations").doc(ORG_ID).collection("paymentRequests").doc("pay_1").update({
      status: "approved",
      reviewedBy: PLATFORM_OWNER_UID,
      decisionActorEmail: PLATFORM_OWNER_EMAIL,
      reviewNotes: "Verified Bankak statement",
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection("organizations").doc(ORG_ID).update({
      planTier: "professional",
      subscriptionStatus: "active",
      updatedAt: now,
    })
  );

  // Re-approving an already approved payment request fails idempotency check
  await assertFails(
    ownerDb.collection("organizations").doc(ORG_ID).collection("paymentRequests").doc("pay_1").update({
      status: "rejected",
      reviewedBy: PLATFORM_OWNER_UID,
      reviewNotes: "Double transition rejected",
      updatedAt: now,
    })
  );
});

test("AI conversations, BOM formulas, platform plans, and support tickets enforce permissions", async () => {
  await seedOrgAndMembership();
  const aliceDb = dbFor(testEnv.authenticatedContext(ALICE_UID));
  const viewerDb = dbFor(testEnv.authenticatedContext(VIEWER_UID));
  const bobDb = dbFor(testEnv.authenticatedContext(BOB_UID));
  const ownerDb = platformOwnerDb();
  const now = new Date(Date.now() - 1000);

  // BOM formula CRUD
  await assertSucceeds(
    aliceDb.collection("organizations").doc(ORG_ID).collection("bomFormulas").doc("bom_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: ALICE_UID,
      productName: "Standard Loaf",
      outputUnit: "unit",
      batchSize: "100",
      ingredients: [{ materialName: "Flour", qtyPerUnit: "0.25", unitCost: "800" }],
      unitMaterialCost: "200.00",
      scrapAllowancePct: "2.00",
      notes: "Baseline recipe",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("organizations").doc(ORG_ID).collection("bomFormulas").get());

  // AI Conversations
  await assertSucceeds(
    viewerDb.collection("organizations").doc(ORG_ID).collection("aiConversations").doc("conv_1").set({
      orgId: ORG_ID,
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, ACCOUNTANT_UID, VIEWER_UID],
      createdBy: VIEWER_UID,
      title: "Margin Check",
      mode: "financial",
      messages: [{ role: "user", text: "What is our gross margin?" }],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Platform plans: only Platform Owner writes, public reads
  await assertFails(
    aliceDb.collection("platformPlans").doc("starter").set({
      key: "starter",
      nameEn: "Starter",
      nameAr: "الأساسية",
      priceUsd: "12.00",
      priceSdg: "25000",
    })
  );
  await assertSucceeds(
    ownerDb.collection("platformPlans").doc("starter").set({
      key: "starter",
      nameEn: "Starter",
      nameAr: "الأساسية",
      priceUsd: "12.00",
      priceSdg: "25000",
      active: true,
    })
  );
  await assertSucceeds(aliceDb.collection("platformPlans").doc("starter").get());

  // Support tickets: sender creates, Platform Owner manages
  await assertSucceeds(
    aliceDb.collection("supportTickets").doc("tkt_1").set({
      createdBy: ALICE_UID,
      senderName: "Alice",
      senderEmail: "alice@fvg.test",
      orgId: ORG_ID,
      orgName: "Alpha Co",
      message: "Please verify Bankak transfer ending in 1822.",
      status: "open",
      ownerReply: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(bobDb.collection("supportTickets").doc("tkt_1").get());
  await assertSucceeds(ownerDb.collection("supportTickets").doc("tkt_1").get());
});
