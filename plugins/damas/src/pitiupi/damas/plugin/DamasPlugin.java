package pitiupi.damas.plugin;

import pitiupi.api.plugin.PeerActionsRegistrar;
import pitiupi.api.plugin.PluginWorkspace;
import pitiupi.application.messaging.MessageDispatcher;
import pitiupi.application.messaging.SendTarget;
import pitiupi.application.messaging.TransportMode;
import pitiupi.application.peeractions.PeerAction;
import pitiupi.application.peeractions.PeerActionTarget;
import pitiupi.application.peeractions.PeerTarget;
import pitiupi.control.ConnectionManager;
import pitiupi.damas.GUI.DamasPanel;
import pitiupi.damas.control.DamasController;
import pitiupi.damas.net.DamasMessage;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;
import pitiupi.models.User;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DamasPlugin extends Plugin {

    private static final int RESEND_INTERVAL_MS = 500;

    private ConnectionManager connectionManager;
    private PluginWorkspace workspace;
    private DamasController controller;
    private DamasPanel panel;

    @Override
    public void createPlugin(ConnectionManager connectionManager, PluginWorkspace workspace) {
        this.connectionManager = connectionManager;
        this.workspace = workspace;
        this.controller = new DamasController(
                connectionManager.getHeartbeatManager().getInstanceId(),
                connectionManager::getUserName,
                this::send,
                new DamasController.Listener() {
                    @Override
                    public void onStateChanged() {
                        panel.refresh();
                        updateStatusBar();
                    }

                    @Override
                    public void onAttentionNeeded() {
                        openTab();
                    }
                }
        );
        this.panel = new DamasPanel(controller, connectionManager);
        bindShellWorkspaceContent(panel);
        installPeerActions(workspace.peerActions());

        connectionManager.addPeerListener(peers -> {
            Set<String> ids = peers.stream().map(User::getInstanceId).collect(Collectors.toSet());
            SwingUtilities.invokeLater(() -> {
                controller.onPeersChanged(ids);
                panel.refreshPeers();
            });
        });
        new Timer(RESEND_INTERVAL_MS, e -> controller.tick()).start();
    }

    @Override
    public void installPeerActions(PeerActionsRegistrar registrar) {
        registrar.register(PeerAction.of(
                "damas.open",
                "Abrir damas",
                PeerActionTarget.ALL_PEERS,
                target -> openTab()
        ));
        registrar.register(PeerAction.of(
                "damas.invite",
                "Convidar para uma partida",
                PeerActionTarget.SINGLE_PEER,
                this::inviteFromPeerList
        ));
    }

    private void inviteFromPeerList(PeerTarget target) {
        openTab();
        if (target instanceof PeerTarget.Single single) {
            controller.invite(single.peer().getInstanceId(), single.peer().getDisplayName());
        }
    }

    private void openTab() {
        workspace.openOrFocusTab(getIdentifier(), getDisplayName());
    }

    /** Com a aba fechada, a barra de status é o que avisa sobre convite e vez de jogar. */
    private void updateStatusBar() {
        String text = statusBarText();
        if (text != null) {
            workspace.setStatusMessage(text);
        }
    }

    private String statusBarText() {
        if (controller.getIncomingInvite() != null) {
            return "Damas: " + controller.getIncomingInvite().fromName() + " convidou você para uma partida";
        }
        return switch (controller.getPhase()) {
            case LOBBY -> null;
            case INVITE_SENT -> "Damas: aguardando resposta de " + controller.getOpponentName();
            case PLAYING -> controller.isMyTurn()
                    ? "Damas: sua vez contra " + controller.getOpponentName()
                    : "Damas: vez de " + controller.getOpponentName();
            case FINISHED -> "Damas: " + controller.getNotice();
        };
    }

    /**
     * Multicast é o único transporte que funciona sempre que o peer aparece na lista, pois é
     * o caminho do heartbeat. A entrega confiável fica a cargo do {@link DamasController}.
     */
    private void send(DamasMessage message) {
        MessageDispatcher.send(connectionManager, message, SendTarget.broadcastAll(), TransportMode.MULTICAST_UDP);
    }

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(DamasMessage.class);
    }

    @Override
    public void receiveMessage(Message message) {
        if (message instanceof DamasMessage damasMessage) {
            SwingUtilities.invokeLater(() -> controller.receive(damasMessage));
        }
    }

    @Override
    public String getName() {
        return "Damas";
    }

    @Override
    public String getDisplayName() {
        return "Damas";
    }

    @Override
    public String getAuthor() {
        return "Felipe Chaves";
    }

    @Override
    public String getVersion() {
        return "1";
    }

    @Override
    public String getIdentifier() {
        return "Pitiupi.Damas";
    }
}
