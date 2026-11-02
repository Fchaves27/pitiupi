package pitiupi.damas.game;

/**
 * Lado de um jogador. As brancas começam e sobem o tabuleiro (linha 7 → linha 0).
 */
public enum Side {
    WHITE("brancas", -1, 0),
    BLACK("pretas", 1, 7);

    private final String label;
    private final int forwardRow;
    private final int promotionRow;

    Side(String label, int forwardRow, int promotionRow) {
        this.label = label;
        this.forwardRow = forwardRow;
        this.promotionRow = promotionRow;
    }

    public String getLabel() {
        return label;
    }

    public int getForwardRow() {
        return forwardRow;
    }

    public int getPromotionRow() {
        return promotionRow;
    }

    public Side opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}
