const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_producer_123";
const BOB_UID = "bob_producer_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validSessionPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    title: "Midnight Cudi Bounce",
    bpm: 92,
    musicalKey: "C minor",
    grooveStyle: "trap_soul",
    hpfHz: 32,
    eqLowDb: 3.0,
    eqMidHz: 1200,
    eqMidDb: -1.5,
    eqHighDb: 2.0,
    compThresholdDb: -16.0,
    saturation: 0.25,
    lastSpark: "Heavy 808 sub pocket with dusty soul Rhodes",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validMessagePayload(uid, sessionId, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    sessionId,
    speaker: "ENGINEER",
    text: "Carved a 32Hz surgical HPF and boosted the 80Hz sub shelf +3.0dB.",
    sampleCredit: "Kid Cudi - Day 'n' Nite (Drum Pocket Study)",
    stemPackIncluded: true,
    stemBpm: 92,
    stemKey: "C minor",
    stemGroove: "trap_soul",
    createdAt: now,
    ...overrides,
  };
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

test("Unauthenticated user: denied read and write on sessions and messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1")
      .set(validSessionPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1")
      .collection("messages").doc("msg_1")
      .set(validMessagePayload(ALICE_UID, "sess_1"))
  );
});

test("Owner: create session, partial DSP update, list sessions, and append message succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1");

  await assertSucceeds(sessRef.set(validSessionPayload(ALICE_UID)));

  // Partial update matching Kotlin StudioRepository.updateDsp(...)
  await assertSucceeds(
    sessRef.update({
      bpm: 96,
      hpfHz: 38,
      eqLowDb: 4.5,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // List sessions matching StudioRepository.observeSessions(...)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").get()
  );

  // Create message under existing session
  const msgRef = sessRef.collection("messages").doc("msg_1");
  await assertSucceeds(msgRef.set(validMessagePayload(ALICE_UID, "sess_1")));

  // List messages matching StudioRepository.observeMessages(...)
  await assertSucceeds(sessRef.collection("messages").get());
});

test("Cross-user isolation: Bob cannot read, list, create, or update Alice's sessions or messages", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1")
      .set(validSessionPayload(ALICE_UID));
    await db.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1")
      .collection("messages").doc("msg_1")
      .set(validMessagePayload(ALICE_UID, "sess_1"));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1")
      .update({ bpm: 120, updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1")
      .collection("messages").get()
  );
});

test("Schema & Red Team guards: reject extra @DocumentId 'id', ghost fields, invalid BPM, future timestamp, and orphan messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessions = aliceDb.collection("users").doc(ALICE_UID).collection("sessions");

  // Extra id field rejected
  await assertFails(sessions.doc("sess_bad_id").set(validSessionPayload(ALICE_UID, { id: "sess_bad_id" })));

  // Ghost field rejected
  await assertFails(sessions.doc("sess_ghost").set(validSessionPayload(ALICE_UID, { isAdmin: true })));

  // Out-of-bounds BPM rejected
  await assertFails(sessions.doc("sess_bpm").set(validSessionPayload(ALICE_UID, { bpm: 999 })));

  // Future timestamp rejected
  await assertFails(
    sessions.doc("sess_future").set(
      validSessionPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) })
    )
  );

  // Orphan message without parent session rejected
  await assertFails(
    sessions.doc("missing_sess").collection("messages").doc("msg_orphan")
      .set(validMessagePayload(ALICE_UID, "missing_sess"))
  );
});
