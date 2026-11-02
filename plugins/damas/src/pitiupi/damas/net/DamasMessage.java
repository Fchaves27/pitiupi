package pitiupi.damas.net;

import pitiupi.damas.game.Side;
import pitiupi.models.Messages.Message;

import java.util.List;

/**
 * Mensagem do protocolo de damas. Viaja em UDP multicast: {@code to} indica o destinatário e os
 * demais peers a ignoram. Exceto {@link Type#ACK}, toda mensagem é confirmada e retransmitida.
 */
public class DamasMessage extends Message {

    private static final long serialVersionUID = 1L;

    public enum Type {
        INVITE,
        ACCEPT,
        DECLINE,
        BUSY,
        CANCEL,
        MOVE,
        RESIGN,
        ACK
    }

    private final Type type;
    private final String gameId;
    private final String from;
    private final String to;
    private final String fromName;
    private final int seq;
    private final Side inviterSide;
    private final int[] path;

    private DamasMessage(
            Type type,
            String gameId,
            String from,
            String to,
            String fromName,
            int seq,
            Side inviterSide,
            int[] path
    ) {
        this.type = type;
        this.gameId = gameId;
        this.from = from;
        this.to = to;
        this.fromName = fromName;
        this.seq = seq;
        this.inviterSide = inviterSide;
        this.path = path;
    }

    public static DamasMessage invite(String gameId, String from, String to, String fromName, int seq, Side inviterSide) {
        return new DamasMessage(Type.INVITE, gameId, from, to, fromName, seq, inviterSide, null);
    }

    public static DamasMessage move(String gameId, String from, String to, String fromName, int seq, List<Integer> path) {
        int[] squares = path.stream().mapToInt(Integer::intValue).toArray();
        return new DamasMessage(Type.MOVE, gameId, from, to, fromName, seq, null, squares);
    }

    /** ACCEPT, DECLINE, BUSY, CANCEL ou RESIGN. */
    public static DamasMessage control(Type type, String gameId, String from, String to, String fromName, int seq) {
        return new DamasMessage(type, gameId, from, to, fromName, seq, null, null);
    }

    public static DamasMessage ack(DamasMessage received, String from) {
        return new DamasMessage(Type.ACK, received.gameId, from, received.from, null, received.seq, null, null);
    }

    public Type getType() {
        return type;
    }

    public String getGameId() {
        return gameId;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getFromName() {
        return fromName;
    }

    public int getSeq() {
        return seq;
    }

    public Side getInviterSide() {
        return inviterSide;
    }

    /** Caminho do lance ou {@code null} se a mensagem não for {@link Type#MOVE}. */
    public List<Integer> getPath() {
        return path == null ? null : java.util.Arrays.stream(path).boxed().toList();
    }
}
