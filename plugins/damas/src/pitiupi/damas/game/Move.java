package pitiupi.damas.game;

import java.util.List;

/**
 * Lance completo: casas percorridas (origem, pousos) e peças capturadas, na ordem do salto.
 */
public record Move(List<Integer> path, List<Integer> captured) {

    public Move {
        path = List.copyOf(path);
        captured = List.copyOf(captured);
    }

    public int from() {
        return path.get(0);
    }

    public int to() {
        return path.get(path.size() - 1);
    }
}
