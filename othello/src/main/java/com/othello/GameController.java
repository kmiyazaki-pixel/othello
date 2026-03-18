package com.othello;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class GameController {

    private OthelloGame getGame(HttpSession session) {
        OthelloGame game = (OthelloGame) session.getAttribute("game");
        if (game == null) {
            game = new OthelloGame();
            session.setAttribute("game", game);
        }
        return game;
    }

    @GetMapping("/state")
    public Map<String, Object> getState(HttpSession session) {
        return buildState(getGame(session));
    }

    @PostMapping("/new")
    public Map<String, Object> newGame(HttpSession session) {
        OthelloGame game = new OthelloGame();
        session.setAttribute("game", game);
        return buildState(game);
    }

    @PostMapping("/move")
    public Map<String, Object> move(@RequestBody Map<String, Integer> body, HttpSession session) {
        OthelloGame game = getGame(session);
        Map<String, Object> result = new HashMap<>();
        int row = body.get("row");
        int col = body.get("col");

        boolean moved = game.playerMove(row, col);
        result.put("moved", moved);

        if (moved && !game.isGameOver() && game.getCurrentPlayer() == OthelloGame.WHITE) {
            int[] aiPos = game.aiMove();
            result.put("aiRow", aiPos != null ? aiPos[0] : -1);
            result.put("aiCol", aiPos != null ? aiPos[1] : -1);
        }

        result.putAll(buildState(game));
        return result;
    }

    @GetMapping("/hints")
    public Map<String, Object> getHints(HttpSession session) {
        OthelloGame game = getGame(session);
        List<int[]> moves = game.getValidMoves(OthelloGame.BLACK);
        List<Map<String, Integer>> hints = new ArrayList<>();
        for (int[] m : moves) {
            Map<String, Integer> h = new HashMap<>();
            h.put("row", m[0]);
            h.put("col", m[1]);
            hints.add(h);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("hints", hints);
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
        state.put("validMoves", game.getValidMoves(game.getCurrentPlayer()));
        return state;
    }
}
