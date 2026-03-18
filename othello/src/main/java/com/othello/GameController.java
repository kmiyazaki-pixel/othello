package com.othello;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api")
public class GameController {

    // In-memory ranking (最大100件)
    private static final List<Map<String, Object>> ranking = new CopyOnWriteArrayList<>();

    private OthelloGame getGame(HttpSession session) {
        OthelloGame game = (OthelloGame) session.getAttribute("game");
        if (game == null) {
            game = new OthelloGame(OthelloGame.Difficulty.NORMAL, OthelloGame.Mode.VS_AI);
            session.setAttribute("game", game);
        }
        return game;
    }

    @GetMapping("/state")
    public Map<String, Object> getState(HttpSession session) {
        return buildState(getGame(session));
    }

    @PostMapping("/new")
    public Map<String, Object> newGame(
            @RequestBody(required = false) Map<String, String> body,
            HttpSession session) {

        OthelloGame.Difficulty diff = OthelloGame.Difficulty.NORMAL;
        OthelloGame.Mode mode = OthelloGame.Mode.VS_AI;

        if (body != null) {
            if (body.containsKey("difficulty")) {
                diff = OthelloGame.Difficulty.valueOf(body.get("difficulty").toUpperCase());
            }
            if ("vs_human".equalsIgnoreCase(body.get("mode"))) {
                mode = OthelloGame.Mode.VS_HUMAN;
            }
        }

        OthelloGame game = new OthelloGame(diff, mode);
        session.setAttribute("game", game);
        return buildState(game);
    }

    @PostMapping("/move")
    public Map<String, Object> move(@RequestBody Map<String, Integer> body, HttpSession session) {
        OthelloGame game = getGame(session);
        Map<String, Object> result = new HashMap<>();
        int row = body.get("row"), col = body.get("col");

        boolean moved = game.playerMove(row, col);
        result.put("moved", moved);

        if (moved && !game.isGameOver()
                && game.getMode() == OthelloGame.Mode.VS_AI
                && game.getCurrentPlayer() == OthelloGame.WHITE) {
            int[] aiPos = game.aiMove();
            result.put("aiRow", aiPos != null ? aiPos[0] : -1);
            result.put("aiCol", aiPos != null ? aiPos[1] : -1);
        }

        result.putAll(buildState(game));

        // ゲーム終了時にランキング登録（VSAIのみ）
        if (game.isGameOver() && game.getMode() == OthelloGame.Mode.VS_AI) {
            String name = (String) session.getAttribute("playerName");
            if (name == null) name = "Player";
            saveRanking(name, game);
        }

        return result;
    }

    @PostMapping("/name")
    public Map<String, Object> setName(@RequestBody Map<String, String> body, HttpSession session) {
        session.setAttribute("playerName", body.getOrDefault("name", "Player"));
        return Map.of("ok", true);
    }

    @GetMapping("/ranking")
    public Map<String, Object> getRanking() {
        List<Map<String, Object>> sorted = ranking.stream()
            .sorted((a, b) -> Integer.compare((int)b.get("score"), (int)a.get("score")))
            .limit(10)
            .toList();
        return Map.of("ranking", sorted);
    }

    private void saveRanking(String name, OthelloGame game) {
        if (!"black".equals(game.getWinner())) return; // 勝利時のみ
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("score", game.getScore(OthelloGame.BLACK));
        entry.put("difficulty", game.getDifficulty().name());
        entry.put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm")));
        ranking.add(entry);
        if (ranking.size() > 100) ranking.remove(0);
    }

    @GetMapping("/hints")
    public Map<String, Object> getHints(HttpSession session) {
        OthelloGame game = getGame(session);
        List<int[]> moves = game.getValidMoves(game.getCurrentPlayer());
        List<Map<String, Integer>> hints = new ArrayList<>();
        for (int[] m : moves) hints.add(Map.of("row", m[0], "col", m[1]));
        return Map.of("hints", hints);
    }

    private Map<String, Object> buildState(OthelloGame game) {
        Map<String, Object> state = new HashMap<>();
        state.put("board", game.getBoard());
        state.put("currentPlayer", game.getCurrentPlayer());
        state.put("gameOver", game.isGameOver());
        state.put("winner", game.getWinner());
        state.put("blackScore", game.getScore(OthelloGame.BLACK));
        state.put("whiteScore", game.getScore(OthelloGame.WHITE));
        state.put("validMoves", game.getValidMoves(game.getCurrentPlayer()));
        state.put("difficulty", game.getDifficulty().name());
        state.put("mode", game.getMode().name());
        return state;
    }
}
