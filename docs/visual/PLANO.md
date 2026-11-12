# PLANO — Fase V: revisão visual do ETRAVA

Versão 1 — 2026-11-12. Pedido do diretor em 12/11: elevar muito a qualidade visual do jogo inteiro sem mudar a identidade (medieval, isométrico, pixel art dark fantasy), usando o Blender como ferramenta principal de produção e a Dreamina como apoio, e corrigir zoom, visibilidade dos personagens, informação técnica na tela, interfaces e rolagem.

Este documento faz para a Fase V o que o `PRD.md` fez para o MVP: diz o que entra, em que ordem, como se prova que ficou pronto e onde parar. A parte técnica está em `SPEC-visual.md`; cada etapa tem o seu documento (`V0.md` … `V8.md`).

Hierarquia em conflito: instruções do diretor > este plano > PRD > `STYLE.md` > o resto.

---

## 1. O que muda e o que não muda

**Muda:** toda a arte do jogo (personagens, criaturas, árvores, vegetação, chão, construções, objetos, interiores, ícones), as animações, a interface inteira, o modo de depuração, a visibilidade dos personagens e o comportamento do zoom e da rolagem.

**Não muda:**
- A simulação (`core-sim`), o conteúdo narrativo, os arcos, os saves e as regras. A Fase V é de apresentação. Se uma etapa precisar mexer no núcleo, é exceção registrada em `DECISIONS.md`.
- Os IDs de asset. Todo arquivo novo entra com o mesmo ID do antigo (RF-423); conteúdo e mapas não percebem a troca.
- Os mapas e as colisões. A arte nova se ajusta às pegadas que já existem nos `.tmj`, não o contrário.
- A identidade: isométrico 2:1, pixel art de paleta única, luz do alto à esquerda, âmbar contra noite fria, terroso e dessaturado. Retratos (Dreamina), ilustrações e a fonte Alegreya ficam.
- Nenhuma funcionalidade sai para facilitar o trabalho.

## 2. Diagnóstico do estado atual (capturas do M8)

| Área | Problema | Causa |
|---|---|---|
| Personagens | bonecos de cilindros, 24 px de altura, sem rosto nem roupa legível, só parado/andar/golpe | boneco articulado desenhado por código (`CharacterArt`) |
| Árvores | copas de bolinhas iguais, 5 desenhos para o mapa inteiro, paradas | elipses por código (`PropArt`) |
| Construções | telhado lido como uma face só, mesma casa repetida, pouco detalhe de material | extrusão simples por código (`BuildingArt`) |
| Chão | grama e terra lisas, transições em degrau, nada sobre o chão | 15 tiles por código, sem camada de decoração |
| Interiores | móveis em caixa, borda da frente aberta | `FurnitureArt` |
| Personagens escuros | em certas horas e zooms o personagem some no fundo | a investigar (V0); suspeita: o buffer de luz multiplica o sprite como se fosse chão |
| Zoom que trava | a roda deixa de aproximar e afastar | suspeita forte (V0): painéis escondidos com `setVisible(false)` mantêm o foco de rolagem num `ScrollPane` invisível; o `Stage` consome a roda antes da câmera |
| Informação técnica | FPS, posição, semente, velocidade e "F1 console" sempre na tela | HUD de desenvolvimento ligado em qualquer build |
| Interface | painéis de papel lisos, listas sem barra de rolagem visível, tamanhos fixos em pixels | tema do M3 nunca refeito |

O que **funciona** e deve ser preservado: a paleta terrosa, a luz noturna âmbar nas janelas, a gradação dia/noite, a névoa e a vinheta, o enquadramento isométrico, a legibilidade da fonte.

## 3. Decisões estruturais

| # | Decisão | Onde |
|---|---|---|
| D-084 | A Fase V existe, com marcos V0–V8 e paradas como as do MVP | `DECISIONS.md` |
| D-085 | Blender 5.3, em linha de comando (`blender -b`), com scripts Python versionados, é a ferramenta principal de arte. Fora do build e da execução, como o Python do áudio | `DECISIONS.md`, `SPEC-visual.md` §2 |
| D-086 | **Densidade dobrada:** 1 px de arte = 2×2 px de mundo (era 4×4). Tile de 64×32 px de arte, gente com ~48 px. *Proposta: confirmada no portão V1* | `SPEC-visual.md` §4 |
| D-087 | A Dreamina faz referência, textura e material de interface; nunca o sprite final direto. Tudo passa pelo funil de paleta | `SPEC-visual.md` §3 |

**Por que não "um modelo 3D com filtro de pixel":** o renderizador do Blender só entrega matéria-prima (cor por material, sombreamento, profundidade, normal, contorno). Quem decide cada pixel é o **pixelizador** do projeto (`SPEC-visual.md` §5): cada material tem uma rampa de 2–4 cores da paleta, a luz é quantizada em faixas, o contorno é seletivo (escurece a cor vizinha em vez de preto chapado), pixels soltos são limpos, olhos e detalhes pequenos são postos como "decalques" em posição exata. É o que separa sprite desenhado de render reduzido.

## 4. Marcos

Cada marco termina **executável**, com `gradlew build` passando, capturas novas em `docs/capturas/vN/` e uma **parada** para o diretor ver. Nada do marco seguinte começa sem ele mandar continuar.

| Marco | Nome | Entregas | Pronto quando |
|---|---|---|---|
| **V0** | Correções e ferramentas | zoom (causa real + teste), rolagem funcional nas listas, modo de depuração, visibilidade dos personagens, `gradlew captures` (cenas × horas × resoluções) com métrica de luminância | VT-01, VT-02, VT-03, VT-08 (parte funcional) passam |
| **V1** | Pipeline e portão visual | pipeline Blender → pixelizador → manifesto; renderizador com densidade dobrada; paleta v3; **fatia**: jogador e Avel (parado, andar, correr, 8 direções), 2 espécies de árvore com vento, 1 casa com telhado completo, chão com textura e decoração num canto da vila; `STYLE.md` v3 | **Portão visual aprovado pelo diretor** (VT-13). Reprovado → iterar. Sem produção em massa antes disso |
| **V2** | Gente | kit humano modular; os 25 humanos (o jogador, os 23 NPCs e o saqueador); conjunto completo de animações; animação pela atividade da agenda; camadas de equipamento do jogador; retratos conferidos com os sprites | VT-04, VT-05, VT-15 para humanos |
| **V3** | Criaturas e combate | lobo e vulto refeitos; golpes por tipo de arma; arma visível na mão; dano, queda, morte; efeitos de impacto; leitura do aviso de golpe | VT-05 completo; AT-13/AT-14 continuam passando |
| **V4** | Natureza e terreno | todas as espécies de árvore (com variantes de tamanho e o ciclo da árvore de Nalia), arbustos, grama, flores, pedras, troncos, plantio; chão com textura e transições; camada de decoração; vento; vida ambiente (pássaros, insetos); além da borda do mapa | todas as áreas abertas sem árvore ou tile antigo |
| **V5** | Arquitetura e exteriores | kit de construção; as 9 construções refeitas (telhados de duas águas, quatro águas, beirais, colmo e telha com espessura), chaminés com fumaça, janelas acesas; Posto, cercas, poço, postes, carroça, lápides e demais objetos de fora; ruína | VT-07; nenhum exterior antigo |
| **V6** | Interiores, objetos e itens | paredes com corte limpo, pisos, todos os móveis, lareira e fogo animados, luz de interior; estrutura enterrada; os 43 ícones de item renderizados dos mesmos modelos | nenhum interior ou ícone antigo |
| **V7** | Interfaces | tema novo (madeira, ferro, pergaminho); menu, novo jogo, opções, saves, pausa, HUD, diálogo, comércio, mochila com boneco equipado, ficha, caderno, mapa, contêiner, epílogo, derrota/morte, console; cursores; rolagem em tudo; escala por resolução | VT-08, VT-09 |
| **V8** | Acabamento e entrega | luz e pós ajustados à arte nova; passe de consistência; desempenho, memória e tamanho; regressão completa; capturas finais | VT-06, VT-10, VT-11, VT-12, VT-14 |

**Por que nessa ordem:** V0 resolve o que incomoda hoje e dá as ferramentas de inspeção que todos os marcos usam. V1 prova o método antes de gastar esforço (portão, como no M1). Gente vem primeiro porque é o que mais tem cara de provisório e é o que o jogador olha o tempo todo; criaturas usam o mesmo kit. Natureza e arquitetura vêm depois, com o método já calibrado. A interface fica no fim porque usa os ícones do V6 e o boneco do V2 — mas a parte que quebra o jogo (rolagem, zoom) já sai no V0.

## 5. Testes de aceitação da Fase V

(A) automático, roda no `gradlew build` ou numa tarefa própria. (M) manual, com roteiro.

| ID | Tipo | Critério |
|---|---|---|
| VT-01 | A | **Zoom nunca trava.** Jogador automático abre e fecha cada painel (diálogo, comércio, mochila, ficha, caderno, mapa, contêiner, console, pausa) 200 vezes em ordem aleatória, com rolagem dentro e fora deles; depois de cada fechamento a roda muda o zoom. Zero falhas |
| VT-02 | A | Sem `--debug`, nenhum texto de FPS, posição, semente, velocidade ou console aparece e F1 não abre nada. Com `--debug`, tudo volta (F3 liga e desliga o painel) |
| VT-03 | A | **Visibilidade** (`gradlew vt03`): na vila (praça e grama), no Posto, na oficina e na taverna, em 8 horas do dia e nos 4 zooms, pelo menos 60 % dos pixels do jogador diferem da luminância média do chão em volta por 0,12 ou mais (escala 0–1). Fora: a área escura, onde enxergar depende da lamparina. *(Critério revisto no V0, D-090: a média de luminância enganava — claro e escuro se anulam.)* |
| VT-04 | A | Todo pixel de sprite, tile, prop e ícone está na paleta v3 (o validador aponta arquivo e posição) |
| VT-05 | A | Cada folha de personagem tem todas as animações e as 8 direções exigidas pelo seu papel (`SPEC-visual.md` §6.2); quadros dentro do tamanho; âncora dentro do quadro |
| VT-06 | A | Nenhum asset em uso vem do gerador antigo (`source` com "código" ou "provisório"); o validador lista os que restarem |
| VT-07 | A | Construções: a pegada declarada na arte bate com a colisão do mapa e a porta da arte cai no tile de porta |
| VT-08 | A | **Rolagem:** com 80 itens no comércio, 60 fatos no caderno e 12 falas na conversa, cada lista rola pela roda (com o mouse em cima), pela barra e pelo teclado, e o último item fica visível e clicável |
| VT-09 | A+M | Cada tela capturada em 1280×720, 1366×768, 1600×900, 1920×1080, 2560×1080, 2560×1440, 3840×2160 e 1280×1024, nos três tamanhos de texto: nenhum elemento fora da tela, nenhum texto cortado (checagem automática de limites) e revisão das capturas |
| VT-10 | A+M | Desempenho: vila à noite, festa, 15 NPCs, decoração e vento ligados: ≥ 60 FPS no hardware de referência (aqui: não cair mais de 40 % em relação ao M8); memória de textura ≤ 512 MB; ZIP ≤ 150 MB; jogo abre em ≤ 3 s |
| VT-11 | A | AT-16 continua passando: tirar 10 assets novos ao acaso → placeholders e aviso no log |
| VT-12 | A | Regressão: todos os testes do MVP passam; saves da 0.1.0 carregam |
| VT-13 | M | **Portão visual V1:** capturas da fatia (antes × depois, dia e noite, zoom perto e longe) e folhas de contato das animações aprovadas pelo diretor |
| VT-14 | M | **Portão final:** vila de dia, ao entardecer e à noite, taverna, Posto, estrutura enterrada, dois interiores e cinco telas de interface aprovadas pelo diretor contra o `STYLE.md` v3 |
| VT-15 | A+M | Animação: nos ciclos parado e andar, o pé de apoio não desliza mais de 1 px entre quadros; sem quadro com pixel isolado de contorno (contagem automática); GIFs de prévia revistos |
| VT-16 | A | Reprodutível: `gradlew renderArt` numa pasta limpa, com a mesma versão do Blender, gera arquivos idênticos byte a byte |

## 6. Orçamentos

| Recurso | Teto | Observação |
|---|---|---|
| Memória de textura em jogo | 512 MB | folhas de personagem carregadas por área (`SPEC-visual.md` §7.3) |
| ZIP de distribuição | 150 MB | hoje 51 MB |
| Tempo de render completo | ≤ 2 h; incremental ≤ 10 min | cache por hash de entrada |
| Créditos Dreamina | ~3.500 na fase inteira | conferir saldo no V1; avisar antes de lote > 500; relatar o gasto em cada parada |
| Tamanho máximo de atlas | 4096×4096 | cabe em qualquer GPU-alvo |

## 7. Riscos

| # | Risco | Mitigação |
|---|---|---|
| RV1 | Sprite com cara de "3D reduzido" | pixelizador por material e rampas, contorno seletivo, limpeza, decalques; portão V1 antes de qualquer lote |
| RV2 | Humano procedural feio ou genérico | testar MPFB2 (extensão gratuita, malhas CC0) contra um kit próprio no V1; silhuetas por papel (ofício, idade, corpo) definidas antes de modelar |
| RV3 | Inconsistência entre lotes e etapas | uma câmera, uma luz, uma paleta, um pixelizador para tudo; folhas de contato lado a lado com a fatia aprovada |
| RV4 | Memória e tamanho estouram | orçamento acima, recorte de bordas, carga por área, menos quadros para quem não luta |
| RV5 | Arte nova quebra jogabilidade (colisão, porta, clique) | arte se ajusta à pegada do mapa; VT-07; o núcleo não muda |
| RV6 | Render lento trava a iteração | cache por hash, Blender em paralelo, Workbench/EEVEE com poucas amostras |
| RV7 | Versão do Blender muda e o resultado muda | versão registrada; o script recusa versão diferente sem `--force`; VT-16 |
| RV8 | Escopo explode | um marco por vez; ordem de corte abaixo |
| RV9 | Textura ou referência com cara de IA | IA só entra como textura de material ou referência; sempre funil de paleta |

**Ordem de corte**, se o tempo apertar (do primeiro ao último):
1. Vida ambiente (pássaros, borboletas, vaga-lumes).
2. Variantes de roupa do jogador além de duas.
3. Sentar e dormir animados (ficam com o parado).
4. Menu principal animado.
5. Espécies de árvore além de quatro.
6. **Nunca cortar:** correção do zoom, modo de depuração, visibilidade, rolagem, gente nova com parado/andar/golpe em 8 direções, árvores novas, telhados novos, interface refeita.

## 8. Documentos da fase

| Arquivo | Conteúdo |
|---|---|
| `docs/visual/PLANO.md` | este |
| `docs/visual/SPEC-visual.md` | pipeline, formatos, renderizador, interface, verificação |
| `docs/visual/V0.md` … `V8.md` | objetivo, tarefas, entregas, testes e parada de cada marco |
| `STYLE.md` | vira v3 no V1 (densidade, paleta, rampas, regras de modelagem) |
| `PROGRESS.md`, `DECISIONS.md` | como sempre |

Pendências humanas do M8 (ouvir o som, sessão de 3 h, AT-15/17/18, termos da Dreamina) continuam abertas e não bloqueiam a Fase V. O AT-15 do MVP passa a ser coberto pelo VT-14.
