# STYLE — guia visual do ETRAVA (v3)

Vale para toda arte do jogo. Direção: **pixel art dark fantasy** — o capricho e a leitura de um bom jogo 16-bit, com a luz e a sujeira dos isométricos clássicos (D-033). A v3 (Fase V, marco V1) troca a arte feita por código pela arte feita no **Blender + pixelizador** e dobra a densidade de pixels (D-086).

**Regra de ouro: nada pode ter cara de "feito por IA" nem de "3D reduzido".** Quem decide cada pixel é o pixelizador do projeto: cor só da paleta, sombreado em faixas de rampa, contorno seletivo, limpeza de pixel solto. O Blender entrega matéria-prima (qual material está em cada ponto, quanta luz, quanta oclusão, quanta textura); a IA entrega referência e textura, nunca sprite.

**Estado da arte hoje (V1):** o jogador, Avel, o carvalho, a bétula, a casa da oficina, o chão da vila (grama, terra, lajota) e a decoração do chão saem do pipeline novo (`assets/arte/`, `assets/manifest/arte.yaml`). O resto ainda é a arte do M8, desenhada ampliada 2× até ser refeita (V2–V6). Retratos e ilustrações seguem da Dreamina (§7).

---

## 1. Regras gerais

1. **Leitura a distância.** Silhueta clara vale mais que detalhe. Gente adulta tem **~48 px** de altura; a cabeça é um pouco maior que a real (~1/6 da altura) para o rosto ler.
2. **Luz conta a história.** Âmbar de lamparina contra noite fria; dia suave e poeirento.
3. **Pixel limpo.** Sem degradê suave, sem borrão: cada pixel é uma cor da paleta, opaco ou transparente. A única exceção é a sombra projetada no chão (preto translúcido, uma cor só).
4. **Saturação controlada.** Tudo terroso e dessaturado; o único brilho é o fogo. Nenhuma cor mais saturada que a ferrugem mais viva (`#C47A48`).
5. **Escuridão é lugar, não regra.** Ruína e noite são escuras; vilarejo ao meio-dia é quente e vivo.
6. **A luz vem do alto, à esquerda.** A face sudoeste (à esquerda na tela) é clara, a sudeste (à direita) fica na sombra, e a sombra projetada cai para a direita.
7. **Contorno seletivo.** Por fora da silhueta, 1 px no tom mais escuro da rampa do próprio material; na base e no lado da sombra, quase preto. Linha interna só onde a profundidade salta (braço na frente do tronco). Folhagem, colmo e reboco não têm linha interna; chão e decoração miúda não têm contorno.
8. **O jogador se lê sobre o chão em qualquer hora** (VT-03, `gradlew vt03`): ele é o mais claro do quadro de gente — camisa de linho, valores altos no pixelizador.

## 2. Paleta v3 e rampas

Definida em **`tools/art/paleta.yaml`**: a paleta do M8 inteira (53 cores, `PixelArt.PALETTE`) mais rampas novas, ~70 cores ao todo. Uma **rampa** é a lista de tons de um material, do mais escuro ao mais claro; o tom mais escuro fica para contorno, dobras e linhas, e os outros recebem a luz em faixas iguais.

| Grupo | Rampas |
|---|---|
| Neutros e metal | `quase_preto` `pedra` `pedra_clara` `ardosia` `ferro` `latao` |
| Terra, palha, madeira | `terra` `terra_seca` `colmo` `palha` `madeira` `madeira_clara` `madeira_escura` `couro` |
| Reboco e tecido claro | `reboco` `linho` `la_crua` |
| Tecidos de cor | `tecido_marrom` `tecido_oliva` `tecido_azul` `tecido_vermelho` `tecido_cinza` `ferrugem` |
| Pele (7 tons, 5 por rampa) | `pele_clara` `pele_media` `pele_escura` |
| Cabelos | `cabelo_preto` `cabelo_castanho` `cabelo_ruivo` `cabelo_grisalho` `cabelo_loiro` |
| Vegetação | `folha_carvalho` `folha_betula` `folha_conifera` `grama` `grama_seca` `casca_carvalho` `casca_betula` |
| Flores (dessaturadas) | `flor_violeta` `flor_amarela` `flor_azul` `flor_rosa` `flor_branca` |
| Água, vidro, fogo, noite | `agua` `vidro` `fogo` `noite` |
| Decalques | `olho` |

`gradlew validateContent` confere cada pixel da arte nova contra a paleta (VT-04) e aponta arquivo e posição. Um material pode usar só um trecho da rampa (`tons: [0, 3]`), por exemplo para um gorro de lã não acender como ferrugem viva.

## 3. Medidas (densidade dobrada, D-086)

Um pixel de arte vale **2×2 pixels de mundo** (`IsoProjection.ART_SCALE = 2`). As medidas de mundo não mudaram: mapas, colisões, portas e luzes ficam onde estavam. O zoom é inteiro (cada pixel de arte vira 1, 2, 3 ou 4 pixels da tela); a escala 1 da v3 mostra a mesma área que a escala 2 do M8. A arte antiga ainda em uso entra no manifesto sem `scale` (vale 4) e aparece ampliada 2×.

| Tipo | Medida em px de arte | Observações |
|---|---|---|
| Tile de chão | losango 64×32 | o chão novo é uma textura de 512×256 que se repete alinhada ao mundo; cada tile a mostra por uma máscara com sangria de borda irregular (§5.3) |
| 1 m de altura | 28 | a câmera do Blender corrige a altura real (39 px) para 28 |
| 1 m na horizontal da tela | 45,25 | diagonal do tile |
| Gente adulta | ~48 px de altura | quadro recortado à menor caixa comum (~52×62); âncora nos pés |
| Folha de personagem | linhas = 8 direções, colunas = animações em sequência | direção 0 = leste do mundo, crescendo para o sul; sem espelhar |
| Árvore | ~170–210 px de altura | duas alturas por espécie; laço de vento de 8 quadros; sombra projetada no próprio sprite |
| Construção | casa de 7×5 m ≈ 480×315 com a sombra | duas faces visíveis, telhado inteiro; janelas na imagem `_brilho` |
| Retrato de NPC | 96×128 | inalterado |
| Ícone de item | 20×20 (M8) | refeito no V6 |

## 4. Como a arte é feita (pipeline)

```
tools/blender/<pasta>/<id>.yaml      o que existe (medidas, roupa, cores = rampas)
tools/blender/**.py                  como se constrói, anima e fotografa
gradlew renderArt [-Pso=id] [-Pforce]  Blender 5.3 em segundo plano, em paralelo, com cache por hash
gradlew pixelize                     só o pixelizador, sobre build/render (itera no visual sem render)
gradlew sheets                       folhas de contato e GIFs em build/revisao
gradlew captures -Proteiro=v1        o jogo fotografado (tools/capturas/v1.yaml)
```

- **Câmera:** ortográfica, elevação 30°, azimute 45°, correção vertical 28/39,19; a origem do objeto cai no canto do pixel da âncora. Superamostragem 4×. Luz: um sol do alto à esquerda, oclusão ambiente, nada mais. Teste do cubo: `gradlew blenderTeste` (um cubo de 1 m cai exatamente num losango, 64×60 px).
- **Passes** (PNG de 16 bits): índice do material, profundidade, emissivo; luz direta com sombra, oclusão, detalhe (luminância da textura).
- **Pixelizador** (`tools/.../art/pixel/Pixelizador.java`): voto por material (nunca média de cor) → valor = ambiente + difusa × luz, vezes a oclusão, mais k × detalhe → tom da rampa em faixas (pontilhado ordenado só em material marcado, nunca em gente) → linhas internas → limpeza → contorno → decalques (olhos, óculos) → brilho. Determinístico: mesmo passe, mesmos bytes (VT-16).
- **Saída:** `assets/arte/<tipo>/<id>.png` + `.json` (quadros, âncora, animações, pontos por quadro: mãos, cabeça, olhos, fumaça) e a entrada em `assets/manifest/arte.yaml` (gerado; tem prioridade sobre `provisorio.yaml`, mesmo ID).

### 4.1 Regras de modelagem
- **Gente** (`kit_humano/corpo.py`): esqueleto padrão de vazios com nomes fixos; corpo de peças rígidas arredondadas; proporção estilizada (pernas um pouco curtas, cabeça um pouco grande, membros grossos — a correção vertical achata, e membro fino some no voto). Roupa por peça (`camisa` com `barra` e `manga`, `calca`, `botas`, `cabelo`, `gorro`, `barba`, `avental`, `cinto`, `bolsa`…), cada uma com sua rampa. Detalhe menor que 2,5 cm não sobrevive: vira decalque.
- **Animação** (`anim/ciclos.py`): `parado` (respira, 1 px), `andar` e `correr` com o pé de apoio plantado por IK de dois ossos. O cliente avança o quadro pela **distância andada** (`stride` no manifesto), não pelo relógio: o pé não desliza em velocidade nenhuma.
- **Árvores** (`natureza/arvore.py`, D-098): copa de poucas massas grandes (uma no alto, um anel em volta), cada uma com a borda recortada em cachos de 4–6 px; folha **lisa, sem textura nem ruído** — o desenho vem da forma e da luz em faixas (realce no alto à esquerda de cada cacho). Ruído por pixel na folha dá cara de render ("folha por folha") e foi reprovado no portão V1. Tronco num tubo que afina, iluminado sem a sombra da copa; galho nunca fura a copa. Vento: a copa inteira balança 1–2 px, cada massa com atraso pequeno — nada de cintilar.
- **Casas** (`construcoes/casa.py`): medidas da pegada do mapa (VT-07); base de pedra baixa, enxaimel com montantes, frechais e mãos-francesas, reboco pontilhado; colmo grosso com fiadas (degraus que fazem linha de sombra), beiral arredondado e cumeeira amarrada; vidro emissivo vai para o `_brilho`.
- **Textura:** entra como variação de valor, nunca como cor. A escala tem de deixar o desenho da textura com 2–4 px de arte; mais fina que isso some na média dos subpixels.

### 4.2 Chão e decoração
- **Chão:** textura de mundo repetível, desenhada por uma máscara de losango com sangria irregular (GroundMasks); o terreno de prioridade maior cobre o vizinho, com um anel escuro de 1 px na divisa. Prioridades: plantio 8 > tábuas 6 > lajota 4 > grama 3 > terra 2 > pedra 1.
- **Decoração** (`assets/config/decoracao.yaml`): tufos (com vento), flores e pedrinhas por terreno, em manchas, longe dos objetos do mapa; determinística por área e tile; não bloqueia nem entra no save.

## 5. Arte por IA (Dreamina)

A IA faz **referência** e **textura** (D-087); o pixelizador faz o resto.

| Uso | Onde fica | Vai para o jogo? |
|---|---|---|
| Prancha frente/lado/costas de personagem | `arte-bruta/referencias/` | não: guia a modelagem |
| Referência de espécie e de arquitetura | `arte-bruta/referencias/` | não |
| Textura de material (casca, folha, colmo, reboco, pedra, grama, terra, lajota, linho, couro) | `arte-bruta/texturas/` → `gradlew importTextures` → `tools/blender/texturas/` (só luminância, repetível, 256 px) | sim, como variação de valor dentro do material |
| Retrato de NPC, ilustração | `arte-bruta/retratos`, `arte-bruta/ilustracoes` | sim, depois do funil de paleta (§7) |

Gerar pela CLI `dreamina-canvas` (canvas "ETRAVA arte"), um nó por imagem, **sempre com teto de créditos**; scripts em `tools/dreamina/` (`referencias_v1.sh`). Seedream 5.5 a 1,5K custa 30 créditos.

Trechos fixos: referência — *"plain flat light grey background, clean concept art, muted earthy desaturated palette, soft even lighting, late medieval rural village, dark fantasy, no modern items, no text"*; textura — *"seamless tileable texture, top-down orthographic view, flat even lighting, no shadows, no perspective, fills the whole frame"*.

**O que não pedir à IA:** sprite final, personagem animado, cenário isométrico.

## 6. Trocar uma arte

Cada asset é um ID no manifesto (`assets/manifest/*.yaml`). A arte nova é gerada (`arte.yaml`) a partir dos YAML de `tools/blender/`: para mudar, edite o YAML ou o script e rode `gradlew renderArt -Pso=<id>`. Arquivo ausente ou quebrado vira um xadrez magenta com aviso no log; o jogo não trava.

## 7. Luz e pós-processamento

- A hora do dia define a cor do céu que multiplica a cena, a saturação, o contraste e a vinheta (`assets/config/daynight.yaml`).
- À noite, janelas e lamparinas acendem: mancha de luz âmbar somada à cena, mais o brilho próprio da vidraça (`_brilho`).
- **Luz de presença** (D-090): personagens nunca recebem menos luz que o céu da hora reforçado; à noite o reforço é azulado.
- No console: `luz off`, `pos-fx off` e `presenca off` desligam cada efeito.

## 8. Retratos e ilustrações

Retratos: Dreamina, `gradlew importPortraits` (96×128 na paleta). Ilustrações: `tools/dreamina/ilustracoes_m7.sh`, `gradlew importIllustrations` (640×360 na paleta). No V2 cada retrato é conferido com o sprite novo.

## 9. Som

Provisório, por síntese (D-071), OGG Vorbis (D-080). Ver a v2 deste guia no histórico do git para a direção completa; nada mudou na Fase V até aqui.

## 10. Interface

Papel e tinta (PRD 10.1) até o V7: fundo `#D8CCB0`, tinta `#241C18`, destaques em ferrugem `#6E2C22`, borda de madeira escura. Fonte Alegreya (D-077). Sem brilho, sem neon, sem marcadores de objetivo.

## 11. Referências internas

Capturas da fatia nova: `docs/capturas/v1/` (antes × depois, dia, entardecer, noite, zoom 1 e 2; folhas de contato e GIFs). Linha de base: `docs/capturas/v0/`. Referências da Dreamina: `arte-bruta/referencias/` (fora do git).
