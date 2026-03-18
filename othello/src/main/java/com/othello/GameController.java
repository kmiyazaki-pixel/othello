package com.othello;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class GameController {

    private final JdbcTemplate jdbc;

    public GameController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initDb() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ranking (
                    id         SERIAL PRIMARY KEY,
                    name       VARCHAR(50)  NOT NULL,
                    score      INTEGER      NOT NULL,
                    difficulty VARCHAR(10)  NOT NULL,
                    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                )
            """);
        } catch (Exception e) {
            System.err.println("テーブル作成スキップ: " + e.getMessage());
        }
    }

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
            if (body.containsKey("difficulty"))
                diff = OthelloGame.Difficulty.valueOf(body.get("difficulty").toUpperCase());
            if ("vs_human".equalsIgnoreCase(body.get("mode")))
                mode = OthelloGame.Mode.VS_HUMAN;
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

        // 誰の手かを明示（VS_AIはBLACK固定、VS_HUMANはcurrentPlayer）
        int movingPlayer = (game.getMode() == OthelloGame.Mode.VS_AI)
                ? OthelloGame.BLACK
                : game.getCurrentPlayer();

        boolean moved = game.playerMove(row, col, movingPlayer);
        result.put("moved", moved);

        // AIの手番
        if (moved && !game.isGameOver()
                && game.getMode() == OthelloGame.Mode.VS_AI
                && game.getCurrentPlayer() == OthelloGame.WHITE) {
            // AIが置く前の盤面を返す（フロント側でアニメ分離に使用）
            result.put("boardAfterPlayer", game.getBoard());
            int[] aiPos = game.aiMove();
            result.put("aiRow", aiPos != null ? aiPos[0] : -1);
            result.put("aiCol", aiPos != null ? aiPos[1] : -1);
        }

        result.putAll(buildState(game));

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
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT name, score, difficulty, " +
                "TO_CHAR(created_at, 'MM/DD HH24:MI') AS date " +
                "FROM ranking ORDER BY score DESC LIMIT 10"
            );
            return Map.of("ranking", rows);
        } catch (Exception e) {
            return Map.of("ranking", List.of());
        }
    }

    @DeleteMapping("/ranking")
    public Map<String, Object> clearRanking() {
        jdbc.execute("DELETE FROM ranking");
        return Map.of("ok", true);
    }

    private void saveRanking(String name, OthelloGame game) {
        if (!"black".equals(game.getWinner())) return;
        try {
            jdbc.update(
                "INSERT INTO ranking (name, score, difficulty) VALUES (?, ?, ?)",
                name, game.getScore(OthelloGame.BLACK), game.getDifficulty().name()
            );
        } catch (Exception e) {
            System.err.println("ランキング保存失敗: " + e.getMessage());
        }
    }

    private Map<String, Object> buildState(OthelloGame game) {
        Map<String, Object> state = new HashMap<>();
        state.put("board", game.getBoard());
        state.put("currentPlayer", game.getCurrentPlayer());
        state.put("gameOver", game.isGameOver());
        state.put("winner", game.getWinner());
        state.put("blackScore", game.getScore(OthelloGame.BLACK));
        state.put("whiteScore", game.getScore(OthelloGame.WHITE));
        state.put("difficulty", game.getDifficulty().name());
        state.put("mode", game.getMode().name());

        // validMoves: プレイヤーの番のときだけ返す（AIの番中は空）
        boolean isPlayerTurn = game.getMode() == OthelloGame.Mode.VS_HUMAN
                || game.getCurrentPlayer() == OthelloGame.BLACK;
        if (!game.isGameOver() && isPlayerTurn) {
            state.put("validMoves", game.getValidMoves(game.getCurrentPlayer()));
        } else {
            state.put("validMoves", List.of());
        }

        return state;
    }
}
