const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const OWNER_UID = "owner_999";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowPast() {
  return new Date(Date.now() - 5000);
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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("videos").where("visibility", "==", "PUBLIC").where("moderationStatus", "==", "ACTIVE").get());
  await assertFails(unauthDb.collection("channels").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("platformSettings").doc("global").get());
});

test("User profile isolation: PII protected, self-create allowed, privilege escalation blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const ts = nowPast();

  // Valid create
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Creator",
      email: "alice@example.com",
      preferredLanguage: "en",
      status: "active",
      uploadDisabled: false,
      restrictedMode: false,
      emailVerified: true,
      storageUsedBytes: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot read Alice's PII profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Alice cannot promote herself to admin via /admins
  await assertFails(
    aliceDb.collection("admins").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      role: "OWNER",
      createdAt: ts,
    })
  );

  // Alice cannot disable her own uploadDisabled or tamper status to suspended/active
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      uploadDisabled: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice can update her own language preference
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredLanguage: "bn",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Channel & Video creation, ownership, public vs private listing, and comment moderation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const ts = nowPast();

  // Create Alice's channel
  await assertSucceeds(
    aliceDb.collection("channels").doc("ch_alice").set({
      ownerId: ALICE_UID,
      name: "Alice Studio",
      handle: "@alicestudio",
      description: "Tech & Education",
      subscriberCount: 0,
      videoCount: 0,
      totalViews: 0,
      status: "active",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob cannot edit Alice's channel metadata
  await assertFails(
    bobDb.collection("channels").doc("ch_alice").update({
      name: "Hijacked Channel",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice creates a PUBLIC video and a PRIVATE video
  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_pub").set({
      ownerId: ALICE_UID,
      channelId: "ch_alice",
      channelName: "Alice Studio",
      channelHandle: "@alicestudio",
      title: "Welcome to YouTV",
      description: "First video",
      playbackUrl: "https://storage.googleapis.com/youtv-demo/sample.mp4",
      category: "Technology",
      tags: ["youtv", "tech"],
      language: "en",
      visibility: "PUBLIC",
      processingStatus: "READY",
      moderationStatus: "ACTIVE",
      shortForm: false,
      ageRestricted: false,
      durationSec: 120,
      fileSizeBytes: 1024000,
      viewCount: 0,
      likeCount: 0,
      dislikeCount: 0,
      commentCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("videos").doc("vid_priv").set({
      ownerId: ALICE_UID,
      channelId: "ch_alice",
      channelName: "Alice Studio",
      channelHandle: "@alicestudio",
      title: "Secret Draft",
      description: "Private draft",
      playbackUrl: "https://storage.googleapis.com/youtv-demo/draft.mp4",
      category: "Technology",
      tags: [],
      language: "bn",
      visibility: "PRIVATE",
      processingStatus: "READY",
      moderationStatus: "ACTIVE",
      shortForm: false,
      ageRestricted: false,
      durationSec: 60,
      fileSizeBytes: 512000,
      viewCount: 0,
      likeCount: 0,
      dislikeCount: 0,
      commentCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob can query public active videos, cannot read Alice's private video, cannot run unfiltered list
  await assertSucceeds(
    bobDb.collection("videos")
      .where("visibility", "==", "PUBLIC")
      .where("moderationStatus", "==", "ACTIVE")
      .get()
  );
  await assertFails(bobDb.collection("videos").doc("vid_priv").get());
  await assertFails(bobDb.collection("videos").get());

  // Bob cannot inject @DocumentId `id` field
  await assertFails(
    bobDb.collection("videos").doc("vid_bad").set({
      id: "vid_bad",
      ownerId: BOB_UID,
      channelId: "ch_alice",
      channelName: "X",
      channelHandle: "@x",
      title: "Bad",
      description: "",
      playbackUrl: "https://example.com/v.mp4",
      category: "Music",
      tags: [],
      language: "en",
      visibility: "PUBLIC",
      processingStatus: "READY",
      moderationStatus: "ACTIVE",
      shortForm: false,
      ageRestricted: false,
      durationSec: 10,
      fileSizeBytes: 100,
      viewCount: 0,
      likeCount: 0,
      dislikeCount: 0,
      commentCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Bob can comment on vid_pub, and Alice (videoOwnerId) can hide Bob's comment
  await assertSucceeds(
    bobDb.collection("comments").doc("cmt_1").set({
      videoId: "vid_pub",
      authorId: BOB_UID,
      authorName: "Bob",
      videoOwnerId: ALICE_UID,
      text: "Great video!",
      moderationStatus: "ACTIVE",
      likeCount: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("comments").doc("cmt_1").update({
      moderationStatus: "HIDDEN",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Deterministic likes and subscriptions prevent duplicates and enforce user ownership", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const ts = nowPast();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("channels").doc("ch_alice").set({
      ownerId: ALICE_UID,
      name: "Alice Studio",
      handle: "@alicestudio",
      description: "",
      subscriberCount: 0,
      videoCount: 0,
      totalViews: 0,
      status: "active",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  // Valid deterministic like ID: vid_1_bob_456
  await assertSucceeds(
    bobDb.collection("likes").doc(`vid_1_${BOB_UID}`).set({
      videoId: "vid_1",
      userId: BOB_UID,
      reaction: "LIKE",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Random doc ID for like fails (prevents duplicate inflation)
  await assertFails(
    bobDb.collection("likes").doc("random_like_id").set({
      videoId: "vid_1",
      userId: BOB_UID,
      reaction: "LIKE",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Valid deterministic subscription ID: ch_alice_bob_456
  await assertSucceeds(
    bobDb.collection("subscriptions").doc(`ch_alice_${BOB_UID}`).set({
      channelId: "ch_alice",
      channelName: "Alice Studio",
      subscriberId: BOB_UID,
      channelOwnerId: ALICE_UID,
      notificationsEnabled: true,
      createdAt: ts,
    })
  );

  // Alice cannot delete Bob's subscription
  await assertFails(aliceDb.collection("subscriptions").doc(`ch_alice_${BOB_UID}`).delete());
});

test("Suspended user is blocked from creating channels, videos, or comments", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const ts = nowPast();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      preferredLanguage: "en",
      status: "suspended",
      uploadDisabled: true,
      restrictedMode: false,
      emailVerified: true,
      storageUsedBytes: 0,
      moderationReason: "Spam",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  await assertFails(
    bobDb.collection("channels").doc("ch_bob").set({
      ownerId: BOB_UID,
      name: "Bob Channel",
      handle: "@bob",
      description: "",
      subscriberCount: 0,
      videoCount: 0,
      totalViews: 0,
      status: "active",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("ADMIT owner-only enforcement: non-admin denied, unverified-email spoof denied, verified owner allowed, auditLogs immutable", async () => {
  const ts = nowPast();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("admins").doc(OWNER_UID).set({
      uid: OWNER_UID,
      email: "owner@example.com",
      role: "OWNER",
      createdAt: ts,
    });
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      preferredLanguage: "en",
      status: "active",
      uploadDisabled: false,
      restrictedMode: false,
      emailVerified: true,
      storageUsedBytes: 0,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const spoofOwnerDb = testEnv.authenticatedContext(OWNER_UID, { email: "owner@example.com", email_verified: false }).firestore();
  const realOwnerDb = testEnv.authenticatedContext(OWNER_UID, { email: "owner@example.com", email_verified: true }).firestore();

  // Ordinary user and unverified-email spoof cannot read auditLogs or write platformSettings
  await assertFails(aliceDb.collection("auditLogs").get());
  await assertFails(spoofOwnerDb.collection("auditLogs").get());
  await assertFails(
    aliceDb.collection("platformSettings").doc("global").set({
      registrationsEnabled: false,
      uploadsEnabled: false,
      maintenanceMode: true,
      maxUploadSizeMb: 500,
      dailyUploadLimit: 10,
      allowedFormats: ["mp4"],
      categories: ["Music"],
      supportedLanguages: ["en", "bn", "hi"],
      autoFlagKeywords: [],
      notificationsEnabled: true,
      transcoderConfigured: false,
      totalStorageQuotaMb: 10240,
      updatedAt: ts,
    })
  );

  // Verified owner can suspend Alice, log moderationAction, and update platformSettings
  await assertSucceeds(
    realOwnerDb.collection("users").doc(ALICE_UID).update({
      status: "suspended",
      moderationReason: "Policy violation",
      moderatedBy: OWNER_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    realOwnerDb.collection("moderationActions").doc("mod_1").set({
      adminUid: OWNER_UID,
      adminEmail: "owner@example.com",
      actionType: "SUSPEND_USER",
      targetType: "USER",
      targetId: ALICE_UID,
      reason: "Policy violation",
      createdAt: ts,
    })
  );

  // Even owner cannot mutate or delete immutable moderationActions or auditLogs
  await assertFails(realOwnerDb.collection("moderationActions").doc("mod_1").delete());
  await assertFails(realOwnerDb.collection("moderationActions").doc("mod_1").update({ reason: "Erased" }));

  await assertSucceeds(
    realOwnerDb.collection("auditLogs").doc("log_1").set({
      actorUid: OWNER_UID,
      action: "SUSPEND_USER",
      targetType: "USER",
      targetId: ALICE_UID,
      result: "SUCCESS",
      summary: "Suspended user Alice",
      createdAt: ts,
    })
  );
  await assertFails(realOwnerDb.collection("auditLogs").doc("log_1").delete());
});

test("AppReleases distribution metadata: signed-in users can read, only verified owner can publish or update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const realOwnerDb = testEnv.authenticatedContext(OWNER_UID, { email: "owner@example.com", email_verified: true }).firestore();
  const ts = nowPast();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("admins").doc(OWNER_UID).set({
      uid: OWNER_UID,
      email: "owner@example.com",
      role: "OWNER",
      createdAt: ts,
    });
  });

  // Regular user cannot create an appRelease
  await assertFails(
    aliceDb.collection("appReleases").doc("rel_100").set({
      releaseId: "rel_100",
      versionName: "1.0.0",
      versionCode: 1,
      buildType: "release",
      channel: "production",
      fileName: "YouTV-ADMIT-v1.0.0-release.apk",
      status: "READY",
      fileSizeBytes: 29500000,
      downloadCount: 0,
      triggeredBy: ALICE_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Verified owner can publish an appRelease, and signed-in Alice can read it
  await assertSucceeds(
    realOwnerDb.collection("appReleases").doc("rel_100").set({
      releaseId: "rel_100",
      versionName: "1.0.0",
      versionCode: 1,
      buildType: "release",
      channel: "production",
      fileName: "YouTV-ADMIT-v1.0.0-release.apk",
      status: "READY",
      fileSizeBytes: 29500000,
      downloadCount: 0,
      triggeredBy: OWNER_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("appReleases").doc("rel_100").get());
});
