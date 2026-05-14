const admin = require("firebase-admin");
const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");

admin.initializeApp();

function topicForGame(gameCode) {
  return `game_${String(gameCode).trim().toUpperCase().replace(/[^A-Z0-9_-]/g, "_")}`;
}

async function sendToGame(gameCode, title, body, data = {}) {
  return admin.messaging().send({
    topic: topicForGame(gameCode),
    notification: { title, body },
    data: Object.fromEntries(
      Object.entries(data).map(([key, value]) => [key, String(value ?? "")])
    )
  });
}

exports.onGameStarted = onDocumentUpdated("games/{gameCode}", async (event) => {
  const before = event.data.before.data();
  const after = event.data.after.data();
  if (!before || !after) return null;
  if (before.phase === after.phase || after.phase !== "RUNNING") return null;

  return sendToGame(
    event.params.gameCode,
    "Partida iniciada",
    after.missionDescription || "El Game Master ha iniciado la partida.",
    { type: "GAME_STARTED", gameCode: event.params.gameCode }
  );
});

exports.onDynamicObjectiveCreated = onDocumentCreated(
  "games/{gameCode}/dynamicObjectives/{objectiveId}",
  async (event) => {
    const objective = event.data.data();
    if (!objective) return null;

    return sendToGame(
      event.params.gameCode,
      `Nuevo objetivo: ${objective.type || "Objetivo"}`,
      objective.description || "Revisa la lista de objetivos.",
      {
        type: "DYNAMIC_OBJECTIVE",
        gameCode: event.params.gameCode,
        objectiveId: event.params.objectiveId,
        targetTeam: objective.targetTeam || ""
      }
    );
  }
);

exports.onPlayerExpelled = onDocumentUpdated(
  "games/{gameCode}/players/{playerId}",
  async (event) => {
    const before = event.data.before.data();
    const after = event.data.after.data();
    if (!before || !after) return null;
    if (before.expelled === true || after.expelled !== true) return null;

    const userDoc = await admin.firestore()
      .collection("users")
      .doc(event.params.playerId)
      .get();
    const token = userDoc.get("fcmToken");
    if (!token) return null;

    return admin.messaging().send({
      token,
      notification: {
        title: "Expulsado de la partida",
        body: "El Game Master te ha expulsado de la partida."
      },
      data: {
        type: "PLAYER_EXPELLED",
        gameCode: event.params.gameCode
      }
    });
  }
);
