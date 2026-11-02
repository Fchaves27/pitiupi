package pitiupi.damas.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Regras do jogo de damas brasileiro (tabuleiro 8×8), sem rede nem Swing.
 *
 * <p>Pedras andam uma casa para a frente e capturam para a frente e para trás. Damas são
 * voadoras. A captura é obrigatória e vale a lei da maioria: só são legais as sequências com
 * o maior número de peças; em caso de igualdade, a escolha é livre. As peças capturadas só
 * saem do tabuleiro no fim da sequência e não podem ser saltadas duas vezes. A pedra que
 * passa pela última linha no meio de uma captura só é promovida se terminar nela.</p>
 *
 * <p>Empate após 20 lances de cada lado só com damas, sem captura nem movimento de pedra.</p>
 */
public final class DamasGame {

    public static final int SIZE = 8;
    private static final int INITIAL_ROWS = 3;
    private static final int DRAW_QUIET_PLIES = 40;
    private static final int[][] DIRECTIONS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

    private final Piece[] board = new Piece[SIZE * SIZE];
    private Side turn = Side.WHITE;
    private int quietPlies;
    private GameResult result = GameResult.IN_PROGRESS;
    private Move lastMove;
    private List<Move> legalMoves;

    public DamasGame() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (!isDarkSquare(row, col)) {
                    continue;
                }
                if (row < INITIAL_ROWS) {
                    board[square(row, col)] = Piece.BLACK_MAN;
                } else if (row >= SIZE - INITIAL_ROWS) {
                    board[square(row, col)] = Piece.WHITE_MAN;
                }
            }
        }
        legalMoves = computeLegalMoves();
    }

    /**
     * Posição arbitrária para testes: uma string por linha, com {@code w b W B} e {@code .}.
     */
    static DamasGame fromRows(Side turn, String... rows) {
        DamasGame game = new DamasGame();
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                game.board[square(row, col)] = switch (rows[row].charAt(col)) {
                    case 'w' -> Piece.WHITE_MAN;
                    case 'W' -> Piece.WHITE_KING;
                    case 'b' -> Piece.BLACK_MAN;
                    case 'B' -> Piece.BLACK_KING;
                    default -> null;
                };
            }
        }
        game.turn = turn;
        game.legalMoves = game.computeLegalMoves();
        return game;
    }

    public static int square(int row, int col) {
        return row * SIZE + col;
    }

    public static int rowOf(int square) {
        return square / SIZE;
    }

    public static int colOf(int square) {
        return square % SIZE;
    }

    /** A casa escura fica no canto inferior esquerdo de cada jogador. */
    public static boolean isDarkSquare(int row, int col) {
        return (row + col) % 2 == 1;
    }

    public Piece pieceAt(int square) {
        return board[square];
    }

    public Side getTurn() {
        return turn;
    }

    public GameResult getResult() {
        return result;
    }

    public Move getLastMove() {
        return lastMove;
    }

    public List<Move> getLegalMoves() {
        return legalMoves;
    }

    public int countPieces(Side side) {
        int count = 0;
        for (Piece piece : board) {
            if (piece != null && piece.getSide() == side) {
                count++;
            }
        }
        return count;
    }

    /**
     * Aplica o lance com este caminho.
     *
     * @return o lance aplicado ou {@code null} se o caminho não for legal nesta posição.
     */
    public Move apply(List<Integer> path) {
        if (result != GameResult.IN_PROGRESS) {
            return null;
        }
        for (Move move : legalMoves) {
            if (move.path().equals(path)) {
                applyLegal(move);
                return move;
            }
        }
        return null;
    }

    private void applyLegal(Move move) {
        Piece piece = board[move.from()];
        board[move.from()] = null;
        for (int captured : move.captured()) {
            board[captured] = null;
        }
        boolean promotes = !piece.isKing() && rowOf(move.to()) == piece.getSide().getPromotionRow();
        board[move.to()] = promotes ? piece.promoted() : piece;

        quietPlies = piece.isKing() && move.captured().isEmpty() ? quietPlies + 1 : 0;
        lastMove = move;
        Side mover = turn;
        turn = mover.opposite();
        legalMoves = computeLegalMoves();

        if (legalMoves.isEmpty()) {
            result = mover == Side.WHITE ? GameResult.WHITE_WINS : GameResult.BLACK_WINS;
        } else if (quietPlies >= DRAW_QUIET_PLIES) {
            result = GameResult.DRAW;
        }
    }

    private List<Move> computeLegalMoves() {
        List<Move> captures = new ArrayList<>();
        for (int square = 0; square < board.length; square++) {
            Piece piece = board[square];
            if (piece != null && piece.getSide() == turn) {
                // A peça em movimento não bloqueia o próprio caminho durante a sequência.
                board[square] = null;
                List<Integer> path = new ArrayList<>(List.of(square));
                collectCaptures(square, piece, path, new ArrayList<>(), captures);
                board[square] = piece;
            }
        }
        if (!captures.isEmpty()) {
            return keepLargestCaptures(captures);
        }
        List<Move> moves = new ArrayList<>();
        for (int square = 0; square < board.length; square++) {
            Piece piece = board[square];
            if (piece != null && piece.getSide() == turn) {
                collectSimpleMoves(square, piece, moves);
            }
        }
        return moves;
    }

    private static List<Move> keepLargestCaptures(List<Move> captures) {
        int largest = 0;
        for (Move move : captures) {
            largest = Math.max(largest, move.captured().size());
        }
        List<Move> kept = new ArrayList<>();
        for (Move move : captures) {
            if (move.captured().size() == largest) {
                kept.add(move);
            }
        }
        return kept;
    }

    private void collectCaptures(
            int from,
            Piece piece,
            List<Integer> path,
            List<Integer> captured,
            List<Move> out
    ) {
        boolean extended = false;
        for (int[] direction : DIRECTIONS) {
            int victim = findVictim(from, direction, piece, captured);
            if (victim < 0) {
                continue;
            }
            int row = rowOf(victim) + direction[0];
            int col = colOf(victim) + direction[1];
            while (isOnBoard(row, col) && board[square(row, col)] == null) {
                extended = true;
                path.add(square(row, col));
                captured.add(victim);
                collectCaptures(square(row, col), piece, path, captured, out);
                captured.remove(captured.size() - 1);
                path.remove(path.size() - 1);
                if (!piece.isKing()) {
                    break;
                }
                row += direction[0];
                col += direction[1];
            }
        }
        if (!extended && !captured.isEmpty()) {
            out.add(new Move(path, captured));
        }
    }

    /**
     * Primeira peça encontrada na direção, se puder ser capturada; {@code -1} caso contrário.
     * Peças já capturadas seguem no tabuleiro e bloqueiam a passagem.
     */
    private int findVictim(int from, int[] direction, Piece piece, List<Integer> captured) {
        int row = rowOf(from) + direction[0];
        int col = colOf(from) + direction[1];
        while (isOnBoard(row, col)) {
            Piece found = board[square(row, col)];
            if (found != null) {
                boolean capturable = found.getSide() != piece.getSide() && !captured.contains(square(row, col));
                return capturable ? square(row, col) : -1;
            }
            if (!piece.isKing()) {
                return -1;
            }
            row += direction[0];
            col += direction[1];
        }
        return -1;
    }

    private void collectSimpleMoves(int from, Piece piece, List<Move> out) {
        for (int[] direction : DIRECTIONS) {
            if (!piece.isKing() && direction[0] != piece.getSide().getForwardRow()) {
                continue;
            }
            int row = rowOf(from) + direction[0];
            int col = colOf(from) + direction[1];
            while (isOnBoard(row, col) && board[square(row, col)] == null) {
                out.add(new Move(List.of(from, square(row, col)), List.of()));
                if (!piece.isKing()) {
                    break;
                }
                row += direction[0];
                col += direction[1];
            }
        }
    }

    private static boolean isOnBoard(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }
}
