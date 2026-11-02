# Testes e gate

## Gate automatizado (núcleo)

O módulo `pitiupi/` inclui testes JUnit:

```bash
cd pitiupi && ant test
```

Falha se `java`, `javac` ou Ant estiverem ausentes.

Pacotes com testes automatizados (JUnit 5): `platform` (`PitiupiHome`, `ConfigLoader`), `plugins.store`, `application.presence`, `application.peeractions`, `application.invites`, `application.messaging`, `application.activity`, `presentation.shell`, `control` (goodbye), `net.tcp` (fechamento de conexões), `models`.

## Gate de build (produto)

Na raiz do clone:

```bash
./scripts/pitiupi.sh build --all
```

Equivalente manual:

```bash
cd pitiupi && ant jar
cd plugins/chat && ant jar
cd plugins/screencast && ant jar
cd plugins/damas && ant jar
```

Depois, para runtime local:

```bash
./scripts/pitiupi.sh link --all
```

## Testes manuais — uma máquina (5000 / 5001)

- [ ] Duas instâncias com portas distintas; nomes diferentes
- [ ] Peers visíveis na lista em até ~15 s
- [ ] Chat: sala geral e conversa por IP; tags de transporte coloridas no histórico
- [ ] ScreenCast: abrir aba, destino sala ou peer; fechar aba com × interrompe compartilhamento
- [ ] Compose do chat sem barra de rolagem vazio em janela ~1024×768
- [ ] Encerrar uma instância remove o peer após timeout de heartbeat

## Testes manuais — dois PCs (mesma Wi‑Fi)

Mesma **porta** em ambos (ex. 5000), **mesmos JARs** em `~/pitiupi/plugins` em cada máquina:

- [ ] Presença e linha **Todos**
- [ ] Troca de mensagens no chat (multicast e, se possível, unicast/TCP)
- [ ] Compartilhamento de tela entre os dois (ambiente com captura de tela funcional — no WSL a captura nativa pode falhar)

## Melhorias futuras

Ver [engineering_backlog.md](engineering_backlog.md) (CI contínuo, mais testes de rede sem sockets reais).
