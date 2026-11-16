# PROGRESS — ETRAVA

Última atualização: 2026-11-16

## Estado atual
- **Fase atual:** **V — revisão visual** (D-084). Documentos em `docs/visual/`.
- **Marco atual:** **V7 — Interfaces** (`docs/visual/V7.md`). V6 aprovado pelo diretor em 2026-11-16 ("continue").
- **Status:** **PARADO no fim do V7**, à espera da aprovação do diretor (seção "Parada do V7", abaixo). Feito por subagentes em Sonnet, em ondas, com o orquestrador integrando (D-134).
- MVP: M0 a M7 concluídos; M8 com o trabalho de máquina concluído e a aprovação humana pendente (seção "Fechamento do M8", abaixo). As pendências do M8 não bloqueiam a Fase V.

## Fase V — visão rápida
| Marco | Nome | Situação |
|---|---|---|
| V0 | Correções e ferramentas (zoom, rolagem, modo de depuração, visibilidade, `gradlew captures`) | **concluído** |
| V1 | Pipeline Blender + pixelizador, densidade dobrada, fatia — **portão visual** | **concluído** (aprovado, D-104) |
| V2 | Gente (25 humanos, animações de vida comum) | **concluído** (aprovado) |
| V3 | Criaturas e combate | **concluído** (aprovado) |
| V4 | Natureza e terreno | **concluído** (aprovado) |
| V5 | Arquitetura e exteriores | **concluído** (aprovado) |
| V6 | Interiores, objetos e itens | **concluído** (aprovado) |
| V7 | Interfaces | **concluído; aguarda aprovação** |
| V8 | Acabamento e entrega — **portão final** | — |

## Feito no V1
- **Pipeline de ponta a ponta** (`tools/blender/`, `tools/.../art/pixel/`): YAML do objeto → Blender 5.3 em segundo plano (6 processos, cache por hash) → passes PNG 16 bits → pixelizador → folha PNG + JSON em `assets/arte/` → `assets/manifest/arte.yaml` (gerado). Tarefas: `renderArt`, `pixelize`, `sheets`, `importTextures`, `blenderTeste`. Teste do cubo: 1 m cai exatamente num losango, 64×60 px.
- **Pixelizador:** voto por material, valor por luz × oclusão + textura, rampas da paleta em faixas, linhas internas por salto de profundidade, limpeza, contorno seletivo, decalques (olhos, óculos), brilho noturno, montagem com recorte comum. 7 testes unitários com passes sintéticos (mesmo passe = mesmos bytes, nada fora da paleta, contorno fechado, voto sem mistura, sombra, brilho).
- **Paleta v3** (`tools/art/paleta.yaml`): as 53 cores do M8 + rampas novas (~70 cores, 47 rampas). Validador: **VT-04** (cada pixel da arte nova na paleta, com arquivo e posição) e parte do **VT-05** (8 direções, animações do papel, âncora).
- **Densidade dobrada (D-086):** `ART_SCALE = 2`; escala por entrada do manifesto (arte do M8 ampliada 2×, sem quebrar nada); câmera começa na escala 1 (720p) ou 2 (1080p).
- **A fatia:**
  - **aprendiz** e **Avel**: kit humano próprio (D-091), `parado`, `andar`, `correr` em 8 direções, 8 quadros cada; pé de apoio plantado por IK; o cliente avança o andar pela distância (D-095). Avel com gorro de lã, óculos na testa, barba grisalha, avental de couro e mangas de baixo; o aprendiz com camisa de linho, cinto, bolsa a tiracolo.
  - **carvalho** e **bétula**, duas alturas cada, laço de vento de 8 quadros com fase por posição, sombra projetada (substituem `prop.arvore` e `prop.arvore_b` no mapa inteiro, inclusive na mata além da borda).
  - **casa da oficina**: pegada 7×5 do mapa, base de pedra, enxaimel com reboco, colmo com fiadas, beiral, cumeeira amarrada, chaminé, porta, três janelas que acendem à noite.
  - **chão** (grama, terra, lajota) em textura de mundo com transição por máscara irregular (D-094) e **decoração** (tufos com vento, flores, pedrinhas) por `assets/config/decoracao.yaml`.
- **Dreamina:** 5 referências (aprendiz, Avel, carvalho, bétula, casa) e 10 texturas, **450 créditos** (saldo 7.850). Originais em `arte-bruta/`; texturas pelo funil em `tools/blender/texturas/`.
- **`STYLE.md` v3**, decisões D-091 a D-097, D-086 atualizada.
- **Testes:** `gradlew build` com **152 testes** (145 + 7 do pixelizador), validador sem erros; `gradlew vt03` **112/112**; **VT-16**: refazer a fatia inteira do zero (`renderArt -Pforce`) dá os mesmos bytes.

## V7 — Interfaces (2026-11-16)
**Feito:**
- **Tema** (D-135): peças em `assets/ui/` por `gradlew :tools:generateUi`, pixel a pixel na paleta (madeira com cantoneiras de ferro, pergaminho, couro, botões nos 4 estados, abas-fita, rolagem, molduras, barras, livro, documento, bilhete, balões, ícones, 6 cursores). Prancha: `docs/capturas/v7/prancha_tema.png`.
- **Base** (D-136), em `game-client/.../ui/`: `Escala` (inteira por resolução), `Tema` (um só, criado no `EtravaGame`), `Ui`, `Foco` (teclado), `Hud`; como montar uma tela: `package-info.java`. Letra de título própria nos painéis.
- **Telas**, todas no tema novo, uma classe por tela em `ui/telas/` (o tema antigo saiu do código):
  - menu principal com névoa, brasas e fumaça; novo jogo; opções; salvar/carregar em cartões; pausa;
  - HUD: plaquinha de couro com sol/lua, dia, hora e bolsa; vida e fôlego em barras com ícone; avisos como bilhetes; balões; barras dos inimigos e números de dano;
  - conversa com retrato emoldurado e opções numeradas; comércio e contêiner com abas por categoria e dica do item; mochila em grade, com o **boneco vestido** e os encaixes de equipamento; ficha;
  - caderno em livro aberto (lista à esquerda, detalhe à direita, abas-fita); documento; mapa emoldurado; epílogo, derrota, morte, "resolver";
  - console de depuração no tema.
- **Teclado** em toda tela (setas, Tab, Enter, Esc; foco visível). **Cursores** ligados aos alvos do mundo (D-137).
- **Verificação:**
  - `gradlew build` verde, validador sem avisos (VT-04 inclui `assets/ui/`);
  - **VT-08:** teste automático com 80 itens no comércio, 60 fatos no caderno e 12 opções na conversa (roda, teclado, último item visível);
  - **VT-09:** `v7_vt09 -Pestrito`, 18 telas × 8 resoluções × 3 tamanhos de texto = 432 cenas, **0 problemas** de limite; capturas olhadas por amostra (o que não foi aberto está em "Problemas conhecidos");
  - `--bench=10 --area=vila`: 369 FPS de média, 1 % pior 224, 159 chamadas (antes do passe final, na mesma condição: 363 / 220 / 159).
- **Dreamina:** nenhum crédito.

## Parada do V7 — o que olhar
- `docs/capturas/v7/prancha_tema.png` (as peças), `docs/capturas/v7/menu.gif`.
- `docs/capturas/v7/telas/`: cada tela em 1280×720, 1920×1080 e 3840×2160 (`menu`, `novo`, `opcoes`, `salvar`, `carregar`, `pausa`, `conversa`, `comercio`, `conteiner`, `mochila`, `ficha`, `caderno*`, `documento`, `mapa`, `epilogo`, `derrota`, `morte`, `rendido`, `vt08_*`).
- `docs/capturas/v7/hud/`: dia, noite, avisos, balões nos três zooms, luta, console.
- No jogo: `gradlew run` (IntelliJ: Gradle → ETRAVA → Tasks → application → run). I mochila, C ficha, J caderno, M mapa; fale com alguém; F1 → `comercio posto`, `vt08 tudo`.
- Roteiros: `gradlew :game-client:captures -Proteiro=v7_telas -Pso=mochila` (uma tela), `-Proteiro=v7_vt09 -Pestrito` (tudo, ~9 min), `-Proteiro=v7_hud`. GIF: `-Pso=menu_gif` e depois `gradlew :tools:menuGif`.
- Roteiro manual: `docs/TESTES_MANUAIS.md`, seção "Fase V — V7 (interface)".

**A aprovar:**
1. **O tema** (é o que o jogador vê em toda tela): madeira, pergaminho com trama, couro, letra de título.
2. O caderno em livro aberto: um clique escolhe a anotação, o segundo (ou Enter, ou o botão) marca. Antes um clique já marcava.
3. O HUD sem número permanente de vida (aparece com o mouse em cima e quando a vida muda).
4. D-134 a D-137.

**Problemas conhecidos do V7:**
- **O critério "eu joguei uma conversa, uma troca longa, a mochila e o caderno em 720p e em 4K" não foi cumprido por jogo de verdade:** foi por captura e por teste automático. Precisa de alguém jogando.
- **Cursores nunca vistos:** captura de tela não mostra o cursor do sistema. Conferir jogando.
- **Miniatura dos saves** não vista com imagem real (os saves das capturas não têm miniatura).
- **Balão com cauda à direita** (`ui.balao_dir`) só conferido pelo código: nenhuma captura tem alguém falando junto à borda direita.
- Capturas não abertas uma a uma: menu em 4K, console, documento, epílogo, derrota, morte, "rendido"; a aba Pessoas do caderno só foi vista vazia.
- O bilhete de aviso cobre o canto de cima à direita dos painéis grandes.
- A trama do pergaminho aparece como manchas pontilhadas nos painéis grandes; discreta, mas visível.
- No comércio, esquerda/direita andam em linha, sem pular de uma coluna para a outra; com poucos itens, uma coluna fica vazia.
- Na mochila os encaixes em volta do boneco ficam soltos no quadro; a quantidade do item é pequena em 720p.
- Em 720p (escala 1) a moldura de madeira fica fina; em 1080p fica no ponto.
- Não há comando de largar item no núcleo, então a mochila não tem "largar". Não existe aba "diário": o caderno mantém as 5 abas do jogo.
- A janela de captura sem moldura (para 4K verdadeiro) só foi testada nesta máquina. A checagem de reticências do `LimitesUi` lê um campo privado do `Label`.
- O GIF do menu tem 6 MB.

## V6 — Interiores, objetos e itens (2026-11-16)
**Feito:**
- **Casca dos cômodos** (D-129): paredes por casa (oficina, taverna, moinho, curandeira), pisos de interior, parede da frente cortada a 0,4 m e borda reta do piso (`CascaInterior.java`, `assets/config/interiores.yaml`). Fim da pendência do M8.
- **Móveis** (D-130): mesa (3), mesa longa, balcão, lareira com fogo em laço, bancada, mó, cama (3), baú, ervas; velas nas mesas e no balcão.
- **Estrutura enterrada** (D-131): ardósia em junta corrida, paredes pela colisão (`ParedesEstrutura.java`), luz do buraco, fibra, entulho, laje.
- **Itens** (D-132): 43 modelos e ícones de 32×32; nenhum ícone do M8 em uso.
- **Luz viva** (D-133): fogo que tremula e feixe de sol com poeira (`LuzViva.java`, `FeixeJanela.java`, `assets/config/luz_viva.yaml`).
- **Verificação:**
  - `gradlew build` verde: 175 testes, validador sem avisos (VT-04, VT-07);
  - capturas v6 refeitas e olhadas: paredes 12, luz 13, estrutura 10, itens 6; capturas v5 refeitas (exteriores sem mudança);
  - `--bench=10 --area=vila`: **417 FPS** de média, 1 % pior 301, 159 chamadas (V5: 398 e 158).
- **Dreamina:** nenhum crédito.

## Parada do V6 — o que olhar
- `docs/capturas/v6/`:
  - `prancha_icones.png`, `prancha_moveis.png`;
  - `paredes/*_frente.png`, `*_dia.png`, `*_noite.png` (os 4 interiores);
  - `luz/` (feixe de manhã, meio-dia e tarde; lareira à noite);
  - `estrutura/entrada_sem.png`, `camara_com.png`, `corredor_*`, `nicho_com.png`, `camara_longe.png`;
  - `itens/mochila_*.png`, `comercio_*.png`.
- No jogo: `gradlew run` (IntelliJ: Gradle → ETRAVA → Tasks → application → run). F1 → `area oficina` (ou `taverna`, `moinho`, `curandeira`), `hora 12` para o feixe, `hora 22` para o fogo; `area estrutura`, `dar lamparina`, `equipar lamparina`.
- Roteiros: `gradlew :game-client:captures -Proteiro=v6_paredes` (ou `v6_luz`, `v6_estrutura`, `v6_itens`) `-Psaida=docs/capturas/v6/<nome>`.

**A aprovar:**
1. **A estrutura enterrada** (D-131): a ardósia em junta corrida, o feixe pontilhado, a laje e os sulcos, as fibras.
2. D-128 a D-133.

**Problemas conhecidos do V6:**
- Com a lamparina, a luz quente deixa a ardósia da estrutura acastanhada; o frio só aparece sem ela ou perto do feixe.
- O feixe de sol passa por cima de móveis e paredes; no piso de pedra do moinho a mancha puxa para o esverdeado.
- No interior a lareira ilumina também de dia (escuridão do cômodo fixa, já era assim).
- Na curandeira, o suporte de ervas fica na frente da boca da lareira (posição do mapa).
- A brasa da forja, do lado de fora, continua uma mancha clara chapada (pendência do V5).
- Itens pequenos em cima das mesas (dados, moedas) somem a 1×. Pares de ícone parecidos: cajado × lança curta, gibão × avental, carta × bilhete × lista.
- `piso_terra_batida` existe e nenhum cômodo usa. Tear/fiandeira, e barril próprio de taverna, não entraram (D-130).
- Sem teste unitário de `ParedesEstrutura` e de `CascaInterior`.
- Entrar e sair de cada cômodo jogando, e a oclusão caso a caso, não foram testados por uma pessoa.

## V5 — Arquitetura e exteriores (2026-11-15)
**Feito:**
- **Kit de construção** (D-124, `construcoes/casa.py`): enxaimel, pedra ou tábua; duas águas, quatro águas ou meia-água; colmo, tabuinha ou lousa; chaminés, alpendre, puxado, tabuleta, ervas, peças soltas, pás do moinho.
- **As 9 construções** refeitas, com a mesma pegada e a porta no mesmo lugar (D-125):
  - taverna, oficina, moinho (pás que giram), Posto, curandeira, celeiro;
  - `casa_pequena` (3 variantes), `casa_media` (3) e `casa_estreita` (2), escolhidas pela posição.
- **22 objetos de fora** (D-127, `construcoes/objetos.py`): cercas, poço, lampião, banca, tenda, carroça, barril, caixote, feno, sacos, espantalho, lápides, marco, placa, estaca, bigorna, forja com brasa, entulho, buraco, terra remexida, laje, banco.
- **Cliente** (D-126): a luz das janelas e a fumaça saem dos pontos do JSON da arte; fumaça nas chaminés e na forja.
- **VT-07** (`ValidaConstrucoes.java`, dentro do `validateContent`): pegada da ficha igual à do mapa, corpo dentro da pegada, porta desenhada perto do ponto de porta em cada lugar do mapa.
- **Verificação:**
  - `gradlew build` verde: 168 testes, validador sem erros (VT-04 e VT-07 passam);
  - capturas v5: 16/16, olhadas (dia, entardecer, noite);
  - nenhum exterior da vila ou da estrada usa mais a arte antiga;
  - `--bench=10 --area=vila`: **398 FPS** de média, 1 % pior 289, 158 chamadas por quadro.
- **Dreamina:** nenhum crédito.

## Parada do V5 — o que olhar
- `docs/capturas/v5/`:
  - `prancha_construcoes.png` e `prancha_variantes.png`;
  - `prancha_objetos_pequenos.png` e `prancha_objetos_grandes.png`;
  - `vila_dia`, `vila_entardecer`, `vila_noite` (vista de longe, todas as casas);
  - `praca_*`, `oficina_forja*`, `taverna*`, `moinho`, `celeiro_campo`, `curandeira`, `norte`, `cemiterio`, `posto*`.
- No jogo: `gradlew run` (IntelliJ: Gradle → ETRAVA → Tasks → application → run).
  - `hora 22` para as janelas acesas; perto do moinho, as pás giram; fumaça nas chaminés.
  - F1 → `area estrada`, `tp 63 20` para o Posto.

**A aprovar:**
1. **A identidade da taverna, da oficina, do moinho e do Posto** (D-125).
2. O moinho de vento, sem roda de água.
3. D-124 a D-127.

**Problemas conhecidos do V5:**
- **Desempenho:** 398 FPS e 158 chamadas por quadro na vila. No V4 eu tinha anotado 708 FPS e 65 chamadas, mas não sei se com a mesma posição e hora, então a queda real não está medida. Está dentro do VT-10 (M8: 503 FPS; queda de 21 %). As casas novas dividem páginas do atlas com as árvores; rever no V8.
- **O roteiro manual de entrar em cada porta clicando (D-072) não foi feito**: precisa de alguém jogando. O VT-07 só confere a posição.
- A oclusão com as casas novas (telhado alto sobre o jogador) não foi conferida caso a caso; a regra de translucidez é a mesma de antes.
- Cada construção isolada nas 4 horas do dia (tarefa 8) não foi capturada: há a prancha de dia e as cenas de dia, entardecer e noite.
- O puxado da `casa_pequena_c` fica na face oeste e não aparece.
- O monte de feno está redondo demais; a brasa da forja é uma mancha chapada.
- A fumaça é discreta; se pedir mais, aumento a taxa.
- Água-furtada, escada externa e varal não entraram (D-124, D-127). `fibra` e `luz_buraco` ficam para o V6.

## V4 — Natureza e terreno (2026-11-15)
**Feito:**
- **Árvores novas** (gerador próprio, sem Sapling — D-118), cada uma com laço de vento de 192 quadros:
  - castanheiro (3), freixo (3), pinheiro-silvestre (3, copa "guarda-chuva") e salgueiro (2, folha em cortina);
  - pincéis de folha novos no `folhas.pxo`; rampas novas só com cores que já estavam na paleta.
- **Espécie por área e região** (D-119, `Especies.java`, seção `especies` da `decoracao.yaml`):
  - vila: carvalho, castanheiro e freixo, com salgueiro junto ao moinho;
  - estrada: mais pinheiro;
  - as árvores além da borda seguem a mesma regra.
- **A árvore de Nalia** (D-120): peça única, `prop.arvore_nalia_morta/_viva/_cortada`, com o mesmo esqueleto nos três estados; o `props.yaml` aponta para ela.
- **Arbustos e miúdos** (D-121):
  - sabugueiro, zimbro, arbusto de folha larga e amora;
  - capim alto com espiga, capim seco, juncos, samambaia e cinco flores do campo;
  - cogumelos, folhas no chão, pedregulhos e troncos caídos (só além da borda);
  - `prop.pedras` refeito com a mesma pegada.
- **Decoração** na vila e na estrada, nada a 1 m de porta; sub-bosque além da borda (`alem_da_borda`).
- **Chão** (D-122): `pedra`, `plantio` e `tabuas` novos (sulcos e assoalho por textura procedural, `texturas/procedurais.py`). Nenhum terreno de mapa usa mais a arte antiga.
- **Vida ambiente** (D-123, `Ambiente.java`):
  - folhas que caem com as rajadas;
  - pássaros e borboletas de dia;
  - vaga-lumes (com luz própria) e mariposas nos lampiões à noite;
  - opção **Detalhes do ambiente: Alto/Baixo**.
- **Verificação:**
  - `gradlew build` verde, validador sem avisos;
  - capturas v4: 11/11;
  - `--bench=10` na vila: **708 FPS** de média, 1 % pior 138, 65 chamadas por quadro, com decoração, vento e vida ambiente (era 1.106 no V1; queda de 36 %, dentro do VT-10).
- **Dreamina:** nenhum crédito.

## Parada do V4 — o que olhar
- `docs/capturas/v4/`:
  - `especies.png`: as espécies lado a lado;
  - `nalia_estados.png`: morta, viva e cortada;
  - `miudos.png`;
  - `vila_manha_longe`, `vila_entardecer`, `vila_noite`;
  - `moinho_nalia_*` (os três estados no lugar);
  - `pomar`, `borda_vila`, `estrada`, `estrada_perto`, `posto`.
- No jogo: `gradlew run`.
  - F1 → `area estrada` para os pinheiros;
  - `tp 46 47` na vila e `prop arvore_nalia viva` (ou `cortada`) para a árvore de Nalia;
  - à noite (`hora 22`), vaga-lumes e mariposas.
- Prancha nova: `python tools/revisao/prancha.py SAIDA ESCALA POR_LINHA natureza/nome …`.

**A aprovar:**
1. **Os três estados da árvore de Nalia** (D-120).
2. As espécies e onde cada uma aparece (D-119).
3. Grama em três tons fora do V4 (D-122).
4. D-118 a D-123.

**Problemas conhecidos do V4:**
- Os avisos "encosta na borda" do castanheiro, do pinheiro e da árvore de Nalia são a ponta da sombra projetada, cortada no quadro. Nas capturas não aparece.
- O capim alto tem espigas que, num quadro ou outro, parecem soltas da haste.
- O assoalho (`tabuas`) é dos interiores e será revisto no V6.
- Os GIFs de vento não foram gerados para a parada. Para vê-los: `gradlew sheets` (vão para `build/revisao/`).

## V3 — Criaturas e combate (2026-11-15)
**Feito:**
- **Combate humano** (`anim/combate.py`):
  - os golpes `golpe_corte`, `golpe_estocada` e `golpe_pesado`, de 6 quadros: a primeira metade é o preparo (o aviso do inimigo) e o impacto cai no meio;
  - `dano` (3 quadros), `cair` (6), `caido` (1) e `levantar` (5).
- **Jogador:**
  - as 3 roupas com as 16 animações;
  - 8 camadas, cada uma numa ficha que roda em paralelo (`aprendiz_c_*.yaml`, `so_camadas`): chapéu, capuz, faca, martelo, cajado, lança curta, machadinha e lamparina (com brilho).
- **Saqueador:** golpe (machadinha), dano, cair e caído; 3 variações de roupa e cor (`saqueador_b`, `saqueador_c`).
- **Lobo** (`criaturas/lobo.py`, `tarefas/lobo.py`):
  - quadrúpede próprio, cinzento, com colar de pelo e dorso escuro;
  - trote em pares diagonais, galope com a coluna que dobra;
  - bote: agacha no aviso e salta no impacto, com a boca aberta;
  - dano, morrer, morto e deitado.
- **Vulto** (`criaturas/vulto.py`):
  - sobre o esqueleto humano, com braços até o joelho e curvatura máxima;
  - massa de terra encaroçada, sem cabeça nem rosto, membros de raiz com veios claros, trapos e fumaça em pontilhado;
  - golpe com o tempo do pesado; ao morrer, afunda e se desfaz.
  - Não confirma nada da camada V2/V3.
- **Cliente** (D-117):
  - golpe pela arma da mão principal;
  - recuo ao levar golpe;
  - corpo que fica no chão; o jogador se levanta depois da derrota;
  - camadas de cabeça e das duas mãos;
  - luz da lamparina na mão;
  - variações do saqueador;
  - efeitos: impacto, gotas com parcimônia, terra do vulto, poeira, aviso do golpe.
- **Ferramentas:**
  - transparência por pontilhado (D-115) e nada de pixel solto em quem anda;
  - despacho genérico de tarefas e `-Prebase` (D-116);
  - aviso de desenho cortado na borda do quadro;
  - `puxar` no console;
  - roteiro `tools/capturas/v3.yaml`.
- **Verificação:**
  - `gradlew build`: 168 testes, validador sem erros (VT-04, VT-05 com os papéis de lobo, vulto, saqueador e jogador, VT-15);
  - capturas v3: 6/6.
- **Dreamina:** 3 referências, 90 créditos.

## Parada do V3 — o que olhar
- `docs/capturas/v3/`:
  - `posto_saqueadores.png`: o bando, cada um com uma roupa;
  - `estrada_lobos.png`: entardecer;
  - `estrutura_vulto.png`: no escuro, com a lamparina;
  - `derrota_acordar.png`;
  - as versões `_longe`.
- `docs/capturas/v3/lobo_contato.png` e `vulto_contato.png`: todas as animações nas 8 direções.
- `docs/capturas/v3/gifs/`: os três golpes do jogador, dano, cair e levantar; o golpe do saqueador; cada animação do lobo e do vulto.
- No jogo: `gradlew run`.
  - F1 → `area estrada`, `dar faca`, `equipar faca`, `encontro saqueadores_posto`, `puxar`;
  - ou `encontro lobos_estrada`, `puxar`;
  - para o vulto: `area estrutura`, `dar lamparina`, `equipar lamparina`, `encontro vulto_camara`, `puxar`.

**A aprovar:**
1. **A aparência do vulto.** É a única criatura sobrenatural do Capítulo I.
2. O lobo.
3. Os golpes por arma.
4. D-115 a D-117.

**Problemas conhecidos do V3:**
- Visto de frente e de costas, o lobo continua estreito (é o ângulo).
- A arma do jogador é pequena a zoom 1. A faca quase não aparece; a lança e o cajado, sim.
- Os golpes de 6 quadros são curtos a 48 px. Se pedir mais força, aumento o arco do braço.
- O roteiro manual de combate do `TESTES_MANUAIS.md` precisa de alguém jogando.

## V2 — Gente (em andamento)
**Feito:**
- **Fichas:** 24 em `tools/blender/personagens/` (23 NPCs + saqueador). Cada uma tem idade, ofício, um traço que identifica a pessoa de longe, jeito de andar (`jeito`), trabalho e `dorme` (só quem dorme à vista).
  - Ficam com `rascunho: true` até o kit cobrir as peças: o `renderArt` só as faz com `-Pso`.
- **Pranchas na Dreamina:** 22, a partir dos retratos (i2i), em `arte-bruta/referencias/v2/` (fora do git). Gasto: 660 créditos.
- **Kit humano V2:**
  - `kit_humano/roupa.py`: saia, corpete, colete, gibão, casaco, capa, capuz, chapéus, bandana, lenços, cachecol, xale/manta, corda e ervas;
  - cabelos: preso, trança, longo, calvo, careca e penteado;
  - barbas: cheia, curta, longa, bigode e por fazer;
  - 15 ferramentas de ofício;
  - corpo feminino, barriga e braços grossos;
  - saias e abas com peso no quadril.
- **Animações** (`anim/vida.py`): sentar, sentado, trabalhar (bancada, martelo, chão), conversar e dormir (deitado de lado, encolhido).
- **Cliente:**
  - a animação de quem está parado sai da atividade da agenda (`assets/config/animacoes.yaml`), com fase própria por NPC;
  - quem está sentado é desenhado no banco mais próximo, e quem dorme, na cama;
  - página do atlas de 8192² (D-107).
- **Ferramentas:**
  - cache de render por objeto (D-105);
  - Blender na GPU, no máximo 3 de cada vez (D-106, `local.properties`);
  - VT-05 completo (animações e quadros por papel) e VT-15 (pixel isolado). Valem quando todos os humanos estiverem na folha nova;
  - roteiro de capturas `tools/capturas/v2.yaml`.
- **Lotes renderizados:**
  - 1: ferreiro, Celi, Anselmo, Bento, Ciro;
  - 2: comerciante, curandeira, estranho, estudioso, Firmino.

- **Lotes 3 a 5 e passada final (2026-11-14):** os 27 humanos com o kit final: 23 NPCs, saqueador, o jogador e as roupas dele (gibão e avental) mais as camadas de chapéu e capuz.
- **Kit (D-109):**
  - remendos no gibão do saqueador;
  - cartola do registrador com copa reta;
  - hastes e cabos sem subdivisão: a lança da Vessa e o cajado do viajante não somem mais na ponta.
- **VT-15 (D-108):**
  - o validador mede o pé de apoio pelos pontos `pe_e`/`pe_d` contra o chão que o cliente anda;
  - a medida achou três defeitos, todos corrigidos:
    - o pé ia junto com o balanço do quadril (`_planta`);
    - a passada no JSON estava sem a escala do corpo;
    - a perna esticava demais no toque do calcanhar (`_alcanca`);
  - todos abaixo de 1 px: o pior era 2,2 e caiu para ~0,8.
- **Camadas do jogador (§6.4):**
  - o corpo muda pela roupa (`assets/config/jogador.yaml` + `Vestimenta`);
  - chapéu e capuz são desenhados por cima, no mesmo quadro.
- **Pixelização em paralelo (D-110):** 3 objetos de cada vez; os 36 em 14 min.
- **Prancha "quem é quem"** (`gradlew sheets` → `build/revisao/quem_e_quem.png`): os 27 lado a lado, com os pés na mesma linha, de frente e de perfil, no tamanho do jogo e a 4×.
- **Verificação:**
  - `gradlew build`: 167 testes, validador sem erros (VT-04, VT-05 e VT-15 passam para humanos);
  - capturas v2: 9/9, visibilidade 8/8.
- **Animação no lugar certo (D-111):** trabalho, assento e cama só perto do objeto ou do terreno que pedem (`lugares` em `animacoes.yaml`); fora disso, parado. 168 testes.

## Parada do V2 — o que olhar
- `docs/capturas/v2/quem_e_quem.png`: todos lado a lado. A faixa de baixo está a 4×.
- `docs/capturas/v2/taverna_noite.png`, `praca_manha_perto.png`, `forja.png`, `campo.png`, `posto.png`, `praca_almoco.png`, `vila_manha_longe.png`.
- `docs/capturas/v2/gifs/`: andar (aprendiz, Avel, ferreiro, Vessa, registrador, Pipo, Tobias), martelo, bancada, chão, sentado, dormir.
- No jogo: `gradlew run`. F1 → `hora 20`, `area taverna`; ou `tp 31 37`, `hora 8` para a praça.

**A aprovar:**
1. A aparência de cada NPC de camada A: Avel, Nália, curandeira, ferreiro, estranho, registrador, agrimensora.
2. As decisões D-108 a D-114.
3. A skin nova do aprendiz: `docs/capturas/v2/gifs/aprendiz_*.gif`.

**Pedidos do diretor depois da 1ª parada (2026-11-15), feitos:**
- **Animação no lugar certo (D-111):** trabalho, assento e cama só perto do objeto ou do terreno; fora disso, parado.
- **Ferramenta só trabalhando (D-113):** ficam sempre na mão só a bengala, o cajado do viajante, a lança da Vessa e a machadinha do saqueador.
- **Pernas com anatomia em todos (D-113):** coxa que afina, joelho, panturrilha, tornozelo fino, calça franzida na bota, dobra do cano, V entre as pernas no parado.
- **O aprendiz:** calça de lã escura, separada da bota de couro.
- **Retratos conferidos (D-114):** nenhum refeito, 0 créditos.
- **Render (D-112):** 5 Blender (3 GPU + 2 CPU). Pixelização com 2 de cada vez (`pixel.threads=2`): com 3, as árvores apertaram a memória e o Claude Code derrubou a tarefa.
- **Verificação:** 168 testes, validador sem erros (VT-04, VT-05, VT-15), vt03 112/112, capturas 9/9.

**Problemas conhecidos do V2:**
- O cache de render das árvores está em dia, mas a pixelização delas leva ~2 GB cada. Com pouca memória livre, rode `pixelize -Pso=<personagens>`.

## Portão V1 — vento a 60 qps e atlas indexado (D-103)
Pedido: árvores com mais fluidez, 96 quadros a 60 qps, e o mesmo no resto que balança; paleta liberada.

Feito:
- **Vento:**
  - carvalho, bétula, macieira, pereira e os tufos de grama passaram a ter 96 quadros a 60 qps (laço de 1,6 s);
  - o vento ganhou um segundo harmônico e um balanço de lado.
- **Atlas indexado:**
  - cada pixel guarda 1 byte (o índice da cor), e a cor sai do shader `paleta.frag`;
  - ocupa ~144 MB de textura. Só as árvores, em RGBA, dariam ~307 MB.
- **Desempenho:** `--bench` na vila dá 1.253 FPS, com 103 chamadas por quadro.
- **Verificação:**
  - 156 testes e validador sem erros;
  - vt03 112/112 e capturas 13/13, com as cores conferidas a olho de dia, de noite e na vista de longe.

O que olhar:
- `docs/capturas/v1/folhas/*_vento.gif` (12 árvores + `tufo_1_vento.gif`);
- no jogo: `gradlew run`, perto de árvores.

## Portão V1 — versão do diretor aplicada (D-102)
O diretor fez a própria corrida do aprendiz (rig orgânico no Blender) e pediu para pô-la no jogo e aplicar ao Avel e às outras animações; depois, seguir os trabalhos.

Feito:
- **Pele contínua** (`kit_humano/pele.py`): camisa, calça, botas, alça e avental deformados por esqueleto, sem "contas" no joelho.
- **Junta `peito`.**
- **Animações:**
  - a corrida dele, em tabela (`anim/correr_diretor.py`);
  - andar e parado no mesmo estilo;
  - sinal da inclinação do tronco corrigido.
- **Avel:** avental preso à coluna.
- **Aprendiz:** túnica mais longa, por causa do VT-03.
- **Verificação:** 156 testes, validador, vt03 112/112 (mínimo 0,62), capturas 13/13.
- A pasta `docs/capturas/minha versao de andar/` foi apagada, como pedido.

O que olhar:
- `docs/capturas/v1/folhas/aprendiz_direcoes.png`, `avel_direcoes.png` e os GIFs.

## Portão V1, 3ª rodada (cont.) — gente melhorada (D-101)
Pedido: melhorar o personagem e as animações com Blender e Pixelorama.

Feito:
- **Corpo:**
  - cabeça maior, com queixo e orelhas, e queixo erguido para a câmera: o rosto agora lê, com olhos, testa e queixo;
  - cabelo com linha do cabelo, em vez de capacete;
  - tronco com cintura, peito e ombros;
  - pernas mais finas e bota separada da calça.
- **Animações:**
  - andar com contrarrotação, troca de peso e braço natural;
  - correr com braço dobrado a 90°;
  - parado com respiração, troca de peso e olhar;
  - passada mais curta.
- **Pixelorama:** aprendiz e Avel viram `.pxo` de 192 quadros (`tools/art/acabamento/personagens/`), com tag por animação e direção e camada `retoque` preservada. A folha final é exportada pelo Pixelorama.
- **Verificação:** 156 testes, validador, vt03 112/112, capturas 13/13.

O que olhar:
- `docs/capturas/v1/folhas/aprendiz_direcoes.png` e `avel_direcoes.png` (linhas: parado, andar, correr; colunas: as 8 direções);
- os GIFs `aprendiz_*.gif` e `avel_*.gif`;
- no jogo, `docs/capturas/v1/oficina_dia_perto.png`.

## Portão V1, 3ª rodada (2026-11-13) — árvores v2 (D-100)
Pedido: árvores com cara de pixel art artesanal (espécies, idades, caráter), Blender para a forma e Pixelorama para o acabamento; o Pixelorama fica liberado para toda a arte.

Feito:
- **Forma no Blender:**
  - galhos por colonização do espaço;
  - tronco sinuoso com colo e raízes;
  - copa aberta com vãos;
  - normal da copa: a copa se ilumina como um volume só.
- **Pintor de folhagem** (`Folhagem.java`):
  - cachos carimbados com pincéis desenhados em pixel;
  - silhueta recortada em folhas e vão escuro sob o tufo da frente;
  - o desenho anda com o vento.
- **Pixelorama no pipeline:**
  - pincéis em `tools/art/pinceis/folhas.pxo`;
  - cada folha de árvore vira um `.pxo` em camadas (`tools/art/acabamento/`), com a camada `retoque` preservada;
  - a folha final é exportada pelo Pixelorama.
  - Guia: `tools/art/LEIA-pixelorama.md`.
- **14 variantes** sorteadas pela posição, com o mapa intacto:
  - carvalho: jovem, adulto, antigo, torto;
  - bétula: jovem, adulta, dupla, alta;
  - macieira e pereira: 2 cada, com frutos;
  - árvore seca: 3, incluindo a da Nália;
  - toco.
- **Paleta:** 92 → 99 cores (verdes do carvalho com sombra fria e luz quente).
- **Atlas:** página de 4096² (uma textura só): 1.091 FPS no zoom 1 e 758 na vista de longe.
- **Verificação:**
  - `gradlew build`: 156 testes (4 novos: `.pxo`, pincéis, folhagem determinística e na paleta, desenho que acompanha o tufo);
  - validador sem erros;
  - vt03 112/112;
  - capturas 13/13;
  - mesma entrada → mesmos bytes, passando pelo Pixelorama.
- **Dreamina:** 0 créditos (usei a referência de carvalho que já existia).

O que olhar:
- no jogo: `docs/capturas/v1/vila_longe_dia.png`, `vila_longe_noite.png`, `vila_dia.png`, `mata_perto.png`, `oficina_*`;
- todas as árvores: `docs/capturas/v1/folhas/arvores_carvalhos.png` e `arvores_demais.png`;
- vento: `docs/capturas/v1/folhas/*_vento.gif`;
- camadas no Pixelorama: `docs/capturas/v1/folhas/arvores_camadas_pixelorama.png`;
- pincéis: `docs/capturas/v1/folhas/pinceis_carvalho.png`.

## Portão V1, 2ª rodada (2026-11-12)
Resposta do diretor à 1ª rodada: 48 px ok (perguntou por que não mais — resposta em D-086); sombreamento e contorno ok; zoom ok, mas afasta pouco; **árvores reprovadas** ("forçadas, sem cara de pixel arte, como se renderizassem toda folha"; parecia mais pesado); telhado da oficina aprovado; liberou o MPFB2; tirar as árvores do meio da praça; o resto, eu decido.

Feito:
- **Árvores refeitas** (D-098): copa de 9 massas com borda recortada em cachos, folha lisa (sem textura nem ruído), luz em faixas; tronco num tubo só, aceso mesmo sob a copa; vento da copa inteira, 1–2 px. VT-16 das árvores: refeitas do zero, mesmos bytes.
- **Desempenho** (D-099): o peso era real — 2.190 chamadas de desenho por quadro (cada sprite uma textura). Atlas dos sprites no carregamento: **51 chamadas, 1.106 FPS** no zoom 1 (eram 223); vista de longe 735 FPS (eram 90). `--bench` agora mostra as chamadas por quadro.
- **Vista de longe** (D-098): zoom 0 (roda para trás depois do 1), o dobro da área; reduz com média, não pisca.
- **Praça:** as duas árvores do meio saíram (clareira no gerador do mapa; nenhuma outra mudou de lugar).
- **Spike do MPFB2** (D-091): instalado em `build/blender-ext`; o corpo realista perde o rosto a 48 px e o rig deforma o braço já parado. Fica o kit próprio. Comparação em `docs/capturas/v1/spike_mpfb.png`.
- Decisões aprovadas no portão: D-086 (densidade), D-093 (sombra), D-094 (chão), D-097 → D-098 (zoom).

O que olhar agora: `docs/capturas/v1/mata_perto.png`, `oficina_*`, `vila_*`, `vila_longe_dia/noite.png`, `praca_poco.png`, e os GIFs `docs/capturas/v1/folhas/carvalho_*`, `betula_*`.

## Portão visual (VT-13), 1ª rodada — o que olhar
- `docs/capturas/v1/`: `antes_depois_*.png` (V0 × V1 na mesma cena), `vila_dia/entardecer/noite`, `oficina_*` (zoom 1 e 2, dia, entardecer, noite), `praca_perto` (zoom 3), `contato.png`.
- Folhas e GIFs: `docs/capturas/v1/folhas/` (`aprendiz_*.gif`, `avel_*.gif`, folhas de contato).
- **Perguntas ao diretor:**
  1. A densidade dobrada (gente com ~48 px) é o nível de detalhe certo?
  2. O jeito de sombrear e contornar (rampas, contorno seletivo) é o ETRAVA?
  3. Zoom inteiro ou com passos intermediários (D-097)?
  4. As duas árvores e o telhado resolvem o que incomodava?
- Decisões que pedem aprovação: **D-086** (densidade), **D-091** (corpo: kit próprio; o spike do MPFB2 ficou pendente — ver abaixo), **D-094** (chão por máscara), **D-093** (sombra projetada à direita, dentro do sprite).

## Como ver o V1 (IntelliJ: Gradle → ETRAVA → Tasks)
| Comando | Faz |
|---|---|
| `gradlew run` | o jogo; a fatia está no canto da oficina (F1 → `tp 25 29`, `hora 21` para a noite); roda do mouse para trás até a vista de longe (`zoom 0`) |
| `gradlew captures -Proteiro=v1` | refaz as capturas do portão (em `game-client/build/capturas/v1`) |
| `gradlew sheets` | folhas de contato e GIFs em `build/revisao` |
| `gradlew renderArt` | refaz a arte que mudou (precisa do Blender 5.3; `-Pso=avel` limita, `-Pforce` refaz tudo) |
| `gradlew blenderTeste` | o teste do cubo |

## Problemas conhecidos (Fase V)
- VT-16 nos personagens: com a pele contínua, raramente um pixel muda de tom entre renders (D-102); investigar no V2.
- Árvores: o carvalho torto e algumas massas ainda lembram "almofadas" (topo aceso, base escura); o retoque à mão pode resolver caso a caso pelo `.pxo`. Precisa do Pixelorama para exportar as folhas (sem ele, compõe em Java e avisa).
- O spike do MPFB2 roda só com a extensão em `build/blender-ext` (instalar: `BLENDER_USER_RESOURCES=build/blender-ext blender -b --online-mode -c extension install mpfb --enable`).
- Folhas de personagem e o chão ainda não estão no atlas (cada um é uma textura); hoje custam pouco, revisar no V2 quando houver 25 pessoas.
- Arte nova e antiga lado a lado: a nova tem mais detalhe e sombra projetada; as casas antigas continuam com o telhado de face única até o V5.
- Avel só aparece de dia dentro da oficina (interior ainda antigo); no portão, vê-se nas folhas e nos GIFs.
- A interface não acompanha a resolução (VT-09, V7); barras de rolagem lisas (V7).

## Próximo passo
Com o ok do diretor no V7: V8, Acabamento e entrega (`docs/visual/V8.md`), o portão final.

---

## Feito no V0 (resumo)
Zoom que travava (D-088), modo de depuração (D-089), rolagem em todas as listas, personagens visíveis (D-090), `gradlew captures`. Capturas em `docs/capturas/v0/`.

# Fechamento do M8 (histórico)

- **Marco em fechamento:** M8 — Polimento e release do MVP.
- **Status:** **PARADO no fim do M8.** Tudo o que dá para fazer sem uma pessoa jogando está feito e testado; o que falta para a "definição de pronto" do PRD 16 depende do diretor (lista em "O que só você pode fechar").
- Marcos concluídos: M0 a M7. M8: trabalho de máquina concluído; aprovação humana pendente.

## Feito no M8

**Aparência e leitura**
- **Fonte própria** (D-077): Alegreya (OFL), gerada no tamanho exato — o texto "Grande" e "Maior" não borra mais. Mudar o tamanho nas opções vale na hora.
- **Ícones dos 43 itens** (por código, na paleta), na mochila, no comércio e nos contêineres.
- **Golpes animados** (D-078): gente ergue o braço e bate; o inimigo ergue enquanto anuncia o golpe (dá para ler e esquivar). O lobo tem bote, cabeça, orelhas e olhos, e não vaza mais para o quadro vizinho.
- **Borda do mapa** (D-079): o chão continua além do mapa, escurece e vira mato. Acabou o vazio preto perto do Posto e nos cantos da vila.
- **HUD:** as teclas de luta só aparecem com inimigo por perto; as barras somem na conversa e no comércio e acompanham o tamanho do texto; balões não se sobrepõem; num bando, só o primeiro fala.
- Posto: árvores afastadas das bancas.

**Jogo**
- **Ritmo** (D-081): prazos dos arcos redistribuídos. Sem o jogador, o último acontecimento caía no dia 18–22 e sobravam dez dias vazios; agora cai entre os dias 24 e 29, e os desfechos que se amontoavam nos dias 9–12 ficam espalhados.
- **Defeito corrigido:** no ritmo Contemplativo o capítulo acabava no dia 30 com metade das histórias em aberto. Agora dura 60 dias.
- **Tempo jogado** nos saves (cabeçalho do save; aparece na lista).
- Inimigos não ficam mais um dentro do outro.

**Som**
- Áudio em **OGG Vorbis** (D-080): 37 MB → 5 MB. O ZIP do jogo tem 51 MB.

**Ferramentas de verificação** (D-082)
- `--bench=S` (mede FPS), `--bot=S` (jogador automático), `--hidden`, `--vel=N`; `ETRAVA_USERDIR` isola saves e opções nos testes.
- `gradlew at16`: AT-16 automático.
- `ChaosTest`: comandos ao acaso no núcleo, 31 dias, 6 sementes, com save e load no meio.
- Os testes agora rodam de novo quando um dado de `content/` muda (antes o Gradle os dava por atualizados).

**Documentos:** `WRITING.md` (guia de escrita), `docs/TESTES_MANUAIS.md` (roteiro dos AT manuais e da sessão de 3 h), D-077 a D-083, `STYLE.md` atualizado, capturas em `docs/capturas/m8/`.

## Definição de pronto (PRD 16) — onde cada critério está

| Critério | Situação |
|---|---|
| AT-01 a AT-09 | **passam** (automáticos, `gradlew build`) |
| AT-10, AT-11, AT-12 | os mecanismos têm teste automático (desfechos do A1 e voluntários em `ArcsTest`; canais das escolhas no validador); **falta alguém jogar** o roteiro |
| AT-13, AT-14 | **passam** no automático (`CombatTest`); roteiro manual escrito |
| AT-15 portão visual | capturas prontas em `docs/capturas/m8/`; **falta a sua aprovação** |
| AT-16 | **passa** (`gradlew at16`, duas sementes: 10 assets a menos, jogo roda, log avisa) |
| AT-17 | medido só nesta máquina (RX 6650 XT, 1280×720): **503 FPS** de média, 286 no 1 % pior, festa à noite com 15 NPCs, luz e pós ligados. **Sem medida no hardware de referência (GTX 1050) nem no mínimo (Iris Xe)** |
| AT-18 | o `Etrava.exe` roda com o `PATH` sem Java e sem `JAVA_HOME`. **Falta uma máquina de fato limpa** |
| AT-19, AT-20, AT-21 | **passam** (`ContentM7Test`) |
| Zero erros no validador | **ok** |
| Sessão de 3 h sem queda | jogador automático: ver "Teste de resistência" abaixo. **A sessão humana de 3 h falta** |
| RNF de tempo | início até o jogo ≈ 1,7 s; salvar 3–19 ms; carregar 4–9 ms; 30 dias headless ≈ 2 s (com a partida do Gradle) — todos com folga |

`gradlew build`: **129 testes passando**, cobertura do núcleo 92 %, validador sem erros.

## Teste de resistência
Dois jogadores automáticos no executável de release, janela oculta, som mudo, pasta de usuário isolada:
- **3 horas em tempo real** (`--bot=10800 --seed=101`): iniciado às 08:27 de 12/11. *Resultado: preencher ao terminar.*
- **40 minutos a 8×** (`--bot=2400 --vel=8 --seed=202`): iniciado às 08:27. *Resultado: preencher ao terminar.*

Antes desses: três rodadas curtas (40 a 150 s) e as duas do AT-16, todas sem erro.

## Como rodar e testar
| Comando | Faz |
|---|---|
| `gradlew run` | abre o jogo no menu principal |
| `gradlew build` | compila, testa, confere cobertura e valida conteúdo e assets |
| `gradlew distWin` | gera `game-client/build/package/Etrava-0.1.0-win.zip` (51 MB, Java embutido) |
| `gradlew at16` | AT-16 automático |
| `gradlew simulate --args="--dias 30 --semente 42"` | 30 dias sem o jogador: crônica e epílogo |
| `gradlew generateArt` / `generateRegion` / `generateAudio` | regeneram arte, mapas e som (o som pede Python com `pip install soundfile`) |

No IntelliJ: janela Gradle → `ETRAVA > Tasks > application > run` (ou `verification > at16`, `distribution > distWin`).

Para ver o M8 em dez minutos:
1. `gradlew run`. Repare no nome do jogo e nos menus com a fonte nova. Opções → Tamanho do texto → Maior: as telas mudam na hora e continuam nítidas.
2. Novo jogo. **I** abre a mochila: ícones. Fale com Avel: as barras de vida saem da frente do retrato.
3. Saia para a vila e ande até um canto do mapa: o chão segue e some no mato.
4. F1 → `area estrada`, `tp 57 24`, `encontro saqueadores_posto`: eles se espalham em volta, um fala, erguem o braço antes de bater; clique para bater de volta. `saude 500` se quiser durar.
5. Esc → Salvar → Jogo 1; depois Esc → Carregar: o slot mostra o tempo de jogo (depois do primeiro minuto).
6. O roteiro completo dos testes manuais está em `docs/TESTES_MANUAIS.md`.

## O que só você pode fechar
1. **Ouvir o som** (D-071). É sintetizado e eu não escuto: conferi níveis e emendas por número. Serve de base, ou trocamos por trilha de verdade?
2. **AT-15:** aprovar (ou não) as capturas de `docs/capturas/m8/`.
3. **Sessão de 3 horas** jogando o `Etrava.exe` (checklist no fim de `docs/TESTES_MANUAIS.md`).
4. **AT-18** numa máquina sem Java, e **AT-17** numa GTX 1050 e numa Iris Xe, se houver como.
5. **Termos de uso comercial da Dreamina** para os 18 retratos e as 4 ilustrações, antes de qualquer distribuição pública.

## Decisões que pedem aprovação
- Do M7, ainda abertas: **D-069** (quem é Oren), **D-073** (como A2 termina e o que Avel confessa), **D-074** (A3 sem desfecho trágico), **D-067** (elenco e nomes), texto de todas as conversas, ilustrações do menu e do mapa.
- Do M8: **D-077** (Alegreya como fonte do jogo — impacto visual), **D-079** (mato além da borda), **D-081** (ritmo novo: veja se os dias 13–17 não ficaram parados para quem não investiga).

## Problemas conhecidos / pendências
- Som nunca ouvido por quem o gerou (acima).
- Sem medida de desempenho no hardware de referência e no mínimo.
- Personagens sem arma na mão; o golpe é o mesmo para faca, cajado e punho.
- De frente e de costas o lobo melhorou, mas continua estreito (é o ângulo; pede sprite desenhado à mão).
- Interiores: a borda da frente do cômodo fica aberta (corte) e o piso se esfuma um pouco além dela.
- O mapa ilustrado é de IA; as marcas das áreas são aproximadas.
- O epílogo usa dois painéis (o terceiro, `epilogo_estrada`, nunca veio da Dreamina).
- Furto só pesa para quem vê; NPCs não têm vida nem revidam (D-065).
- `gradlew generateAudio` depende de Python + `soundfile` para a etapa OGG (D-080).

## Próximo passo
O MVP (M0–M8) está entregue do lado da máquina. Depois da sua rodada (itens de "O que só você pode fechar"), o caminho é: corrigir o que a sessão de 3 h apontar → marcar a versão 0.1.0 → só então abrir os itens [PÓS] do PRD 14. **Nada de [PÓS] começa sem o diretor mandar.**

Para quem retomar (qualquer modelo): leia este arquivo, depois `CLAUDE.md`, a seção do PRD em questão e a `docs/SPEC.md`. Vocabulário de condições e efeitos: `CONTENT.md`. Tom do texto: `WRITING.md`.
