package pitiupi.damas.GUI;

import pitiupi.damas.game.DamasGame;
import pitiupi.damas.game.Move;
import pitiupi.damas.game.Piece;
import pitiupi.damas.game.Side;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tabuleiro desenhado em Swing. O lance é montado clique a clique (origem e cada pouso) e só é
 * entregue a {@code onMove} quando o caminho formar um lance legal completo.
 */
final class BoardView extends JComponent {

    private static final Color LIGHT_SQUARE = new Color(238, 217, 181);
    private static final Color DARK_SQUARE = new Color(148, 98, 64);
    private static final Color LAST_MOVE = new Color(186, 162, 72);
    private static final Color MOVABLE = new Color(90, 200, 120);
    private static final Color SELECTED = new Color(250, 210, 60);
    private static final Color WHITE_PIECE = new Color(245, 240, 228);
    private static final Color BLACK_PIECE = new Color(44, 40, 38);
    private static final Color CROWN = new Color(214, 160, 40);

    private final Consumer<List<Integer>> onMove;
    private final List<Integer> partialPath = new ArrayList<>();
    private DamasGame game;
    private Side viewSide = Side.WHITE;
    private boolean interactive;

    BoardView(Consumer<List<Integer>> onMove) {
        this.onMove = onMove;
        setPreferredSize(new Dimension(520, 520));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int square = squareAt(e.getX(), e.getY());
                if (interactive && square >= 0) {
                    onSquareClicked(square);
                }
            }
        });
    }

    void setState(DamasGame game, Side viewSide, boolean interactive) {
        this.game = game;
        this.viewSide = viewSide != null ? viewSide : Side.WHITE;
        this.interactive = interactive && game != null;
        partialPath.clear();
        setCursor(Cursor.getPredefinedCursor(this.interactive ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        repaint();
    }

    private void onSquareClicked(int square) {
        for (Move move : candidates()) {
            if (!partialPath.isEmpty() && move.path().get(partialPath.size()) == square) {
                partialPath.add(square);
                if (move.path().size() == partialPath.size()) {
                    List<Integer> complete = List.copyOf(partialPath);
                    partialPath.clear();
                    onMove.accept(complete);
                }
                repaint();
                return;
            }
        }
        partialPath.clear();
        if (isMovable(square)) {
            partialPath.add(square);
        }
        repaint();
    }

    /** Lances legais que continuam o caminho já clicado. */
    private List<Move> candidates() {
        List<Move> matching = new ArrayList<>();
        for (Move move : game.getLegalMoves()) {
            if (move.path().size() > partialPath.size()
                    && move.path().subList(0, partialPath.size()).equals(partialPath)) {
                matching.add(move);
            }
        }
        return matching;
    }

    private boolean isMovable(int square) {
        return game.getLegalMoves().stream().anyMatch(move -> move.from() == square);
    }

    private int boardSize() {
        return Math.min(getWidth(), getHeight());
    }

    private int cellSize() {
        return boardSize() / DamasGame.SIZE;
    }

    private int originX() {
        return (getWidth() - cellSize() * DamasGame.SIZE) / 2;
    }

    private int originY() {
        return (getHeight() - cellSize() * DamasGame.SIZE) / 2;
    }

    /** Linha na tela: quem joga de pretas vê o tabuleiro girado 180°. */
    private int toView(int index) {
        return viewSide == Side.WHITE ? index : DamasGame.SIZE - 1 - index;
    }

    private int squareAt(int x, int y) {
        int cell = cellSize();
        if (cell == 0) {
            return -1;
        }
        int viewCol = Math.floorDiv(x - originX(), cell);
        int viewRow = Math.floorDiv(y - originY(), cell);
        if (viewCol < 0 || viewRow < 0 || viewCol >= DamasGame.SIZE || viewRow >= DamasGame.SIZE) {
            return -1;
        }
        return DamasGame.square(toView(viewRow), toView(viewCol));
    }

    private int cellX(int square) {
        return originX() + toView(DamasGame.colOf(square)) * cellSize();
    }

    private int cellY(int square) {
        return originY() + toView(DamasGame.rowOf(square)) * cellSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintSquares(g);
        if (game != null) {
            paintPieces(g);
            if (interactive) {
                paintHints(g);
            }
        }
        g.dispose();
    }

    private void paintSquares(Graphics2D g) {
        Move lastMove = game != null ? game.getLastMove() : null;
        int cell = cellSize();
        for (int square = 0; square < DamasGame.SIZE * DamasGame.SIZE; square++) {
            boolean dark = DamasGame.isDarkSquare(DamasGame.rowOf(square), DamasGame.colOf(square));
            boolean highlighted = lastMove != null && lastMove.path().contains(square);
            g.setColor(highlighted ? LAST_MOVE : dark ? DARK_SQUARE : LIGHT_SQUARE);
            g.fillRect(cellX(square), cellY(square), cell, cell);
        }
    }

    private void paintPieces(Graphics2D g) {
        List<Integer> capturedSoFar = capturedSoFar();
        Piece moving = partialPath.isEmpty() ? null : game.pieceAt(partialPath.get(0));
        for (int square = 0; square < DamasGame.SIZE * DamasGame.SIZE; square++) {
            Piece piece = game.pieceAt(square);
            boolean movedAway = !partialPath.isEmpty() && partialPath.get(0) == square;
            if (piece != null && !movedAway) {
                paintPiece(g, piece, square, capturedSoFar.contains(square));
            }
        }
        if (moving != null) {
            paintPiece(g, moving, partialPath.get(partialPath.size() - 1), false);
        }
    }

    private List<Integer> capturedSoFar() {
        if (partialPath.size() < 2) {
            return List.of();
        }
        List<Move> matching = candidates();
        if (matching.isEmpty() || matching.get(0).captured().isEmpty()) {
            return List.of();
        }
        return matching.get(0).captured().subList(0, partialPath.size() - 1);
    }

    private void paintPiece(Graphics2D g, Piece piece, int square, boolean dimmed) {
        int cell = cellSize();
        int margin = cell / 8;
        int size = cell - 2 * margin;
        int x = cellX(square) + margin;
        int y = cellY(square) + margin;
        Color fill = piece.getSide() == Side.WHITE ? WHITE_PIECE : BLACK_PIECE;
        Color edge = piece.getSide() == Side.WHITE ? BLACK_PIECE : WHITE_PIECE;
        if (dimmed) {
            fill = new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 90);
            edge = new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 90);
        }
        g.setColor(fill);
        g.fillOval(x, y, size, size);
        g.setColor(edge);
        g.setStroke(new BasicStroke(Math.max(1f, cell / 40f)));
        g.drawOval(x, y, size, size);
        g.drawOval(x + size / 6, y + size / 6, size * 2 / 3, size * 2 / 3);
        if (piece.isKing()) {
            paintCrown(g, x, y, size);
        }
    }

    private void paintCrown(Graphics2D g, int x, int y, int size) {
        int left = x + size / 4;
        int right = x + size * 3 / 4;
        int top = y + size * 3 / 10;
        int bottom = y + size * 2 / 3;
        int mid = (left + right) / 2;
        int inner = y + size / 2;
        g.setColor(CROWN);
        g.fillPolygon(
                new int[]{left, left, (left + mid) / 2, mid, (mid + right) / 2, right, right},
                new int[]{bottom, top, inner, top, inner, top, bottom},
                7
        );
    }

    private void paintHints(Graphics2D g) {
        int cell = cellSize();
        g.setStroke(new BasicStroke(Math.max(2f, cell / 20f)));
        if (partialPath.isEmpty()) {
            g.setColor(MOVABLE);
            for (Move move : game.getLegalMoves()) {
                g.drawRect(cellX(move.from()) + 1, cellY(move.from()) + 1, cell - 3, cell - 3);
            }
            return;
        }
        int current = partialPath.get(partialPath.size() - 1);
        g.setColor(SELECTED);
        g.drawRect(cellX(current) + 1, cellY(current) + 1, cell - 3, cell - 3);
        int dot = cell / 3;
        for (Move move : candidates()) {
            int next = move.path().get(partialPath.size());
            g.fillOval(cellX(next) + (cell - dot) / 2, cellY(next) + (cell - dot) / 2, dot, dot);
        }
    }
}
