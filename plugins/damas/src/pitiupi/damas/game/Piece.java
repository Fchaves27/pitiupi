package pitiupi.damas.game;

public enum Piece {
    WHITE_MAN(Side.WHITE, false),
    WHITE_KING(Side.WHITE, true),
    BLACK_MAN(Side.BLACK, false),
    BLACK_KING(Side.BLACK, true);

    private final Side side;
    private final boolean king;

    Piece(Side side, boolean king) {
        this.side = side;
        this.king = king;
    }

    public Side getSide() {
        return side;
    }

    public boolean isKing() {
        return king;
    }

    public Piece promoted() {
        return side == Side.WHITE ? WHITE_KING : BLACK_KING;
    }
}
