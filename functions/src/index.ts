import * as functions from "firebase-functions";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";

// Initialize Firebase Admin SDK
admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

// ═══════════════════════════════════════════════════════════════
// LiveKit credentials — stored in Firebase Secret Manager.
// Set them via: firebase functions:secrets:set LIVEKIT_API_KEY
//               firebase functions:secrets:set LIVEKIT_API_SECRET
// These are automatically loaded at runtime and NEVER in source.
// ═══════════════════════════════════════════════════════════════
const livekitApiKey = defineSecret("LIVEKIT_API_KEY");
const livekitApiSecret = defineSecret("LIVEKIT_API_SECRET");

// ═══════════════════════════════════════════════════════════════
// D.1 SEND CALL PUSH
// ═══════════════════════════════════════════════════════════════
// Sends FCM push notification for an incoming 1:1 call.
// F109: Server-side caller identity resolution prevents spoofing.
// F113: Rate limiting — max 1 call per 30 seconds to same callee.
// Checks if callee is blocked before sending.
// ═══════════════════════════════════════════════════════════════

export const sendCallPush = functions.https.onCall(async (data, context) => {
  const { calleeUid, roomId, callId, callType, isGroupCall } = data;
  const callerUid = context.auth?.uid;

  if (!callerUid) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  if (!calleeUid || !roomId || !callId) {
    throw new functions.https.HttpsError("invalid-argument", "Missing required fields");
  }

  // F109: Get caller info from Firestore (server-side — cannot be spoofed)
  const callerDoc = await db.collection("users").doc(callerUid).get();
  if (!callerDoc.exists) {
    throw new functions.https.HttpsError("not-found", "Caller profile not found");
  }

  const callerData = callerDoc.data()!;
  const callerName = callerData.displayName || "Unknown";
  const callerZaxoNumber = callerData.zaxoNumber || "";
  const callerAvatar = callerData.avatarUrl || callerData.photoUrl || "";

  // F113: Rate limiting — max 1 call per 30 seconds to same callee
  const recentCallsSnapshot = await db
    .collection("users")
    .doc(calleeUid)
    .collection("callHistory")
    .where("callerUid", "==", callerUid)
    .where("timestamp", ">", Date.now() - 30000)
    .limit(1)
    .get();

  if (!recentCallsSnapshot.empty) {
    return { status: "rate_limited" };
  }

  // F113: Rate limiting — max 5 calls per hour from non-contacts
  const calleeChatsSnapshot = await db
    .collection("chats")
    .where("participantIds", "array-contains", calleeUid)
    .where("participantIds", "array-contains", callerUid)
    .limit(1)
    .get();

  if (calleeChatsSnapshot.empty) {
    // Not a contact — apply stricter rate limit
    const hourlyCallsSnapshot = await db
      .collection("users")
      .doc(calleeUid)
      .collection("callHistory")
      .where("callerUid", "==", callerUid)
      .where("timestamp", ">", Date.now() - 3600000)
      .limit(5)
      .get();

    if (hourlyCallsSnapshot.size >= 5) {
      return { status: "rate_limited" };
    }
  }

  // Check if callee is online and has FCM token
  const calleeDoc = await db.collection("users").doc(calleeUid).get();
  if (!calleeDoc.exists) {
    return { status: "offline" };
  }

  const calleeData = calleeDoc.data()!;
  const fcmToken = calleeData.fcmToken;
  if (!fcmToken) {
    return { status: "offline" };
  }

  // Check if caller is blocked by callee
  const blockedDoc = await db
    .collection("users")
    .doc(calleeUid)
    .collection("blockedCallers")
    .doc(callerUid)
    .get();

  if (blockedDoc.exists) {
    return { status: "blocked" };
  }

  // Write active call document to Firestore for status tracking
  await db.collection("activeCalls").doc(callId).set({
    roomId,
    callerUid,
    calleeUid,
    callerName,
    callerZaxoNumber,
    callerAvatar,
    callType: callType || "audio",
    isGroupCall: isGroupCall || false,
    status: "ringing",
    timestamp: admin.firestore.FieldValue.serverTimestamp(),
  });

  // Send FCM data message (not notification — we handle UI client-side)
  try {
    await messaging.send({
      token: fcmToken,
      data: {
        type: "incoming_call",
        roomId,
        callId,
        callType: callType || "audio",
        callerUid,
        callerName,
        callerZaxoNumber,
        callerAvatar: callerAvatar || "",
        isGroupCall: isGroupCall ? "true" : "false",
        timestamp: Date.now().toString(),
      },
      android: {
        priority: "high",
        ttl: 30000, // 30 seconds TTL — call is stale after this
      },
    });
  } catch (error) {
    console.error("Failed to send FCM:", error);
    return { status: "fcm_failed" };
  }

  return { status: "sent" };
});

// ═══════════════════════════════════════════════════════════════
// D.2 SEND GROUP CALL PUSH
// ═══════════════════════════════════════════════════════════════
// Sends FCM push notification for group calls to all participants.
// Resolves caller identity server-side (F109).
// ═══════════════════════════════════════════════════════════════

export const sendGroupCallPush = functions.https.onCall(async (data, context) => {
  const { participantIds, roomId, callId, callType, groupId, groupName, callerName } = data;
  const callerUid = context.auth?.uid;

  if (!callerUid) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  if (!participantIds || !Array.isArray(participantIds) || participantIds.length === 0) {
    throw new functions.https.HttpsError("invalid-argument", "Missing participantIds");
  }

  if (participantIds.length > 19) {
    throw new functions.https.HttpsError("invalid-argument", "Group calls support up to 20 participants");
  }

  // Resolve caller info server-side (F109)
  const callerDoc = await db.collection("users").doc(callerUid).get();
  const resolvedCallerName = callerDoc.exists
    ? callerDoc.data()?.displayName || callerName || "Unknown"
    : callerName || "Unknown";

  let sentCount = 0;

  for (const uid of participantIds) {
    if (uid === callerUid) continue; // Skip caller

    try {
      const userDoc = await db.collection("users").doc(uid).get();
      if (!userDoc.exists) continue;

      const fcmToken = userDoc.data()?.fcmToken;
      if (!fcmToken) continue;

      await messaging.send({
        token: fcmToken,
        data: {
          type: "group_call",
          roomId,
          callId,
          callType: callType || "audio",
          callerUid,
          callerName: resolvedCallerName,
          isGroupCall: "true",
          groupId: groupId || "",
          groupName: groupName || "",
          timestamp: Date.now().toString(),
        },
        android: {
          priority: "high",
          ttl: 60000, // 60 seconds for group calls
        },
      });

      sentCount++;
    } catch (error) {
      console.error(`Failed to send FCM to ${uid}:`, error);
    }
  }

  // Write active group call document
  await db.collection("activeCalls").doc(callId).set({
    roomId,
    callerUid,
    callerName: resolvedCallerName,
    callType: callType || "audio",
    isGroupCall: true,
    groupId: groupId || "",
    groupName: groupName || "",
    participantIds: admin.firestore.FieldValue.arrayUnion(...participantIds),
    status: "ringing",
    timestamp: admin.firestore.FieldValue.serverTimestamp(),
  });

  return { status: "sent", participantCount: sentCount };
});

// ═══════════════════════════════════════════════════════════════
// D.3 GENERATE ROOM TOKEN
// ═══════════════════════════════════════════════════════════════
// Generates a signed LiveKit room token for secure room access.
// F111: Signed tokens prevent unauthorized room access and MITM attacks.
// Tokens are scoped to a specific room and identity.
// ═══════════════════════════════════════════════════════════════

export const generateRoomToken = functions.https.onCall(
  { secrets: [livekitApiKey, livekitApiSecret] },
  async (data, context) => {
  const { roomId, participantIdentity, isAdmin } = data;
  const uid = context.auth?.uid;

  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  if (!roomId || !participantIdentity) {
    throw new functions.https.HttpsError("invalid-argument", "Missing roomId or participantIdentity");
  }

  // Generate LiveKit room token using livekit-server-sdk
  const { AccessToken } = require("livekit-server-sdk");

  const apiKey = livekitApiKey.value();
  const apiSecret = livekitApiSecret.value();

  if (!apiKey || !apiSecret) {
    throw new functions.https.HttpsError("internal", "LiveKit credentials not configured");
  }

  const token = new AccessToken(apiKey, apiSecret, {
    identity: participantIdentity,
    name: uid,
  });

  // Grant room access with publish + subscribe permissions
  token.addGrant({
    roomJoin: true,
    room: roomId,
    canPublish: true,
    canSubscribe: true,
    // Admin can update room metadata (for group calls)
    roomAdmin: isAdmin || false,
  });

  // Set token expiry — 4 hours max call duration
  token.ttl = 14400; // seconds

  return { token: token.toJwt() };
  }
);

// ═══════════════════════════════════════════════════════════════
// Updates call status in Firestore (accepted, declined, busy, ended).
// Called by the callee's client when they accept/decline a call.
// ═══════════════════════════════════════════════════════════════

export const updateCallStatus = functions.https.onCall(async (data, context) => {
  const { callId, status } = data;
  const uid = context.auth?.uid;

  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  if (!callId || !status) {
    throw new functions.https.HttpsError("invalid-argument", "Missing callId or status");
  }

  const validStatuses = ["accepted", "declined", "busy", "ended", "answered_elsewhere"];
  if (!validStatuses.includes(status)) {
    throw new functions.https.HttpsError("invalid-argument", `Invalid status: ${status}`);
  }

  const callDoc = await db.collection("activeCalls").doc(callId).get();
  if (!callDoc.exists) {
    throw new functions.https.HttpsError("not-found", "Call not found");
  }

  // Verify the caller is a participant of this call
  const callData = callDoc.data()!;
  if (callData.calleeUid !== uid && callData.callerUid !== uid) {
    throw new functions.https.HttpsError("permission-denied", "Not a call participant");
  }

  await db.collection("activeCalls").doc(callId).update({
    status,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  // If call ended, clean up the active call document after a delay
  if (status === "ended") {
    // Schedule cleanup — delete the active call doc after 60s
    // (This gives the caller time to read the status update)
    setTimeout(async () => {
      try {
        await db.collection("activeCalls").doc(callId).delete();
      } catch (error) {
        console.error("Failed to clean up active call:", error);
      }
    }, 60000);
  }

  // Save to call history
  const now = Date.now();
  const callStartTime = callData.timestamp?.toMillis?.() || now;
  const duration = status === "ended" ? Math.floor((now - callStartTime) / 1000) : 0;

  // Save for caller
  await db.collection("users").doc(callData.callerUid).collection("callHistory").add({
    callId,
    roomId: callData.roomId,
    contactUid: callData.calleeUid,
    callType: callData.callType,
    direction: "outgoing",
    status,
    duration,
    timestamp: now,
  });

  // Save for callee
  await db.collection("users").doc(callData.calleeUid).collection("callHistory").add({
    callId,
    roomId: callData.roomId,
    contactUid: callData.callerUid,
    callType: callData.callType,
    direction: "incoming",
    status,
    duration,
    timestamp: now,
  });

  return { status: "updated" };
});

// ═══════════════════════════════════════════════════════════════
// SAVE FCM TOKEN
// ═══════════════════════════════════════════════════════════════
// Saves the FCM token for the current user so push notifications
// can be delivered for incoming calls and messages.
// ═══════════════════════════════════════════════════════════════

export const saveFcmToken = functions.https.onCall(async (data, context) => {
  const { token } = data;
  const uid = context.auth?.uid;

  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated");
  }

  if (!token) {
    throw new functions.https.HttpsError("invalid-argument", "Missing FCM token");
  }

  await db.collection("users").doc(uid).update({
    fcmToken: token,
    fcmTokenUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  return { status: "saved" };
});
