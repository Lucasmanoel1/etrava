# PROGRESS — ETRAVA

Última atualização: 2026-11-12

## Estado atual
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
