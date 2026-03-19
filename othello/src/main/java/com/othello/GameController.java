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
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS ranking (" +
                "id SERIAL PRIMARY KEY, " +
                "name VARCHAR(50) NOT NULL, " +
                "score INTEGER NOT NULL, " +
                "difficulty VARCHAR(10) NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            System.out.println("=== rankingテーブル準備完了 ===");
        } catch (Exception e) {
            System.err.println("=== テーブル作成失敗: " + e.getMessage() + " ===");
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
        boolean playerIsWhite = false;

        if (body != null) {
            if (body.containsKey("difficulty"))
                diff = OthelloGame.Difficulty.valueOf(body.get("difficulty").toUpperCase());
            if ("vs_human".equalsIgnoreCase(body.get("mode")))
                mode = OthelloGame.Mode.VS_HUMAN;
            if ("white".equalsIgnoreCase(body.get("playerColor")))
                playerIsWhite = true;
        }

        OthelloGame game = new OthelloGame(diff, mode, playerIsWhite);
        session.setAttribute("game", game);

        Map<String, Object> state = buildState(game);

        // 後攻（プレイヤーが白）の場合、AIが先に打つ
        if (playerIsWhite && mode == OthelloGame.Mode.VS_AI) {
            List<int[]> aiMoves = new ArrayList<>();
            while (!game.isGameOver() && game.getCurrentPlayer() == OthelloGame.BLACK) {
                int[] aiPos = game.aiMove();
                if (aiPos != null) aiMoves.add(aiPos);
            }
            if (!aiMoves.isEmpty()) {
                int[] lastAI = aiMoves.get(aiMoves.size() - 1);
                state.put("aiRow", lastAI[0]);
                state.put("aiCol", lastAI[1]);
            }
            // AIが打った後の盤面で state を再構築
            state.putAll(buildState(game));
        }

        return state;
    }

    @PostMapping("/move")
    public Map<String, Object> move(@RequestBody Map<String, Integer> body, HttpSession session) {
        OthelloGame game = getGame(session);
        Map<String, Object> result = new HashMap<>();
        int row = body.get("row"), col = body.get("col");

        boolean moved = game.playerMove(row, col);
        result.put("moved", moved);

        if (moved && game.getMode() == OthelloGame.Mode.VS_AI) {
            result.put("boardAfterPlayer", game.getBoard());

            List<int[]> aiMoves = new ArrayList<>();
            while (!game.isGameOver() && game.getCurrentPlayer() != game.getPlayerColor()) {
                int[] aiPos = game.aiMove();
                if (aiPos != null) aiMoves.add(aiPos);
            }

            if (!aiMoves.isEmpty()) {
                int[] lastAI = aiMoves.get(aiMoves.size() - 1);
                result.put("aiRow", lastAI[0]);
                result.put("aiCol", lastAI[1]);
            } else {
                result.put("aiRow", -1);
                result.put("aiCol", -1);
            }
        }

        result.putAll(buildState(game));
        return result;
    }

    @PostMapping("/name")
    public Map<String, Object> setName(@RequestBody Map<String, String> body, HttpSession session) {
        session.setAttribute("playerName", body.getOrDefault("name", "Player"));
        return Map.of("ok", true);
    }

    @PostMapping("/ranking/submit")
    public Map<String, Object> submitRanking(
            @RequestBody(required = false) Map<String, String> body,
            HttpSession session) {
        if (body != null && body.containsKey("score") && body.containsKey("difficulty")) {
            String name = body.getOrDefault("name", "Player");
            int score = Integer.parseInt(body.get("score"));
            String difficulty = body.get("difficulty");
            try {
                jdbc.update(
                    "INSERT INTO ranking (name, score, difficulty) VALUES (?, ?, ?)",
                    name, score, difficulty
                );
                System.out.println("=== ランキング登録: " + name + " score=" + score + " diff=" + difficulty + " ===");
                return Map.of("ok", true);
            } catch (Exception e) {
                System.err.println("=== ランキング保存失敗: " + e.getMessage() + " ===");
                return Map.of("ok", false, "error", e.getMessage());
            }
        }
        OthelloGame game = getGame(session);
        if (game == null || !game.isGameOver()) return Map.of("ok", false, "reason", "game not over");
        String name = (String) session.getAttribute("playerName");
        if (name == null) name = "Player";
        saveRanking(name, game);
        return Map.of("ok", true);
    }

    @GetMapping("/ranking")
    public Map<String, Object> getRanking() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT name, score, difficulty, " +
                "TO_CHAR(created_at, 'MM/DD') AS date " +
                "FROM ranking ORDER BY difficulty, score DESC"
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
        // プレイヤーが勝った場合のみ登録
        String playerWinner = game.isPlayerWhite() ? "white" : "black";
        if (!playerWinner.equals(game.getWinner())) return;
        try {
            int playerScore = game.getScore(game.getPlayerColor());
            jdbc.update(
                "INSERT INTO ranking (name, score, difficulty) VALUES (?, ?, ?)",
                name, playerScore, game.getDifficulty().name()
            );
        } catch (Exception e) {
            System.err.println("ランキング保存失敗: " + e.getMessage());
        }
    }

    @GetMapping("/debug")
    public Map<String, Object> debug() {
        Map<String, Object> result = new HashMap<>();
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            result.put("db", "connected");
        } catch (Exception e) {
            result.put("db", "FAILED: " + e.getMessage());
        }
        try {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ranking", Integer.class);
            result.put("ranking_rows", count);
        } catch (Exception e) {
            result.put("ranking_table", "MISSING");
            try {
                jdbc.execute(
                    "CREATE TABLE IF NOT EXISTS ranking (" +
                    "id SERIAL PRIMARY KEY, " +
                    "name VARCHAR(50) NOT NULL, " +
                    "score INTEGER NOT NULL, " +
                    "difficulty VARCHAR(10) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                result.put("table_created", true);
            } catch (Exception e2) {
                result.put("table_create_error", e2.getMessage());
            }
        }
        return result;
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
        state.put("playerColor", game.getPlayerColor());

        // validMoves: プレイヤーの番のときだけ返す
        boolean isPlayerTurn = game.getMode() == OthelloGame.Mode.VS_HUMAN
                || game.getCurrentPlayer() == game.getPlayerColor();
        if (!game.isGameOver() && isPlayerTurn) {
            state.put("validMoves", game.getValidMoves(game.getCurrentPlayer()));
        } else {
            state.put("validMoves", List.of());
        }

        return state;
    }
}
