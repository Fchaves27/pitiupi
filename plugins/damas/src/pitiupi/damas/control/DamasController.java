package pitiupi.damas.control;

import pitiupi.damas.game.DamasGame;
import pitiupi.damas.game.GameResult;
import pitiupi.damas.game.Side;
import pitiupi.damas.net.DamasMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Sessão de jogo: convites, lances e entrega confiável sobre UDP.
 *
 * <p>Cada mensagem enviada fica pendente até chegar o ACK e é reenviada a cada {@link #tick()}.
 * O receptor confirma sempre, mesmo as duplicadas, e processa cada mensagem uma única vez.
 * Lances recebidos são validados pelas regras locais antes de serem aplicados.</p>
 *
 * <p>Não é thread-safe: todas as chamadas devem vir da mesma thread (no plugin, a EDT).</p>
 */
public final class DamasController {

    public enum Phase {
        LOBBY,
        INVITE_SENT,
        PLAYING,
        FINISHED
    }

    public interface Listener {
        void onStateChanged();

        /** Algo exige a atenção do jogador: convite recebido ou partida iniciada. */
        void onAttentionNeeded();
    }

    /** Convite recebido aguardando a resposta do jogador. */
    public record IncomingInvite(String gameId, String fromId, String fromName, Side inviterSide) {
    }

    static final int INVITE_MAX_ATTEMPTS = 20;
    static final int MESSAGE_MAX_ATTEMPTS = 120;

    private final String selfId;
    private final Supplier<String> selfName;
    private final Consumer<DamasMessage> link;
    private final Listener listener;

    private final List<Pending> pending = new ArrayList<>();
    private final Set<String> processed = new HashSet<>();
    private Set<String> knownPeers = Set.of();
    private int nextSeq = 1;

    private Phase phase = Phase.LOBBY;
    private String gameId;
    private String opponentId;
    private String opponentName;
    private Side mySide;
    private DamasGame game;
    private boolean opponentAway;
    private IncomingInvite incomingInvite;
    private String notice = "";

    public DamasController(
            String selfId,
            Supplier<String> selfName,
            Consumer<DamasMessage> link,
            Listener listener
    ) {
        this.selfId = selfId;
        this.selfName = selfName;
        this.link = link;
        this.listener = listener;
    }

    public Phase getPhase() {
        return phase;
    }

    public DamasGame getGame() {
        return game;
    }

    public Side getMySide() {
        return mySide;
    }

    public String getOpponentId() {
        return opponentId;
    }

    public String getOpponentName() {
        return opponentName;
    }

    public boolean isOpponentAway() {
        return opponentAway;
    }

    public IncomingInvite getIncomingInvite() {
        return incomingInvite;
    }

    /** Último aviso para o jogador (recusa, fim de partida, adversário saiu…). */
    public String getNotice() {
        return notice;
    }

    public boolean isMyTurn() {
        return phase == Phase.PLAYING && game.getTurn() == mySide;
    }

    public boolean canInvite() {
        return (phase == Phase.LOBBY || phase == Phase.FINISHED) && incomingInvite == null;
    }

    public boolean invite(String peerId, String peerName) {
        if (!canInvite()) {
            return false;
        }
        gameId = UUID.randomUUID().toString();
        opponentId = peerId;
        opponentName = peerName;
        mySide = ThreadLocalRandom.current().nextBoolean() ? Side.WHITE : Side.BLACK;
        game = null;
        opponentAway = false;
        phase = Phase.INVITE_SENT;
        notice = "";
        sendReliable(DamasMessage.invite(gameId, selfId, peerId, selfName.get(), nextSeq++, mySide));
        listener.onStateChanged();
        return true;
    }

    public void cancelInvite() {
        if (phase != Phase.INVITE_SENT) {
            return;
        }
        forgetCurrentGameMessages();
        sendControl(DamasMessage.Type.CANCEL);
        phase = Phase.LOBBY;
        notice = "";
        listener.onStateChanged();
    }

    public void acceptInvite() {
        IncomingInvite invite = incomingInvite;
        if (invite == null) {
            return;
        }
        incomingInvite = null;
        gameId = invite.gameId();
        opponentId = invite.fromId();
        opponentName = invite.fromName();
        mySide = invite.inviterSide().opposite();
        sendControl(DamasMessage.Type.ACCEPT);
        startGame();
    }

    public void declineInvite() {
        IncomingInvite invite = incomingInvite;
        if (invite == null) {
            return;
        }
        incomingInvite = null;
        sendReliable(DamasMessage.control(
                DamasMessage.Type.DECLINE, invite.gameId(), selfId, invite.fromId(), selfName.get(), nextSeq++));
        listener.onStateChanged();
    }

    /**
     * Joga o lance local.
     *
     * @return {@code false} se não for a vez do jogador ou o caminho não for legal.
     */
    public boolean play(List<Integer> path) {
        if (!isMyTurn() || game.apply(path) == null) {
            return false;
        }
        sendReliable(DamasMessage.move(gameId, selfId, opponentId, selfName.get(), nextSeq++, path));
        finishIfGameOver();
        listener.onStateChanged();
        return true;
    }

    /** Desiste da partida ou, se o adversário saiu da rede, apenas a encerra. */
    public void resign() {
        if (phase != Phase.PLAYING) {
            return;
        }
        sendControl(DamasMessage.Type.RESIGN);
        phase = Phase.FINISHED;
        notice = opponentAway
                ? "Partida encerrada: " + opponentName + " saiu da rede."
                : "Você desistiu. " + opponentName + " venceu.";
        listener.onStateChanged();
    }

    public void backToLobby() {
        if (phase != Phase.FINISHED) {
            return;
        }
        phase = Phase.LOBBY;
        game = null;
        notice = "";
        listener.onStateChanged();
    }

    /** Reenvia o que ainda não foi confirmado. Chamar periodicamente. */
    public void tick() {
        boolean changed = false;
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending item = it.next();
            if (item.attempts >= maxAttempts(item.message)) {
                it.remove();
                if (item.message.getType() == DamasMessage.Type.INVITE && isCurrentGame(item.message)
                        && phase == Phase.INVITE_SENT) {
                    phase = Phase.LOBBY;
                    notice = opponentName + " não respondeu ao convite.";
                    changed = true;
                }
                continue;
            }
            item.attempts++;
            link.accept(item.message);
        }
        if (changed) {
            listener.onStateChanged();
        }
    }

    /** Atualiza a presença. O adversário que sumir pode voltar (ex.: falha rápida no Wi-Fi). */
    public void onPeersChanged(Set<String> peerIds) {
        Set<String> previous = knownPeers;
        knownPeers = Set.copyOf(peerIds);
        boolean changed = false;

        if (incomingInvite != null && previous.contains(incomingInvite.fromId())
                && !peerIds.contains(incomingInvite.fromId())) {
            notice = incomingInvite.fromName() + " saiu da rede; convite cancelado.";
            incomingInvite = null;
            changed = true;
        }
        if (phase == Phase.INVITE_SENT && previous.contains(opponentId) && !peerIds.contains(opponentId)) {
            forgetCurrentGameMessages();
            phase = Phase.LOBBY;
            notice = opponentName + " saiu da rede; convite cancelado.";
            changed = true;
        }
        if (phase == Phase.PLAYING) {
            boolean away = previous.contains(opponentId) && !peerIds.contains(opponentId)
                    || opponentAway && !peerIds.contains(opponentId);
            if (away != opponentAway) {
                opponentAway = away;
                changed = true;
            }
        }
        if (changed) {
            listener.onStateChanged();
        }
    }

    public void receive(DamasMessage message) {
        if (!selfId.equals(message.getTo()) || message.getFrom() == null || message.getGameId() == null) {
            return;
        }
        if (message.getType() == DamasMessage.Type.ACK) {
            pending.removeIf(item -> item.message.getSeq() == message.getSeq()
                    && item.message.getGameId().equals(message.getGameId())
                    && item.message.getTo().equals(message.getFrom()));
            return;
        }
        link.accept(DamasMessage.ack(message, selfId));
        String key = message.getFrom() + "|" + message.getGameId() + "|" + message.getSeq();
        if (!processed.add(key)) {
            return;
        }
        switch (message.getType()) {
            case INVITE -> onInvite(message);
            case ACCEPT -> onAccept(message);
            case DECLINE -> onInviteRefused(message, " recusou o convite.");
            case BUSY -> onInviteRefused(message, " está ocupado no momento.");
            case CANCEL -> onCancel(message);
            case MOVE -> onMove(message);
            case RESIGN -> onResign(message);
            default -> {
            }
        }
    }

    private void onInvite(DamasMessage message) {
        if (message.getInviterSide() == null) {
            return;
        }
        if (!canInvite()) {
            sendReliable(DamasMessage.control(
                    DamasMessage.Type.BUSY, message.getGameId(), selfId, message.getFrom(), selfName.get(), nextSeq++));
            return;
        }
        incomingInvite = new IncomingInvite(
                message.getGameId(), message.getFrom(), message.getFromName(), message.getInviterSide());
        listener.onStateChanged();
        listener.onAttentionNeeded();
    }

    private void onAccept(DamasMessage message) {
        if (phase == Phase.INVITE_SENT && isFromOpponent(message)) {
            startGame();
        }
    }

    private void onInviteRefused(DamasMessage message, String reason) {
        if (phase == Phase.INVITE_SENT && isFromOpponent(message)) {
            phase = Phase.LOBBY;
            notice = opponentName + reason;
            listener.onStateChanged();
        }
    }

    private void onCancel(DamasMessage message) {
        if (incomingInvite != null && incomingInvite.gameId().equals(message.getGameId())) {
            notice = incomingInvite.fromName() + " cancelou o convite.";
            incomingInvite = null;
            listener.onStateChanged();
        } else if (phase == Phase.PLAYING && isFromOpponent(message)) {
            // O convite foi cancelado enquanto o aceite estava a caminho.
            phase = Phase.LOBBY;
            game = null;
            notice = opponentName + " cancelou o convite.";
            listener.onStateChanged();
        }
    }

    private void onMove(DamasMessage message) {
        if (phase != Phase.PLAYING || !isFromOpponent(message) || message.getPath() == null || isMyTurn()) {
            return;
        }
        if (game.apply(message.getPath()) == null) {
            phase = Phase.FINISHED;
            notice = "Partida interrompida: lance inválido recebido de " + opponentName + ".";
        } else {
            finishIfGameOver();
        }
        listener.onStateChanged();
    }

    private void onResign(DamasMessage message) {
        if (phase == Phase.PLAYING && isFromOpponent(message)) {
            phase = Phase.FINISHED;
            notice = opponentName + " desistiu. Você venceu!";
            listener.onStateChanged();
        }
    }

    private void startGame() {
        game = new DamasGame();
        phase = Phase.PLAYING;
        opponentAway = false;
        notice = "";
        listener.onStateChanged();
        listener.onAttentionNeeded();
    }

    private void finishIfGameOver() {
        GameResult result = game.getResult();
        if (result == GameResult.IN_PROGRESS) {
            return;
        }
        phase = Phase.FINISHED;
        if (result == GameResult.DRAW) {
            notice = "Empate: 20 lances de cada lado só com damas, sem captura.";
        } else {
            Side winner = result == GameResult.WHITE_WINS ? Side.WHITE : Side.BLACK;
            notice = winner == mySide ? "Você venceu!" : opponentName + " venceu.";
        }
    }

    private boolean isFromOpponent(DamasMessage message) {
        return isCurrentGame(message) && message.getFrom().equals(opponentId);
    }

    private boolean isCurrentGame(DamasMessage message) {
        return message.getGameId().equals(gameId);
    }

    /** Um lance da partida em curso nunca é descartado: o adversário pode voltar à rede. */
    private int maxAttempts(DamasMessage message) {
        return switch (message.getType()) {
            case INVITE -> INVITE_MAX_ATTEMPTS;
            case MOVE -> phase == Phase.PLAYING && isCurrentGame(message) ? Integer.MAX_VALUE : MESSAGE_MAX_ATTEMPTS;
            default -> MESSAGE_MAX_ATTEMPTS;
        };
    }

    /** Preserva o que ainda está a caminho de partidas anteriores, como o lance final. */
    private void forgetCurrentGameMessages() {
        pending.removeIf(item -> isCurrentGame(item.message));
    }

    private void sendControl(DamasMessage.Type type) {
        sendReliable(DamasMessage.control(type, gameId, selfId, opponentId, selfName.get(), nextSeq++));
    }

    private void sendReliable(DamasMessage message) {
        pending.add(new Pending(message));
        link.accept(message);
    }

    private static final class Pending {
        private final DamasMessage message;
        private int attempts;

        private Pending(DamasMessage message) {
            this.message = message;
        }
    }
}
