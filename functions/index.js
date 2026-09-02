/**
 * Cloud Functions для MiMes Mix.
 *
 * Отправляют FCM-уведомления на Android-клиент:
 *  - о новых сообщениях (триггер на chats/{chatId}/messages/{messageId});
 *  - о входящих звонках (триггер на calls/{callId} со статусом "ringing").
 *
 * Данные о сообщении/звонке уже лежат в Firestore, токены устройств хранятся
 * в users/{login}.fcmToken (их сохраняет сам Android-клиент при входе).
 *
 * Деплой:  firebase deploy --only functions
 */
const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();
const FCM_DATA = {
  ANDROID_PRIORITY_HIGH: { android: { priority: "high", ttl: 30000 } },
};

async function getFcmToken(login) {
  if (!login || login === "bot") return null;
  const doc = await db.collection("users").doc(login).get();
  const token = doc.exists ? doc.data().fcmToken : null;
  return typeof token === "string" && token.length > 0 ? token : null;
}

/**
 * Новое сообщение в чате -> push получателю.
 * Message хранит senderId (от кого) и receiverId (кому, см. Message.kt).
 */
exports.sendMessagePush = functions.firestore
  .document("chats/{chatId}/messages/{messageId}")
  .onCreate(async (snap) => {
    const msg = snap.data();
    if (!msg) return null;

    const senderId = msg.senderId || "";
    const receiverId = msg.receiverId || "";
    if (!receiverId || receiverId === senderId) return null;

    const token = await getFcmToken(receiverId);
    if (!token) return null;

    const text = (msg.text || "").toString();
    const body = text.length > 0 ? text : "📎 Вложение";

    await admin.messaging().send({
      token,
      data: {
        type: "message",
        chatId: snap.ref.parent.parent.id, // chats/{chatId}
        peerName: senderId, // @логин собеседника (открывает нужный чат)
        senderId,
        text,
        title: senderId,
        body,
      },
      ...FCM_DATA.ANDROID_PRIORITY_HIGH,
    });
    return null;
  });

/**
 * Входящий звонок -> push получателю с кнопками «Принять» / «Отклонить».
 * Триггеримся только при создании звонка со статусом "ringing".
 */
exports.sendCallPush = functions.firestore
  .document("calls/{callId}")
  .onCreate(async (snap) => {
    const call = snap.data();
    if (!call) return null;
    if (call.status !== "ringing") return null;

    const callerId = call.callerId || "";
    const receiverId = call.receiverId || "";
    if (!callerId || !receiverId || callerId === receiverId) return null;

    const token = await getFcmToken(receiverId);
    if (!token) return null;

    await admin.messaging().send({
      token,
      data: {
        type: "call",
        callId: snap.id,
        callerId,
        isVideo: call.type === "video" ? "true" : "false",
        title: "Входящий звонок",
        body: `${callerId} — ${call.type === "video" ? "видеозвонок" : "аудиозвонок"}`,
      },
      ...FCM_DATA.ANDROID_PRIORITY_HIGH,
    });
    return null;
  });
