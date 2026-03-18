package com.othello;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    // roomId -> Room
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    // sessionId -> roomId
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();

    static class Room {
        String id;
        WebSocketSession black; // 先に入った人
        WebSocketSession white; // 後に入った人
        OthelloGame game;
        String blackName = "Player1";
        String whiteName = "Player2";

        Room(String id) { this.id = id; }

        boolean isFull() { return black != null && white != null; }

        WebSocketSession getOpponent(WebSocketSession s) {
            return s == black ? white : black;
        }

        int getColor(WebSocketSession s) {
            return s == black ? OthelloGame.BLACK : OthelloGame.WHITE;
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {}

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> msg = mapper.readValue(message.getPayload(), Map.class);
        String type = (String) msg.get("type");

        switch (type) {
            case "join"  -> handleJoin(session, msg);
            case "move"  -> handleMove(session, msg);
            case "leave" -> handleLeave(session);
        }
    }

    private void handleJoin(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String roomId = ((String) msg.get("roomId")).toUpperCase().trim();
        String name   = (String) msg.getOrDefault("name", "Player");

        // すでに別の部屋にいたら退出
        String oldRoom = sessionToRoom.get(session.getId());
        if (oldRoom != null) leaveRoom(session, oldRoom);

        Room room = rooms.computeIfAbsent(roomId, Room::new);

        if (room.isFull()) {
            // 満室
            send(session, Map.of("type", "error", "message", "この部屋は満員です"));
            return;
        }

        if (room.black == null) {
            room.black = session;
            room.blackName = name;
            sessionToRoom.put(session.getId(), roomId);
            send(session, Map.of(
                "type", "waiting",
                "roomId", roomId,
                "color", "black",
                "message", "対戦相手を待っています…"
            ));
        } else {
            room.white = session;
            room.whiteName = name;
            sessionToRoom.put(session.getId(), roomId);

            // ゲーム開始
            room.game = new OthelloGame(OthelloGame.Difficulty.NORMAL, OthelloGame.Mode.VS_HUMAN);

            Map<String, Object> startMsg = new HashMap<>();
            startMsg.put("type", "start");
            startMsg.put("roomId", roomId);
            startMsg.put("blackName", room.blackName);
            startMsg.put("whiteName", room.whiteName);
            startMsg.putAll(buildState(room.game));

            // 黒に送信
            Map<String, Object> forBlack = new HashMap<>(startMsg);
            forBlack.put("yourColor", "black");
            send(room.black, forBlack);

            // 白に送信
            Map<String, Object> forWhite = new HashMap<>(startMsg);
            forWhite.put("yourColor", "white");
            send(room.white, forWhite);
        }
    }

    private void handleMove(WebSocketSession session, Map<String, Object> msg) throws Exception {
        String roomId = sessionToRoom.get(session.getId());
        if (roomId == null) return;
        Room room = rooms.get(roomId);
        if (room == null || room.game == null) return;

        int row = (int) msg.get("row");
        int col = (int) msg.get("col");
        int color = room.getColor(session);

        // 自分の番かチェック
        if (room.game.getCurrentPlayer() != color) return;

        boolean moved = room.game.playerMove(row, col);
        if (!moved) return;

        Map<String, Object> state = new HashMap<>();
        state.put("type", "update");
        state.put("row", row);
        state.put("col", col);
        state.put("movedColor", color);
        state.putAll(buildState(room.game));

        // 両プレイヤーに送信
        send(room.black, state);
        send(room.white, state);

        // ゲーム終了時にランキング登録は今回なし（2人対戦）
    }

    private void handleLeave(WebSocketSession session) throws Exception {
        String roomId = sessionToRoom.get(session.getId());
        if (roomId == null) return;
        Room room = rooms.get(roomId);
        if (room == null) return;

        // 相手に通知
        WebSocketSession opponent = room.getOpponent(session);
        if (opponent != null && opponent.isOpen()) {
            send(opponent, Map.of("type", "opponent_left", "message", "相手が退出しました"));
        }

        leaveRoom(session, roomId);
    }

    private void leaveRoom(WebSocketSession session, String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) return;
        if (session == room.black) room.black = null;
        if (session == room.white) room.white = null;
        sessionToRoom.remove(session.getId());

        // 両者いなくなったら部屋を削除
        if (room.black == null && room.white == null) {
            rooms.remove(roomId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        handleLeave(session);
    }

    private void send(WebSocketSession session, Map<String, Object> data) throws Exception {
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(data)));
        }
    }

    private Map<String, Object> buildState(OthelloGame game) {
        Map<String, Object> s = new HashMap<>();
        s.put("board", game.getBoard());
        s.put("currentPlayer", game.getCurrentPlayer());
        s.put("gameOver", game.isGameOver());
        s.put("winner", game.getWinner());
        s.put("blackScore", game.getScore(OthelloGame.BLACK));
        s.put("whiteScore", game.getScore(OthelloGame.WHITE));
        s.put("validMoves", game.getValidMoves(game.getCurrentPlayer()));
        return s;
    }
}
