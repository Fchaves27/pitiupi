package pitiupi.damas.control;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pitiupi.damas.game.Move;
import pitiupi.damas.game.Side;
import pitiupi.damas.net.DamasMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamasControllerTest {

    /** Rede simulada em multicast: todos recebem tudo, inclusive o eco do próprio envio. */
    private final Deque<DamasMessage> wire = new ArrayDeque<>();
    private Random loss;
    private double lossRate;
    private int attentionA;
    private int attentionB;
    private DamasController a;
    private DamasController b;
    private DamasController c;

    @BeforeEach
    void setUp() {
        lossRate = 0;
        loss = new Random(42);
        a = controller("a", () -> attentionA++);
        b = controller("b", () -> attentionB++);
        c = controller("c", () -> { });
    }

    private DamasController controller(String id, Runnable onAttention) {
        return new DamasController(id, () -> id.toUpperCase(), wire::add, new DamasController.Listener() {
            @Override
            public void onStateChanged() {
            }

            @Override
            public void onAttentionNeeded() {
                onAttention.run();
            }
        });
    }

    /** Entrega o que está na rede, passando pela serialização real e descartando conforme a perda. */
    private void deliver() {
        for (int guard = 0; guard < 10_000 && !wire.isEmpty(); guard++) {
            DamasMessage message = wire.poll();
            for (DamasController target : List.of(a, b, c)) {
                if (loss.nextDouble() >= lossRate) {
                    target.receive(roundTrip(message));
                }
            }
        }
    }

    private void settle() {
        deliver();
        for (int i = 0; i < 200; i++) {
            a.tick();
            b.tick();
            c.tick();
            deliver();
        }
    }

    private static DamasMessage roundTrip(DamasMessage message) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(message.toByteArray()))) {
            return (DamasMessage) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private void startGame() {
        assertTrue(a.invite("b", "B"));
        deliver();
        b.acceptInvite();
        deliver();
    }

    private DamasController whitePlayer() {
        return a.getMySide() == Side.WHITE ? a : b;
    }

    private DamasController blackPlayer() {
        return a.getMySide() == Side.WHITE ? b : a;
    }

    @Test
    void acceptedInviteStartsGameWithOppositeSides() {
        startGame();
        assertEquals(DamasController.Phase.PLAYING, a.getPhase());
        assertEquals(DamasController.Phase.PLAYING, b.getPhase());
        assertEquals(a.getMySide().opposite(), b.getMySide());
        assertEquals("B", a.getOpponentName());
        assertEquals("A", b.getOpponentName());
        assertTrue(attentionA > 0 && attentionB > 0);
    }

    @Test
    void thirdPeerIgnoresTheConversation() {
        startGame();
        assertEquals(DamasController.Phase.LOBBY, c.getPhase());
        assertNull(c.getIncomingInvite());
    }

    @Test
    void declinedInviteReturnsInviterToLobby() {
        a.invite("b", "B");
        deliver();
        b.declineInvite();
        deliver();
        assertEquals(DamasController.Phase.LOBBY, a.getPhase());
        assertEquals("B recusou o convite.", a.getNotice());
        assertNull(b.getIncomingInvite());
    }

    @Test
    void cancelledInviteDisappearsFromInvitee() {
        a.invite("b", "B");
        deliver();
        assertNotNull(b.getIncomingInvite());
        a.cancelInvite();
        deliver();
        assertNull(b.getIncomingInvite());
    }

    @Test
    void inviteWhileBusyIsAnsweredAutomatically() {
        startGame();
        assertTrue(c.invite("b", "B"));
        deliver();
        assertEquals(DamasController.Phase.LOBBY, c.getPhase());
        assertEquals("B está ocupado no momento.", c.getNotice());
        assertEquals(DamasController.Phase.PLAYING, b.getPhase());
    }

    @Test
    void movesReachOpponentAndAlternateTurns() {
        startGame();
        DamasController white = whitePlayer();
        DamasController black = blackPlayer();
        assertTrue(white.isMyTurn());
        assertFalse(black.isMyTurn());
        assertFalse(black.play(black.getGame().getLegalMoves().get(0).path()));

        Move move = white.getGame().getLegalMoves().get(0);
        assertTrue(white.play(move.path()));
        deliver();
        assertEquals(move, black.getGame().getLastMove());
        assertTrue(black.isMyTurn());
    }

    @Test
    void duplicatedMoveIsAppliedOnce() {
        startGame();
        DamasController white = whitePlayer();
        DamasController black = blackPlayer();
        white.play(white.getGame().getLegalMoves().get(0).path());
        DamasMessage sent = wire.peek();
        deliver();
        black.receive(sent);
        deliver();
        assertTrue(black.isMyTurn());
        assertEquals(DamasController.Phase.PLAYING, black.getPhase());
    }

    @Test
    void wholeGameStaysInSyncOverLossyNetwork() {
        lossRate = 0.4;
        a.invite("b", "B");
        settle();
        b.acceptInvite();
        settle();
        assertEquals(DamasController.Phase.PLAYING, a.getPhase());

        Random choice = new Random(7);
        for (int ply = 0; ply < 400 && a.getPhase() == DamasController.Phase.PLAYING; ply++) {
            DamasController mover = a.isMyTurn() ? a : b;
            assertTrue(mover.isMyTurn(), "um dos jogadores deve ter a vez");
            List<Move> moves = mover.getGame().getLegalMoves();
            assertTrue(mover.play(moves.get(choice.nextInt(moves.size())).path()));
            settle();
            assertEquals(a.getGame().getLastMove(), b.getGame().getLastMove());
            assertEquals(a.getGame().getTurn(), b.getGame().getTurn());
            assertEquals(a.getGame().getLegalMoves(), b.getGame().getLegalMoves());
        }
        assertEquals(a.getPhase(), b.getPhase());
    }

    @Test
    void unansweredInviteTimesOut() {
        lossRate = 1;
        a.invite("b", "B");
        for (int i = 0; i <= DamasController.INVITE_MAX_ATTEMPTS; i++) {
            a.tick();
        }
        assertEquals(DamasController.Phase.LOBBY, a.getPhase());
        assertEquals("B não respondeu ao convite.", a.getNotice());
    }

    @Test
    void illegalMoveFromOpponentEndsGameWithoutApplyingIt() {
        startGame();
        DamasController white = whitePlayer();
        DamasController black = blackPlayer();
        String blackId = black == a ? "a" : "b";
        String whiteId = white == a ? "a" : "b";
        String gameId = gameIdOf(white, whiteId, blackId);
        black.receive(DamasMessage.move(gameId, whiteId, blackId, "X", 999, List.of(0, 63)));
        assertEquals(DamasController.Phase.FINISHED, black.getPhase());
        assertEquals(12, black.getGame().countPieces(Side.WHITE));
    }

    private String gameIdOf(DamasController white, String whiteId, String blackId) {
        white.play(white.getGame().getLegalMoves().get(0).path());
        DamasMessage sent = wire.poll();
        wire.clear();
        return sent.getGameId();
    }

    @Test
    void forgedMoveFromThirdPeerIsIgnored() {
        startGame();
        DamasController black = blackPlayer();
        String blackId = black == a ? "a" : "b";
        DamasController white = whitePlayer();
        white.play(white.getGame().getLegalMoves().get(0).path());
        DamasMessage real = wire.poll();
        wire.clear();
        black.receive(DamasMessage.move(real.getGameId(), "c", blackId, "C", 1000, real.getPath()));
        assertFalse(black.isMyTurn());
        assertEquals(DamasController.Phase.PLAYING, black.getPhase());
    }

    @Test
    void resignFinishesBothSides() {
        startGame();
        a.resign();
        deliver();
        assertEquals(DamasController.Phase.FINISHED, a.getPhase());
        assertEquals(DamasController.Phase.FINISHED, b.getPhase());
        assertEquals("A desistiu. Você venceu!", b.getNotice());
    }

    @Test
    void opponentLeavingMarksAwayAndReturningClearsIt() {
        a.onPeersChanged(Set.of("b"));
        startGame();
        a.onPeersChanged(Set.of());
        assertTrue(a.isOpponentAway());
        assertEquals(DamasController.Phase.PLAYING, a.getPhase());
        a.onPeersChanged(Set.of("b"));
        assertFalse(a.isOpponentAway());
    }

    @Test
    void refusedRematchLeavesNoStaleBoard() {
        startGame();
        a.resign();
        deliver();
        a.invite("b", "B");
        deliver();
        b.declineInvite();
        deliver();
        assertEquals(DamasController.Phase.LOBBY, a.getPhase());
        assertNull(a.getGame());
    }

    @Test
    void cancellingRematchStillDeliversLastMessageOfPreviousGame() {
        startGame();
        lossRate = 1;
        a.resign();
        deliver();
        assertEquals(DamasController.Phase.PLAYING, b.getPhase());
        a.invite("c", "C");
        a.cancelInvite();
        lossRate = 0;
        settle();
        assertEquals(DamasController.Phase.FINISHED, b.getPhase());
    }

    @Test
    void rematchWorksAfterGameEnds() {
        startGame();
        a.resign();
        deliver();
        assertTrue(a.invite("b", "B"));
        deliver();
        assertNotNull(b.getIncomingInvite());
        b.acceptInvite();
        deliver();
        assertEquals(DamasController.Phase.PLAYING, a.getPhase());
        assertEquals(DamasController.Phase.PLAYING, b.getPhase());
        assertEquals(12, a.getGame().countPieces(Side.WHITE));
    }
}
