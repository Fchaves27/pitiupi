package pitiupi.damas.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamasGameTest {

    private static int sq(int row, int col) {
        return DamasGame.square(row, col);
    }

    @Test
    void initialPositionHasTwelvePiecesAndSevenMovesForWhite() {
        DamasGame game = new DamasGame();
        assertEquals(12, game.countPieces(Side.WHITE));
        assertEquals(12, game.countPieces(Side.BLACK));
        assertEquals(Side.WHITE, game.getTurn());
        assertEquals(7, game.getLegalMoves().size());
    }

    @Test
    void lowerLeftCornerIsDarkForWhite() {
        assertTrue(DamasGame.isDarkSquare(7, 0));
        assertFalse(DamasGame.isDarkSquare(7, 7));
    }

    @Test
    void manCannotMoveBackwards() {
        DamasGame game = DamasGame.fromRows(Side.WHITE,
                "........",
                "b.......",
                "........",
                "........",
                "...w....",
                "........",
                "........",
                "........");
        assertNull(game.apply(List.of(sq(4, 3), sq(5, 2))));
        assertNotNull(game.apply(List.of(sq(4, 3), sq(3, 2))));
    }

    @Test
    void captureIsMandatoryAndManCapturesBackwards() {
        DamasGame game = DamasGame.fromRows(Side.WHITE,
                "........",
                "........",
                "........",
                "..w.....",
                "...b....",
                "........",
                ".w......",
                "........");
        assertEquals(1, game.getLegalMoves().size());
        assertNull(game.apply(List.of(sq(6, 1), sq(5, 0))));
        Move move = game.apply(List.of(sq(3, 2), sq(5, 4)));
        assertEquals(List.of(sq(4, 3)), move.captured());
        assertNull(game.pieceAt(sq(4, 3)));
    }

    @Test
    void majorityRuleForcesLongestSequence() {
        DamasGame game = DamasGame.fromRows(Side.WHITE,
                "........",
                "........",
                "...b....",
                "........",
                "...b.b..",
                "....w...",
                "........",
                "........");
        assertEquals(1, game.getLegalMoves().size());
        assertEquals(List.of(sq(5, 4), sq(3, 2), sq(1, 4)), game.getLegalMoves().get(0).path());
    }

    @Test
    void capturedPiecesCannotBeJumpedTwiceAndOriginCountsAsEmpty() {
        // Ciclo de quatro saltos que termina na origem, sem repetir a primeira peça.
        DamasGame game = DamasGame.fromRows(Side.WHITE,
                "........",
                "........",
                ".b.b....",
                "........",
                ".b.b....",
                "..w.....",
                "........",
                "........");
        assertEquals(2, game.getLegalMoves().size());
        Move move = game.apply(List.of(sq(5, 2), sq(3, 4), sq(1, 2), sq(3, 0), sq(5, 2)));
        assertEquals(4, move.captured().size());
        assertEquals(0, game.countPieces(Side.BLACK));
    }

    @Test
    void flyingKingMayLandOnAnySquareBehindTheCapturedPiece() {
        DamasGame game = DamasGame.fromRows(Side.WHITE,
                "........",
                "........",
                "........",
                "........",
                "...b....",
                "........",
                ".W......",
                "........");
        assertEquals(4, game.getLegalMoves().size());
        for (Move move : game.getLegalMoves()) {
            assertEquals(List.of(sq(4, 3)), move.captured());
        }
    }

    @Test
    void flyingKingMovesAnyDistanceButNotOverOwnPieces() {
        DamasGame game = DamasGame.fromRows(Side.WHITE,
                "........",
                "........",
                "........",
                "........",
                "........",
                "..w.....",
                "........",
                "W......b");
        assertNotNull(game.getLegalMoves().stream()
                .filter(m -> m.from() == sq(7, 0)).findAny().orElse(null));
        assertTrue(game.getLegalMoves().stream().noneMatch(m -> m.from() == sq(7, 0) && m.to() == sq(4, 3)));

        DamasGame open = DamasGame.fromRows(Side.WHITE,
                "........",
                "........",
                "........",
                "........",
                "........",
                "........",
                "........",
                "W......b");
        assertNotNull(open.apply(List.of(sq(7, 0), sq(0, 7))));
    }

    @Test
    void manReachingLastRowPromotes() {
        DamasGame game = DamasGame.fromRows(Side.WHITE,
                "........",
                "..w.....",
                "........",
                "........",
                "........",
                "........",
                "........",
                "b.......");
        game.apply(List.of(sq(1, 2), sq(0, 1)));
        assertEquals(Piece.WHITE_KING, game.pieceAt(sq(0, 1)));
    }

    @Test
    void manPassingLastRowDuringCaptureDoesNotPromote() {
        DamasGame game = DamasGame.fromRows(Side.WHITE,
                "........",
                "..b.b...",
                ".w......",
                "........",
                "........",
                "........",
                "........",
                "...b....");
        Move move = game.getLegalMoves().get(0);
        assertEquals(List.of(sq(2, 1), sq(0, 3), sq(2, 5)), move.path());
        game.apply(move.path());
        assertEquals(Piece.WHITE_MAN, game.pieceAt(sq(2, 5)));
    }

    @Test
    void playerWithoutMovesLoses() {
        DamasGame game = DamasGame.fromRows(Side.WHITE,
                "........",
                "..b.....",
                ".w......",
                "........",
                "........",
                "........",
                "........",
                "........");
        game.apply(List.of(sq(2, 1), sq(0, 3)));
        assertEquals(GameResult.WHITE_WINS, game.getResult());
        assertTrue(game.getLegalMoves().isEmpty());
        assertNull(game.apply(List.of(sq(0, 3), sq(1, 2))));
    }

    @Test
    void twentyQuietKingMovesEachIsADraw() {
        DamasGame game = DamasGame.fromRows(Side.WHITE,
                ".B......",
                "........",
                "........",
                "........",
                "........",
                "........",
                "........",
                "W.......");
        int[][] whiteSquares = {{7, 0}, {6, 1}};
        int[][] blackSquares = {{0, 1}, {1, 0}};
        for (int ply = 0; ply < 40; ply++) {
            assertEquals(GameResult.IN_PROGRESS, game.getResult());
            int[][] squares = ply % 2 == 0 ? whiteSquares : blackSquares;
            int[] from = squares[ply % 4 < 2 ? 0 : 1];
            int[] to = squares[ply % 4 < 2 ? 1 : 0];
            assertNotNull(game.apply(List.of(sq(from[0], from[1]), sq(to[0], to[1]))));
        }
        assertEquals(GameResult.DRAW, game.getResult());
    }

    @Test
    void manMoveResetsTheDrawCount() {
        DamasGame game = DamasGame.fromRows(Side.WHITE,
                ".B......",
                "........",
                "........",
                "........",
                "........",
                "w.......",
                "........",
                "..W.....");
        for (int ply = 0; ply < 38; ply++) {
            boolean out = ply % 4 < 2;
            if (ply % 2 == 0) {
                game.apply(out ? List.of(sq(7, 2), sq(6, 3)) : List.of(sq(6, 3), sq(7, 2)));
            } else {
                game.apply(out ? List.of(sq(0, 1), sq(1, 2)) : List.of(sq(1, 2), sq(0, 1)));
            }
        }
        assertNotNull(game.apply(List.of(sq(5, 0), sq(4, 1))));
        // Sem zerar, estes dois lances de dama completariam os 40 e dariam empate.
        assertNotNull(game.apply(List.of(sq(1, 2), sq(0, 1))));
        assertNotNull(game.apply(List.of(sq(6, 3), sq(7, 2))));
        assertEquals(GameResult.IN_PROGRESS, game.getResult());
    }

    @Test
    void illegalPathIsRejectedWithoutChangingTurn() {
        DamasGame game = new DamasGame();
        assertNull(game.apply(List.of(sq(5, 0), sq(3, 2))));
        assertEquals(Side.WHITE, game.getTurn());
    }
}
