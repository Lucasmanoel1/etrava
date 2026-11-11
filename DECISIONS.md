# DECISIONS — ETRAVA

Registro de decisões. Formato: data, decisão, motivo, alternativa descartada.
Decisões marcadas **[A APROVAR]** ainda dependem do usuário.

---

## D-001 — Java 21 (LTS) via Gradle toolchain
- **Data:** 2026-11-09
- **Decisão:** o projeto compila e roda em **Java 21**, fixado por *toolchain* do Gradle (`java.toolchain.languageVersion = 21`). O JDK 25 pode continuar como padrão da máquina (`JAVA_HOME`); o Gradle localiza sozinho o JDK 21 instalado em `C:\Program Files\Java\jdk-21.0.12`.
- **Motivo:** o `CLAUDE.md` pede "Java 21+"; 21 é o alvo mais testado com libGDX/LWJGL3 e com as ferramentas de build. Nada no projeto precisa de recurso exclusivo do 25. Fixar por toolchain torna o build independente de qual JDK está "com prioridade" na máquina.
- **Alternativa descartada:** Java 25 — funciona em princípio, mas traz risco de incompatibilidade de ferramentas sem nenhum ganho para o MVP. Subir para 25 depois é trocar um número.
- **A verificar no M0:** que o Gradle wrapper escolhido roda com o JDK 25 como `JAVA_HOME` e compila com o toolchain 21 (se não rodar, apontar `org.gradle.java.home` para o JDK 21).

## D-002 — SPEC não será escrita sem o PRD
- **Data:** 2026-11-09
- **Decisão:** a escrita de `docs/SPEC.md` fica suspensa até `docs/PRD.md` existir no projeto.
- **Motivo:** o PRD é a fonte principal (escopo do MVP, marcos M0–M8, testes de aceitação, seção 20.1 que define o conteúdo da SPEC, camadas de fato V1/V2/V3). Ele não está em `docs/` nem em pastas próximas. Os dois documentos disponíveis não bastam e em parte se contradizem com o `CLAUDE.md` (Ponto Seguro fala em 3D com três câmeras; `CLAUDE.md` fala em isométrico) — só o PRD resolve isso.
- **Alternativa descartada:** inferir o PRD a partir do `CLAUDE.md`, do Ponto Seguro e da lore — produziria uma SPEC baseada em suposições sobre escopo e marcos, com retrabalho quase certo.
- **Situação:** resolvida em 2026-11-09 — o usuário adicionou `docs/PRD.md` e a SPEC foi escrita.

## D-003 — Documentos de `docs/` renomeados para `.md`
- **Data:** 2026-11-09
- **Decisão:** `ponto-seguro.txt` e `lore-etrava.txt` viraram `.md`. Conteúdo intocado.
- **Motivo:** o `CLAUDE.md` e o PRD os citam como `.md`, e o texto já é Markdown.
- **Alternativa descartada:** manter `.txt` e ajustar as referências.

## D-004 — Três módulos Gradle + duas pastas de dados
- **Data:** 2026-11-09
- **Decisão:** `core-sim` (Java puro), `game-client` (libGDX), `tools` (CLI); `content/` e `assets/` são pastas de dados. O validador e o carregador de conteúdo moram no `core-sim`.
- **Motivo:** é o mínimo que garante a separação simulação/apresentação (RNF-010) e deixa cliente e ferramentas usarem o mesmo validador.
- **Alternativa descartada:** módulo `content` separado com código próprio — fronteira a mais sem ganho.

## D-005 — Conteúdo em YAML, save em JSON, ambos via Jackson
- **Data:** 2026-11-09
- **Decisão:** arquivos de conteúdo em YAML; save em JSON; uma biblioteca só (Jackson).
- **Motivo:** YAML aceita comentários e texto de várias linhas (importa para escrever diálogo); JSON é mais rígido e previsível para o save e para o hash de estado.
- **Alternativa descartada:** JSON para tudo (ruim de escrever diálogo à mão); formato binário para o save (dificulta depuração e migração).

## D-006 — Formato próprio de diálogo, com condições e efeitos estruturados
- **Data:** 2026-11-09
- **Decisão:** diálogos e arcos em YAML próprio; condições/efeitos são dados estruturados de um vocabulário único, não uma linguagem de script.
- **Motivo:** o validador precisa analisar o conteúdo sem executá-lo (2 fontes por fato, camadas V2/V3, nós inalcançáveis, desfecho padrão). Isso é simples com dados estruturados e difícil com script.
- **Alternativa descartada:** Ink ou Yarn Spinner (runtime Java pouco maduro ou inexistente, e análise estática difícil); scripting embarcado (RNF-014).

## D-007 — Tempo em ticks fixos; avanço rápido por blocos
- **Data:** 2026-11-09
- **Decisão:** simulação a 30 ticks por segundo real; 1 dia = 36.000 ticks (20 min reais), configurável e gravado no save. Esperar/dormir/headless pulam de hora em hora e de evento em evento, sem rodar ticks detalhados.
- **Motivo:** passo fixo é a base do determinismo (RF-144); o avanço por blocos cumpre os limites de desempenho (RNF-024/025) e a ordem correta dos gatilhos (AT-07).
- **Alternativa descartada:** passo variável (não determinístico); rodar todos os ticks detalhados no avanço rápido (lento sem necessidade).

## D-008 — RNG próprio (SplitMix64)
- **Data:** 2026-11-09
- **Decisão:** gerador único da simulação com estado de um `long`, gravado no save.
- **Motivo:** os geradores do JDK não expõem o estado para salvar e restaurar de forma simples. São poucas linhas.
- **Alternativa descartada:** `java.util.Random` / `SplittableRandom`.

## D-009 — Eventos externos (E1–E5) usam o motor de arcos
- **Data:** 2026-11-09
- **Decisão:** não há sistema separado de "eventos de calendário"; são arcos que acontecem fora de tela e emitem fatos.
- **Motivo:** um sistema a menos; E4 (Berta) já é uma história com estágios.
- **Alternativa descartada:** agendador de eventos externos dedicado.

## D-010 — Mapas no Tiled (`.tmj`), terreno plano no MVP
- **Data:** 2026-11-09
- **Decisão:** áreas são plantas ortogonais do Tiled (1 tile = 1 m), exportadas em JSON e lidas direto pelo `core-sim` (colisão, pontos) e pelo cliente (cena 3D). Sem relevo no MVP.
- **Motivo:** DP-03 do PRD; arquivo versionável, editável por humano e por IA; um parser só. Relevo complica navegação e autoria sem ser exigido.
- **Alternativa descartada:** editor interno (mais código); carregador TMX do libGDX (prenderia o `core-sim` ao libGDX).
- **Revisar:** portão visual do M1, se o plano ficar pobre.

## D-011 — Pathfinding A* próprio em grade
- **Data:** 2026-11-09
- **Decisão:** A* sobre grade de 0,5 m, dentro do `core-sim`.
- **Motivo:** a navegação precisa estar na simulação (headless), e as bibliotecas de pathfinding do ecossistema libGDX trazem dependência do framework. A* em grade é pequeno e bem conhecido.
- **Alternativa descartada:** gdx-ai; navmesh.

## D-012 — Save em arquivo único `.etrava` (ZIP) em `%APPDATA%\Etrava`
- **Data:** 2026-11-09
- **Decisão:** cada save é um ZIP com `meta.json`, `state.json` e `thumb.png`; escrita em arquivo temporário + renomear.
- **Motivo:** um arquivo só torna a escrita atômica trivial (RF-365). `%APPDATA%` evita problemas de pastas "Documentos" redirecionadas para nuvem.
- **Alternativa descartada:** pasta por save (renomear pasta não é atômico no Windows); `Documentos\Etrava`.

## D-013 — UI e console de depuração em scene2d
- **Data:** 2026-11-09
- **Decisão:** toda a interface, inclusive o console de depuração, usa o scene2d do libGDX.
- **Motivo:** já vem no framework; nenhuma dependência nativa extra.
- **Alternativa descartada:** Dear ImGui para depuração.

## D-014 — Distribuição por `jpackage` app-image, sem instalador
- **Data:** 2026-11-09
- **Decisão:** o release do MVP é uma pasta com `Etrava.exe` + runtime, entregue em ZIP.
- **Motivo:** cumpre "rodar sem Java instalado" (RNF-004) sem exigir ferramenta externa; instalador `.msi`/`.exe` pelo `jpackage` exige WiX (RNF-006).
- **Alternativa descartada:** instalador no MVP.

## D-015 — Código e chaves de esquema em inglês; IDs e textos em português
- **Data:** 2026-11-09
- **Decisão:** identificadores Java e chaves dos arquivos de dados em inglês; IDs de conteúdo, nomes e textos em pt-BR (IDs sem acento). Documentos e commits em pt-BR.
- **Motivo:** chaves espelham o código (mapeamento direto); o conteúdo fica legível para quem escreve a história.
- **Alternativa descartada:** tudo em português (mistura com APIs em inglês); tudo em inglês (conteúdo menos legível).

## D-016 — Reputação derivada das opiniões
- **Data:** 2026-11-09
- **Decisão:** reputação numa facção = média das opiniões dos membros sobre o jogador; atos só mudam a opinião de quem viu ou ouviu falar.
- **Motivo:** cumpre "não onisciente" (RF-280/281) sem um segundo número para manter em sincronia.
- **Alternativa descartada:** valor de reputação independente por facção.

## D-017 — Pós-processamento com passe próprio
- **Data:** 2026-11-09
- **Decisão:** um passe de tela cheia próprio para gradação de cor e vinheta; névoa na cena; bloom opcional avaliado no M1.
- **Motivo:** são poucos efeitos e simples; evita depender de biblioteca de efeitos cuja compatibilidade ainda não foi testada.
- **Alternativa descartada:** adotar de saída uma biblioteca de pós-processamento.

## D-018 — Visual 2D isométrico com sprites (substitui a DP-01 do PRD)
- **Data:** 2026-11-09
- **Decisão:** o jogo é **2D isométrico** (projeção 2:1, tile de 128×64 px), com cenário pintado e personagens em sprites de 8 direções. A DP-01 do PRD recomendava 3D estilizado com câmera isométrica.
- **Motivo:** o diretor delegou a escolha e declarou preferência por um 2D bonito, no espírito dos isométricos de 1996 (instrução do usuário > PRD). Concordo com a troca por três razões: (1) cenário 2D pintado é onde a IA generativa de imagem rende mais, enquanto props 3D dependeriam de pacotes prontos — o risco R2, "cara de asset pack"; (2) variantes de estado (árvore viva/morta) viram troca de sprite; (3) o cliente fica mais simples (sem shaders PBR, sombras, importação de modelos em tempo de execução).
- **O que se perde:** câmera livre, 3ª e 1ª pessoa (RF-026, [LP]) deixam de ser evolução natural; rotação de câmera em 90° (RF-022, opcional) sai; a luz dinâmica vira mapa de luz 2D, menos rica que luz 3D.
- **Reversibilidade:** a simulação não conhece a apresentação (RNF-010); trocar o visual de novo não toca na lógica.
- **Alternativa descartada:** 3D estilizado (DP-01).
- **Revisar:** portão visual do M1.

## D-019 — Sprites de personagem por pré-renderização de modelos 3D (hipótese, a confirmar no M1)
- **Data:** 2026-11-09
- **Decisão:** o caminho principal para personagens é renderizar modelos 3D já animados em folhas de sprites de 8 direções, com uma ferramenta própria em `tools`. Os modelos são matéria-prima; não vão para o jogo.
- **Motivo:** o ponto fraco do 2D apontado pelo PRD (5.1) é manter o mesmo personagem consistente em 8 direções × várias animações com IA de imagem. A pré-renderização resolve isso e é o método histórico do gênero.
- **Alternativa a testar no mesmo portão:** sprites gerados por IA e retocados.
- **Situação:** hipótese de trabalho; a decisão final sai do portão visual, com imagem na tela.

## D-020 — Versões fixadas no M0
- **Data:** 2026-11-09
- **Decisão:** Gradle 9.9.0 (wrapper), libGDX 1.14.2, Jackson 2.23.0, SLF4J 2.0.20, Logback 1.5.38, JUnit 5.14.4.
- **Motivo:** versões estáveis atuais, testadas juntas no M0: build com o JDK 25 como padrão da máquina e toolchain 21, testes, execução e empacotamento.
- **Observação:** o gdx-gltf saiu do cliente com a D-018; só volta, dentro de `tools`, se a D-019 for confirmada.

## D-021 — Mapas do Tiled em orientação isométrica
- **Data:** 2026-11-09
- **Decisão:** os `.tmj` usam orientação isométrica (ajuste da D-010), com tileset embutido e camadas em CSV sem compressão.
- **Motivo:** com o jogo em 2D isométrico, o Tiled mostra o mapa como ele aparece no jogo. A grade lógica continua a mesma (1 tile = 1 m).
- **Alternativa descartada:** planta ortogonal (exigiria imaginar o resultado).

## D-022 — Mapa de teste gerado por script
- **Data:** 2026-11-09
- **Decisão:** `content/areas/area_teste.tmj` foi gerado por script, não desenhado à mão; o formato é o do Tiled, mas ainda não foi aberto no Tiled para conferir.
- **Motivo:** rapidez no M0; é uma área descartável, só para provar movimento, colisão e câmera.

## D-023 — Construções são um sprite só por fora; o interior é outra área
- **Data:** 2026-11-10
- **Decisão:** no exterior, cada construção é um prop grande, com um único sprite e base que bloqueia a passagem. Entrar pela porta carrega o interior como área separada (RF-005).
- **Motivo:** é o jeito simples e bonito de fazer prédios em 2D isométrico; evita montar paredes e telhados em peças que precisam sumir. O PRD 6.3 já lista os interiores como áreas próprias. O "telhado some ao entrar" (RF-006) fica atendido pela troca de área.
- **Alternativa descartada:** interiores na mesma área, com telhado e paredes da frente recortados.
- **Consequência:** quem está na rua não vê quem está dentro. Aceito para o MVP.

## D-024 — Arte provisória gerada por código
- **Data:** 2026-11-10
- **Decisão:** toda a arte da cena de referência (tiles, objetos, casas, personagens) é desenhada por código Java em `tools` (`gradlew generateArt`), com manifesto próprio (`provisorio.yaml`). Qualquer outro manifesto que repita um ID tem prioridade.
- **Motivo:** eu não gero imagens por IA; a arte definitiva depende do usuário. Com arte provisória coerente dá para validar luz, escala, ordem de desenho e clima agora, e trocar arquivo por arquivo depois. Sem ferramenta externa: é Java puro, roda pelo Gradle.
- **Alternativa descartada:** seguir só com caixas coloridas até a arte chegar (não permitiria avaliar o visual); baixar pacotes de arte de terceiros (licenças e estilo misturado — risco R2).

## D-025 — Personagens: boneco articulado desenhado por código (forma atual da D-019)
- **Data:** 2026-11-10
- **Decisão:** as folhas de sprites dos personagens saem de um boneco 3D simples (segmentos articulados) projetado nas 8 direções e em cada quadro, dentro do `generateArt`. Não há modelo 3D externo nem gdx-gltf no projeto.
- **Motivo:** dá a consistência entre direções e quadros que a D-019 buscava, sem depender de modelos de terceiros. O visual é de manequim — serve como provisório.
- **Situação:** a qualidade final dos personagens continua em aberto e é o ponto mais fraco do visual. Opções para depois do portão: melhorar o boneco (roupas, volumes, mais animações) ou pré-renderizar modelos 3D licenciados.

## D-026 — Luz 2D: buffer de luz multiplicado + buffer de brilho
- **Data:** 2026-11-10
- **Decisão:** a cena é desenhada num buffer; outro buffer recebe a cor do céu e as manchas de luz e é multiplicado sobre ela; um terceiro recebe só o que brilha (janelas, chamas), com os demais sprites em preto para tapar o que está atrás, e é somado no fim. Depois vem um passe de saturação, contraste e vinheta.
- **Motivo:** dá noite com luz quente localizada usando só sprites e três buffers, sem normal maps.
- **Limite conhecido:** a luz não respeita paredes (atravessa objetos) e não projeta sombras.

## D-027 — Figurantes de ronda, provisórios
- **Data:** 2026-11-10
- **Decisão:** mapas podem ter, na camada `spawns`, objetos de classe `figurante` que andam entre pontos sorteados pelo RNG da simulação.
- **Motivo:** o M1 pede dois personagens animados na cena; agendas de NPC só existem no M2.
- **Situação:** substituído pelas agendas no M2.

## D-028 — Portão visual do vilarejo aprovado com arte provisória
- **Data:** 2026-11-10
- **Decisão:** o diretor aprovou o clima (luz, paleta, noite) e aceitou seguir com a arte provisória. O M1 fica concluído quanto ao vilarejo; as capturas de taverna por dentro, estrutura enterrada e Posto (AT-15) serão avaliadas quando essas áreas existirem.
- **Pendência registrada:** o MCP da Dreamina não está disponível para o Claude Code nesta máquina (não há servidor MCP configurado além de Claude Docs e Google Drive). A arte definitiva segue dependendo disso ou de outra fonte.
- **Regra mantida:** nada de produção de arte em massa enquanto a arte definitiva não tiver uma amostra aprovada.

## D-029 — Agendas conferidas a cada 10 minutos de jogo; todo o estado mora no `World`
- **Data:** 2026-11-10
- **Decisão:** as agendas dos NPCs são avaliadas a cada 10 minutos de jogo (250 ticks), para o mundo inteiro. Caminhos em andamento, direção do teclado e fila de eventos ficam no `World` e vão para o save; a `Simulation` só guarda o que dá para recalcular.
- **Motivo:** 10 minutos é fino o bastante para blocos que começam em horas quebradas e barato o bastante (30 dias headless em centésimos de segundo). Guardar tudo no `World` é o que faz "salvar, carregar e continuar" dar exatamente o mesmo resultado (AT-05).
- **Alternativa descartada:** avaliar por hora cheia (a SPEC original; perderia blocos às :30); recalcular caminhos ao carregar (quebraria a igualdade de estado).

## D-030 — NPC "dentro de casa" fica oculto; entra e sai da área pelo ponto `entrada`
- **Data:** 2026-11-10
- **Decisão:** um bloco de agenda pode ter `hidden: true`: o NPC vai até a porta e deixa de aparecer (está dentro de uma construção sem interior jogável). Para trocar de área, o NPC anda até o ponto `entrada` do mapa, se houver, e some; ao chegar, aparece nele.
- **Motivo:** consequência da D-023 (interiores são outras áreas). Mantém o vilarejo coerente antes de os interiores existirem.
- **Revisar:** M7, quando os interiores existirem — o NPC passará a estar de fato na área interna.

## D-031 — Troca de área por comando; gatilhos de porta ficam para depois
- **Data:** 2026-11-10
- **Decisão:** existe o comando `EnterArea` na simulação (usado pelo console `area ID`), mas ainda não há gatilhos no mapa (portas, fim da estrada).
- **Motivo:** o M2 só precisava de duas áreas para provar a simulação fora da área carregada e o salvamento automático na troca. Os gatilhos entram com os interiores.

## D-032 — Save sem miniatura por enquanto
- **Data:** 2026-11-10
- **Decisão:** o `.etrava` tem `meta.json` e `state.json`; a `thumb.png` prevista na SPEC entra junto com a tela de salvar/carregar (M7).
- **Motivo:** sem tela de saves, a miniatura não teria uso.

## D-033 — Direção visual: pixel art dark fantasy, paleta única
- **Data:** 2026-11-11
- **Decisão:** a arte do jogo é pixel art. Um pixel de arte vale 4×4 pixels de mundo (tile isométrico de 32×16, personagem de ~24 px). Toda imagem — de código, IA ou mão — é reduzida para essa grade e tem as cores trocadas pelas de uma paleta única de 53 cores (`PixelArt`). A câmera só usa zoom inteiro (1× a 4×) e os sprites são alinhados à grade.
- **Motivo:** pedido do diretor ("pixel art, bonito, nível Stardew Valley e Diablo, bem dark fantasy", sem cara de feito por IA). A paleta compartilhada unifica origens diferentes e elimina o risco R2; na escala pequena os personagens deixam de parecer manequins.
- **Alternativa descartada:** o estilo pintado em alta resolução do M1 (capturas em `docs/capturas/m1`).
- **Consequência:** a mistura suave entre terrenos virou borda irregular de pixels; o projeto continua isométrico (D-018).

## D-034 — Dreamina pela CLI, para retratos; cenário e personagens seguem por código
- **Data:** 2026-11-11
- **Decisão:** retratos de NPC são gerados na Dreamina (CLI `dreamina-canvas`, modelo Seedream 5.5, 30 créditos cada) e convertidos por `gradlew importPortraits`. Os originais ficam em `arte-bruta/` (fora do git). Cenário isométrico e personagens animados continuam saindo do gerador por código.
- **Motivo:** retrato é onde a IA rende mais e onde consistência de ângulo não importa; já cenário isométrico e animação em 8 direções são onde ela falha. O diretor liberou os créditos da conta.
- **Regra de uso:** sempre com teto de créditos por imagem; informar o gasto.
- **Pendência:** conferir os termos de uso comercial da Dreamina antes de um release (registrado no manifesto de cada retrato).

## D-035 — Formato do save subiu para a versão 2, sem migração da 1
- **Data:** 2026-11-11
- **Decisão:** o M3 mudou o formato dos efeitos agendados e acrescentou conhecimento, opiniões e conversas ao save. `SCHEMA_VERSION` = 2; saves da versão 1 são recusados com mensagem clara.
- **Motivo:** ainda não existe jogo publicado nem save que valha migrar; escrever a migração seria trabalho sem usuário. O mecanismo de migração existe e é testado.

## D-036 — Rumores por proximidade, uma vez por hora
- **Data:** 2026-11-11
- **Decisão:** a cada hora de jogo, NPCs acordados a até 6 m um do outro têm 50% de chance de contar um fato que o outro não sabe; em 15% das vezes ele passa noutra versão. Um NPC fica com a primeira versão que ouviu.
- **Motivo:** é a regra simples que o PRD pede (RF-122, MVP-S) e passa no AT-08 em 10 sementes. Proximidade funciona melhor que "mesmo ponto da agenda": quem está dentro da taverna e quem está à porta conversam.
- **Ajustável:** as três constantes ficam em `Story`.

## D-037 — Conversa ambiente só acontece com o jogador por perto
- **Data:** 2026-11-11
- **Decisão:** uma conversa ambiente só começa se o jogador estiver a até 9 m; se ele se afastar antes do fim, não anota nada. Conversas com `cooldownHours: 0` tocam uma única vez.
- **Motivo:** é o que torna "perder a conversa" possível (P3) sem gastar o conteúdo fora de cena. Como todo fato de ramificação tem duas fontes, perder uma não tranca o caminho.

## D-038 — "Perguntar sobre" usa os fatos marcados no caderno
- **Data:** 2026-11-11
- **Decisão:** numa conversa, até 3 fatos que o jogador marcou no caderno aparecem como opções de pergunta. O NPC responde com a versão que conhece (o jogador anota como afirmação dele) ou diz que não sabe.
- **Motivo:** cumpre o RF-126 na forma simples, sem tela extra, e dá uso à marcação do caderno.
