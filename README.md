# pitiupi

Ferramenta **P2P** para práticas de Redes de Computadores: núcleo em Java com arquitetura de **plugins** (conversa na rede e compartilhamento de tela).

**Documentação técnica:** [docs/README.md](docs/README.md) · **Quickstart:** [docs/documentation/00_quickstart.md](docs/documentation/00_quickstart.md) · **Convenções:** [docs/documentation/00_convencoes.md](docs/documentation/00_convencoes.md)

---

## Início rápido (um comando)

Na **raiz do repositório**:

```bash
chmod +x scripts/pitiupi.sh   # só na primeira vez
./scripts/pitiupi.sh run --all
```

Compila o núcleo, os plugins do [catálogo](plugins.registry.json), cria symlinks em **`PITIUPI_HOME/plugins`** (padrão `~/pitiupi/plugins`) e abre a aplicação.

1. Login: **nome** e **porta** (ex.: `5000`).
2. Abra plugins na aba **Plugins** (galeria) ou pelas ações na lista **Usuários nesta rede** (peer actions).
3. P2P no mesmo PC: segunda instância com **porta diferente** (ex.: `5001`).

```bash
./scripts/pitiupi.sh list      # ids, categorias, JAR buildado?
./scripts/pitiupi.sh check     # ferramentas + catálogo
```

---

## Catálogo de plugins (`plugins.registry.json`)

Novos módulos entram no repositório adicionando uma entrada no JSON (não é preciso editar o script):

| Campo | Significado |
| --- | --- |
| `id` | Usado em `--plugins chat,meuJogo` |
| `dir` | Pasta do módulo (ex.: `plugins/xadrez`) |
| `build` | `ant` ou `make` |
| `jar` | Caminho do JAR relativo a `dir` |
| `menuCategory` | `Social`, `Jogos` ou `Streaming` — filtro CLI e documentação |

Plugins de referência hoje:

| id | Uso didático | Pasta |
| --- | --- | --- |
| `chat` | Sala geral, peer por IP, TCP privado | `plugins/chat` |
| `screencast` | Multicast ou unicast de tela | `plugins/screencast` |
| `damas` | Jogo para dois peers com entrega confiável sobre UDP multicast | `plugins/damas` |

Na interface, o texto das abas vem de `getDisplayName()` em cada plugin. O campo `menuCategory` no JSON alinha build/link com a documentação.

```bash
./scripts/pitiupi.sh run --all
./scripts/pitiupi.sh run --category Social
./scripts/pitiupi.sh run --plugins chat,screencast
./scripts/pitiupi.sh discover   # pastas em plugins/ com SPI ainda não no catálogo
```

---

## Novo plugin (resumo)

1. Copiar estrutura de `plugins/chat` → `plugins/meuPlugin` ([tutorial](docs/documentation/10_tutorial_plugin.md)).
2. Em `createPlugin`, registrar painel com `PluginWorkspace` / `bindShellWorkspaceContent` e, se fizer sentido, **peer actions** na lista lateral.
3. Adicionar entrada em [`plugins.registry.json`](plugins.registry.json).
4. `./scripts/pitiupi.sh link --all` e reiniciar o app.

O núcleo carrega **todos os `*.jar`** em `PITIUPI_HOME/plugins`. Detalhes: [pitiupi/plugins/README.md](pitiupi/plugins/README.md).

---

## Pré-requisitos

| Ferramenta | Uso |
| --- | --- |
| **JDK** com `javac` | Compilação **Java 17+** (núcleo e plugins usam bytecode 17) |
| **Apache Ant** | Módulos NetBeans |
| **python3** | Script `scripts/pitiupi.sh` (ler o catálogo) |

```bash
sudo apt install openjdk-21-jdk ant
./scripts/pitiupi.sh check
cd pitiupi && ant test    # testes JUnit do núcleo
```

**GUI:** Swing — WSLg / `DISPLAY=:0`.

---

## Comandos do script

| Comando | Ação |
| --- | --- |
| `run` | Build + link + app |
| `build` | Build + link |
| `link` | Só symlinks (JARs já existem) |
| `list` | Catálogo e status do JAR |
| `discover` | Sugestão de módulos com SPI |
| `check` | PATH + JSON válido |

Opções: `--all` (padrão), `--plugins ids`, `--category Social|Jogos|Streaming`.

---

## Teste em rede

- **Mesmo PC:** duas instâncias, portas distintas (ex. 5000 e 5001).
- **Dois PCs na mesma Wi‑Fi:** mesma porta (ex. 5000), nomes diferentes, mesmos JARs em `~/pitiupi/plugins` em cada máquina.

Ver [docs/documentation/15_testes_e_gate.md](docs/documentation/15_testes_e_gate.md) e [appendix_portas_e_multicast.md](docs/documentation/appendix_portas_e_multicast.md).

---

## Origem

Projeto base UFSJ (Redes). GitHub: [GabrielFSSantos/pitiupi](https://github.com/GabrielFSSantos/pitiupi).
