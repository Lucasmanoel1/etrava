# SPEC — ETRAVA (especificação técnica do MVP)

**Versão:** 0.1 · **Data:** 2026-11-09 · **Status:** aguardando aprovação do diretor
**Base:** `docs/PRD.md` v0.1 (seção 20.1), `docs/ponto-seguro.md`, `docs/lore-etrava.md`
**Escopo:** só o MVP (Origem I + Capítulo I + Pedra-Mansa). Itens [PÓS]/[LP] aparecem apenas como "não fechar a porta".

Cada escolha desta SPEC está registrada em `DECISIONS.md` (D-xxx). Versões exatas de bibliotecas são fixadas e testadas no M0, como o PRD pede (RNF-002) — aqui ficam os nomes, não os números.

---

## 1. Visão geral da arquitetura

```
┌──────────────────────────── game-client (libGDX / LWJGL3) ────────────────────────────┐
│  render 3D · câmera · entrada · UI (scene2d) · áudio · assets + placeholders · debug   │
└───────────────▲──────────────────────────────────────────────┬────────────────────────┘
                │ lê estado (somente leitura) + SimEvents       │ envia Commands
┌───────────────┴──────────────────────────────────────────────▼────────────────────────┐
│                         core-sim (Java puro, headless)                                 │
│  tempo · RNG · estado do mundo · NPCs · fatos/rumores · diálogos · arcos · economia ·  │
│  combate · navegação · save/load · carregador de conteúdo · validador                  │
└───────────────▲──────────────────────────────────────────────▲────────────────────────┘
                │                                               │
        content/ (YAML + mapas .tmj)                      tools (CLI)
        assets/  (GLB, PNG, OGG + manifesto)              validar · simular headless · licenças
```

Três regras que sustentam tudo:

1. **A simulação não sabe que existe tela.** `core-sim` não importa nada de libGDX. Toda regra de jogo roda e é testada sem janela (RNF-010).
2. **Definição ≠ estado.** *Definições* vêm do conteúdo e são imutáveis (o que é Avel, o que é o arco A1). *Estado* é o que muda (onde Avel está, em que estágio A1 está). **O save guarda só estado.**
3. **Um vocabulário único de condições e efeitos** serve diálogos, arcos, conversas ambientes, agendas e epílogo. Uma história nova = dados novos, não código novo (P6).

### 1.1 Stack

| Necessidade | Escolha | Observação |
|---|---|---|
| Linguagem / build | Java 21 (toolchain), Gradle com wrapper | D-001 |
| Framework | libGDX, backend LWJGL3 | DP-14 do PRD |
| Modelos, animação, PBR, luz, névoa | extensão **gdx-gltf** | glTF/GLB (RF-422) |
| UI | scene2d.ui + gdx-freetype | já vem com o libGDX |
| Áudio | áudio do libGDX (OpenAL), OGG | posicional = volume/pan por distância |
| Dados (conteúdo e save) | Jackson (YAML para conteúdo, JSON para save) | D-005 |
| Mapas | Tiled, exportado em JSON (`.tmj`) | DP-03, D-010 |
| Log | SLF4J + Logback | arquivo na pasta do usuário |
| Testes | JUnit 5 + JaCoCo (cobertura ≥ 70% no `core-sim`) | RNF-030 |
| Distribuição | `jlink` + `jpackage` (app-image) | D-014 |

Nada de ECS, scripting embarcado ou rede (RNF-014).

---

## 2. Módulos e estrutura do repositório

```
ETRAVA/
├─ core-sim/            Java puro. Depende só de Jackson e slf4j-api.
├─ game-client/         libGDX. Depende de core-sim.
├─ tools/               CLI de desenvolvimento. Depende de core-sim.
├─ content/             Dados do jogo (YAML, mapas .tmj). Sem código.
├─ assets/              Arquivos de arte/áudio + manifestos.
├─ docs/                PRD, SPEC, ponto-seguro, lore.
├─ PROGRESS.md  DECISIONS.md  CLAUDE.md
└─ (ao longo do MVP) STYLE.md  WRITING.md  CONTENT.md  ASSETS_LICENSES.md
```

Dependências: `game-client → core-sim` e `tools → core-sim`. Nunca o contrário; sem ciclos (RNF-012).

### 2.1 Pacotes do `core-sim` (`etrava.sim.*`)

| Pacote | Responsabilidade |
|---|---|
| `time` | relógio em ticks, calendário, agenda de eventos futuros |
| `rng` | gerador aleatório único, com semente, serializável |
| `world` | `World` (raiz do estado), flags/variáveis, estado de áreas e props, crônica |
| `content` | definições imutáveis (`ContentDb`) e carregador YAML/`.tmj` |
| `rules` | condições e efeitos (vocabulário único) |
| `npc` | agendas, memória, opinião, percepção/testemunho |
| `knowledge` | fatos, conhecimento, caderno, propagação de rumores |
| `dialogue` | execução de diálogos e conversas ambientes |
| `arc` | motor de arcos (máquinas de estado), capítulos, epílogo |
| `economy` | preços, estoques, comércio, trabalhos |
| `actor` | jogador, habilidades, inventário, equipamento |
| `combat` | combate, derrota não letal, morte |
| `nav` | grade de navegação e A* |
| `save` | serialização, versão de esquema, migrações, hash de estado |
| `validation` | validador de conteúdo |
| `api` | `Simulation` (fachada), `Command`, `SimEvent`, políticas de jogador headless |

### 2.2 Pacotes do `game-client` (`etrava.client.*`)

`app` (boot, laço, telas) · `render` (cena, luz dia/noite, pós-processamento, oclusão, variantes de props) · `camera` · `input` · `ui` (HUD, diálogo, caderno, mapa, inventário, comércio, opções, save/load, epílogo) · `audio` · `assets` (resolução por ID, placeholders) · `debug` (console/painel, só em build de desenvolvimento).

### 2.3 `tools` — comandos

| Comando Gradle | Faz |
|---|---|
| `gradlew run` | roda o jogo em modo desenvolvimento |
| `gradlew test` | testes + cobertura |
| `gradlew validateContent` | validador de conteúdo; **falha quebra o build** (RF-442) |
| `gradlew simulate --args="--dias 30 --semente 42 --politica parado"` | simulação headless + relatório (RF-441) |
| `gradlew licenses` | gera `ASSETS_LICENSES.md` a partir dos manifestos |
| `gradlew packageWin` | gera a pasta com `Etrava.exe` e runtime embutido |

---

## 3. Núcleo de simulação

### 3.1 Tempo

- A unidade é o **tick**. O jogo roda a simulação em **passo fixo de 30 ticks por segundo real**.
- 1 dia de jogo = 20 min reais = **36.000 ticks** (`ticksPerDay`, configurável e gravado no save — RF-320). 1 hora de jogo = 1.500 ticks; 1 minuto = 25 ticks.
- O calendário do MVP é só "dia N, HH:MM" (DP-12). O jogo começa no dia 1, 06:00.
- A simulação **nunca lê o relógio do sistema** (RNF-013). O cliente acumula o tempo real e chama `step()` quantas vezes couber; a posição desenhada é interpolada entre dois ticks.
- O relógio para em menus, inventário e diálogos iniciados pelo jogador (RF-324, DP-09): o cliente simplesmente não chama `step()`.

### 3.2 Duas escalas de processamento (P5)

| Escala | Quando roda | O que faz |
|---|---|---|
| **Detalhada** | todo tick, só na área carregada | movimento, navegação, combate, percepção/testemunho, conversas ambientes, gatilhos de área |
| **Por bloco** | a cada hora de jogo, para o mundo todo | agendas dos NPCs fora da área, troca de rumores, avaliação dos arcos; uma vez por dia: economia e reabastecimento |
| **Agendada** | no tick exato marcado | efeitos tardios (RF-162), prazos de arcos, eventos externos |

**Avanço rápido** (esperar, dormir, simulação headless): não roda ticks detalhados. Pula de fronteira em fronteira — próxima hora cheia ou próximo evento agendado, o que vier primeiro — processando tudo **na ordem** `(tick, sequência de inserção)`. Um evento marcado para 03:00 dispara mesmo que o jogador durma a noite inteira (AT-07). Um efeito marcado como `interrupts` encerra o avanço rápido naquele ponto (RF-323).

### 3.3 Determinismo (RF-144, AT-03)

- **Um único RNG** (`SplitMix64`, estado = um `long`), gravado no save. Efeitos visuais usam outro gerador, do cliente.
- Coleções percorridas pela simulação têm ordem estável (ordenadas por ID ou por inserção).
- Toda entrada do jogador é um `Command` com o tick em que foi aplicado. Gravar a lista de comandos + semente permite reexecutar a partida e comparar a crônica.

### 3.4 Fachada `Simulation`

```java
Simulation sim = Simulation.newGame(contentDb, seed, originId, options);
sim.submit(Command);            // MoveTo, Interact, Attack, ChooseDialogueOption, Wait, Sleep, Trade, ...
List<SimEvent> ev = sim.step(); // avança 1 tick e devolve o que a apresentação precisa saber
sim.fastForward(ticks);         // esperar / dormir / headless
WorldView v = sim.view();       // leitura somente
```

`SimEvent` é o canal de mão única para a apresentação: "fala ambiente X dita por Y", "fato novo no caderno", "prop mudou de variante", "som em tal posição", "diálogo aberto". O cliente nunca altera o estado direto.

### 3.5 Estado do mundo (`World`)

Raiz única, serializável:

```
World
├─ meta          semente, estado do RNG, tick atual, ticksPerDay, ritmo de eventos, capítulo
├─ flags / vars  mapa nome → booleano / número
├─ player        área, posição, vida, fôlego, habilidades, inventário, equipamento, dinheiro, conhecimento
├─ npcs[id]      status, área/ponto, posição, atividade, opiniões, memória, conhecimento,
│                inventário, sobreposições de agenda
├─ arcs[id]      estágio atual, tick de entrada, variáveis locais, desfecho
├─ areas[id]     estado dos props (variantes), contêineres, itens no chão, descoberta
├─ economy       modificadores de preço por região × categoria, estoque e caixa dos comerciantes
├─ factions      relações numéricas entre facções
├─ deeds         atos testemunhados (roubo, agressão, ajuda…) — viram rumor
├─ scheduled     fila de efeitos futuros (tick, seq, efeitos)
└─ chronicle     registro interno de tudo que aconteceu (RF-143)
```

---

## 4. Conteúdo: formatos de dados

Todo conteúdo fica em `content/`, em **YAML** (aceita comentário e texto de várias linhas, o que importa para quem escreve diálogo). **Convenção:** chaves do esquema em inglês (espelham o código); IDs, nomes e textos em português, `snake_case`, sem acento nos IDs.

```
content/
├─ world/        civilizations.yaml  factions.yaml  regions.yaml  locations.yaml
├─ areas/        pedra_mansa.tmj  taverna.tmj  ...  + areas.yaml (metadados)
├─ props.yaml    items.yaml  facts.yaml  phenomena.yaml  enemies.yaml  jobs.yaml  merchants.yaml
├─ npcs/         avel.yaml  nalia.yaml  ...
├─ dialogues/    avel.yaml  ...
├─ ambient/      taverna.yaml  ...           (conversas ambientes)
├─ arcs/         a1_nalia.yaml  a2_avel.yaml  a3_estrutura.yaml  h1…h5  e1…e5
├─ documents/    carta_avel.yaml  ...
├─ chapters.yaml origins/pedra_mansa.yaml  epilogues/cap1.yaml
├─ glossario.yaml                            (nomes provisórios — PRD 20.3)
└─ i18n/ui_pt_BR.properties                  (textos de interface)
```

Todo texto exibido vem desses arquivos (RF-382). Cada texto tem um endereço estável (`dialogues/avel#n12`), o que permite tradução futura por arquivo paralelo sem tocar no original.

### 4.1 Condições e efeitos (vocabulário único)

**Condições** — combináveis com `all`, `any`, `not`:

| Condição | Exemplo |
|---|---|
| `flag` / `var` | `{flag: avel_recebeu_carta}` · `{var: voluntarios, min: 3}` |
| `knows` | `{knows: {who: player, fact: custo_pode_ser_dividido}}` |
| `opinion` | `{opinion: {npc: avel, min: 40}}` |
| `skill` | `{skill: {name: labia, min: 30}}` |
| `time` | `{time: {dayMin: 8, hourFrom: 18, hourTo: 23}}` |
| `has` | `{has: {item: carta_iria}}` · `{has: {money: 20}}` |
| `arc` | `{arc: {id: a1_nalia, stage: vozes}}` |
| `npc` | `{npc: {id: moleiro, status: present}}` · `{npc: {id: nalia, at: moinho}}` |
| `prop` | `{prop: {key: arvore_nalia, state: viva}}` |
| `chapter` · `reputation` · `chance` | `{chance: 0.3}` usa o RNG da simulação |

**Efeitos:**

| Efeito | Faz |
|---|---|
| `setFlag` / `setVar` / `addVar` | altera estado global |
| `learn` | adiciona fato ao conhecimento de alguém (`who`, `fact`, `version`, `certainty`) |
| `opinion` | altera opinião de um NPC |
| `give` / `take` | item ou dinheiro |
| `arc` | avança ou muda o estágio de um arco |
| `setProp` | troca a variante de um prop (árvore `viva → morta`) |
| `npcStatus` | `present`, `away`, `departed`, `dead` |
| `override` | sobrepõe a agenda de um NPC com prioridade e prazo |
| `approach` | NPC vai até o jogador e inicia conversa (RF-108) |
| `priceMod` | altera modificador de preço de uma categoria/região |
| `faction` | altera relação entre facções |
| `schedule` | agenda efeitos para daqui a X horas/dias (RF-162) |
| `startCombat` · `startTrade` · `startJob` · `endDialogue` | abrem outros modos |
| `chronicle` | escreve na crônica |

Condições e efeitos são **dados estruturados**, não uma linguagem de script. É isso que permite ao validador analisar o conteúdo sem executá-lo. Tipo novo só entra quando uma história realmente não couber nos existentes (PRD 20.2).

### 4.2 Fatos e conhecimento

```yaml
# facts.yaml
- id: nalia_restaurou_arvore
  layer: V0                 # V0..V3 (PRD 8.2)
  truth: true               # oculto do jogador: true | false | partial | unknown
  tags: [nalia, arvore, sobrenatural]
  spread: local             # none | local | wide — o quanto vira rumor
  versions:
    - id: base
      text: "A filha do moleiro fez a árvore velha brotar de novo."
    - id: exagero
      text: "A menina do moinho levantou uma árvore morta só de encostar nela."
    - id: ceticismo
      text: "Dizem que a árvore do moinho voltou. Deve ter sido a chuva."
  interpretations:          # RF-064: como cada grupo conta
    comitiva_caldris: ceticismo
```

Uma **entrada de conhecimento** (de jogador ou NPC) guarda: fato, versão, **fonte**, dia, local e **grau de certeza** — `rumor` (ouvi dizer), `claim` (alguém me afirmou), `witnessed` (vi), `read` (li), `verified` (conferi). O caderno é uma vista do conhecimento do jogador, e o grau de certeza decide a aba e a redação: "Fulano disse que…", nunca "É verdade que…" sem `verified` (RF-104, RF-121). O caderno não tem campo de objetivo.

**Atos do jogador** (roubo, agressão, ajuda) geram registros `deed` quando testemunhados e circulam como rumor. Quem não ficou sabendo não muda de atitude (RF-281). A reputação numa facção é derivada das opiniões dos membros, não um número onisciente (RF-280).

**Propagação de rumores (RF-122):** a cada hora de jogo, NPCs que estão no mesmo local trocam até N fatos com `spread ≠ none`. A chance depende da relação entre eles, e há uma chance pequena de o fato passar numa versão distorcida. Notícias externas entram pelos NPCs viajantes e do Posto, já na versão da facção deles (RF-125).

### 4.3 NPCs

```yaml
# npcs/avel.yaml
id: avel
name: Avel
layer: A                    # A personagem | B morador | C figurante
faction: pedra_mansa
home: pedra_mansa/casa_avel
portrait: retrato.avel
model: personagem.avel
dialogue: avel
relations: [{npc: moleiro, kind: amizade}]
opinions: {player: 60}
knows: [mir_foi_acidente, avel_nao_e_tio]
schedule:
  - {from: "05:30", to: "12:00", do: trabalhar, at: pedra_mansa/oficina_bancada}
  - {from: "12:00", to: "13:00", do: comer,     at: pedra_mansa/casa_avel_mesa}
  - {from: "13:00", to: "18:30", do: trabalhar, at: pedra_mansa/oficina_bancada}
  - {from: "18:30", to: "21:00", do: taverna,   at: taverna/mesa_fundo, when: {chance: 0.6}}
  - {from: "21:00", to: "05:30", do: dormir,    at: pedra_mansa/casa_avel_cama}
topics:                     # "perguntar sobre" um fato do caderno (RF-126)
  mir: {goto: desconversa_mir}
```

- **Agenda:** blocos de horário apontando para pontos nomeados dos mapas. Na área carregada o NPC anda até lá; fora dela, é posicionado direto a cada bloco (RF-081/082). Arcos sobrepõem a agenda com `override` (RF-083).
- **Memória:** lista circular com limite configurável (RF-084).
- **Ausência persistente:** `departed`/`dead` tira o NPC das agendas, e as conversas ambientes condicionadas a isso passam a comentar (RF-087).
- **Camada C** não tem arquivo próprio: sai de modelos em `npcs/_figurantes.yaml`.

### 4.4 Diálogos

Formato próprio em YAML (D-006). Um arquivo por NPC ou por situação.

```yaml
# dialogues/avel.yaml
id: avel
start:                       # primeira entrada cuja condição passa
  - {when: {flag: avel_recebeu_carta}, goto: depois_da_carta}
  - {goto: oficina}
nodes:
  oficina:
    speaker: avel
    text: "A dobradiça do moleiro ficou torta. De novo. Você mediu ou adivinhou?"
    choices:
      - text: "Medi."
        goto: mediu
      - text: "[Observação] Essa régua não é a nossa."
        skill: {name: observacao, min: 25}      # mostra a habilidade, nunca a consequência (RF-103)
        effects: [{learn: {who: player, fact: regua_tem_marca_estranha, certainty: witnessed}}]
        goto: regua
      - text: "Quem era o homem que passou aqui ontem?"
        when: {knows: {who: player, fact: estranho_procura_mir}}
        relevant: true                          # RF-161
        channels: [dialogue, npc]
        effects: [{opinion: {npc: avel, add: -5}}, {setFlag: avel_sabe_que_jogador_viu}]
        goto: desconversa_mir
```

- Nó: `speaker`, `text`, `effects` (ao entrar), `choices` ou `goto`. Sem `choices` e sem `goto`, a conversa acaba.
- O jogador pode sair a qualquer momento; um nó pode declarar `onLeave` com efeitos (RF-107).
- **Conversas ambientes** (`ambient/*.yaml`): lista curta de falas entre 2–3 NPCs, com `when`, local, e `facts` que o jogador aprende **se estiver no raio de escuta** — que cresce com Observação (RF-088, RF-184). Não pausam o jogo.

### 4.5 Arcos de mundo

Máquinas de estado em dados (RF-140). **Os eventos externos E1–E5 usam o mesmo motor** (D-009): são arcos que acontecem fora de tela e só emitem fatos.

```yaml
# arcs/a1_nalia.yaml
id: a1_nalia
start: {stage: antes, at: {day: 1}}
stages:
  antes:
    timeout: {after: {days: 1, jitterHours: 12}, to: restauracao}
  restauracao:
    onEnter:
      - {setProp: {key: arvore_nalia, state: viva}}
      - {learn: {who: moleiro, fact: nalia_restaurou_arvore, certainty: witnessed}}
    timeout: {after: {days: 2}, to: insonia}
  # ... insonia → vozes → impasse ...
  impasse:
    transitions:                               # caminhos abertos por ações (do jogador ou do mundo)
      - {when: {var: voluntarios, min: 3}, to: custo_dividido}
      - {when: {flag: ligacao_cortada},    to: arvore_morre}
    timeout:                                   # desfecho padrão, sem o jogador (RF-141)
      after: {days: 8}
      choose:
        - {when: {flag: estudioso_passou_pelo_posto}, to: estabilizada}
        - {weight: 2, to: pai_corta_arvore}
        - {weight: 1, to: nalia_piora}
  custo_dividido: {terminal: true, outcome: leve}
  estabilizada:   {terminal: true, outcome: ambiguo}
  pai_corta_arvore: {terminal: true, outcome: ambiguo, onEnter: [{setProp: {key: arvore_nalia, state: cortada}}]}
  nalia_piora:    {terminal: true, outcome: tragico}
```

Regras do motor:
- **Todo estágio não terminal tem `timeout`**, e a cadeia de timeouts sempre chega a um estágio terminal. É a garantia de que nenhum arco trava e de que o mundo anda sozinho (RF-141, risco R6). O validador confere.
- Prazos usam o RNG com semente para variar (`jitter`) e são multiplicados pelo **ritmo de eventos** — *Normal* ×1, *Contemplativo* ×2 (RF-145).
- Um estágio pode declarar `requires: [npc]` com `ifUnavailable: <estágio>` para lidar com NPC morto ou ausente.
- Todo desfecho tem `outcome` (`leve`, `ambiguo`, `tragico`) — usado pelo teste de tom (AT-21) e pelo AT-02.
- Arcos são avaliados a cada hora de jogo e imediatamente quando um efeito mexe em algo que eles observam.

### 4.6 Capítulos, origem e epílogo

- `chapters.yaml`: capítulo = estado global com condição de entrada, **camadas de verdade permitidas** e condição de saída. Capítulo I permite V0 e V1; sai ao deixar a região com intenção declarada ou no dia 30 (PRD 8.6). Quantidade de capítulos não é fixa.
- `origins/pedra_mansa.yaml`: pacote de dados — local inicial, itens, habilidades, relações e conhecimentos iniciais, arcos pessoais (PRD 8.4). Outra origem = outro arquivo.
- `epilogues/cap1.yaml`: fragmentos com `slot` (ordem), `priority`, `when`, `text`, `illustration`. Em cada `slot` vence o primeiro fragmento cuja condição passa; os slots de A1, A2 e A3 têm obrigatoriamente um fragmento sem condição, para sempre haver texto mesmo se o jogador nunca participou (RF-163).

### 4.7 Mundo, áreas e mapas

- `world/*.yaml`: civilizações (os 12 campos do RF-060; as 6 potências cadastradas), facções (as 4 do MVP), regiões e locais. **Existem como dado mesmo sem área jogável** (RF-040) — Fornal é só um registro e um assunto.
- **Áreas são mapas do Tiled** (`.tmj`), planta ortogonal vista de cima, **1 tile = 1 metro**. O jogo monta a cena 3D a partir da planta. O `core-sim` lê o mesmo arquivo para colisão e pontos; o cliente lê para montar o visual.

| Camada no Tiled | Tipo | Uso |
|---|---|---|
| `terrain` | tiles | tipo de chão → textura e som de passo |
| `collision` | tiles | onde não se anda |
| `props` | objetos | prop por ID, posição e rotação livres; `key` opcional para variantes |
| `spots` | pontos | lugares nomeados usados por agendas e arcos (`oficina_bancada`) |
| `triggers` | retângulos | transição de área, descoberta de local, zona de telhado, zona de perigo |
| `lights` | pontos | lamparinas, fornos, velas (cor, raio, se acende só à noite) |
| `spawns` | pontos | entrada do jogador, encontros |

- `props.yaml`: para cada prop, o asset, a pegada de colisão, se é inspecionável/contêiner e suas **variantes** (`viva`, `morta`, `cortada`). O estado fica em `World.areas[..]` e muda só por efeito `setProp` (RF-043).
- **Terreno plano no MVP** (D-010): relevo fica para depois do portão visual, se fizer falta.
- **Navegação:** grade de 0,5 m derivada de `collision` + pegadas dos props; A* próprio no `core-sim` (D-011). Clique em ponto inalcançável leva ao ponto alcançável mais próximo (RF-001).

### 4.8 Itens, economia, trabalhos, combate, sobrenatural

- `items.yaml`: os campos do RF-202. Documentos são itens com `document: <id>` (RF-124).
- **Preço** = valor base × modificador regional da categoria × modificador de opinião × modificador de Comércio (RF-262). Eventos mexem no modificador regional com `priceMod` (E2 → itens com tag `sal`).
- `merchants.yaml`: estoque mínimo/máximo, caixa, intervalo de reabastecimento.
- `jobs.yaml`: ação temporizada — duração, pagamento, habilidade que treina, conversas/eventos que pode disparar (RF-264).
- **Habilidades** (Luta, Vigor, Ofício, Lábia, Observação, Comércio): 0–100, sobem por uso com ganho decrescente por curva configurável; sem nível global (RF-180–182).
- **Combate** roda no `core-sim`: alcance, tempos de ataque, vida e fôlego, 2 habilidades com recarga. Inimigos humanos têm limiar de fuga/rendição. `enemies.yaml` e as áreas dizem se a derrota ali é letal; o padrão é acordar horas depois, roubado, com o mundo avançado (RF-340/341).
- `phenomena.yaml` (RF-241): tipo de custo, quem carrega, origem e destino do custo. O MVP usa em A1 e no rumor de Berta; o jogador não conjura (DP-04).

---

## 5. Save

### 5.1 Arquivo

- Local: `%APPDATA%\Etrava\saves\` (logs e opções em `%APPDATA%\Etrava\`). Nunca na pasta do jogo (RF-366).
- **Um arquivo por save**, `nome.etrava`, que é um ZIP com:

| Entrada | Conteúdo |
|---|---|
| `meta.json` | versão do esquema, versão do jogo, dia/hora de jogo, local, tempo jogado, data real |
| `state.json` | o `World` inteiro |
| `thumb.png` | miniatura para a tela de carregar |

- Slots: manuais (livres), `quicksave`, e 3 automáticos rotativos — ao dormir e ao trocar de área (RF-360/361).
- **Escrita atômica:** grava em `nome.etrava.tmp` e renomeia por cima (RF-365). O estado é copiado no tick; a compressão e a escrita vão para uma thread à parte, para não travar a tela (RNF-022).

### 5.2 Esquema (resumo de `state.json`)

```json
{
  "schemaVersion": 1,
  "meta":   { "seed": 42, "rngState": 123456789, "tick": 252000, "ticksPerDay": 36000,
              "pace": "normal", "chapter": "cap1", "origin": "pedra_mansa" },
  "flags":  { "avel_recebeu_carta": true },
  "vars":   { "voluntarios": 2 },
  "player": { "area": "pedra_mansa", "pos": [41.5, 88.0], "hp": 80, "stamina": 100, "money": 34,
              "skills": { "oficio": 22.4 }, "inventory": [ { "item": "martelo", "qty": 1 } ],
              "equipment": { "mainHand": "martelo" },
              "knowledge": [ { "fact": "nalia_restaurou_arvore", "version": "exagero", "certainty": "rumor",
                               "source": "npc:apostador_1", "day": 3, "place": "taverna", "marked": false } ] },
  "npcs":   { "avel": { "status": "present", "area": "pedra_mansa", "spot": "oficina_bancada",
                        "pos": [40.0, 87.0], "opinions": { "player": 55 },
                        "memory": [], "knowledge": [], "overrides": [] } },
  "arcs":   { "a1_nalia": { "stage": "insonia", "enteredTick": 180000, "vars": {}, "outcome": null } },
  "areas":  { "pedra_mansa": { "props": { "arvore_nalia": "viva" }, "containers": {}, "discovered": true } },
  "economy":   { "priceMods": { "terras_ferrugem": { "remedio": 1.0 } }, "merchants": {} },
  "factions":  { "pedra_mansa|comitiva_caldris": -10 },
  "deeds":     [],
  "scheduled": [ { "tick": 300000, "seq": 17, "effects": [] } ],
  "chronicle": [ { "tick": 36000, "type": "arc.stage", "ref": "a1_nalia", "data": "restauracao" } ]
}
```

O save referencia definições **por ID**; nunca copia o conteúdo.

### 5.3 Versão, migração e integridade

- `schemaVersion` inteiro. Migrações são passos `v(N) → v(N+1)` aplicados em cadeia sobre a árvore JSON, antes de virar objeto. Versão mais nova que o jogo, ou sem migração: recusa com mensagem clara (RF-363).
- Arquivo ilegível → erro tratado e volta ao menu, nunca crash (RF-365).
- ID que sumiu do conteúdo (NPC ou item removido entre versões) → ignorado com aviso no log.
- **Hash de estado:** SHA-256 do `state.json` em forma canônica (chaves ordenadas). É o que os testes AT-03 e AT-05 comparam.

---

## 6. Apresentação (`game-client`)

- **Laço:** acumulador de tempo → N × `sim.step()` → consome `SimEvent`s → desenha com interpolação.
- **Cena:** chão a partir da camada `terrain`; props e personagens em GLB via gdx-gltf; luz direcional (sol/lua) com sombra + luzes pontuais dos mapas, com limite de luzes ativas (risco R9).
- **Dia/noite (RF-027):** tabela de pontos-chave por hora (direção e cor do sol, cor ambiente, névoa, gradação) com interpolação. Fica em dados, por área.
- **Pós-processamento (RF-028):** um passe próprio de tela cheia — gradação de cor e vinheta; névoa na cena; bloom opcional. Cada efeito desliga nas opções.
- **Câmera (DP-02):** interface `CameraRig`; implementação do MVP = isométrica, perspectiva de campo estreito, 35–50° de inclinação, zoom limitado, segue com suavização. Perspectiva × ortográfica é decidida no olho, no M1. Interação usa **posição de mundo** (raio da câmera → chão/objeto), nunca coordenada de tela (RF-024).
- **Oclusão (RF-006, RF-023):** zona de telhado esconde o telhado quando o jogador entra; props entre a câmera e o jogador ficam translúcidos; silhueta de segurança.
- **Escuridão (RF-008):** ambiente quase preto + luz no personagem, maior com lamparina equipada.
- **UI:** scene2d, tema "papel e tinta" (PRD 10.1), fonte serifada via FreeType com 3 tamanhos (RNF-040). Telas do PRD 10.2. Nenhum marcador de objetivo em lugar nenhum.
- **Áudio:** canais geral/música/ambiente/efeitos/interface (RF-403); ambiente por área × período com transição suave; música por contexto.
- **Depuração (RF-440):** console e painéis em scene2d, só em build de desenvolvimento — avançar tempo, mexer em flags, teletransporte, ver arcos, conhecimento, opiniões e crônica.
- **Log e crash (RNF-032):** arquivo em `%APPDATA%\Etrava\logs\`; relatório de crash inclui stack trace, semente e dia de jogo.

---

## 7. Pipeline de assets

### 7.1 IDs lógicos e manifesto

O conteúdo só conhece **IDs** (`retrato.avel`, `prop.arvore_velha.viva`, `som.passo.terra`). Manifestos em `assets/manifest/*.yaml` ligam ID → arquivo:

```yaml
- id: retrato.avel
  type: portrait            # model | animation | texture | portrait | icon | illustration | sound | music | font
  file: portraits/avel.png
  source: "Gerado por IA (Dreamina), prompt em STYLE.md §3.1"
  license: proprio
```

Trocar um asset = trocar o arquivo e manter o ID (RF-423).

### 7.2 Placeholders (RF-421)

| Tipo | Placeholder |
|---|---|
| modelo de personagem | cápsula colorida pela facção |
| prop | caixa do tamanho da pegada, cor por categoria |
| textura | xadrez |
| retrato / ícone / ilustração | quadro liso com as iniciais ou o ID |
| som / música | silêncio |

ID sem entrada no manifesto, arquivo ausente ou arquivo que falha ao carregar → placeholder + **um** aviso no log por ID. Nunca encerra o jogo (AT-16). O M0 inteiro roda só com placeholders.

### 7.3 Formatos e personagens

- GLB (modelos e animações), PNG, OGG (RF-422). Conversão de outros formatos acontece fora do jogo, por ferramenta de linha de comando — **sem Blender** (RNF-006).
- **Esqueleto padrão único** para humanos, com o conjunto de animações do RF-424 em arquivos separados do modelo, para qualquer corpo compatível usar qualquer animação.
- **A fonte de personagens/animações é escolhida no M1**, dentro do portão visual, com três critérios: proporções humanas naturais, licença que permita distribuição comercial e exportação para GLB sem Blender. Não é decidida agora de propósito (risco R8).

### 7.4 Estilo e licenças

- `STYLE.md` (nasce no M1): paletas por cultura, regras de luz e modelos de prompt por tipo de asset (RF-425).
- `ASSETS_LICENSES.md` é **gerado** a partir dos manifestos. O build de release falha se houver asset real sem `source` e `license` (RF-426, RNF-050).
- **Portão M1:** até a aprovação visual explícita do diretor, só se produzem os assets da cena de referência.

---

## 8. Validador de conteúdo (RF-442)

Roda no build, no boot em modo desenvolvimento e sob demanda. Cada erro aponta **arquivo e item**.

| Verificação | Regra do PRD |
|---|---|
| Referências a IDs inexistentes (fatos, NPCs, itens, arcos, props, pontos, diálogos, assets) | RF-442 |
| Nós de diálogo inalcançáveis; `goto` para nó inexistente | RF-442 |
| Estágio de arco sem `timeout`, ou cadeia de timeouts que não chega a um terminal | RF-141 |
| Arco sem nenhum desfecho que não seja `tragico` | PRD 20.3 |
| Fato usado em condição `knows` do jogador com menos de **2 fontes distintas** (fala, conversa ambiente, documento, objeto, efeito de arco) | RF-123 |
| Fato V2/V3 que o jogador consegue aprender no Capítulo I; fato V1 entregue com certeza `verified` | PRD 8.2 |
| Escolha `relevant` com menos de 2 canais, ou canal declarado sem nada que reaja ao que a escolha altera | RF-161 |
| Epílogo: slots de A1–A3 sem fragmento de reserva; menos de 15 fragmentos | RF-163 |
| Texto do caderno/fato no imperativo ("Vá até…", "Fale com…") — aviso | AT-20 |
| Nome próprio fora do `glossario.yaml` — aviso | PRD 20.3 |

Asset com ID no manifesto mas sem arquivo é **aviso**, não erro (vira placeholder).

---

## 9. Testes

- **Unitários** (JUnit 5) por pacote do `core-sim`; cobertura ≥ 70% conferida no build.
- **Testes de aceitação automáticos** do PRD viram testes de integração headless:

| AT | Como é testado |
|---|---|
| AT-01, AT-02 | simulação de 30 dias com política `parado`, em 20 sementes; confere desfechos |
| AT-03 | mesma semente + mesmo registro de comandos → mesma crônica e mesmo hash |
| AT-04 | pasta de conteúdo quebrado de propósito em `core-sim/src/test/resources/` |
| AT-05, AT-06 | salvar/carregar/comparar hash; save antigo e save corrompido |
| AT-07 | evento às 03:00 durante sono de 12 h |
| AT-08 | fato plantado na taverna chega a ≥ 3 NPCs em ≤ 3 dias |
| AT-09 | preço dos itens `sal` depois de E2; estoque depois do reabastecimento |

- **Políticas de jogador headless:** `parado` (fica na oficina), `trabalhador` (trabalha, come, dorme) e `registro` (reexecuta comandos gravados).
- **Testes manuais** (AT-10 a AT-21): roteiro escrito em `docs/TESTES_MANUAIS.md`, criado conforme os marcos chegam.
- Desempenho: 30 dias headless em ≤ 60 s (RNF-024) e salto de 12 h em ≤ 3 s (RNF-025) têm teste com tempo-limite.

---

## 10. Build e distribuição

- Um comando para cada coisa (seção 2.3). Tudo que o build precisa vem pelo Gradle; nenhuma ferramenta externa obrigatória.
- **Release:** `jlink` monta um runtime mínimo e `jpackage --type app-image` gera uma pasta com `Etrava.exe` + runtime. Entrega = essa pasta em ZIP. Roda em Windows sem Java instalado (RNF-004, AT-18). Instalador `.msi`/`.exe` fica fora do MVP porque exigiria instalar o WiX.
- Modo desenvolvimento liga console, validador no boot e avisos de placeholder; o release desliga.
- IntelliJ: o projeto abre direto como projeto Gradle; `.idea/` fica fora do git.

---

## 11. O que cada marco usa desta SPEC

| Marco | Seções |
|---|---|
| M0 Fundação | 2, 3.1, 3.4 (esqueleto), 4.7 (área de teste), 6 (câmera, clique para mover, console), 7.2, 10 |
| M1 Fatia visual | 6 (luz, pós, oclusão), 7 inteiro, `STYLE.md` — **portão visual** |
| M2 Simulação básica | 3 inteiro, 4.3 (agendas), 5, 9 (AT-03/05/06/07) |
| M3 Conhecimento e diálogo | 4.1, 4.2, 4.4, 8 |
| M4 Mundo independente | 4.5, 4.6, variantes de props, memória/opinião |
| M5 Vida e economia | 4.8 (itens, economia, trabalhos, habilidades) |
| M6 Combate e perigo | 4.8 (combate), escuridão, estrutura enterrada |
| M7 Conteúdo completo | conteúdo final em `content/`, áudio, mapa, menus |
| M8 Polimento | desempenho, release |

---

## 12. Pontos a confirmar

**No M0 (técnicos, eu resolvo e registro):**
- Versões compatíveis entre libGDX, gdx-gltf, Gradle e JDK 21; Gradle rodando com o JDK 25 como padrão da máquina.
- `jpackage` gerando o app-image a partir do build.

**No M1 (visuais, dependem do diretor):**
- Perspectiva de campo estreito × ortográfica.
- Fonte dos personagens e animações.
- Se o terreno plano basta ou se precisa de relevo.

**Narrativos (ficam para os marcos que o PRD indica):** Q-07 (o que há na estrutura enterrada, M6) e Q-08 (quem é o estranho, M7).
