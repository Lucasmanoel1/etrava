# STYLE — guia visual do ETRAVA (v1)

Vale para toda arte do jogo, feita por IA, por código ou à mão. Direção: **"gótico pintado"** — a luz e a sujeira dos isométricos clássicos, com materiais e silhuetas que dizem a cultura antes do texto (PRD 5.4).

**Estado da arte hoje:** tudo o que aparece no jogo é **arte provisória gerada por código** (`gradlew generateArt`). Ela serve para provar luz, escala e leitura. A arte definitiva substitui arquivo por arquivo, sem mexer em código (seção 5).

---

## 1. Regras gerais

1. **Leitura a distância.** O jogador vê tudo pequeno. Silhueta clara vale mais que detalhe fino.
2. **Luz conta a história.** Luz quente (lamparina, forno, vela) contra ambiente frio à noite; dia suave e poeirento.
3. **Pintado, não fotográfico.** Aparência de guache/óleo, pincelada visível, sem fotorrealismo e sem pixel art.
4. **Saturação controlada.** Quase tudo dessaturado; cor forte só com significado (fogo, sangue, estandarte).
5. **Escuridão é lugar, não regra.** Ruína e noite são escuras; vilarejo ao meio-dia é quente e vivo.
6. **A luz vem do alto, à esquerda.** Faces viradas para a esquerda da tela são claras; para a direita, sombreadas. Sombra no chão cai para a direita.

## 2. Paleta de Pedra-Mansa (Terras de Ferrugem, interior)

| Uso | Cor | Hex |
|---|---|---|
| Ocre (terra, reboco sujo) | ocre | `#9C7A45` |
| Ferrugem (metal velho, folhas secas) | ferrugem | `#8A4E2E` |
| Verde-oliva desbotado (mato, folhagem) | oliva | `#6B7045` |
| Cinza quente (pedra) | cinza quente | `#7F776C` |
| Palha (colmo, feno) | palha | `#B89A5A` |
| Madeira escura | madeira | `#4A3526` |
| Reboco | reboco | `#B9A888` |
| Contorno e sombra funda | quase preto quente | `#241C18` |
| Luz de lamparina | âmbar | `#FFBC66` |

Materiais: pedra irregular, madeira gasta, ferro oxidado, palha. Nada novo, nada limpo.

As outras culturas (Caldris, Selvar, Orra, Nácre, Cantões do Sal) têm paleta própria no PRD 5.4; entram neste guia quando forem produzidas.

## 3. Especificação técnica dos arquivos

O jogo é **2D isométrico 2:1**. Um quadrado de 1 m no chão vira um losango de **128×64 px**. Um metro de altura são **56 px**.

| Tipo | Formato | Tamanho | Observações |
|---|---|---|---|
| Tile de chão | PNG com transparência | 256×128 px | losango de 128×64 no centro; a textura continua para fora e some aos poucos (é o que mistura terrenos vizinhos); precisa emendar com cópias de si mesma |
| Objeto (prop) | PNG com transparência | livre | fundo transparente; sombra suave no chão pintada junto; informar o **ponto de apoio** |
| Construção | PNG com transparência | livre (uma casa de 7×5 m dá ~1050×800 px) | vista de fora, duas paredes e o telhado visíveis; porta numa das duas paredes da frente |
| Brilho noturno (opcional) | PNG com transparência | igual ao da imagem principal | só o que acende: vidraças, chama. O resto transparente |
| Personagem | PNG com transparência | folha de 12 colunas × 8 linhas, quadro de 96×128 px | linhas = 8 direções; colunas = 4 quadros parado + 8 andando; pés no pixel (48, 112) do quadro |
| Retrato de NPC | PNG | 512×640 px | busto, fundo neutro escuro |
| Ícone de item | PNG com transparência | 128×128 px | objeto único, centralizado |

**Ponto de apoio** (`anchor` no manifesto): o pixel da imagem, contado do canto superior esquerdo, que encosta no chão no centro da base do objeto. É ele que o jogo alinha com a posição do objeto no mapa.

**Ângulo.** Todo cenário usa exatamente o mesmo ângulo: câmera a ~30° acima do horizonte, girada 45°. Linhas horizontais do objeto descem na tela na proporção 2 para 1. Imagem em ângulo diferente destoa de tudo e não serve.

## 4. Modelos de prompt para IA de imagem

Sempre no mesmo esqueleto: **[o quê] + [ângulo/enquadramento] + [estilo] + [paleta] + [luz] + [o que evitar]**. Os trechos fixos abaixo não mudam entre pedidos — é isso que mantém o conjunto coerente.

**Trecho fixo de estilo (colar em todos):**
> hand-painted gouache game art, visible brushwork, muted desaturated palette of ochre, rust, faded olive green and warm grey, weathered and worn materials, dark fantasy rural village, soft light from the upper left, no photorealism, no pixel art, no text, no watermark

### 4.1 Objeto do cenário
> Isometric game asset, single **{objeto, ex.: old wooden barrel with rusted iron hoops}**, 2:1 dimetric projection, camera 30 degrees above horizon rotated 45 degrees, centered, soft contact shadow on the ground falling to the right, plain flat magenta background for easy removal, **[trecho fixo]**

### 4.2 Construção
> Isometric game asset, **{construção, ex.: small half-timbered village workshop, rough stone base, dirty plaster walls with dark wooden beams, steep thatched roof, one stone chimney, wooden door and two shuttered windows on the front-left wall}**, exterior only, 2:1 dimetric projection, camera 30 degrees above horizon rotated 45 degrees, front-left wall lit, front-right wall in shade, whole building visible with margin, plain flat magenta background, **[trecho fixo]**

### 4.3 Tile de chão
> Seamless tileable top-down ground texture, **{terreno, ex.: trampled dry grass with patches of bare ochre earth}**, even lighting, no shadows, no objects, no horizon, **[trecho fixo]**

A IA gera a textura vista de cima; a conversão para losango 2:1 com borda suave é feita por ferramenta nossa (a criar quando a primeira textura chegar).

### 4.4 Retrato de NPC
> Portrait of **{personagem, ex.: a tired miller in his fifties, flour on his sleeves, wary eyes}**, bust, three-quarter view, plain dark warm background, natural human proportions, painted like an old oil study, **[trecho fixo]**

### 4.5 Ícone de item
> Game inventory icon, single **{item, ex.: a small iron oil lamp}**, centered, slight three-quarter view, plain flat magenta background, **[trecho fixo]**

### 4.6 Ilustração (mapa, menu, epílogo)
> Wide illustration, **{cena}**, painted on aged parchment, restrained ink linework with gouache washes, **[trecho fixo]**

**Personagens animados não são pedidos à IA de imagem** (D-019): manter a mesma pessoa em 8 direções × 12 quadros é justamente onde essas ferramentas falham. Eles saem de um modelo articulado desenhado por código, que garante consistência.

## 5. Como trocar uma arte provisória pela definitiva

1. Gere a imagem e remova o fundo (PNG com transparência).
2. Salve em `assets/` — por exemplo `assets/props/barril_final.png`.
3. Acrescente uma entrada em `assets/manifest/definitivo.yaml` (crie o arquivo se não existir), **com o mesmo `id`** da provisória:

```yaml
- id: prop.barril
  type: sprite
  file: props/barril_final.png
  anchor: [60, 118]        # pixel que encosta no chão, no centro da base
  source: "Gerado por IA (Dreamina), prompt 4.1 do STYLE.md"
  license: proprio
```

4. Rode o jogo. Entradas fora de `provisorio.yaml` têm prioridade, então a nova imagem aparece no lugar da antiga. Se o arquivo faltar ou estiver quebrado, o jogo mostra um xadrez magenta e avisa no log — nunca trava.

`gradlew validateContent` avisa de arquivos ausentes e de assets sem origem/licença. `gradlew licenses` atualiza o `ASSETS_LICENSES.md`.

## 6. Luz e pós-processamento

- A hora do dia define a cor do céu que multiplica a cena, a saturação, o contraste e a vinheta. A tabela fica em `assets/config/daynight.yaml` e pode ser editada sem recompilar.
- À noite, janelas e lamparinas acendem: cada uma é uma mancha de luz âmbar somada à cena, mais o brilho próprio da vidraça ou da chama.
- Efeitos disponíveis: gradação de cor por hora, vinheta. No console de desenvolvimento, `luz off` e `pos-fx off` desligam cada um, para comparar.

## 7. Referências internas

Capturas da cena de referência com a arte provisória: `docs/capturas/m1/` (`dia.png`, `tarde.png`, `noite.png`, `noite_taverna.png`).
