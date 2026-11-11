# STYLE — guia visual do ETRAVA (v2)

Vale para toda arte do jogo, venha de código, de IA ou de mão. Direção: **pixel art dark fantasy** — o capricho e a leitura de um bom jogo 16-bit, com a luz e a sujeira dos isométricos clássicos (D-033).

**Regra de ouro: nada pode ter cara de "feito por IA".** Toda imagem, de qualquer origem, passa pelo mesmo funil antes de entrar no jogo: é reduzida para a grade de pixels do jogo e tem as cores trocadas pelas da paleta única. É a paleta compartilhada que faz o conjunto parecer de um autor só.

**Estado da arte hoje:** cenário e personagens são gerados por código (`gradlew generateArt`) e já saem em pixel art. Os retratos de NPC são gerados na Dreamina e convertidos para a paleta (`gradlew importPortraits`).

---

## 1. Regras gerais

1. **Leitura a distância.** Silhueta clara vale mais que detalhe. Um personagem tem ~24 px de altura.
2. **Luz conta a história.** Âmbar de lamparina contra noite fria; dia suave e poeirento.
3. **Pixel limpo.** Sem degradê suave, sem borrão, sem serrilhado suavizado: cada pixel é uma cor da paleta, opaco ou transparente. Dois ou três tons por material.
4. **Saturação controlada.** Tudo terroso e dessaturado; o único brilho é o fogo.
5. **Escuridão é lugar, não regra.** Ruína e noite são escuras; vilarejo ao meio-dia é quente e vivo.
6. **A luz vem do alto, à esquerda.** Faces viradas para a esquerda são claras; para a direita, sombreadas.
7. **Contorno escuro** de 1 px em objetos e personagens; tiles de chão não têm contorno.

## 2. Paleta (única, 53 cores)

Definida em `tools/src/main/java/etrava/tools/art/PixelArt.java` (`PALETTE`). Famílias:

| Família | Uso | Tons (do escuro ao claro) |
|---|---|---|
| Quase pretos | contorno, sombra funda | `#14101A` `#1E1A22` `#2A2428` |
| Cinzas quentes | pedra | `#3A3634` `#524C46` `#6B645A` `#857D70` `#A39A8A` `#C4BBA8` |
| Cinzas frios | ferro, ardósia | `#3B4048` `#565C66` `#7A808A` |
| Terra, ocre, palha | chão, reboco sujo, colmo | `#3D2A1C` `#5A3F26` `#7A5632` `#9C7A45` `#B8975A` `#D4B878` `#E8D8A0` |
| Ferrugem | metal velho, tecido, folha seca | `#4A1E1A` `#6E2C22` `#8A4E2E` `#A85F38` `#C47A48` |
| Madeira | vigas, móveis | `#2E2018` `#4A3526` `#624632` `#7C5A40` |
| Verde-oliva | mato, folhagem | `#1E2A1C` `#2E3C24` `#44502E` `#5A6438` `#6B7045` `#868C52` `#A4A866` |
| Pele | rostos e mãos | `#7A4E3A` `#A06C50` `#C08A68` `#D8A884` `#ECC8A4` |
| Azuis | noite, tecido | `#1C2238` `#2A3350` `#3E4A6E` `#5A6890` |
| Reboco, linho | paredes, roupas claras | `#8C7E66` `#B9A888` `#D8CCB0` `#EEE6D2` |
| Fogo | lamparina, janela acesa | `#C86428` `#F09A3C` `#FFC864` `#FFE9A8` |
| Acento | sangue, estandarte | `#9C2A2A` |

Mudar a paleta muda o jogo inteiro de uma vez: basta rodar `generateArt` e `importPortraits` de novo.

## 3. Medidas

O jogo é **2D isométrico 2:1 em pixel art**. Um pixel de arte ocupa 4×4 pixels de mundo, e a câmera só usa zoom inteiro (cada pixel de arte vira 1, 2, 3 ou 4 pixels da tela).

| Tipo | Tamanho em pixels de arte | Observações |
|---|---|---|
| Tile de chão | 64×32 | losango de 32×16 no centro; a borda irregular é o que mistura terrenos vizinhos |
| 1 metro de altura | 14 | |
| Personagem | quadro de 24×32 | folha com 8 linhas (direções) × 12 colunas (4 parado + 8 andando); pés em (12, 28) |
| Objeto | livre | fundo transparente; informar o **ponto de apoio** (pixel que encosta no chão, no centro da base) |
| Construção | livre (casa de 7×5 m ≈ 265×200) | vista de fora, duas paredes e o telhado |
| Brilho noturno | igual ao da imagem principal | só o que acende: vidraças, chama |
| Retrato de NPC | 96×128 | mostrado em dobro na tela de diálogo |
| Ícone de item | 32×32 | |

## 4. Arte por IA (Dreamina)

Uso atual: **retratos**. A IA gera a imagem grande; o jogo nunca usa essa imagem direto.

1. Gerar pela CLI `dreamina-canvas` (canvas "ETRAVA arte"), um nó por imagem, sempre com teto de créditos.
2. Salvar o original em `arte-bruta/retratos/<id>.png` (pasta fora do git).
3. `gradlew importPortraits` recorta, reduz para 96×128, aplica a paleta e escreve `assets/retratos/<id>.png` e o manifesto.

**Trecho fixo de estilo (colar em todo pedido):**
> Style: 16-bit pixel art game portrait, chunky clearly visible square pixels, limited muted palette of ochre, rust, faded olive green, warm grey and dark browns, strong readable shapes, flat shading with two or three tones per color, dark outline, warm lantern light from the upper left. No text, no watermark, no frame.

**Retrato:**
> Portrait of **{quem: idade, ofício, um detalhe de roupa, um detalhe de expressão}**, bust, three-quarter view, plain flat dark brown background. Dark fantasy rural village, late medieval, no modern items. **[trecho fixo]**

Modelo: Seedream 5.5 (30 créditos por imagem). Para peças de destaque (ilustrações de menu, mapa, epílogo), usar o modelo mais forte disponível.

**O que não pedir à IA:** personagens animados (não mantém a mesma pessoa em 8 direções e 12 quadros) e, por enquanto, cenário isométrico (o ângulo varia de imagem para imagem). Esses saem do gerador por código, que garante consistência.

## 5. Trocar uma arte

Cada asset é um ID no manifesto (`assets/manifest/*.yaml`). Para substituir uma imagem, salve o PNG novo e repita o ID em outro arquivo de manifesto — qualquer arquivo tem prioridade sobre `provisorio.yaml`. Medidas (`anchor`, `frame`) em pixels de arte. Arquivo ausente ou quebrado vira um xadrez magenta com aviso no log; o jogo não trava.

`gradlew validateContent` avisa de arquivos ausentes e de assets sem origem/licença; `gradlew licenses` atualiza o `ASSETS_LICENSES.md`.

## 6. Luz e pós-processamento

- A hora do dia define a cor do céu que multiplica a cena, a saturação, o contraste e a vinheta (`assets/config/daynight.yaml`, editável sem recompilar).
- À noite, janelas e lamparinas acendem: mancha de luz âmbar somada à cena, mais o brilho próprio da vidraça ou da chama.
- No console: `luz off` e `pos-fx off` desligam cada efeito, para comparar.

## 7. Interface

Papel e tinta (PRD 10.1): fundo `#D8CCB0`, tinta `#241C18`, destaques em ferrugem `#6E2C22`, borda de madeira escura. Sem brilho, sem neon, sem marcadores de objetivo.

## 8. Referências internas

Capturas com a arte atual: `docs/capturas/pixel/` (`dia`, `noite`, `dialogo`, `caderno`). As de `docs/capturas/m1/` são do estilo pintado anterior, mantidas só como histórico.
