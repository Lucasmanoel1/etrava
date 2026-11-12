# PROGRESS — ETRAVA

Última atualização: 2026-11-12

## Estado atual
- **Fase atual:** **V — revisão visual** (D-084). Documentos em `docs/visual/`.
- **Marco atual:** **V1 — Pipeline e portão visual: segunda rodada do portão (VT-13).** O diretor aprovou densidade, sombreamento, zoom e telhado; reprovou as árvores. Árvores refeitas, vista de longe, praça limpa, desempenho corrigido, spike do MPFB2 feito.
- **Status:** **PARADO no portão visual do V1** esperando o diretor olhar as árvores novas. Nada de V2 sem o "pode seguir".
- MVP: M0 a M7 concluídos; M8 com o trabalho de máquina concluído e a aprovação humana pendente (seção "Fechamento do M8", abaixo). As pendências do M8 não bloqueiam a Fase V.

## Fase V — visão rápida
| Marco | Nome | Situação |
|---|---|---|
| V0 | Correções e ferramentas (zoom, rolagem, modo de depuração, visibilidade, `gradlew captures`) | **concluído** |
| V1 | Pipeline Blender + pixelizador, densidade dobrada, fatia — **portão visual** | **2ª rodada: esperando o diretor ver as árvores** |
| V2 | Gente (25 humanos, animações de vida comum) | — |
| V3 | Criaturas e combate | — |
| V4 | Natureza e terreno | — |
| V5 | Arquitetura e exteriores | — |
| V6 | Interiores, objetos e itens | — |
| V7 | Interfaces | — |
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
- O spike do MPFB2 roda só com a extensão em `build/blender-ext` (instalar: `BLENDER_USER_RESOURCES=build/blender-ext blender -b --online-mode -c extension install mpfb --enable`).
- Folhas de personagem e o chão ainda não estão no atlas (cada um é uma textura); hoje custam pouco, revisar no V2 quando houver 25 pessoas.
- Arte nova e antiga lado a lado: a nova tem mais detalhe e sombra projetada; as casas antigas continuam com o telhado de face única até o V5.
- Avel só aparece de dia dentro da oficina (interior ainda antigo); no portão, vê-se nas folhas e nos GIFs.
- A interface não acompanha a resolução (VT-09, V7); barras de rolagem lisas (V7).

## Próximo passo
Depois da aprovação das árvores novas: V2, tarefa 1 de `docs/visual/V2.md` (kit humano modular e silhuetas por papel). Se o portão reprovar algo, iterar no V1.

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
