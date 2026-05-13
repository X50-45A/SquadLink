const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

exports.onGameScheduleUpdate = functions.firestore
    .document('games/{gameId}')
    .onWrite(async (change, context) => {
        const gameData = change.after.data();
        if (!gameData) return null;

        const { code, startTime, restStartTime, restEndTime, endTime } = gameData;

        console.log(`Programando notificaciones para la partida ${code}`);

        const message = {
            topic: `game_${code}`,
            notification: {
                title: 'SquadLink: Descanso',
                body: `La primera ronda ha terminado. Descanso hasta las ${restEndTime}.`
            }
        };
        return admin.messaging().send(message);


        return null;
    });
