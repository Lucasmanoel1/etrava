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

## D-039 — A árvore começa morta; o conteúdo do M3 passou a depender do arco
- **Data:** 2026-11-11
- **Decisão:** o jogo começa com a árvore do moinho morta e o arco A1 em `antes`. O que os NPCs sabem sobre a árvore deixou de vir do arquivo deles: é o arco que ensina, estágio a estágio. Diálogos e conversas ambientes do M3 só oferecem esses assuntos quando o NPC já sabe.
- **Motivo:** o PRD põe a restauração por volta do dia 2, e ver a árvore mudar é a prova mais clara de "o mundo mudou sem mim" (AT-10). Com o conteúdo antigo, todo mundo já comentava a árvore às 6 h do dia 1.
- **Alternativa descartada:** começar com a árvore já viva (menos trabalho, mas o jogador nunca veria a mudança).

## D-040 — Arco sobrepõe agenda por bloco com condição, não por efeito `override`
- **Data:** 2026-11-11
- **Decisão:** um bloco de agenda pode ter `when`; vale o primeiro bloco da lista que contém a hora e cuja condição passa. O efeito `override` da SPEC não foi implementado.
- **Motivo:** cumpre o RF-083 sem estado novo no save: "Nalia passa a noite na árvore enquanto o arco está em insônia" é uma linha no arquivo dela, e some sozinha quando o estágio muda.
- **Alternativa descartada:** efeito `override` com prazo (precisa guardar sobreposições por NPC e não expressa bem "toda noite, enquanto durar").

## D-041 — Desfecho de A1 sem o jogador depende de um viajante e de um rumor
- **Data:** 2026-11-11
- **Decisão:** um arco à parte sorteia se um estudioso passa pelo Posto (2 chances em 5). Ele só estabiliza a árvore se, além disso, o viajante tiver ouvido que a menina escuta vozes — isto é, se o rumor saiu do vilarejo. Senão, sorteio: o pai corta a árvore (peso 2) ou Nalia piora (peso 1). Em 20 sementes: 7 estabilizada, 7 cortada, 6 piora.
- **Motivo:** o PRD pede desfecho "decidido pelo estado do mundo" e ao menos um não trágico sem o jogador. Amarrar o final à rede de rumores faz os sistemas do M3 e do M4 conversarem.
- **Ajustável:** pesos e prazos estão em `content/arcs/`.
- **[A APROVAR] (narrativo):** os dois caminhos exclusivos do jogador (dividir o custo entre 3 voluntários; convencer Nalia a soltar a árvore) e o texto de todos os desfechos são proposta minha.

## D-042 — Reação a atos: efeito `deed` pronto; os gatilhos de jogo vêm depois
- **Data:** 2026-11-11
- **Decisão:** o M4 entrega o mecanismo (testemunhas, opinião, memória, rumor do ato, recusa de conversa com opinião ≤ −40), acionado por efeito de conteúdo ou pelo console (`ato roubo`). Furto, invasão e agressão como ações do jogador entram com inventário (M5) e combate (M6). Confronto e expulsão ficam para o M6.
- **Motivo:** hoje o jogador não tem como roubar nem bater; inventar um gatilho provisório seria trabalho jogado fora. O RF-089 é MVP-S.

## D-043 — Notícias chegam ao jogador por um nó de "novidades"
- **Data:** 2026-11-11
- **Decisão:** um nó de diálogo com `gossip` conta o fato mais recente que o NPC sabe e o jogador não. O viajante e a taberneira têm esse nó. Os eventos externos E1–E5 só ensinam o fato ao viajante; o resto é a rede de rumores.
- **Motivo:** cumpre o RF-125 sem escrever um diálogo por notícia, e a versão que o jogador ouve depende de quem conta.
- **Limite:** o validador não conta essa fonte na regra das 2 fontes (ele não sabe o que cada NPC vai saber), então fato que destrava caminho continua precisando de 2 fontes escritas.

## D-044 — Save na versão 3, sem migração da 2
- **Data:** 2026-11-11
- **Decisão:** o M4 acrescentou arcos, variantes de props, memórias, situação dos NPCs, ritmo e epílogo ao save. `SCHEMA_VERSION` = 3; saves da versão 2 são recusados com mensagem clara.
- **Motivo:** o mesmo da D-035 — ainda não há save que valha migrar. Além disso, um save antigo começaria os arcos no meio do mês.

## D-045 — Epílogo ao fim do dia 30; ritmo Contemplativo sem tela ainda
- **Data:** 2026-11-11
- **Decisão:** o Capítulo I termina quando o dia 30 acaba (meia-noite): o epílogo é montado uma vez, aparece numa tela de papel e o jogo continua. A outra saída do PRD (deixar a região com intenção declarada) entra no M7, com a estrada. O ritmo Contemplativo (prazos ×2) já funciona na simulação e no `simulate --ritmo contemplativo`; a escolha no início do jogo entra com o menu (M7).
- **Motivo:** sem a área da estrada e sem menu, as duas coisas não teriam onde aparecer.
- **Observação:** em Contemplativo, A1 pode não ter terminado no dia 30; o epílogo usa então o fragmento "ainda não acabou".

## D-046 — Fome é a única necessidade do personagem
- **Data:** 2026-11-11
- **Decisão:** a fome vai de 0 a 100 em um dia sem comer. A partir de 60 o HUD diz "com fome"; a partir de 90 o personagem está faminto e rende e aprende pela metade (pagamento e ganho de habilidade). Ninguém morre de fome. Comer não gasta tempo.
- **Motivo:** o marco pede "viver 10 dias só trabalhando e comerciando", e a SPEC já previa a política `trabalhador` (trabalha, come, dorme). Sem um custo de vida, trabalhar seria só juntar dinheiro. Fome é o custo mais simples e mais legível.
- **Alternativa descartada:** sono, frio ou aluguel como necessidades — mais telas e regras sem mudar o que o marco prova. O fôlego do combate entra no M6 e pode passar a sentir a fome.

## D-047 — Fórmula do preço e "sem lucro garantido"
- **Data:** 2026-11-11
- **Decisão:** preço de compra = valor × modificador regional × (1 − opinião/500) × (1 − Comércio/400); o comerciante paga 40% disso, com os fatores invertidos, arredondando para baixo. Opinião ±100 mexe ±20%; Comércio 100 tira até 25%. Coisa barata demais vale 0 e o comerciante não compra. O modificador regional é por categoria ou marca e vale para o mundo todo (o MVP tem uma região); o efeito `priceMod` multiplica o atual. E2 multiplica a marca `sal` por 1,5.
- **Motivo:** cumpre o RF-262 com números que se leem; mesmo no melhor caso o jogador não lucra comprando e revendendo no mesmo balcão.
- **Ajustável:** constantes em `Economy`; valores e estoques em `content/`.

## D-048 — Reposição volta o estoque ao máximo
- **Data:** 2026-11-11
- **Decisão:** à meia-noite, a cada `restockDays`, todo item abaixo do mínimo ou acima do máximo volta ao máximo, e o caixa volta ao valor inicial se tiver caído abaixo. Contêineres com reposição voltam ao conteúdo inicial (sem tirar o que o jogador pôs).
- **Motivo:** é a leitura direta do AT-09 ("o estoque volta aos limites") e é determinística, sem sorteio.

## D-049 — Trabalho é um salto de tempo oferecido na conversa
- **Data:** 2026-11-11
- **Decisão:** trabalho (`jobs.yaml`) é uma espera que paga: o tempo salta como no `esperar` (o mundo segue e um evento pode interromper; interrompido, paga proporcional), o personagem termina no posto de trabalho, as habilidades sobem por hora, e quem deu o trabalho pode contar uma novidade. Cada trabalho uma vez por dia, numa janela de horário, com quem o dá presente e acordado. É oferecido na conversa: escolha com `{when: {job: id}}` e efeito `{startJob: id}`. Três trabalhos: oficina (ferreiro), colheita (aldeã) e carga no Posto.
- **Motivo:** PRD 7.2 pede ação temporizada sem minijogo, que também seja lugar de ouvir coisas. Oferecer pela conversa evita botão de "trabalhar" sem contexto e deixa quem detesta o jogador não oferecer (RF-282).
- **Provisório:** até Avel existir (M7), quem dá o trabalho da oficina é o ferreiro.

## D-050 — Onde ficam os dois comerciantes até o M7
- **Data:** 2026-11-11
- **Decisão:** o comércio do vilarejo é a taverna; para dar para negociar, a taberneira passa a servir do lado de fora das 15 h às 23 h. O comerciante do Posto (NPC novo, sem nome próprio) atende na área de teste, que já fazia as vezes da estrada para o viajante, até o Posto ganhar mapa no M7. Até lá, chegar ao Posto é pelo console (`area area_teste`).
- **Motivo:** o MVP pede 2 comerciantes (vilarejo e Posto) e o AT-09 fala do Posto; interiores e o mapa do Posto são do M7.

## D-051 — Contêiner com dono: pegar é roubo, se alguém vir
- **Data:** 2026-11-11
- **Decisão:** contêineres (`containers.yaml`) ficam num ponto do mapa; clicar perto abre a lista. Pegar de um contêiner com dono dispara o ato `roubo` — que, como todo ato (D-042), só pesa para quem estiver a até 10 m, acordado e à vista. O dono não descobre depois sozinho.
- **Motivo:** RF-204 diz "se testemunhado"; descoberta posterior seria regra nova sem pedido.

## D-052 — Reputação é a média das opiniões de quem é da comunidade
- **Data:** 2026-11-11
- **Decisão:** NPC ganha o campo `faction` (hoje `pedra_mansa` e `posto`). A reputação numa comunidade é a média das opiniões dos membros presentes; condição `{reputation: {faction, min, max}}`. A taverna não vende para quem tem reputação abaixo de −25 em Pedra-Mansa; os trabalhos não são oferecidos por quem tem opinião abaixo de −20.
- **Motivo:** RF-280 pede reputação derivada, não onisciente; como cada opinião só muda pelo que o NPC viu ou ouviu (RF-281), a média herda isso sem estado novo.

## D-053 — Mochila e ficha param o relógio; o comércio também
- **Data:** 2026-11-11
- **Decisão:** com o comércio ou um contêiner aberto, a simulação não avança (como no diálogo). Com a mochila (I) ou a ficha (C) abertas, o cliente aplica os comandos por `Simulation.stepPaused()`, que não avança o relógio. Comer e trocar de roupa valem a qualquer hora.
- **Motivo:** RF-324 (o relógio pausa em menus e no inventário). Os comandos continuam no registro, então a partida segue reexecutável.

## D-054 — Habilidades: curva 1,6 e o que treina cada uma
- **Data:** 2026-11-11
- **Decisão:** ganho = base × (1 − valor/100)^1,6. Ofício e Vigor sobem nos trabalhos (por hora), Comércio a cada compra e venda, Observação ao escutar uma conversa alheia até o fim (e cada ponto aumenta a distância de escuta em 6 cm, RF-184), Lábia ao usar uma fala com condição `skill` (que aparece com o rótulo da habilidade). Luta fica para o M6. Valores iniciais vêm da origem.
- **Motivo:** RF-180–182; habilidade sobe pelo que se faz, sem pontos para distribuir. Em 10 dias de trabalho, Ofício vai de 20 a ~39.

## D-055 — Origem já é um arquivo de dados
- **Data:** 2026-11-11
- **Decisão:** `content/origins/pedra_mansa.yaml` existe desde o M5, com dinheiro (8), itens, equipamento e habilidades iniciais. Local inicial, relações e conhecimentos entram no M7.
- **Motivo:** o personagem precisava começar com alguma coisa, e pôr isso em código seria trabalho jogado fora (PRD 8.4: origem é pacote de dados).

## D-056 — Save na versão 4, sem migração da 3
- **Data:** 2026-11-11
- **Decisão:** o M5 acrescentou dinheiro, mochila, equipamento, habilidades, fome, trabalhos do dia, modificadores de preço, comerciantes e contêineres. `SCHEMA_VERSION` = 4; saves da versão 3 são recusados com mensagem clara.
- **Motivo:** o mesmo da D-035/D-044. Um save antigo começaria sem nada no bolso e sem habilidades.

## D-057 — Combate: números, golpe anunciado e esquiva
- **Data:** 2026-11-11
- **Decisão:** combate em tempo real na `core-sim` (classe `Combat`), só na área carregada. Vida = 100 + 0,5 × Vigor; fôlego idem. Golpe custa 10 de fôlego, esquiva 22 (2,6 m em 0,3 s, sem levar dano), golpe pesado 30 (× 2,2, recarga 6 s), empurrão 20 (2,5 m para trás e 1,4 s atordoado, recarga 8 s). O fôlego volta 24/s depois de 0,7 s sem esforço; faminto, pela metade. Chance de acertar = 0,72 + (Luta − Luta do alvo)/200; dano = arma × (1 + Luta/100) − proteção. O inimigo **anuncia** o golpe (`windup`, a barra dele acende) e só acerta se o jogador ainda estiver ao alcance e não estiver esquivando. Luta sobe a cada golpe que acerta e a cada inimigo abatido. A vida volta 3 a cada 10 minutos de jogo fora de combate; o unguento cura 20 (stat `cura`).
- **Motivo:** PRD 7.3 pede "preparação importa, reflexo não". O aviso do golpe dá tempo de sobra para esquivar; o que decide é chegar descansado, armado, vestido e com luz.
- **Controles:** clique no inimigo ataca, segurar continua (RF-220); Q golpe pesado, E empurrão, Shift esquiva (na direção do WASD, ou para trás), R render-se. Espaço já pausava o jogo (RF-226).
- **Ajustável:** constantes no topo de `Combat`; inimigos em `content/enemies.yaml`.

## D-058 — Inimigos só existem na área carregada
- **Data:** 2026-11-11
- **Decisão:** inimigos vivem em `World.enemies` enquanto o jogador está na área; ao sair, somem. Encontros (`encounters.yaml`) são sorteados ao chegar à área e a cada hora nela, inclusive dormindo ou esperando — e aí o ataque interrompe o descanso (RF-323). Cada encontro tem condição, chance e intervalo mínimo. Efeito `{encounter: id}` força um (RF-102, "iniciar combate").
- **Motivo:** SPEC 3.2 já põe o combate na escala detalhada. Simular lobos fora de cena não muda nada que o jogador possa perceber no MVP.
- **Alternativa descartada:** inimigos persistentes por área — estado e regras novas sem ganho visível.

## D-059 — Derrota não letal: 4 a 8 horas, roubado, achado por alguém
- **Data:** 2026-11-11
- **Decisão:** vida zero fora de área ou inimigo letal = desmaio. Se havia saqueador na luta, levam 60% do dinheiro e até 2 itens da mochila (os mais valiosos, nunca o que está no corpo). O personagem acorda 4 a 8 horas depois (sorteado), no ponto `wakeAt` da área, com 30% da vida; o mundo andou nesse meio-tempo, como num sono. O NPC `finder` da área (se presente) fica sabendo (fato `jogador_achado_desacordado`) e lembra (`socorreu`) — o comerciante do Posto comenta. Sem saqueador (lobo), ninguém rouba.
- **Motivo:** RF-340 e AT-14 ("acordar horas depois, roubado, com o mundo avançado"). O "achado por alguém" dá consequência social sem punir mais.

## D-060 — Morte: área letal inteira, tela com o mundo e carregar
- **Data:** 2026-11-11
- **Decisão:** a estrutura enterrada é uma área `lethal`; o vulto também é `lethal`. Morrendo, o mundo para (a simulação não aceita mais nada), e a tela de morte mostra o epílogo como estaria agora ("o que aconteceu com o mundo", RF-341) e os saves que existem (rápido e automáticos) para carregar. Morto não salva.
- **Motivo:** o PRD quer a morte rara e explícita. Uma área letal inteira é a regra mais legível: o jogador sabe que desce por conta própria.
- **Alternativa descartada:** só o fundo da câmara letal — exigiria zonas dentro da área, que o MVP não tem.

## D-061 — Fuga e rendição valem para os dois lados
- **Data:** 2026-11-11
- **Decisão:** inimigo com pouca vida (`flee`) foge; se for humano, pode se render (`surrender`). Rendido, o jogador decide numa folha: poupar, tomar o que ele leva, ou acabar com ele (ato `execucao`: −50 para quem viu, −20 para quem ouviu). O jogador também pode se render (R): saqueadores levam 60% do dinheiro e vão embora; lobo e vulto não aceitam. Fugir é correr: longe de casa (`leash`) ou do jogador, o inimigo desiste e volta.
- **Motivo:** RF-224 e PRD 7.3 (poupar, roubar, ameaçar). Render-se e fugir são o "caminho pacífico" de um combate.

## D-062 — Escuridão e luz também valem na simulação
- **Data:** 2026-11-11
- **Decisão:** área `dark` (a estrutura) tem raio de luz de 1,5 m; à noite (20 h–5 h), 4,5 m; cada ponto de `luz` equipado soma 0,75 m (lamparina: +4,5). No escuro sem luz, o jogador acerta 20% menos. O vulto teme a lamparina: dentro do raio dela fica mais lento, golpeia 2,5× menos, apanha 50% mais e causa 30% menos dano. O cliente desenha o mesmo raio, e só se mira em inimigo dentro dele.
- **Motivo:** RF-008 e "preparação importa": descer sem lamparina é possível, mas é mais perigoso. A luz deixa de ser só enfeite.

## D-063 — Q-07: o que há na estrutura enterrada (proposta, a aprovar)
- **Data:** 2026-11-11
- **Decisão provisória:** uma sala sob o buraco, um corredor com fibras petrificadas saindo das paredes (parecem raiz, são pedra), um nicho e uma câmara com uma laje riscada. No nicho: lascas de fibra mornas e uma placa com marcas que se repetem em grupos de nove. Na câmara mora o **vulto**: fibra escura e terra que se move no escuro, sem rosto, que recua da lamparina; ao perceber alguém, ouve-se "um som de voz, longe, chamando alguém por um nome que não é o seu" (o "eco"). Nada se explica; ninguém no Capítulo I sabe o que é. Camada V1: só "há algo sob a terra".
- **Motivo:** PRD 6 e A3 pedem área curta, luz limitada, algum perigo, objetos, fragmentos e um eco inexplicável, coerentes com a Sutura (lore: rede de fibras orgânico-minerais) sem confirmá-la. Os nove remetem de longe à Concordata dos Nove — pista, não fato.
- **Pendente:** aprovação do diretor (Q-07). Tudo está em dados (`enemies.yaml`, `items.yaml`, `containers.yaml`, mapa gerado), troca-se sem código. A aparição durante um trabalho e a reação de Avel são A3, no M7.

## D-064 — Saídas de área por ponto; a estrutura se alcança pela área de teste até o M7
- **Data:** 2026-11-11
- **Decisão:** `areas.yaml` ganha `exits`: andar até o ponto leva à outra área (sem clique extra, como uma porta). A área de teste tem a boca do buraco (`buraco`) e a estrutura tem a escada de volta. A área de teste segue fazendo as vezes da estrada (encontros com lobos de noite e saqueadores no fim da tarde) até o mapa da estrada no M7.
- **Motivo:** SPEC 4.7 previa `triggers` de transição; um ponto com raio dá o mesmo resultado com o que já existe no mapa.

## D-065 — Agressão a morador é um empurrão, não uma luta
- **Data:** 2026-11-11
- **Decisão:** Ctrl + clique num NPC = empurrão (ato `agressao`, −45 para quem viu, inclusive o empurrado). NPCs não têm vida nem revidam no MVP.
- **Motivo:** o PROGRESS do M5 prometia agressão no M6; matar moradores mexeria em arcos e agendas de um jeito que o MVP não precisa (P10). Fica a consequência social, que é o que o PRD pede (RF-224, RF-281).

## D-066 — Save na versão 5, com a primeira migração
- **Data:** 2026-11-11
- **Decisão:** o M6 acrescenta o bloco `combat` (vida, fôlego, inimigos, recargas, encontros, morte). `SCHEMA_VERSION` = 5. Saves da versão 4 **são migrados**: entram com vida e fôlego cheios e ninguém por perto. Versões anteriores à 4 continuam recusadas.
- **Motivo:** RF-363 pede migração ou recusa; aqui a migração é trivial e preserva os saves de quem testou o M5.

## D-067 — Elenco do Capítulo I e mapa da região (M7)
- **Data:** 2026-11-12
- **Decisão:** 22 NPCs nomeados (8 de camada A: Avel, Nalia, Tomé, Odila, Oren, Aurel Dask, Sena Brask, Ibbo) e 2 figurantes. Doran, o ferreiro, continua separado de Avel: Avel tem a oficina de ferramentas e mecanismos (lore), Doran tem a forja e é quem propõe cortar a árvore no A1. Região gerada por código (`gradlew generateRegion`): Pedra-Mansa com 15 construções, cemitério, pomar, lavouras com divisa; a estrada com o Posto da Encruzilhada numa área só; 4 interiores. Nomes novos no glossário, todos provisórios.
- **Motivo:** PRD 6.3, 9.1 e 13.2. Gerar os mapas por código mantém tudo editável no Tiled e reproduzível.
- **Alternativa descartada:** fundir Doran em Avel — perderia o terceiro morador do A1 e um amigo de Avel que conta o que sabe.

## D-068 — Interiores em corte, com luz própria
- **Data:** 2026-11-12
- **Decisão:** cada interior é uma área à parte (D-023), vista em corte: só as paredes de fundo (norte e oeste) são desenhadas, como props de 1 m. `interior: true` em `areas.yaml` dá penumbra quente fixa e lareira acesa a qualquer hora. Porta da casa → ponto de saída; NPCs entram e saem pelas mesmas portas.
- **Motivo:** RF-006 (telhado oculto ao entrar) resolvido pela troca de área; corte é o padrão dos isométricos clássicos e não esconde o jogador.

## D-069 — Q-08: quem é o homem de fora (proposta, a aprovar)
- **Data:** 2026-11-12
- **Decisão provisória:** Oren, que trabalhou perto de Mir e perdeu um filho de quatro anos na evacuação. Disseram que o menino morreu na estrada; ele não acredita e procura "o técnico que saiu na noite do acidente com umas crianças". Carrega uma lista de nomes (um "A." riscado). Nada confirma V2: Mir é "o acidente", o técnico é suspeita, e o próprio protagonista não é o filho dele (a mancha atrás da orelha fica sem resposta).
- **Motivo:** PRD 8.5 (A2) e 8.3 (L2: Avel levou "algumas crianças"). Dá ao estranho um motivo humano, sem vilania, e abre gancho para as outras crianças de Mir (Q-01).
- **Pendente:** aprovação do diretor (alto impacto narrativo).

## D-070 — Save na versão 6, com migração da 5
- **Data:** 2026-11-12
- **Decisão:** as áreas mudaram de nome (`vila_ref` → `vila`, `area_teste` → `estrada`). A migração 5→6 troca os nomes em todo o estado; ao carregar, quem estiver dentro de uma parede (mapa novo) volta ao ponto de chegada. O mundo guarda também o nome do personagem.
- **Motivo:** RF-363; não jogar fora os saves de quem testou o M6.

## D-071 — Áudio provisório por síntese, em WAV
- **Data:** 2026-11-12
- **Decisão:** `gradlew generateAudio` gera música (8 faixas: vila, noite, casa, taverna, estrada, ruína, combate, menu), ambientes por área × período e 26 efeitos por síntese própria (cordas dedilhadas, sinos, flauta, tambor, ruídos filtrados, reverb), em WAV mono 22 kHz (38 MB). O cliente toca por ID do manifesto, com 5 canais e transições.
- **Motivo:** a Dreamina desta conta só faz voz (TTS), não música; não há ffmpeg nem codificador OGG maduro em Java no projeto. Síntese própria não tem problema de licença e é reproduzível.
- **Alternativa descartada:** música CC0 baixada da internet — licença a conferir item por item; fica como opção para o M8 (troca-se o arquivo mantendo o ID). PRD RF-422 pede OGG: a conversão é só trocar os arquivos.

## D-072 — Portas: clique só entra se o destino era a porta
- **Data:** 2026-11-12
- **Decisão:** seguindo um caminho de clique, a saída só leva para outra área se o destino do clique estava na porta; andando pelo teclado, encostar basta. Portas e passagens aparecem sob o cursor ("Entrar: A taverna"). Saídas podem ter condição (o buraco do campo só existe depois que a terra cede).
- **Motivo:** caminhos que passavam rente a uma porta faziam o jogador entrar sem querer.

## D-073 — A2: desfechos e limites (proposta, a aprovar)
- **Data:** 2026-11-12
- **Decisão:** desfechos `confessa` (leve: Avel conta que trabalhou em Mir e saiu de lá com a criança, e diz o nome Iria; exige opinião ≥ 40, papéis lidos e a pergunta na fase da comitiva ou da carta), `encontrado` (o homem de fora acha Avel; padrão sorteado), `partiu` (Avel vai embora e deixa um bilhete; padrão sorteado; a oficina passa ao aprendiz) e `averiguacao` (trágico: só se o jogador contar ao registrador de onde Avel veio — ato `delatou`). Papéis no baú de Avel (registro com nome raspado, carta assinada por Iria, desenho com as marcas em grupos de nove).
- **Motivo:** PRD 8.5 e o limite de revelação: Avel nunca diz "não sou seu tio" (L3 fica como suspeita); Iria é um nome.

## D-074 — A3: a vala, o buraco e quem o tapa
- **Data:** 2026-11-12
- **Decisão:** a terra cede no campo de Bento na segunda vala aberta (trabalho `vala_campo`) ou por volta do dia 7. Avel vai olhar e diz que não reconhece as marcas ("rápido demais"). Desfechos, nenhum trágico: `tapado` (padrão), `registrado` (se a comitiva estiver no vilarejo) e `mostrado` (leve: o jogador desceu e mostrou o que achou a alguém). O eco da estrutura vira fato para quem desce.
- **Motivo:** PRD 8.5 (A3): surge durante um trabalho, Avel reconhece e evita explicar, não explica nada.

## D-075 — Menus, opções e saves com miniatura
- **Data:** 2026-11-12
- **Decisão:** o jogo abre no menu principal (ilustração da vila); `--area`, `--seed`, `--cmd` ou `--screenshot` pulam o menu (desenvolvimento). Novo jogo: origem (só a I), nome, ritmo. Três slots manuais, o rápido e três automáticos, cada um com miniatura. Opções gravadas em `%APPDATA%\Etrava\opcoes.json`. Esc no jogo abre a pausa (antes fechava o jogo).
- **Motivo:** PRD 10.2 e RF-383. "Tempo jogado" no slot ainda não existe (fica para o M8).

## D-076 — Documentos não se gastam; contêineres com condição
- **Data:** 2026-11-12
- **Decisão:** item de categoria `documento` (ou com `text`) abre uma folha para ler e continua na mochila; o caderno ganhou a aba Documentos. Contêineres podem ter `when` (as cinzas da lareira só têm o canto da carta depois que ela chega) e `restockDays: 0` (nunca repõe).
- **Motivo:** RF-124; documentos precisavam ser relidos e aparecer na hora certa.

## D-077 — Fonte própria: Alegreya, gerada no tamanho exato
- **Data:** 2026-11-12
- **Decisão:** a interface usa a Alegreya (texto) e a Alegreya SC Bold (nome do jogo), SIL Open Font License 1.1, em `assets/fontes/` e no manifesto (`fonte.texto`, `fonte.titulo`). O `gdx-freetype` gera a fonte em 19 px × o tamanho de texto das opções; ao mudar o tamanho, as telas são refeitas com a fonte nova. Texto solto sobre o cenário (relógio, avisos, balões) leva uma sombra de 1 px. Se o arquivo faltar, entra a fonte embutida do libGDX (RF-421).
- **Motivo:** a fonte embutida esticada borrava no texto grande (RNF-040) e tinha cara de protótipo; uma serifada de livro combina com "papel e tinta" (PRD 10.1) e lê bem em português.
- **Alternativa descartada:** fonte em pixel — combina com o cenário, mas cansa em texto corrido e acentua mal em 16 px; fonte única em tamanho máximo, reduzida — fica mole no tamanho normal.

## D-078 — Golpes animados e lobo redesenhado
- **Data:** 2026-11-12
- **Decisão:** as folhas de personagem ganham a animação `attack` (4 quadros: recua, ergue, desce, termina). O inimigo usa os dois primeiros enquanto anuncia o golpe (dá para ler e esquivar) e os dois últimos quando ele sai; o do jogador passa inteiro depois do clique. O lobo tem um bote no lugar, quadro mais largo (44 px de arte; de lado ele vazava para o quadro vizinho), cabeça baixa, orelhas, focinho claro e olhos âmbar. Sem arma desenhada na mão.
- **Motivo:** pendência do M6 (combate sem animação; lobo de frente era uma mancha). A animação é só apresentação: o núcleo não mudou.

## D-079 — Além da borda do mapa: chão que escurece e mato
- **Data:** 2026-11-12
- **Decisão:** nas áreas abertas o cliente desenha 14 tiles além da borda, repetindo o terreno da borda (a estrada segue), escurecendo até sumir, com árvores cada vez mais juntas. Não se anda ali. Interiores e a estrutura enterrada continuam acabando onde acabam.
- **Motivo:** perto da borda (o Posto fica a 8 m dela) a câmera mostrava o vazio preto. É desenho, não mapa: nada muda em `content/areas`.
- **Alternativa descartada:** prender a câmera dentro do mapa — em isométrico a área visível é um losango, e o jogador deixaria de ficar no centro justamente no Posto.

## D-080 — Áudio em OGG Vorbis
- **Data:** 2026-11-12
- **Decisão:** `gradlew generateAudio` continua sintetizando em WAV e, em seguida, `encodeAudio` chama `tools/scripts/wav2ogg.py` (Python + módulo `soundfile`), que grava OGG mono 22 kHz e apaga os WAV: 37 MB → 5 MB. O manifesto aponta para `.ogg`; se o `.ogg` faltar e houver o `.wav` de mesmo nome, o cliente toca o WAV. O repositório guarda os OGG — Python só é preciso para regenerar o som.
- **Motivo:** RF-422 pede OGG; o ZIP do jogo fica em 51 MB. Não há codificador Vorbis consolidado em Java puro.
- **Ressalva:** é a única ferramenta fora do Gradle no projeto (RNF-006). Não entra em compilar, testar nem rodar.

## D-081 — Ritmo do capítulo: prazos redistribuídos; o contemplativo estica o capítulo
- **Data:** 2026-11-12
- **Decisão:** medido em 20 sementes sem o jogador, tudo fechava até o dia 18–22 e os dias 22–30 ficavam vazios, com seis desfechos entre os dias 9 e 12. Prazos novos (só dados): comitiva chega no dia ~8–9 (antes 7), a terra cede no dia ~10–12 (antes 8), a briga da taverna fecha no dia ~18 (13), o ataque ao Posto no dia ~19 (11), A3 no dia ~19 (14), A2 no dia ~21 (16), A1 no dia ~23 (18); as notícias de fora se espalham até o dia ~27. Sal, pomar, ferrovia e festa ficam onde estavam, para o começo não esvaziar. No ritmo Contemplativo o capítulo passa a durar 60 dias: antes acabava no dia 30 com metade dos arcos em aberto (defeito).
- **Motivo:** R4/R5 do PRD (mundo independente que vira tédio; jogador que perde tudo e acha o jogo vazio). Agora o último acontecimento cai entre os dias 24 e 29.
- **A conferir pelo diretor:** se o meio do capítulo (dias 13–17) não ficou parado demais para quem não investiga.

## D-082 — Medição, jogador automático e pasta de usuário isolada
- **Data:** 2026-11-12
- **Decisão:** `--bench=S` mede o desempenho sem V-Sync e grava o resultado no log (AT-17); `--bot=S` põe um jogador automático a fazer de tudo por S segundos (resistência); `--hidden` esconde a janela; `--vel=N` acelera o tempo de jogo. A variável `ETRAVA_USERDIR` (ou `-Detrava.userdir`) manda saves, opções e logs para outra pasta — é como os testes rodam sem tocar nos saves de quem joga. `gradlew at16` automatiza o AT-16. `ChaosTest` faz o equivalente sem janela, no núcleo.
- **Motivo:** PRD 16 (definição de pronto) e R6. O jogador automático não substitui a sessão humana de 3 h: ele acha queda e travamento, não acha jogo chato.

## D-083 — Miúdos do polimento
- **Data:** 2026-11-12
- **Decisão:** (a) tempo de jogo real contado pelo cliente e gravado só no cabeçalho do save (`playedSeconds`), fora do estado — a simulação continua sem relógio de parede (RNF-013); saves antigos mostram zero. (b) Inimigos se afastam quando encostam (0,75 m), sem entrar em parede. (c) Ícones de item de 20 px de arte, por código, na mochila, no comércio e nos contêineres. (d) As teclas de luta só aparecem no HUD com inimigo na área; as barras somem na conversa e no comércio. (e) Documento na mochila diz "[ler]". (f) Os testes do Gradle declaram `content/` como entrada: antes, mudar um dado não fazia os testes rodarem de novo.
- **Motivo:** pendências listadas no fim do M5–M7.

## D-084 — Fase V: revisão visual, com marcos V0–V8
- **Data:** 2026-11-12
- **Decisão:** a pedido do diretor, abre-se a Fase V (revisão visual do jogo inteiro, sem mudar a identidade): V0 correções e ferramentas, V1 pipeline e portão visual, V2 gente, V3 criaturas e combate, V4 natureza e terreno, V5 arquitetura e exteriores, V6 interiores, objetos e itens, V7 interfaces, V8 acabamento. Plano, especificação e um documento por marco em `docs/visual/`. Mesmas paradas do MVP: fim de cada marco e portão visual no V1 antes de produção em massa. A simulação, o conteúdo e os IDs de asset não mudam.
- **Motivo:** pedido do diretor de 12/11 ("isso tudo de uma vez não; planeja os passos e escreva os documentos para cada etapa").
- **Observação:** as pendências humanas do M8 (som, sessão de 3 h, AT-15/17/18, termos da Dreamina) continuam abertas e não bloqueiam a Fase V. O AT-15 passa a ser coberto pelo VT-14.

## D-085 — Blender 5.3 como ferramenta principal de arte, fora do build
- **Data:** 2026-11-12
- **Decisão:** a arte passa a ser produzida no Blender 5.3 em linha de comando (`blender -b --factory-startup -P tools/blender/entrada.py`), com modelos, animações e câmera descritos em scripts Python e fichas YAML versionados em `tools/blender/`. Um pixelizador próprio (Java, em `tools`) transforma os passes do render em pixel art na paleta. Os PNG e JSON gerados ficam no repositório. Compilar e rodar o jogo continua sem Blender (RNF-006, como o Python do D-080). O MCP do Blender serve só para exploração interativa; nunca é fonte de verdade. Revoga a frase "Sem Blender (RNF-006)" da `SPEC.md` §7.3.
- **Motivo:** o gerador por código (D-025) chegou ao limite (bonecos de cilindro, árvores de elipses, telhado de uma face). Pré-renderizar 3D é o caminho previsto desde o D-019 e garante consistência entre direções e quadros; o diretor liberou o Blender explicitamente.
- **Alternativa descartada:** continuar desenhando por código em Java (teto baixo de qualidade); sprites direto da IA (não mantém a mesma pessoa em 8 direções); filtro de pixel sobre render (cara de 3D reduzido — por isso o pixelizador por material e rampa).

## D-086 — Densidade de pixel dobrada (proposta, a confirmar no portão V1)
- **Data:** 2026-11-12
- **Decisão provisória:** um pixel de arte passa a valer 2×2 pixels de mundo (era 4×4): tile de 64×32 px de arte, 28 px por metro de altura, gente adulta com ~48 px, quadro de 64×80. As medidas de mundo não mudam (mapas, colisões, cliques, luzes no mesmo lugar). Zoom inteiro de 1 a 4; a 1280×720 a escala 1 mostra a mesma área que a escala 2 de hoje. Durante a transição, a arte antiga é desenhada ampliada 2×.
- **Motivo:** com 24 px de altura não há rosto, roupa nem arma legíveis; 48 px é o nível dos isométricos de referência e ainda é pixel art (72 px já começaria a parecer render reduzido e multiplicaria memória e quadros).
- **Pendente:** aprovação do diretor no portão V1 (VT-13).

## D-087 — Papel da Dreamina na Fase V
- **Data:** 2026-11-12
- **Decisão:** a Dreamina gera referências (pranchas de personagem, espécies, arquitetura), texturas de material e materiais de interface. Nunca o sprite final. Toda textura passa por um funil (repetível, reduzida, dessaturada) e entra no render só como variação de valor; a cor final sai da rampa da paleta. Teto de créditos por pedido; gasto relatado em cada parada; aviso antes de lote acima de 500 créditos. Estimativa da fase: ~3.500.
- **Motivo:** STYLE §4 ("nada pode ter cara de feito por IA") e o limite já conhecido: a IA não mantém ângulo nem identidade entre imagens.
