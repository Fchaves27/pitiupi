package pitiupi.damas.GUI;

import pitiupi.application.presence.UserPeerView;
import pitiupi.control.ConnectionManager;
import pitiupi.damas.control.DamasController;
import pitiupi.damas.game.DamasGame;
import pitiupi.damas.game.Side;
import pitiupi.presentation.shell.ui.ShellUi;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicTextAreaUI;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;

/**
 * Aba do plugin: tabuleiro à esquerda e, à direita, as ações da fase atual da sessão.
 */
public final class DamasPanel extends JPanel {

    private static final String CARD_LOBBY = "lobby";
    private static final String CARD_INVITE_SENT = "inviteSent";
    private static final String CARD_INVITE_RECEIVED = "inviteReceived";
    private static final String CARD_PLAYING = "playing";
    private static final String CARD_FINISHED = "finished";

    private final DamasController controller;
    private final ConnectionManager connectionManager;
    private final BoardView boardView;

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final JTextArea noticeText = wrappedText();
    private final DefaultListModel<PeerItem> peerModel = new DefaultListModel<>();
    private final JList<PeerItem> peerList = new JList<>(peerModel);
    private final JButton inviteButton = new JButton("Convidar");
    private final JTextArea inviteSentText = wrappedText();
    private final JTextArea inviteReceivedText = wrappedText();
    private final JTextArea playingText = wrappedText();
    private final JTextArea awayText = wrappedText();
    private final JButton resignButton = new JButton("Desistir");
    private final JTextArea finishedText = wrappedText();

    public DamasPanel(DamasController controller, ConnectionManager connectionManager) {
        this.controller = controller;
        this.connectionManager = connectionManager;
        this.boardView = new BoardView(controller::play);

        setLayout(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(boardView, BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);

        refreshPeers();
        refresh();
    }

    private JComponent buildSidePanel() {
        cardHost.add(buildLobbyCard(), CARD_LOBBY);
        cardHost.add(buildInviteSentCard(), CARD_INVITE_SENT);
        cardHost.add(buildInviteReceivedCard(), CARD_INVITE_RECEIVED);
        cardHost.add(buildPlayingCard(), CARD_PLAYING);
        cardHost.add(buildFinishedCard(), CARD_FINISHED);

        JPanel side = new JPanel(new BorderLayout(0, 8));
        side.setPreferredSize(new Dimension(220, 0));
        side.add(cardHost, BorderLayout.CENTER);
        side.add(noticeText, BorderLayout.SOUTH);
        return side;
    }

    private JComponent buildLobbyCard() {
        peerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        peerList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                // Nomes vêm da rede: não deixar o Swing interpretar HTML.
                label.putClientProperty("html.disable", Boolean.TRUE);
                return label;
            }
        });
        peerList.addListSelectionListener(e -> updateInviteButton());
        inviteButton.addActionListener(e -> {
            PeerItem selected = peerList.getSelectedValue();
            if (selected != null) {
                controller.invite(selected.id(), selected.name());
            }
        });

        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.add(titleLabel("Escolha um adversário"), BorderLayout.NORTH);
        card.add(new JScrollPane(peerList), BorderLayout.CENTER);
        card.add(inviteButton, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildInviteSentCard() {
        return stackedCard("Convite enviado", inviteSentText, button("Cancelar", controller::cancelInvite));
    }

    private JComponent buildInviteReceivedCard() {
        return stackedCard(
                "Convite recebido",
                inviteReceivedText,
                button("Aceitar", controller::acceptInvite),
                button("Recusar", controller::declineInvite)
        );
    }

    private JComponent buildPlayingCard() {
        resignButton.addActionListener(e -> confirmResign());
        return stackedCard("Partida", playingText, awayText, resignButton);
    }

    private JComponent buildFinishedCard() {
        return stackedCard(
                "Fim de partida",
                finishedText,
                button("Jogar de novo", this::rematch),
                button("Escolher outro adversário", controller::backToLobby)
        );
    }

    private void confirmResign() {
        if (controller.isOpponentAway()) {
            controller.resign();
            return;
        }
        int answer = JOptionPane.showConfirmDialog(
                this,
                "Desistir desta partida?",
                "Damas",
                JOptionPane.YES_NO_OPTION
        );
        if (answer == JOptionPane.YES_OPTION) {
            controller.resign();
        }
    }

    private void rematch() {
        controller.invite(controller.getOpponentId(), controller.getOpponentName());
    }

    /** Sincroniza a tela com o estado do controller. */
    public void refresh() {
        DamasController.Phase phase = controller.getPhase();
        DamasGame game = controller.getGame();
        boardView.setState(game, controller.getMySide(), controller.isMyTurn());

        DamasController.IncomingInvite invite = controller.getIncomingInvite();
        if (invite != null) {
            inviteReceivedText.setText(invite.fromName() + " convidou você para uma partida. "
                    + "Você jogaria de " + invite.inviterSide().opposite().getLabel() + ".");
            cards.show(cardHost, CARD_INVITE_RECEIVED);
        } else {
            switch (phase) {
                case LOBBY -> cards.show(cardHost, CARD_LOBBY);
                case INVITE_SENT -> {
                    inviteSentText.setText("Aguardando resposta de " + controller.getOpponentName() + "…");
                    cards.show(cardHost, CARD_INVITE_SENT);
                }
                case PLAYING -> {
                    updatePlayingCard(game);
                    cards.show(cardHost, CARD_PLAYING);
                }
                case FINISHED -> {
                    finishedText.setText(controller.getNotice());
                    cards.show(cardHost, CARD_FINISHED);
                }
            }
        }
        boolean showNotice = phase != DamasController.Phase.FINISHED && !controller.getNotice().isEmpty();
        noticeText.setText(showNotice ? controller.getNotice() : "");
        noticeText.setVisible(showNotice);
        updateInviteButton();
    }

    private void updatePlayingCard(DamasGame game) {
        Side me = controller.getMySide();
        Side other = me.opposite();
        String turnLine = controller.isMyTurn() ? "Sua vez." : "Vez de " + controller.getOpponentName() + ".";
        String captureLine = controller.isMyTurn() && !game.getLegalMoves().get(0).captured().isEmpty()
                ? "\nCaptura obrigatória."
                : "";
        playingText.setText(
                "Você: " + me.getLabel() + " (" + game.countPieces(me) + " peças)\n"
                        + controller.getOpponentName() + ": " + other.getLabel()
                        + " (" + game.countPieces(other) + " peças)\n\n"
                        + turnLine + captureLine
        );
        boolean away = controller.isOpponentAway();
        awayText.setText(away
                ? controller.getOpponentName() + " saiu da rede. A partida continua se voltar."
                : "");
        awayText.setVisible(away);
        resignButton.setText(away ? "Encerrar partida" : "Desistir");
    }

    public void refreshPeers() {
        PeerItem selected = peerList.getSelectedValue();
        List<UserPeerView> views = connectionManager.getPresenceService().listPeerViews();
        peerModel.clear();
        for (UserPeerView view : views) {
            if (!view.isBroadcastTarget() && !view.isSelf()) {
                peerModel.addElement(new PeerItem(view.getInstanceId(), view.getDisplayName()));
            }
        }
        if (selected != null && peerModel.contains(selected)) {
            peerList.setSelectedValue(selected, false);
        }
        updateInviteButton();
    }

    private void updateInviteButton() {
        inviteButton.setEnabled(peerList.getSelectedValue() != null && controller.canInvite());
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        return button;
    }

    private static JLabel titleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static JPanel stackedCard(String title, JComponent... rows) {
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        JLabel titleLabel = titleLabel(title);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(titleLabel);
        for (JComponent row : rows) {
            stack.add(Box.createVerticalStrut(8));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (row instanceof JButton) {
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            }
            stack.add(row);
        }
        JPanel card = new JPanel(new BorderLayout());
        card.add(stack, BorderLayout.NORTH);
        return card;
    }

    /** Texto com quebra de linha sem HTML, pois inclui nomes vindos da rede. */
    private static JTextArea wrappedText() {
        JTextArea area = new JTextArea();
        // O Nimbus pinta fundo e borda de campo de texto mesmo com setOpaque(false).
        area.setUI(new BasicTextAreaUI());
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(null);
        area.setForeground(UIManager.getColor("Label.foreground"));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(UIManager.getFont("Label.font"));
        return area;
    }

    private record PeerItem(String id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
