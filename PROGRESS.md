# PROGRESS — ETRAVA

Última atualização: 2026-11-11

## Estado atual
- **Marco concluído:** M6 — Combate e perigo.
- **Status:** **PARADO no fim do M6**, aguardando o usuário mandar continuar para o M7 (Conteúdo completo).
- Marcos concluídos: M0, M1, M2, M3, M4, M5, M6.
- Aviso do usuário (2026-11-11): do M5 ao M7 ele usa o Opus; volta ao Fable no M8, para polir.

## Feito no M6

- **Combate em tempo real** (`core-sim`, classe `combat.Combat`, D-057): vida e fôlego (crescem com o Vigor), golpe, esquiva (Shift), golpe pesado (Q) e empurrão (E) com recarga. O inimigo **anuncia** o golpe (a barrinha dele acende): esquivando, nada acerta. Chance e dano pela Luta dos dois lados, pela arma e pela proteção do que se veste. Luta sobe lutando. Clique no inimigo ataca; segurar continua (RF-220). Espaço pausa (RF-226).
- **3 inimigos em dados** (`content/enemies.yaml`, RF-223): lobo (de noite, na estrada), saqueador (fim da tarde, na curva) e o vulto (na estrutura). Arte provisória gerada por código (o lobo ganhou um gerador de quadrúpede).
- **Encontros** (`content/encounters.yaml`, D-058): sorteados ao chegar à área e a cada hora nela; dormindo na estrada, um ataque **interrompe o sono** (RF-323). Efeito `{encounter: id}` inicia combate pelos dados (RF-102).
- **Fuga e rendição dos dois lados** (D-061): inimigo ferido foge ou, se humano, se rende — clicar nele abre a folha "poupar / tomar o que ele leva / acabar com ele" (ato `execucao`). O jogador pode se render (R): levam 60% do dinheiro e vão embora. Correr para longe faz o inimigo desistir.
- **Derrota não letal** (D-059): saqueadores levam dinheiro e até 2 itens; o personagem acorda 4–8 h depois na beira da estrada, com 30% da vida; o comerciante do Posto o achou, fica sabendo, lembra e comenta na conversa.
- **Morte** (D-060): a estrutura é área letal. Morrendo, o mundo para; a tela mostra o epílogo como estaria agora e os saves para carregar.
- **A estrutura enterrada** (Q-07, proposta D-063): mapa gerado (`gradlew generateEstrutura`), escuro, com fibras petrificadas, a laje riscada, um nicho (lascas de fibra mornas e uma placa com marcas em grupos de nove) e o vulto na câmara. Entra-se pela **boca do buraco** na área de teste; a escada leva de volta (saídas de área, D-064).
- **Escuridão e luz** (RF-008, D-062): na estrutura só se vê o raio de luz; a lamparina (mão secundária) aumenta o raio, deixa a luz quente, e faz o vulto hesitar. À noite, fora, o raio também vale. No escuro sem luz se erra mais.
- **Agressão** (D-065): Ctrl + clique num morador = empurrão, ato `agressao`.
- **Vida volta** com o tempo fora de combate; o unguento de sal cura.
- **AT-13 garantido pelo validador**: todo desfecho de arco precisa de um caminho que não dependa de vencer combate (`abatidos.*`, flags `combate*`).
- Validador: inimigos, encontros, saídas de área, `wakeAt`/`finder`, atos de combate e o RF-225. Save na **versão 5**, com a primeira migração de verdade (saves da 4 carregam, D-066).
- Comandos de console novos: `encontro ID`, `saude N`, `inimigos`, `equipar ITEM`.
- De quebra: a UI da mochila e do comércio mandava comandos para a simulação antiga depois de carregar um save; agora segue a atual.

## Testes de aceitação do marco
| Critério | Situação | Teste |
|---|---|---|
| AT-13 | passa: o validador exige caminho pacífico até cada desfecho; 30 dias sem lutar, todo arco termina, ninguém abatido | `at13_nenhumArcoExigeVencerCombate`, `validadorPegaCombateQuebrado_eArcoQueSoTerminaBemNaBriga` |
| AT-14 (não letal) | passa: perder para saqueadores → acorda 4–8 h depois na beira da estrada, sem 60% do dinheiro e sem o sal, com o mundo avançado; o comerciante sabe | `at14_perderParaSaqueadores_acordaHorasDepoisRoubadoEOMundoAndou` |
| AT-14 (letal) | passa: morrer na estrutura mostra o mundo, nada mais anda, não salva, e o save anterior carrega igual | `at14_morrerNaEstruturaMostraOMundoEPermiteCarregar` |

`gradlew build`: 122 testes, cobertura do `core-sim` em 91% (combate 84%), validador sem erros. `simulate --politica trabalhador` dá o mesmo resultado do M5 (134 moedas em 10 dias).

## Como rodar e testar
| Comando | Faz |
|---|---|
| `gradlew run` | abre o jogo |
| `gradlew build` | compila, testa, confere cobertura e valida conteúdo e assets |
| `gradlew simulate --args="--dias 10 --semente 42 --politica trabalhador --resumo"` | 10 dias de um jogador que só trabalha e come |
| `gradlew simulate --args="--dias 30 --semente 42"` | 30 dias sem o jogador: arcos, cenário, epílogo e crônica |
| `gradlew generateArt` / `generateRefMap` / `generateEstrutura` / `importPortraits` | regeneram arte, mapas e retratos |
| `gradlew distWin` | gera o `Etrava.exe` e o ZIP |

No IntelliJ: janela Gradle → `ETRAVA > Tasks > application > run` (ou `verification > simulate`, com os argumentos em "Arguments").

Controles: clique anda, fala com um NPC, abre um contêiner ou **ataca um inimigo** (segurar continua) · **Ctrl + clique** empurra um morador · WASD anda · **Shift** esquiva · **Q** golpe pesado · **E** empurrão · **R** render-se · roda do mouse muda o zoom · I mochila · C habilidades · J ou N caderno · Espaço pausa · T espera 1 h · F5/F9 salva/carrega · F1 console.

Roteiro para ver o M6 (uns 10 minutos):
1. `gradlew run`, F1 e `area area_teste`. Embaixo à esquerda: vida e fôlego; acima, as recargas.
2. Saqueadores: `hora 17`, `encontro saqueadores_estrada`, `inimigos` (mostra onde estão) e `tp X Y` perto deles. Eles avisam ("A bolsa. Devagar.") e partem para cima. Clique e segure sobre um para atacar; olhe a barrinha dele acender antes do golpe e aperte Shift para esquivar. Q e E gastam fôlego e entram em recarga.
3. Com pouca vida, um deles foge ou se rende. Rendido, clique nele: poupar, tomar o que leva ou acabar com ele. Faça isso perto do comerciante do Posto e depois `sabe comerciante_posto`.
4. Render-se: novo encontro, aperte R perto deles. Levam 60% do dinheiro.
5. Perder: `saude 5` no meio da luta. A folha "Você acorda" conta quanto tempo passou e o que levaram. Fale com o comerciante do Posto: "Foi você que me achou na beira da estrada?".
6. Sono interrompido: `hora 21` na área de teste e `dormir` algumas vezes (ou T): às vezes os lobos chegam e acordam você.
7. A estrutura: ande até a boca do buraco (sudeste da área de teste, perto de 35, 36). Lá embaixo está escuro. Sem lamparina, o raio é de um passo e meio. Volte (a escada), compre uma lamparina no Posto (`comercio posto`) ou `dar lamparina` + `equipar lamparina`, e desça de novo. O nicho fica no fim do corredor de cima; o vulto, no fundo da câmara.
8. Morrer: deixe o vulto pegar você (`saude 5`). A tela "Fim" mostra o mundo e os saves; clique num para voltar. Salve antes com F5.
9. Sem janela: `gradlew test --tests "*CombatTest*"`.

## Decisões que pedem aprovação
- **D-063 / Q-07 (narrativa, alto impacto):** o que há na estrutura enterrada — fibras petrificadas, a laje riscada, lascas mornas, a placa com marcas em grupos de nove (pista distante da Concordata dos Nove) e o vulto, que não tem rosto, recua da luz e faz ouvir "um som de voz, longe, chamando alguém por um nome que não é o seu". Tudo em dados; troca-se sem código.
- **D-060:** a estrutura **inteira** é área letal (morte de verdade), e o vulto também.
- **D-057 (equilíbrio):** os números do combate. Pelas contas (não joguei para medir): um saqueador sozinho cai ou foge em uns 10 golpes do martelo inicial, enquanto tira uns 4 de vida por segundo; dois juntos são perigosos; o vulto sem lamparina é quase certeza de morte. Vale o diretor jogar e dizer se está duro ou mole demais. Ajustável em `enemies.yaml` e no topo de `Combat`.
- **D-061 / D-059:** render-se custa 60% do dinheiro; perder custa o mesmo e mais até 2 itens, e 4–8 horas.
- **D-065:** empurrar morador (Ctrl + clique) é o único "ataque" a NPC; moradores não revidam nem morrem no MVP.
- Texto novo (falas do saqueador, do comerciante sobre a estrada e o socorro, folhas de derrota/morte/rendido, descrições dos itens da estrutura): proposta minha, para a leitura de tom do diretor.
- Arte provisória do lobo, do saqueador, do vulto e dos objetos da ruína: feita pelo mesmo gerador aprovado no M1; não é arte final.

## Problemas conhecidos / pendências
- **Retratos que faltam** (placeholder na conversa): Nalia, curandeira e o comerciante do Posto. São 3 imagens na Dreamina, cerca de 90 créditos.
- **Combate sem animação de golpe:** os bonecos só têm parado/andando; o golpe aparece como número de dano, lampejo vermelho e a barrinha que acende. Animações de ataque ficam para a arte final (M7/M8).
- **Sem áudio de combate** (RF-402 entra com o áudio, no M7).
- O lobo visto de frente/de costas vira uma mancha; de lado e na diagonal lê bem.
- Inimigos atravessam uns aos outros (como os NPCs).
- A estrada ainda é a área de teste (mapa da estrada no M7). A estrutura se alcança pelo buraco dessa área; a aparição durante um trabalho e a reação de Avel são o A3 (M7).
- A estrutura é escura por inteiro: a captura do portão visual (AT-15) vai precisar da lamparina.
- Itens aparecem só como texto: os ícones (`icone.<id>`) ainda não existem. Arma equipada não aparece no personagem (RF-203, MVP-S).
- Furto: o dono não descobre depois sozinho; só quem vê (D-051).
- A fonte padrão do libGDX não tem travessão, "×" nem aspas curvas; evitei os três na interface.
- Do M4, ainda valem: sem tela de salvar/carregar nem menu (a tela de morte já lista os saves); Capítulo I só termina pelo dia 30; conferir os termos de uso comercial da Dreamina antes de um release; AT-18 testado só nesta máquina.

## Próximo passo — M7 (Conteúdo completo), só com o "continue" do usuário
PRD 18: A2, A3, H1–H5, E1–E5, todos os NPCs, layout final da região, áudio, mapa ilustrado, menus finais. AT-19, AT-20, AT-21. Inclui: Avel (e a oficina passa dele), o mapa da estrada e do Posto (os encontros saem da área de teste), interiores, A3 usando a estrutura do M6 (a aparição durante um trabalho), H4 com o efeito `encounter`, menu e tela de salvar/carregar.

Para quem retomar (qualquer modelo): leia este arquivo, depois `CLAUDE.md`, as seções do PRD do marco e a `docs/SPEC.md`. O vocabulário de condições e efeitos está em `CONTENT.md`; cada tipo novo precisa de leitura e escrita em `Rules` (os efeitos agendados vão para o save), de um caso no `Story` (teste/aplicação) e de um caso no `NarrativeValidator`.

## Paradas obrigatórias (lembrete)
- Fim de cada marco (M0…M8).
- Antes de decisão irreversível ou de alto impacto narrativo/visual.
