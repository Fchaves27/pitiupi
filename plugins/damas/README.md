# Plugin Damas

Jogo de damas para dois peers da mesma sala, com regras brasileiras. Não exige nenhuma mudança no núcleo.

## Como jogar

1. Abra **Damas** na aba **Plugins**, escolha um adversário e clique em **Convidar**. Outra opção é clicar no nome do peer na lista lateral e usar **Damas → Convidar para uma partida**.
2. O convidado vê o convite na aba do plugin, que abre sozinha, e clica em **Aceitar** ou **Recusar**. Os lados são sorteados.
3. Na sua vez, as peças que podem jogar ficam contornadas em verde. Clique na peça e depois em cada casa de pouso, uma por vez nas capturas em sequência. Para refazer a escolha no meio de uma sequência, clique em qualquer outra casa.

Fechar a aba não encerra a partida: ela continua em andamento e a barra de status avisa quando for a sua vez. Para voltar, abra **Damas** de novo.

Os dois peers precisam ter a **mesma versão do JAR** e usar a **mesma porta** no login, inclusive no mesmo computador. Com portas diferentes, as instâncias não se enxergam.

## Regras

- Tabuleiro 8×8, 12 peças para cada lado, e as brancas começam.
- Pedras andam uma casa para a frente e capturam para a frente e para trás.
- Damas são voadoras: andam e capturam a qualquer distância na diagonal.
- A captura é obrigatória, e a lei da maioria exige a sequência com mais peças. Em caso de igualdade, a escolha é livre.
- Peças capturadas só saem no fim da sequência e não podem ser saltadas duas vezes.
- Pedra que passa pela última linha no meio de uma captura só vira dama se terminar nela.
- Perde quem fica sem peças ou sem lances.
- Empate após 20 lances de cada lado só com damas, sem captura nem movimento de pedra.

## Protocolo

**Teoria:** UDP não garante entrega nem ordem. Para ter confiabilidade sobre ele, a aplicação precisa confirmar o recebimento (ACK), retransmitir o que não foi confirmado e ignorar duplicatas.

**No Pitiupi:** toda comunicação é uma única `DamasMessage`, enviada por UDP multicast com `to` indicando o destinatário. O multicast foi escolhido porque é o caminho do heartbeat: se o peer aparece na lista, o canal funciona. TCP e unicast usam a porta local como destino e não alcançam outra instância no mesmo computador.

| Tipo | Quando |
| --- | --- |
| `INVITE` | Convite, com o lado sorteado de quem convida |
| `ACCEPT`, `DECLINE`, `CANCEL` | Resposta ao convite ou cancelamento |
| `BUSY` | Resposta automática de quem já está em partida ou com convite aberto |
| `MOVE` | Caminho completo do lance |
| `RESIGN` | Desistência ou encerramento |
| `ACK` | Confirmação de `gameId` + `seq` |

`DamasController` reenvia a cada 500 ms o que não foi confirmado e processa cada mensagem uma única vez. O convite é abandonado após 10 s sem ACK. O lance da partida em curso é reenviado até ser confirmado, porque o adversário pode voltar à rede. Cada peer valida o lance recebido com o próprio `DamasGame`.

**Limitação:** não há criptografia nem autenticação, e qualquer peer da sala pode ler as mensagens. As partidas não são salvas em disco e não existe proposta de empate. A regra oficial de empate em finais como duas damas contra uma não foi implementada.

## Build e testes

```bash
cd plugins/damas
ant jar    # requer pitiupi/dist/pitiupi.jar
ant test   # requer o JUnit em pitiupi/test/lib (ver README da pasta)
```

| Pacote | Conteúdo |
| --- | --- |
| `game` | Regras, sem rede nem Swing |
| `net` | `DamasMessage` |
| `control` | Sessão, convites e entrega confiável |
| `GUI` | Tabuleiro e painel |
| `plugin` | Integração com o núcleo (`DamasPlugin`) |
