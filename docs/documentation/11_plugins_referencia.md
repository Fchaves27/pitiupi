# Plugins de referência

Três módulos buildáveis no catálogo; o chat unifica o antigo mpChat (multicast, unicast, TCP e canal TCP privado).

## Comparativo

| | plugins/chat | plugins/screencast |
| --- | --- | --- |
| Build | Ant | Ant |
| id | `chat` | `screencast` |
| Classe | `ChatPlugin` | `ScreenCastPlugin` |
| Mensagem | `ChatMessage`, `LegacyMpChatMessage` | `ScreenCastMessage` |
| UI | `ChatPanel` (destino + transporte) | `ScreenCastPanel` (destino + transporte) |
| TCP privado | Toggle no `ChatPanel` + hooks `receiveBytes` (histórico `private:<IP>`) | — |

## plugins/chat

Fluxo resumido:

1. `ChatPlugin.createPlugin` registra `ChatPanel` e peer actions (sala, peer, TCP privado).
2. Envio: usuário escolhe destino e `TransportMode` → `MessageDispatcher` → UDP ou TCP do núcleo.
3. Recepção: `ChatMessage` em `receiveMessage` → histórico na aba correta (sala, IP ou canal privado).
4. TCP privado: toggle desliga o combo de transporte e usa bytes dedicados ao plugin.

Código de entrada: `plugins/chat/src/pitiupi/chat/plugin/ChatPlugin.java`.

```mermaid
flowchart LR
    UI[ChatPanel] --> Disp[MessageDispatcher]
    Disp --> CM[ConnectionManager]
    CM --> Net[UDP ou TCP]
    Net --> CM2[ConnectionManager peer]
    CM2 --> CP[ChatPlugin.receiveMessage]
    CP --> UI2[ChatPanel peer]
```

## plugins/screencast

Fluxo resumido:

1. Captura de tela em loop (com throttling) → `ScreenCastMessage` com imagem serializada.
2. Destino geral: multicast; um peer: unicast/TCP conforme painel.
3. `SessionNoticeMessage` (núcleo) marca início/fim de sessão para fechar abas de recepção.

Código de entrada: `plugins/screencast/src/pitiupi/screenCast/plugin/ScreenCastPlugin.java`.

**Limitação:** largura de banda e MTU — frames grandes podem sofrer perda em UDP; adequado para demonstração, não para produção.

## plugins/damas

Jogo de damas para dois peers, com regras brasileiras. Detalhes de uso e protocolo: [plugins/damas/README.md](../../plugins/damas/README.md).

1. Convite pela aba do plugin ou pela peer action na lista lateral.
2. Mensagens em UDP multicast, com destinatário explícito; `DamasController` faz ACK, retransmissão e descarte de duplicatas.
3. Cada peer valida o lance recebido com o próprio `DamasGame` antes de aplicá-lo.

Código de entrada: `plugins/damas/src/pitiupi/damas/plugin/DamasPlugin.java`.

## Limitações (não são bugs)

Os plugins de referência são **exemplos de demonstração**, não produtos completos:

| Plugin | Inclui | Não inclui (de propósito) |
| --- | --- | --- |
| **chat** | texto, sala geral, unicast/multicast/TCP, TCP privado básico | imagens, moderação, histórico persistente, criptografia |
| **screencast** | stream read-only, avisos de sessão | controle remoto do mouse/teclado, áudio |

Para evoluir: copie o padrão (`Plugin` + mensagens + painel na aba) e acrescente features no seu JAR — sem inflar o núcleo. Ver [10_tutorial_plugin.md](10_tutorial_plugin.md).
