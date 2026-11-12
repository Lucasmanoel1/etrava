# SPEC — Fase V (especificação técnica da revisão visual)

Versão 1 — 2026-11-12. Complementa a `docs/SPEC.md` (que continua valendo para o resto) e substitui o que ela diz sobre produção de arte (§7.3, "sem Blender": ver D-085). Plano e marcos: `PLANO.md`.

---

## 1. Fluxo

```
 Dreamina ──► referências (arte-bruta/, fora do git)      ┐
          └─► texturas e materiais de UI ─► funil ─► tools/blender/texturas/ (no git)
                                                          │
 tools/blender/**.yaml  (o que existe: gente, árvore, casa, móvel, item)
 tools/blender/**.py    (como se constrói, anima e fotografa)
        │  gradlew renderArt  →  blender.exe -b -P …  (em paralelo, com cache)
        ▼
 build/render/<id>/…  passes em PNG de 16 bits + metadados JSON por quadro
        │  pixelizador (Java, tools)  — paleta v3, rampas, contorno, limpeza, decalques
        ▼
 assets/<tipo>/<id>.png  +  assets/<tipo>/<id>.json  +  assets/manifest/arte.yaml
        │  validador (paleta, animações, pegadas, orçamento)   ◄── gradlew build
        ▼
 cliente: atlas por área, animação por estado, camadas, decoração, vento, FX
        │  gradlew captures / sheets   → capturas, folhas de contato, GIFs
        ▼
 inspeção (eu leio as imagens) → ajuste → parada do marco
```

Compilar e rodar o jogo continua sem Blender e sem Python (RNF-006): os PNG e JSON gerados ficam no repositório, como os OGG do áudio.

## 2. Blender

### 2.1 Invocação
- **Versão fixa: Blender 5.3** (`C:\Program Files\Blender Foundation\Blender 5.3\blender.exe`). Caminho por `BLENDER` (variável de ambiente) ou `blender.path` em `local.properties`; na falta, procura em `Program Files`. O script de entrada confere `bpy.app.version` e recusa outra versão sem `--force` (RV7).
- Sempre em segundo plano: `blender -b --factory-startup -P tools/blender/entrada.py -- <tarefa> <id> <saída>`. `--factory-startup` garante que preferências e add-ons do usuário não mudam o resultado; extensões necessárias são ativadas pelo próprio script.
- **MCP da Blender** (add-on "MCP" do Blender Lab, porta 9876): só para exploração interativa — olhar um modelo, testar um material. **Nunca é fonte de verdade**: o que serve vira script em `tools/blender/`. Esta sessão do Claude Code não tem as ferramentas do MCP registradas; o pipeline não depende delas.

### 2.2 Estrutura
```
tools/blender/
  entrada.py            despacha a tarefa; confere versão; semeia o RNG
  nucleo/               câmera iso, luz, passes, saída, metadados (comum a tudo)
  kit_humano/           corpo, esqueleto padrão, roupas, cabelo, acessórios
  anim/                 ciclos e poses por nome (parado, andar, …), parametrizados
  criaturas/            lobo, vulto
  natureza/             árvores (por espécie), arbustos, grama, flores, pedras
  construcoes/          módulos (parede, enxaimel, telhado, chaminé, porta, janela…)
  interiores/           pisos, paredes de corte, móveis
  itens/                um modelo por item: ícone, item no chão e camada de equipamento
  texturas/             texturas já passadas pelo funil (PNG pequenos, no git)
  personagens/<id>.yaml   descrição de cada personagem (corpo, roupa, cores, papel, jeito de andar)
  natureza/<id>.yaml, construcoes/<id>.yaml, interiores/<id>.yaml, itens/<id>.yaml
```

### 2.3 Tarefas Gradle (projeto `tools`)
| Tarefa | Faz |
|---|---|
| `renderArt` | renderiza tudo o que mudou e pixeliza; `-Pso=avel,arvore_carvalho` limita |
| `pixelize` | só o pixelizador sobre passes já renderizados (iterar no visual sem re-renderizar) |
| `sheets` | folhas de contato e GIFs de cada personagem/objeto animado em `build/revisao/` |
| `captures` | capturas do jogo pelo roteiro `tools/capturas.yaml` (cliente, janela oculta) |

**Cache:** cada saída guarda o hash de (yaml do objeto + scripts usados + texturas + versão do Blender). Igual → pula. **Paralelismo:** N processos do Blender (N = núcleos / 2), um objeto por processo.

### 2.4 Determinismo (VT-16)
Cycles na CPU, semente fixa, amostras fixas, sem denoiser; nenhuma dependência de hora ou de ordem de arquivo. Mesma versão + mesma entrada → mesmos bytes.

### 2.5 Extensões gratuitas permitidas
- **MPFB2** (MakeHuman para Blender; código GPL, malhas e roupas CC0) — candidata a base do corpo humano. Avaliada no V1 contra um kit próprio; a escolhida vira decisão registrada.
- **Sapling Tree Gen** — candidata para troncos e galhos. Avaliada no V4 contra um gerador próprio (colonização de espaço).
- Sprite Sheet Maker e similares: **não precisamos** — a montagem das folhas é do pixelizador, que já conhece paleta, âncora e metadados.

Toda extensão é instalada pelo script na primeira execução (pasta de extensões isolada em `build/blender-ext/`), registrada em `ASSETS_LICENSES.md` se gerar conteúdo, e nunca exigida para compilar o jogo.

## 3. Dreamina

| Uso | Exemplo | Vai para o jogo? |
|---|---|---|
| Referência de personagem | prancha frente/lado/costas de cada NPC: roupa, cores, silhueta | não — guia a modelagem |
| Referência de espécie e arquitetura | carvalho, bétula, telhado de colmo, enxaimel | não |
| Textura de material | casca, folha, colmo, tábua, reboco, pedra, tecido, couro, terra, grama | sim, **depois** do funil e como material no Blender |
| Material de interface | pergaminho, couro, madeira, ferragens | sim, depois do funil, como base dos 9-patches |

**Funil de textura** (`gradlew importTextures`): original em `arte-bruta/texturas/` → recorte → torna repetível (deslocamento + mescla nas bordas) → reduz à resolução útil → dessatura para a família da paleta → `tools/blender/texturas/`. A textura entra no render como **variação de valor** dentro do material; a cor final sempre sai da rampa da paleta (§5), nunca da textura.

Prompts: trecho fixo de estilo do `STYLE.md` §4, mais, para texturas: *"seamless tileable texture, top-down flat lighting, no shadows, no perspective"*. Sempre `--credit-ceiling`. Gasto relatado em cada parada.

## 4. Geometria, escala e câmera

### 4.1 Densidade (D-086)
| Medida | Antes (M8) | Fase V |
|---|---|---|
| Pixel de arte | 4×4 px de mundo | **2×2 px de mundo** (`IsoProjection.ART_SCALE = 2`) |
| Tile de 1 m (losango) | 32×16 px de arte | **64×32** |
| 1 m de altura | 14 px de arte | **28** |
| Humano adulto (1,70 m) | ~24 px | **~48 px** |
| Quadro de humano | 24×32 | **64×80** (âncora nos pés em (32, 72)) |

As medidas de **mundo** (`HALF_W`, `HALF_H`, `Z_PX`) não mudam: mapas, colisões, cliques e luzes ficam onde estão. Só cada pixel de arte fica menor.

### 4.2 Câmera de render
- Ortográfica, elevação **30°** (rotação X = 60°), azimute **45°**: um quadrado no chão vira losango 2:1, como no jogo.
- **Correção vertical:** na projeção real 1 m de altura mediria 39,2 px de arte; o jogo usa 28 (`Z_PX`). Todo objeto é filho de um vazio com escala Z = **0,7145** (28 / 39,19) antes do render. Mantém a altura de tudo igual ao que o jogo já desenha (portas, luzes, balões).
- `ortho_scale = largura_do_quadro_em_px / 45,25` (45,25 px de arte por metro de tela na diagonal do tile) × superamostragem.
- O ponto (0, 0, 0) do objeto cai **exatamente** no centro do pixel da âncora (deslocamento de câmera calculado).
- **Superamostragem 4×**: o render sai com 4× a resolução final; o pixelizador reduz.
- **Direções:** gira-se o **objeto**, não a câmera, para a luz ficar fixa em relação à tela. 8 direções de 45°, direção 0 = leste do mundo (convenção atual). Sem espelhar (a luz vem da esquerda).

### 4.3 Luz de render
Uma luz principal do alto à esquerda da tela (STYLE §1.6), oclusão ambiente, nada mais. O sombreamento final é decidido pelo pixelizador a partir da normal e da oclusão; a luz do Blender serve só para a passagem de sombra projetada (§5.1).

### 4.4 Zoom no jogo
Escalas inteiras de pixel de arte na tela: 1, 2, 3, 4 (a 1280×720, a escala 1 mostra a mesma área que a escala 2 de hoje). Em 1920×1080 o padrão vira 2 (um pouco mais perto que hoje). **Opção a avaliar no V1:** desenhar o mundo num buffer na escala de arte e ampliar com filtro "sharp-bilinear", para ter passos 1,5 e 2,5 sem pixel desigual. Só entra se o diretor preferir no portão.

## 5. Pixelizador (`tools/.../art/pixel/`)

### 5.1 Passes de entrada (PNG de 16 bits, por quadro)
| Passe | Conteúdo |
|---|---|
| `material` | índice do material (render sem antisserrilhado) |
| `normal` | normal em espaço de câmera |
| `profundidade` | distância à câmera |
| `oa` | oclusão ambiente |
| `detalhe` | luminância da textura do material (cor difusa) |
| `sombra` | sombra projetada da luz principal |
| `alfa` | cobertura |

Mais o JSON do quadro: posição na tela de ossos e vazios marcados (mãos, cabeça, olhos, ponto de luz, fumaça, pegada).

### 5.2 Passos
1. **Redução 4:1 por voto:** cada pixel final pega o material mais presente nos 16 subpixels (alfa < 50 % → transparente). Nada de média de cores: é isso que tira a cara de "render encolhido".
2. **Valor:** `v = luz(normal · L) × oa × sombra + k × (detalhe − 0,5)`, com `k` por material (tecido tem pouca textura; casca e colmo, muita).
3. **Rampa:** cada material aponta para uma rampa da paleta (2–5 cores, `tools/art/rampas.yaml`); `v` é quantizado em faixas fixas. Pontilhado ordenado só em material estático marcado (`pontilhar: true`: reboco, terra); nunca em gente (piscaria na animação).
4. **Contorno seletivo:** borda externa = tom mais escuro da rampa do material vizinho; nas bordas do lado da luz, um tom acima; quase preto só na base e no lado da sombra. **Linhas internas** onde a profundidade salta (braço na frente do tronco) ou entre materiais de rampas próximas.
5. **Limpeza:** pixel isolado vira a cor dos vizinhos; contorno em "L" duplo vira diagonal limpa; no máximo N cores por material por quadro.
6. **Decalques:** olhos, boca, fivela, botões, pregos — pontos marcados no modelo, carimbados com 1–2 px na posição exata do JSON, só se virados para a câmera. É o que faz um rosto de 48 px ter expressão.
7. **Brilho:** materiais emissivos (vidraça, chama) vão também para a imagem `_brilho` (sistema noturno atual, D-026).
8. **Montagem:** quadros cortados à menor caixa comum por animação, em folha + JSON com âncora, contagem de quadros e pontos (mão, cabeça, luz).

### 5.3 Paleta v3
A paleta atual (53 cores, `PixelArt.PALETTE`) é mantida inteira e ganha rampas novas, até ~96 cores: verdes frios (coníferas) e amarelados (bétula), flores dessaturadas (violeta, amarelo, azul-acinzentado, rosa-pó), pele mais longa (do mais escuro ao mais claro, 7 tons), cabelos (preto, castanho, ruivo, grisalho, loiro-palha), metais (ferro, latão), água turva. Definida em `tools/art/paleta.yaml` com nome por rampa; `PixelArt` passa a ler dela. Saturação e brilho continuam controlados: nenhuma cor nova é mais saturada que a ferrugem mais viva.

## 6. Personagens

### 6.1 Modelo
- **Esqueleto padrão único** para todo humano (nomes de osso fixos), o que permite qualquer animação em qualquer corpo (RF-424).
- Corpo paramétrico: altura, idade (criança, adulto, velho), corpo (magro, médio, largo), postura (ereta, curvada). Cabeça levemente maior que o real (~1/6,5 da altura) para leitura a 48 px.
- Roupa por camadas de malha (camisa, túnica, avental, gibão, capa, saia, calça, botas, chapéu, capuz, cinto, bolsa), cada uma com material → rampa.
- Silhueta por ofício: o ferreiro é largo de avental; a agrimensora tem bastão e bolsa de couro; a curandeira, capuz e ervas no cinto; o registrador, chapéu alto e pasta. As pranchas de referência (Dreamina) fixam isso antes de modelar.

### 6.2 Animações e papéis
| Animação | Quadros | Gente comum | Jogador | Saqueador | Lobo | Vulto |
|---|---|---|---|---|---|---|
| `parado` (respira) | 8 | ✔ | ✔ | ✔ | ✔ | ✔ |
| `andar` | 8 | ✔ | ✔ | ✔ | ✔ | ✔ |
| `correr` | 8 | — | ✔ | ✔ | ✔ | — |
| `golpe_corte`, `golpe_estocada`, `golpe_pesado` | 6 cada | — | ✔ | ✔ (um) | `bote` 6 | `golpe` 6 |
| `dano` | 3 | — | ✔ | ✔ | ✔ | ✔ |
| `cair` + `caido` | 6 + 1 | — | ✔ | ✔ | ✔ (`morrer`) | ✔ (`desfazer`) |
| `levantar` | 5 | — | ✔ | — | — | — |
| `sentar` + `sentado` | 3 + 4 | ✔ | ✔ | — | — | — |
| `trabalhar_bancada`, `trabalhar_martelo`, `trabalhar_chao` | 8 cada | conforme ofício | ✔ | — | — | — |
| `conversar` (gesto) | 6 | ✔ | — | — | — | — |
| `dormir` | 4 | ✔ | ✔ | — | ✔ (`deitado`) | — |

Todas em 8 direções. O validador (VT-05) conhece esta tabela por `papel` no yaml do personagem.

### 6.3 Animação pelo que o NPC está fazendo
A simulação já expõe `ActorView.activity()` (o `do:` da agenda). O cliente lê `assets/config/animacoes.yaml`:
```yaml
atividades:
  trabalhar: {padrao: trabalhar_bancada, por_npc: {ferreiro: trabalhar_martelo, bento: trabalhar_chao}}
  comer: sentado
  descansar: sentado
  dormir: dormir
  conversar: conversar
  festejar: [conversar, parado]   # alterna
  olhar: parado
```
Andando, vale `andar`/`correr`; em combate, os golpes; o resto, `parado`. Pura apresentação: o núcleo não muda.

### 6.4 Camadas do jogador
O corpo do jogador sai em uma folha por **roupa do corpo** (camisa, gibão, avental — as do jogo). Cabeça (capuz, chapéu), mão principal (faca, martelo, cajado, lança, machadinha) e mão secundária (lamparina) saem como **folhas de camada** com a mesma grade de quadros, renderizadas com o corpo como máscara (*holdout*): o que o corpo esconde já vem recortado. No jogo, desenha-se corpo + camadas por cima, sempre nessa ordem. A luz da lamparina segue o ponto `mao_esq` do quadro.
NPCs saem "assados": uma folha só, com o que carregam.

### 6.5 Retratos
Os 18 retratos ficam. No V2, cada retrato é comparado ao sprite novo (roupa, cabelo, cores); o que divergir é refeito na Dreamina usando a prancha de referência do personagem.

## 7. Cliente

### 7.1 Renderizador
- `ART_SCALE = 2`; arte antiga ainda presente durante a transição é desenhada com escala 2 (cada pixel antigo vira 2×2 pixels novos) — o jogo nunca quebra no meio da fase.
- **Atlas por área:** ao entrar numa área, o cliente monta (ou lê do cache em disco) um atlas com os sprites dela; folhas de personagem carregadas por quem está na área (§6 do `PLANO`, orçamento de 512 MB).
- **Variantes de prop:** uma entrada do manifesto pode listar `variantes: [...]`; o cliente escolhe por hash da posição. Dá variedade (tamanho, espécie, desgaste) sem mexer nos mapas.
- **Laços ambientais:** sprites com animação em laço (copa ao vento, fogo, varal) têm fase pelo hash da posição e velocidade modulada pelo vento global (rajadas por ruído).
- **Pontos de luz e fumaça** vêm do JSON do asset (onde ficam as janelas e a chaminé na arte nova); `content/props.yaml` continua dizendo cor e intensidade.

### 7.2 Chão e decoração
- Tiles 128×64 px de arte (losango 64×32 + sangria). Textura com variação por tile.
- **Transições por máscara** (16 casos por par de terrenos, borda irregular), calculadas ao carregar a área; o terreno de prioridade maior cobre o menor.
- **Camada de decoração** (`assets/config/decoracao.yaml`): por terreno e por área, decalques planos (folhas, pedrinhas, terra pisada) e elementos em pé (tufos, flores, samambaias, cogumelos) com densidade, agrupamento por ruído e distância mínima de caminho, porta e colisão. Determinística pela área e pelo tile. Não bloqueia, não é clicável, não entra no save. Elementos em pé entram na ordenação por profundidade e balançam com o vento.

### 7.3 Vida ambiente (`assets/config/ambiente.yaml`)
Emissores de partículas em pixel de arte, na paleta: fumaça de chaminé, fagulha de forja e lareira, poeira em raio de luz (interior, de dia), folhas caindo perto de árvore com vento, pássaros que cruzam o céu de dia, mariposas nos lampiões e vaga-lumes no mato à noite. Limite global de partículas; desligável nas opções ("Detalhes do ambiente: alto/baixo").

### 7.4 Interiores
Paredes do fundo inteiras; paredes da frente cortadas a ~0,4 m com acabamento (viga ou pedra de topo), soleira onde há porta. O piso termina numa borda acabada, sem esfumar. Feixe de luz pelas janelas de dia (aditivo, com poeira). Lareira com fogo em laço e luz que tremula.

### 7.5 Visibilidade dos personagens (V0)
**Feito no V0 (D-090).** Causa: o buffer de luz multiplica o personagem como se fosse chão, e o sprite do jogador tinha valores e matizes iguais aos da grama e da terra. Correção: (1) **luz de presença** — máscara dos personagens (com oclusão) somada ao buffer de luz por `max(luz, ambiente × actor)`, teto 0,88, com `actor` por hora em `daynight.yaml` (1 de dia, 1,2–1,6 ao entardecer e à noite; 1,25 em interiores; nada na área escura); (2) o jogador veste a camisa de linho clara que já é o seu item inicial. O buffer de luz acompanha o zoom corretamente (verificado). Console: `presenca on/off`.

### 7.6 Modo de depuração (V0)
- Liga com `--debug` ou `ETRAVA_DEBUG=1`. `gradlew run` liga; o pacote de release não.
- Sem ele: nada de FPS, posição, semente, velocidade, dica de console; F1 e F3 não fazem nada; console de comandos inacessível.
- Com ele: F3 mostra e esconde o painel técnico; F1 abre o console. `--bench`, `--bot` e `--screenshot` continuam funcionando sem `--debug`.

## 8. Interface

### 8.1 Entrada, foco e rolagem (V0 — causa provável do zoom)
- **Regra:** a roda do mouse vai para a lista **sob o cursor**; se não houver nenhuma, vai para a câmera. Nada de foco de rolagem "grudado": `ScrollPane` ganha o foco ao receber o mouse e o perde ao sair.
- Todo painel que se esconde chama um único `Ui.esconder(ator)`, que faz `setVisible(false)` **e** `stage.unfocus(ator)`. É exatamente o que falta hoje: `GameUi` e `MenuUi` escondem painéis com `setVisible(false)` e o foco continua no `ScrollPane` invisível, que consome a roda.
- A câmera acumula a roda fracionária (touchpad) até um passo inteiro.
- Toda lista: barra visível quando não cabe, roda, arrastar, setas/Page Up/Page Down, e a lista rola até o item escolhido pelo teclado.

### 8.2 Escala
- `ScreenViewport` com unidades por pixel = 1/k, **k = max(1, ⌊altura/540⌋)**: a interface é desenhada numa tela virtual de 540 a 1079 de altura e ampliada por um inteiro (nitidez dos 9-patches).
- Fonte gerada no tamanho físico final (19 × altura/720 × tamanho de texto), sem escala de bitmap (D-077).
- Layout por proporção e por altura de linha da fonte, com máximos; nada de largura fixa em pixels. Telas largas (21:9) centralizam com largura máxima.
- Verificação automática de limites: nenhum ator visível fora da tela, nenhum `Label` cortado (VT-09).

### 8.3 Tema
Pixel art na mesma paleta: moldura de madeira escura com cantoneiras de ferro, miolo de pergaminho com textura sutil, divisórias em fita de couro, botões que afundam ao clicar, abas do caderno como marcadores de fita, barra de rolagem de couro. 9-patches em `assets/ui/` e `assets/manifest/ui.yaml`, com placeholder se faltar (RF-421). Cursores próprios (apontar, falar, entrar, pegar, atacar, esperar). Sem neon, sem brilho, sem marcador de objetivo (PRD 10.1).

## 9. Verificação

### 9.1 Capturas (`gradlew captures`)
Roteiro em `tools/capturas.yaml`: área, posição, hora, zoom, painel aberto, resolução, tamanho de texto. Sai em `docs/capturas/<marco>/` + `contato.png` (todas numa folha). Cada marco tem o seu roteiro e repete as cenas do anterior, para comparar antes × depois.

### 9.2 Folhas de contato e GIFs (`gradlew sheets`)
Por personagem: uma folha com todas as animações × 8 direções em 2× e um GIF por animação (escritor GIF do próprio JDK). Ficam em `build/revisao/`, fora do git.

### 9.3 Métrica de visibilidade (VT-03)
As capturas de visibilidade pedem também a máscara do jogador (silhueta branca num buffer à parte, com o que está na frente dele em preto). Na imagem final: L_entorno = luminância média de uma moldura de 12 px de arte em volta da silhueta; **destaque** = fração dos pixels da silhueta com |L − L_entorno| ≥ 0,12. Passa com destaque ≥ 0,60 (D-090). Roteiro: `tools/capturas/visibilidade.yaml`; relatório em `build/capturas/visibilidade/relatorio.csv`.

### 9.4 Validador (`gradlew validateContent`)
Passa a checar: paleta (VT-04), animações por papel (VT-05), origem provisória (VT-06), pegada e porta das construções contra o mapa (VT-07), tamanho de atlas por área e soma de memória de textura (VT-10).

## 10. Manifesto: campos novos
```yaml
- id: personagem.avel
  type: spritesheet
  file: personagens/avel.png
  meta: personagens/avel.json      # quadros, animações, âncora, pontos por quadro
  role: comum                      # papel (tabela §6.2)
  source: "Blender (tools/blender/personagens/avel.yaml) + pixelizador"
  license: proprio
- id: prop.arvore
  type: sprite
  variants: [natureza/carvalho_m_1.png, natureza/carvalho_m_2.png, natureza/castanheiro_m_1.png]
  meta: natureza/carvalho.json     # âncora, laço de vento, pontos
```
O manifesto da arte nova é **gerado** (`assets/manifest/arte.yaml`) e tem prioridade sobre `provisorio.yaml`, que encolhe a cada marco até sumir.

## 11. O que cada marco usa
| Marco | Seções |
|---|---|
| V0 | 7.5, 7.6, 8.1, 9.1, 9.3 |
| V1 | 2, 3, 4, 5, 6.1, 7.1, 7.2 (parcial), 9.2, 10 |
| V2 | 6 inteiro, 9.4 (VT-05) |
| V3 | 6.2 (combate), 6.4 (armas), 7.3 (impacto) |
| V4 | 2.5 (árvores), 7.1 (variantes, laços), 7.2, 7.3 |
| V5 | 5.2.7 (brilho), 7.1 (pontos de luz e fumaça), 9.4 (VT-07) |
| V6 | 7.4, itens (§2.2 `itens/`) |
| V7 | 8 inteiro |
| V8 | 9 inteiro, orçamentos do `PLANO` §6 |
