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

## D-086 — Densidade de pixel dobrada (aprovada no portão V1)
- **Data:** 2026-11-12
- **Decisão provisória:** um pixel de arte passa a valer 2×2 pixels de mundo (era 4×4): tile de 64×32 px de arte, 28 px por metro de altura, gente adulta com ~48 px, quadro de 64×80. As medidas de mundo não mudam (mapas, colisões, cliques, luzes no mesmo lugar). Zoom inteiro de 1 a 4; a 1280×720 a escala 1 mostra a mesma área que a escala 2 de hoje. Durante a transição, a arte antiga é desenhada ampliada 2×.
- **Motivo:** com 24 px de altura não há rosto, roupa nem arma legíveis; 48 px é o nível dos isométricos de referência e ainda é pixel art (72 px já começaria a parecer render reduzido e multiplicaria memória e quadros).
- **Pendente:** aprovação do diretor no portão V1 (VT-13).
- **V1 (2026-11-12):** implementada. `ART_SCALE = 2`; cada entrada do manifesto diz quantos px de mundo vale o seu pixel (`scale`; sem o campo, 4 — a arte do M8 aparece ampliada 2×). Câmera: escala 1 a 720p e 2 a 1080p ou mais. Gente nova: ~48 px de altura (quadro recortado de 52×62). Continua pendente do portão.
- **Portão V1 (2026-11-12):** aprovada ("com 48 px está bom"). O diretor perguntou por que não mais. Resposta registrada: o mundo tem 128 px de mundo por tile e a arte precisa de um número inteiro de px de mundo por pixel de arte, então os próximos passos possíveis são 4 px (M8, 24 px de gente), 2 px (hoje, ~48 px) e 1 px (~96 px de gente). 96 px custaria 4× a memória e o tempo de render de toda folha, pediria mais quadros por animação para não parecer travado e já é o território do render reduzido (Diablo II), não de pixel art; e cada passo de densidade fecha a vista (a 720p, 96 px mostrariam um quarto da área de hoje no zoom 1), o contrário da vista mais aberta que o diretor pediu na mesma resposta. Fica 2.

## D-087 — Papel da Dreamina na Fase V
- **Data:** 2026-11-12
- **Decisão:** a Dreamina gera referências (pranchas de personagem, espécies, arquitetura), texturas de material e materiais de interface. Nunca o sprite final. Toda textura passa por um funil (repetível, reduzida, dessaturada) e entra no render só como variação de valor; a cor final sai da rampa da paleta. Teto de créditos por pedido; gasto relatado em cada parada; aviso antes de lote acima de 500 créditos. Estimativa da fase: ~3.500.
- **Motivo:** STYLE §4 ("nada pode ter cara de feito por IA") e o limite já conhecido: a IA não mantém ângulo nem identidade entre imagens.

## D-088 — Zoom que travava: a roda vai para a lista sob o cursor
- **Data:** 2026-11-12
- **Causa confirmada:** o `ScrollPane` do libGDX 1.14.2 chama `event.cancel()` no início do seu `scrolled` — consome toda roda que recebe, mesmo invisível e mesmo sem ter o que rolar (conferido no bytecode). O `Stage` entrega a roda ao "foco de rolagem"; a mochila, o caderno e o epílogo davam esse foco à sua lista ao abrir e, ao fechar, só se escondiam (`setVisible(false)`). A lista invisível seguia engolindo a roda, e o `GameInput` (último no `InputMultiplexer`) nunca recebia o zoom — até o painel ser reconstruído por outro motivo, o que explica o "às vezes". Reproduzido em `UiStageTest` (teste do `Stage` comum) e de ponta a ponta: com o código antigo, `gradlew vt01` falha no primeiro minuto.
- **Decisão:** `UiStage` (subclasse de `Stage`) para a interface, o menu e o console: a roda vai para o `ScrollPane` mais interno **sob o cursor** que tenha o que rolar; se não houver, o Stage não consome e a roda segue para a câmera. Ninguém mais chama `setScrollFocus`. A câmera acumula roda fracionária (touchpad) até um passo. O jogador automático ganhou `--paineis` e a tarefa `gradlew vt01` (411 verificações, 0 falhas).
- **Alternativa descartada:** limpar o foco em cada lugar que esconde painel — conserta os casos de hoje e deixa a armadilha armada para o próximo painel.

## D-089 — Modo de depuração: painel técnico só com F3
- **Data:** 2026-11-12
- **Decisão:** o modo de depuração liga com `--debug`, `ETRAVA_DEBUG=1` ou `-Detrava.dev=true` (o `gradlew run` liga; o pacote de release não). Mesmo ligado, o painel técnico (FPS, posição, semente, velocidade, zoom) começa escondido: F3 mostra e esconde. F1 abre o console só no modo de depuração; fora dele, F1 e F3 não fazem nada. Classe `DebugMode`, testada em `DebugModeTest` (VT-02).
- **Motivo:** o diretor joga pelo IntelliJ (`gradlew run`), que liga o modo dev; por isso via FPS e semente o tempo todo.

## D-090 — Personagens visíveis: luz de presença, camisa de linho e o VT-03 revisto
- **Data:** 2026-11-12
- **Causa (medida):** na grade do VT-03 (vila, Posto, oficina, taverna × 8 horas × 4 zooms, 112 medidas), 50 falhavam. O buffer de luz multiplica o personagem como chão: ao amanhecer, ao entardecer, à noite e dentro de casa ele afunda junto com o piso. E de dia o jogador vestia túnica `#7B6A45`, quase o tom da grama e da terra. O buffer de luz acompanha o zoom (não era defeito de câmera).
- **Decisão:** (a) **luz de presença**: um passe de máscara (silhuetas dos personagens, com o que está na frente deles em preto) entra no buffer de luz por `max(luz, ambiente × actor)`, teto 0,88. `actor` por hora em `daynight.yaml`: 1 de dia, 1,2 às 18h30, 1,3 às 6h, 1,6 às 19h30, 1,5 à noite; 1,25 nos interiores; nada na área escura (lá enxergar é a lamparina, RF-008). O reforço tem a cor do céu: à noite é azulado, e a noite continua noite. (b) O jogador passa a vestir linho claro (`#B9A888`) — o item inicial dele já é a "Camisa de linho". (c) **Critério do VT-03 revisto:** a razão de luminâncias médias reprovava o jogador de camisa clara e contorno escuro (claro e escuro se anulam na média) e aprovava a figura toda escura no chão escuro. Novo critério: pelo menos 60 % dos pixels do jogador diferem da média do chão em volta por 0,12 ou mais. Antes: mínimo 0,42 e 50 falhas; depois: mínimo 0,66, 0 falhas.
- **Alternativa descartada:** clarear a noite inteira (perde a atmosfera que funciona); contorno claro em volta do personagem (efeito de interface, não de luz); o critério antigo do PLANO (media o que não se vê).
- **A conferir pelo diretor:** se a camisa clara à noite ficou "acesa" demais (`actor` noturno é um número só em `daynight.yaml`).

## D-091 — Corpo humano: kit próprio (confirmado pelo spike do MPFB2)
- **Data:** 2026-11-12
- **Decisão:** o corpo de gente sai de um kit próprio em `tools/blender/kit_humano/`: esqueleto padrão de vazios com nomes fixos (`raiz`, `quadril`, `tronco`, `cabeca`, `braco_e`… `pe_d`) e peças rígidas arredondadas (tubos e esferas subdivididos), roupa por peça, cada peça com a sua rampa. Proporção estilizada: cabeça ~1/6 da altura, pernas um pouco curtas, membros grossos.
- **Motivo:** a 48 px de altura, com a correção vertical da câmera (28/39), o que lê é silhueta e massa; peças rígidas não têm surpresa de peso e qualquer ciclo serve em qualquer corpo. O resultado passou no VT-03 e lê rosto, roupa e ofício (folhas em `docs/capturas/v1/`).
- **Spike do MPFB2 não feito:** a instalação da extensão (download de extensions.blender.org, numa pasta isolada) foi bloqueada pela permissão do ambiente desta sessão. Se o diretor liberar, o spike roda no começo do V2, com o mesmo pixelizador e a mesma pose, e esta decisão é revista.
- **Alternativa descartada (por ora):** malha única com pesos (skin modifier ou MPFB2): mais orgânica, mas a 48 px a diferença some e o custo de pesos e roupas é alto.
- **Spike do MPFB2 (2026-11-12, liberado pelo diretor):** extensão MPFB instalada em `build/blender-ext` (fora das preferências do usuário; `RenderArt` passa `BLENDER_USER_RESOURCES` só para trabalhos com `spike: true`). Tarefa `tarefas/spike_mpfb.py`, YAML `tools/blender/spike/mpfb_teste.yaml` (só roda com `-Pso=mpfb_teste`, não entra no manifesto). Corpo padrão do MPFB, mesma câmera, luz, pixelizador e rampas do aprendiz, roupa pintada por faixa. Resultado em `docs/capturas/v1/spike_mpfb.png`: a proporção realista deixa a cabeça pequena e o rosto some a 48 px; o rig (mais de 160 ossos, rolagens próprias) deforma ombro e braço já na pose parada, então cada ciclo pediria acerto de pesos; roupa de verdade exige pacotes de assets à parte. O kit próprio lê melhor e anima sem surpresa. **Decisão: kit próprio.** O MPFB fica instalado para servir de referência de proporção, se preciso.

## D-092 — Onde mora a arte nova
- **Data:** 2026-11-12
- **Decisão:** PNG e JSON gerados em `assets/arte/<personagens|natureza|construcoes|chao|decoracao>/`, manifesto gerado em `assets/manifest/arte.yaml` (prioridade sobre `provisorio.yaml`, mesmo ID — RF-423). Campos novos no manifesto: `scale`, `variants`, `meta`, `role`, `frames`, `fps` e `stride` nas animações.
- **Motivo:** `gradlew generateArt` reescreve os arquivos de `assets/personagens`, `assets/props`… — se a arte nova morasse lá, uma regeneração da arte provisória a apagaria. Pastas separadas também deixam claro o que já foi refeito (VT-06 no V8).

## D-093 — Passes por troca de material, sol calibrado e sombra no sprite
- **Data:** 2026-11-12
- **Decisão:** cada quadro é renderizado duas vezes no Cycles (CPU, semente fixa, filtro de 0,01 px — sem antisserrilhado), trocando os nós de todos os materiais: passe "a" só de emissão (índice, profundidade, emissivo) e passe "b" (luz direta com sombra no vermelho, oclusão no verde, detalhe da textura no azul). Sem compositor nem AOV. O sol foi calibrado (energia 1/0,3654) para o passe de luz valer exatamente N·L. Direção do sol: horizontal (-0,55, -0,85), elevação 58° — face sudoeste clara, sudeste na sombra, sombra projetada à direita, como no M8. Objetos parados (árvores, casas) fotografam junto um chão que só recebe sombra; a sombra vira preto translúcido no próprio sprite.
- **Motivo:** a API de compositor e de saída de arquivo mudou no Blender 5; a troca de material usa só o básico e é determinística. Índice misturado numa aresta (amostra caindo exatamente sobre ela) conta como vazio no voto.
- **Tempo:** personagem de 192 quadros ≈ 50–90 s; árvore com 2 variantes × 8 quadros ≈ 4 min; casa ≈ 15 s; tudo em paralelo (6 processos). A fatia inteira do zero: ~7 min.

## D-094 — Chão: textura de mundo e máscara de sangria
- **Data:** 2026-11-12
- **Decisão:** cada terreno novo é uma textura de 512×256 px de arte, periódica, renderizada de um plano com a textura do material (escala que divide o período de 8 m) e desenhada **alinhada ao mundo** por um shader; cada tile a mostra através de uma máscara (losango + sangria irregular em blocos de 2 px, 8 variantes). O terreno de prioridade maior é desenhado depois e cobre o vizinho; um anel de 1 px mais escuro marca a divisa. Duas passadas por terreno (anel, depois miolo) para o anel não aparecer entre tiles do mesmo terreno.
- **Motivo:** dá a borda irregular sem os 16 casos por par de terrenos da SPEC §7.2 (que multiplicam arte e código) e acaba com a costura entre tiles.
- **Alternativa descartada:** 16 máscaras por par de terrenos (mais arte, mesmo resultado visual); tiles com variação por tile (costura visível).

## D-095 — Andar pela distância
- **Data:** 2026-11-12
- **Decisão:** nas folhas novas, o quadro de `andar` e `correr` sai da distância percorrida dividida pela passada (`stride`) e não do relógio; acima de 2,4 m/s o personagem corre. O jogador anda a 3,5 m/s (`Simulation.WALK_SPEED`), então usa `correr`; os NPCs passeiam a 1,5 m/s e usam `andar`. `parado` segue o relógio.
- **Motivo:** o pé de apoio fica parado no chão em qualquer velocidade (VT-15), inclusive com carga demais (60 %).

## D-096 — Âncora no canto do pixel
- **Data:** 2026-11-12
- **Decisão:** a origem do objeto cai no canto superior esquerdo do pixel da âncora, não no centro (a SPEC dizia centro).
- **Motivo:** é o ponto que o cliente já usa (`left = sx − anchorX × px`); com o centro, tudo sairia meio pixel deslocado. Conferido pelo teste do cubo (`gradlew blenderTeste`: 64×60 px exatos).

## D-097 — Zoom fracionário fica para depois do portão
- **Data:** 2026-11-12
- **Decisão:** a tarefa 8 do V1 (buffer na escala de arte + sharp-bilinear, passos 1,5 e 2,5) não foi feita. O zoom continua inteiro (1 a 4).
- **Motivo:** é opcional e depende da resposta do diretor à pergunta 3 do portão ("zoom inteiro ou com passos intermediários?"). Se ele quiser, entra no começo do V2.
- **Portão V1:** o diretor aprovou o zoom como está e pediu para afastar mais. Zoom fracionário descartado; entra a vista de longe (D-098).

## D-098 — Árvores refeitas como pixel art e vista de longe (portão V1)
- **Data:** 2026-11-12
- **Pedido do diretor:** as árvores do V1 "pareciam forçadas, sem cara de pixel arte, como se renderizassem toda folha"; o zoom afasta pouco; tirar as árvores do meio da praça (escondiam o poço).
- **Decisão (árvores):** a copa deixa de ser ~40 tufos texturizados com ruído e passa a ser 9 massas grandes, cada uma com a borda recortada em cachos (esferas de 4 a 6 px na tela), **sem textura e sem ruído** na folha — a luz em faixas dá a cada cacho o seu realce, como o pixel artista desenha. Tronco num tubo só que afina (sem as raízes em "pata" e sem subdivisão, que arredondava as pontas), com luz sem a sombra da copa (`sem_sombra` no material: o lado do sol fica claro mesmo debaixo da copa). Galhos nunca furam a copa. Vento: a copa balança junta, cada massa com atraso pequeno, amplitude de 1–2 px (antes cada tufo tinha fase própria e cintilava). Quadro menor (264×214 em vez de 400×320).
- **Decisão (praça):** o gerador do mapa ganhou clareiras (raio 6,5 tiles em volta do poço): as árvores continuam sorteadas, só não são plantadas ali, então nenhuma outra árvore muda de lugar. Saíram exatamente as duas do meio da praça.
- **Decisão (zoom):** passo 0 = vista de longe, meio pixel de tela por pixel de arte (o dobro da área do zoom 1). As texturas reduzem com filtro linear (média 2×2) e ampliam sem filtro, então os outros passos não mudam e a vista de longe não pisca.
- **Alternativa descartada:** zoom fracionário (1,5, 2,5) — o diretor achou o zoom bom; o que faltava era afastar.

## D-099 — Atlas dos sprites do mundo
- **Data:** 2026-11-12
- **Causa medida:** cada sprite (árvore, decoração, objeto) era uma textura própria; na ordem de profundidade elas se intercalam e o lote quebra a cada troca: **2.190 chamadas de desenho por quadro** no zoom 1 e 5.566 na vista de longe (`--bench` agora mostra a contagem). Era o "peso" que o diretor sentiu: 223 FPS no zoom 1 (eram 503 no M8) e 90 na vista de longe, na RX 6650 XT; numa GTX 1050 ficaria perto do limite.
- **Decisão:** no início, `Assets.preloadSprites()` empacota cada quadro de cada sprite do manifesto (`PixmapPacker`, páginas de 2048², 2 px de margem) — hoje 65 sprites numa página só. Folhas de personagem e chão continuam como estão.
- **Resultado:** 51 chamadas por quadro e **1.106 FPS** no zoom 1; 178 chamadas e 735 FPS na vista de longe. Capturas iguais às de antes (VT-03 112/112).



## D-100 — Árvores v2: estrutura no Blender, acabamento em pixel e Pixelorama no pipeline
- **Data:** 2026-11-13
- **Pedido do diretor:** as árvores da 2ª rodada melhoraram, mas "ainda parecem várias bolinhas sobrepostas"; quer árvores com cara de pixel art feita à mão, com espécies, idades e caráter, usando o Blender para a forma e o **Pixelorama** para o acabamento. Liberou o Pixelorama para qualquer arte do jogo.
- **Decisão (forma, Blender — `natureza/arvore.py`):**
  - galhos por **colonização do espaço**: pontos espalhados no volume da copa, com a silhueta da espécie e lobos irregulares, puxam os galhos, e a ramificação sai sozinha;
  - raio dos galhos pelo modelo do cano, que também afina com o comprimento;
  - tronco sinuoso com colo e raízes de apoio, tudo em curvas com bisel;
  - tufos de folha só nas pontas: massas deformadas por ruído, com espaçamento que deixa vãos e galho à mostra;
  - a normal da folha mistura a do tufo com a do elipsoide da copa, então a copa acende e escurece como um volume só e os tufos ficam como detalhe.
- **Decisão (acabamento):**
  - o pixelizador ganhou o **pintor de folhagem** (`Folhagem.java`), que carimba cachos de folha desenhados em pixel, de cima para baixo;
  - cada cacho tem um tom só (o da luz onde cai), com luz no alto à esquerda e sombra embaixo; a borda da silhueta sai recortada em folhas, e há um vão escuro sob o tufo da frente;
  - a grade de carimbos fica presa ao tufo (âncora por quadro), então o desenho anda com o vento em vez de cintilar.
- **Decisão (Pixelorama):**
  - os pincéis são um projeto do Pixelorama (`tools/art/pinceis/folhas.pxo`), uma camada por espécie, em cinza por nível;
  - cada folha de árvore sai como projeto em camadas (`tools/art/acabamento/natureza/*.pxo`: sombra, tronco, folhagem, contorno, **retoque**), e a folha final é exportada pelo próprio Pixelorama pela linha de comando;
  - a camada `retoque` é a única que o pipeline preserva: o retoque à mão fica versionado no `.pxo`, que é a fonte, e continua reproduzível (VT-16);
  - sem o Pixelorama instalado, as camadas são compostas em Java (mesmo resultado, conferido pixel a pixel) e o pipeline avisa.
- **Variedade:** 14 variantes, sorteadas pelo hash da posição; mapas, pegadas e colisões não mudaram.

  | Asset | Variantes |
  |---|---|
  | carvalho | jovem, adulto, antigo, torto (com tufos pardos misturados) |
  | bétula | jovem, adulta, dupla, alta (tronco branco com marcas pretas) |
  | macieira | 2, com maçãs na copa e no chão |
  | pereira | 2, copa em ogiva e peras |
  | árvore seca | carvalho seco, partido, bétula seca (sem vento) |
  | toco | 1 |

- **Paleta:** rampas de folha com deslocamento de matiz (sombra fria, luz quente). Só o carvalho trouxe cores novas; a paleta foi de 92 para 99 cores.
- **Desempenho:** o atlas passa a usar página de 4096² quando a placa aceita (todo sprite numa textura só); com 2048² eram 3 páginas e 633 chamadas por quadro. Resultado: 1.091 FPS no zoom 1 (147 chamadas) e 758 FPS na vista de longe (RX 6650 XT, 1280×720).
- **Limite honesto:** a linha de comando do Pixelorama não roda as ferramentas de edição. A limpeza automática (cachos, pixel solto, contorno, paleta) roda no Java; o Pixelorama é onde ficam os pincéis e as camadas e onde se faz o retoque à mão, e é ele que exporta a folha final.
- **Alternativas descartadas:**
  - folha com ruído ou textura: reprovada no portão;
  - mais esferas: dava o efeito "bolinhas";
  - cartões de folha 3D: viram ruído a 48 px;
  - editar o PNG final à mão: se perderia na próxima geração.

## D-101 — Gente melhorada: corpo, animações e acabamento no Pixelorama
- **Data:** 2026-11-13
- **Pedido do diretor:** melhorar o personagem e as animações, usando o Blender e o Pixelorama.
- **Problemas medidos nas folhas do V1:**
  - o rosto tinha 3 px: cabeça de 8 px, e o cabelo, uma bola que cobria até os olhos;
  - o tronco era um caixote;
  - na corrida, os braços ficavam esticados para a frente;
  - no andar, o braço da frente parecia apontar, porque na isométrica ele "deita" nas direções de costas;
  - a passada era larga demais;
  - bota e calça no mesmo marrom.
- **Decisão (Blender, `kit_humano/corpo.py`):**
  - tronco por seções (cintura, peito, ombros) com deltoide;
  - cabeça com queixo e orelhas, maior que a real (`cabeca: 1.25` no aprendiz, `1.2` no Avel);
  - cabelo em calota com linha do cabelo, mais uma franja;
  - pernas mais finas, mãos e pés menores;
  - as botas do aprendiz passaram a couro.
- **Decisão (animações, `anim/ciclos.py`):**
  - truque do pixel artista: a cabeça sobe o queixo para a câmera (`rosto`, 16°; 10° no Avel, que é curvado);
  - **andar:** contrarrotação quadril × ombros, peso de um pé para o outro, cabeça que olha para onde vai, balanço curto e mais para trás, cotovelo que dobra;
  - **correr:** braço a 90° colado ao corpo, mais para trás que para a frente, contrarrotação forte;
  - **parado:** respiração, troca de peso, cabeça que olha em volta, pés um pouco abertos;
  - passada menor (aprendiz 1,15 m, Avel 1,05 m). O jogo avança o quadro por essa mesma passada, então o pé continua plantado.
- **Decisão (Pixelorama):**
  - o acabamento em camadas vale para qualquer folha com `pixelorama: true` no YAML, inclusive com 8 direções;
  - o personagem vira um `.pxo` de 192 quadros (camadas `corpo`, `contorno`, `retoque`), com uma tag por animação e direção;
  - a folha final é exportada pelo Pixelorama e confere pixel a pixel com a composição de referência.
- **Verificação:** 156 testes, validador, vt03 112/112 (o jogador continua o mais claro sobre o chão) e capturas 13/13.
- **Alternativas descartadas:**
  - cabeça do tamanho real: o rosto não lê a 48 px;
  - cabelo como bola deslocada: vira capacete;
  - redesenhar os quadros à mão no Pixelorama: seriam 192 quadros por personagem, que se perderiam a cada nova geração; o caminho é a camada `retoque`.

## D-102 — O rig do diretor vira o kit humano: pele contínua, peito e a corrida dele
- **Data:** 2026-11-13
- **Pedido do diretor:** ele fez a própria versão do aprendiz no Blender (rig orgânico, corrida "V2 com joelhos um pouco mais baixos", pixelizada pelo pipeline) e pediu para pôr no jogo, aplicar ao Avel e aos outros sprites e animações, e apagar a pasta depois.
- **O que o rig dele tinha, medido no `.blend`:**
  - camisa, calça, botas e alça contínuas, deformadas por um esqueleto (as juntas vazias mandam na pose);
  - uma junta `peito` entre o tronco e os ombros;
  - tronco inclinado **para a frente** (+4,5° a +7,5°). No V1, por erro de sinal, eu inclinava para trás;
  - quadril baixo na corrida (−9 a −12 cm);
  - cotovelo dobrado de 34° a 47°;
  - braço que vai mais para trás do que para a frente;
  - nenhum pixel retocado: o ganho veio todo do modelo e da animação.
- **Decisão (forma, `kit_humano/pele.py`):**
  - as peças rígidas de camisa (com barra), calça, botas, alça e avental viram, em repouso, uma malha por grupo;
  - antes de unir, entram esferas de ponte no quadril, no joelho e no tornozelo (sem elas a perna esticada virava "contas");
  - a malha é refeita em voxels (1,1 cm), suavizada, e ganha pesos por distância aos ossos (os dois mais perto), sem nunca cruzar de uma perna para a outra;
  - o avental só segue quadril e coluna, para não se rasgar no passo;
  - os ossos não têm hierarquia: a cada quadro, cada osso copia o movimento da sua junta (`pele.atualizar`);
  - braços, mãos e cabeça continuam rígidos, como no rig dele.
- **Decisão (animação, `anim/ciclos.py`):**
  - junta `peito` no esqueleto;
  - **correr:** são as 8 poses dele, extraídas do `.blend` para `anim/correr_diretor.py` e interpoladas se o número de quadros mudar. Passada 1,9 m e 12,5 qps no aprendiz (no Avel, 1,8 m e 11,5 qps, um pouco mais curvado);
  - **andar e parado:** refeitos no mesmo estilo (tronco à frente com o sinal certo, contrarrotação no peito, quadril baixo, braço para trás com cotovelo moderado);
  - a convenção de sinal ficou anotada no código.
- **Ajuste por VT-03:** a calça contínua ocupa mais do sprite e tem o marrom do chão de madeira da oficina. Às 21h, no zoom 4, o destaque caía para 0,60, no limite. A camisa do aprendiz virou túnica até o meio da coxa (`barra: 0.20`, coerente com a época), e o destaque mínimo foi para 0,62 (vt03 112/112).
- **Problema conhecido (VT-16):** com a malha deformada, o passe de luz varia de 1 em 65.535 entre renders (e às vezes um degrau na oclusão), provavelmente pelas normais calculadas em paralelo. Raramente isso troca um pixel de tom na folha: em 3 tentativas, 2 deram bytes iguais. Renderizar com 1 núcleo não resolveu. Fica para investigar no V2.
- **Fonte:** a pasta `docs/capturas/minha versao de andar/` foi apagada a pedido do diretor; as poses estão em `anim/correr_diretor.py`.

## D-103 — Vento a 60 qps (96 quadros) e atlas indexado pela paleta
- **Data:** 2026-11-13
- **Pedido do diretor:** as árvores estão boas; falta fluidez. Ele pediu 96 quadros a 60 qps e, se fizer sentido, o mesmo para o resto, "para não ficar desproporcional". Também liberou a paleta para crescer à vontade.
- **Decisão (vento):**
  - árvores vivas (carvalho, bétula, macieira, pereira) e tufos de grama passam a ter 96 quadros a 60 qps (laço de 1,6 s), em vez de 8 a 5 qps (e 4 a 3 nos tufos);
  - o vento ganhou um segundo harmônico e um leve balanço de lado, para não parecer seno perfeito;
  - como antes, o desenho da folha anda preso a cada tufo, e cada tufo cruza o pixel no seu próprio instante — é isso que dá a fluidez.
- **Custo medido:** em RGBA, 96 quadros das 12 árvores vivas dariam ~77 milhões de pixels de textura (~307 MB), mais da metade do teto de 512 MB do VT-10.
- **Decisão (memória): atlas indexado.**
  - a arte nova é de paleta única, então cada pixel guarda só o índice da cor: 1 byte (textura de alfa) em vez de 4;
  - a cor sai de uma textura de paleta 256×1, montada na carga com as cores que aparecem, no shader `paleta.frag`;
  - na vista de longe, a média 2×2 é feita no shader, já em cor (filtro linear misturaria índices), e fica igual à de antes;
  - a máscara de silhueta (luz de presença, VT-03) também lê pela paleta;
  - entram só os sprites do mundo na escala da arte nova (não os ícones, que a interface desenha com o shader padrão);
  - um sprite cujas cores não caibam (mais de 255 no total) fica no atlas RGBA, com aviso no log.
- **Paleta:** liberada pelo diretor. O teto passa a ser o do atlas indexado, 255 cores (o teste agora confere isso).
- **Alternativas descartadas:**
  - menos quadros: o diretor pediu 96;
  - interpolar quadros no cliente: pixel art não se mistura;
  - recorte por variante: só reduziria a ~227 MB;
  - montar a copa em tempo real com um sprite por tufo: as partes escondidas de cada tufo não existem no render.
- **Resultado medido (2026-11-13):**
  - atlas: 65 sprites em 5 páginas indexadas + 1 RGBA de 4096² (~144 MB de textura, antes eram ~307 MB só das árvores em RGBA); a paleta usada em jogo tem 57 cores;
  - `--bench` na vila, 1280×720: 1.253 FPS de média, 103 chamadas por quadro (eram 51: cada troca entre página indexada e RGBA esvazia o lote);
  - 156 testes, validador, vt03 112/112 e capturas 13/13; render do vento novo: 44 min (Blender, sem cache).
- **Nota:** o GIF de revisão não toca exatamente a 60 qps (o formato só guarda atrasos em centésimos de segundo); no jogo é 60.

## D-104 — Vento das árvores na metade da velocidade, ainda a 60 qps; V1 aprovado
- **Data:** 2026-11-13
- **Pedido do diretor:** o vento a 60 qps ficou "perfeito", mas as folhas se mexem demais; reduzir a velocidade pela metade **sem baixar os 60 qps**.
- **Decisão:**
  - árvores vivas passam a ter 192 quadros a 60 qps (laço de 3,2 s, antes 1,6 s). A amplitude é a mesma, então o movimento fica com a metade da velocidade;
  - os tufos de grama ficam como estão (96 quadros a 60 qps): o pedido foi sobre as árvores.
- **Custo:** o dobro de quadros das árvores, no atlas indexado (1 byte por pixel). Fica medido na entrega.
- **Alternativas descartadas:**
  - 96 quadros a 30 qps: mesma velocidade, mas o diretor quer os 60 qps;
  - reduzir a amplitude pela metade: deixaria o balanço menor, e não mais lento.
- **Portão do V1:** aprovado pelo diretor ("tá perfeito, pode continuar"). O V2 começa.

## D-105 — Chave do cache de render por objeto
- **Data:** 2026-11-14
- **Problema:** a chave do cache (`RenderArt.hash`) incluía todos os `.py` de `tools/blender`. Qualquer mudança no kit humano refazia também as árvores (~1 h 30 de Blender), e o V2 mexe no kit o tempo todo.
- **Decisão:**
  - a chave passa a ser: o YAML do objeto, `entrada.py`, a tarefa do tipo (`tarefas/<tipo>.py`) e os módulos locais que ela importa, seguidos transitivamente pelos `import`/`from … import`, mais as texturas e o caminho do Blender;
  - na primeira rodada, a marca gravada no esquema antigo é aceita e regravada no novo, sem render;
  - teste: a árvore não depende de `kit_humano/` nem de `anim/`, e o personagem depende dos dois.
- **Alternativas descartadas:**
  - lista manual de dependências por tipo: esquece um módulo e o cache fica velho sem aviso;
  - hash só do YAML: mudança de script não refaria nada.
- **Fichas em rascunho:** `rascunho: true` no YAML faz o `renderArt` pular o objeto (só sai com `-Pso=id`). As 24 fichas do V2 entram assim até o kit ter as peças delas.

## D-106 — Blender na GPU, com a CPU como apoio, e no máximo 3 de cada vez
- **Data:** 2026-11-14
- **Pedido do diretor:** com 6 Blender a máquina ficou sem memória e o render foi interrompido. Ele pediu no máximo 3 de cada vez e, depois, usar a GPU e a CPU juntas, para não pesar num só.
- **Medido** (um carvalho, 12 quadros, RX 6650 XT):

  | Dispositivo | Tempo |
  |---|---|
  | CPU | 109 a 112 s |
  | GPU (HIP) | 64 a 66 s |
  | GPU + CPU no mesmo Blender (`HIP+CPU`) | 112 s |

  No mesmo Blender, a CPU segura a GPU, porque os quadros são pequenos.
- **Decisão:**
  - `renderArt` aceita `-Pprocessos=N` e `-Pdispositivo=…`, com padrões no `local.properties` (fora do git), que vale também pelo IntelliJ;
  - o dispositivo pode ser uma lista de vagas: `HIP,HIP,CPU` = três Blender, dois na GPU e um na CPU;
  - o dispositivo vai na linha de comando (`--cycles-device`, depois do `--`) e não entra na chave do cache.
- **Determinismo (VT-16):**
  - GPU contra GPU: diferença de no máximo 1/65535, o mesmo ruído que a CPU já tinha (D-102);
  - GPU contra CPU: o ruído da oclusão muda;
  - por isso cada objeto vai sempre para o mesmo dispositivo, escolhido pelo id, e refazer do zero na mesma máquina dá o mesmo resultado.
- **Alternativas descartadas:**
  - `HIP+CPU` no mesmo processo: sem ganho;
  - fila livre (cada objeto na primeira vaga livre): o dispositivo variaria de uma rodada para outra.
- **Escolha para esta máquina:** o diretor deixou a escolha comigo ("se GPU mais CPU ficou mais lento, pode usar apenas GPU"). Ficam os 3 Blender só na GPU (`blender.dispositivo=HIP`): é o mais rápido medido e deixa a CPU livre. A lista de vagas (`HIP,HIP,CPU`) continua disponível.
- **Resultado (D-104, 2026-11-14):**
  - árvores com 192 quadros: 10 páginas indexadas + 1 RGBA, ~224 MB de textura (teto do VT-10: 512 MB);
  - vt03 112/112;
  - o `--bench` caiu para 438 FPS e 344 chamadas por quadro, medido com o Blender usando a GPU ao mesmo tempo; as árvores se espalham por mais páginas e cada troca esvazia o lote. Medir de novo com a GPU livre; se preciso, agrupar os quadros de cada espécie na mesma página;
  - a pixelização estourou os 2 GB do Java ao ler o `.pxo`: o heap de `renderArt`/`pixelize` subiu para 4 GB e o leitor solta os bytes brutos de cada quadro ao decodificar.

## D-107 — Sem teto de memória de textura nem de disco: o que importa é rodar leve
- **Data:** 2026-11-14
- **Pedido do diretor:** "não precisa ter um teto de textura, pode literalmente não ter limite": melhor um jogo pesado em disco e memória, bonito e leve para rodar, do que leve no disco, com a arte limitada e pesado para rodar.
- **Decisão:**
  - o VT-10 perde os tetos de memória de textura (512 MB) e de ZIP (150 MB). Os dois continuam medidos e relatados;
  - o que fica com teto é o que se sente jogando: ≥ 60 FPS no hardware de referência e o tempo de abertura;
  - quando houver troca, vence a fluidez. Primeira aplicação: a página do atlas passa de 4096² para 8192² quando a placa suporta. Uma espécie de árvore inteira cabe numa página e o desenho troca menos de textura, com menos chamadas por quadro;
  - o atlas indexado (D-103) continua: não custa em tempo de execução (uma leitura de textura a mais no shader) e deixa caber mais arte por página, o que também corta chamadas.
- **Alternativas descartadas:**
  - voltar ao RGBA: 4× mais memória por página, sem ganho de velocidade, e mais páginas, logo mais chamadas;
  - manter o teto: limitaria o vento a 60 qps e os quadros dos personagens sem ganho para quem joga.

## D-108 — VT-15: como medir o pé que desliza
- **Data:** 2026-11-14
- **Decisão:**
  - o kit humano marca o meio da sola de cada pé (`pe_e`, `pe_d`), que sai no JSON da folha como os outros pontos;
  - o validador compara, entre dois quadros seguidos de `parado` e `andar`, o deslocamento de cada pé com o do chão. No parado, o chão não anda. No andar, o cliente avança o quadro pela distância (D-095): entre dois quadros, o chão corre `stride/n` m para trás, projetado como no `IsoProjection`;
  - o pé de apoio é o que fica mais perto desse deslocamento, e a diferença não pode passar de 1 px de arte;
  - folha sem os pontos dos pés fica de fora: vale para a arte antiga e para criaturas, até o V3.
- **O que a medida achou e foi corrigido:**
  - o andar plantava o pé só no plano da passada, a partir do centro do quadril. Quando o quadril desliza de lado (±1,4 cm), gira (6°) e rola (2°), o pé de apoio ia junto, deslizando até 2 px, mais de frente para a câmera. Agora `_planta` (`anim/ciclos.py`) leva o alvo para o espaço do quadril e a coxa abre para o lado e anula o giro. O parado também usa;
  - o JSON gravava a passada da ficha, mas o ciclo a escala pelo corpo (`passada × k`). Com o Pipo (k = 0,72), o cliente o levava 40 % mais longe que o pé empurrava o chão. Agora o JSON grava `passada × k`;
  - no toque do calcanhar, a perna da frente vai esticada e o quadril está no auge do balanço lateral. O alvo do tornozelo passava do comprimento da perna, o IK encurtava, e o pé pousava ~4 cm fora do lugar: 1,1 a 1,4 px nas direções 3 e 7, em quem tem passada longa. Agora `_alcanca` baixa o quadril o mínimo para o alvo caber, com folga de 1,5 %;
  - resultado:
    - Gil: o andar caiu de 1,9 para 0,36 px e o parado, de 1,0 para 0,10 px;
    - agrimensora: o andar caiu de 1,9 para 0,13 px.
- **Alternativa descartada:** achar o pé pelos pixels mais baixos do quadro. No isométrico, o pé mais baixo na tela nem sempre é o que pisa, e os dois pés se confundem de frente e de costas.

## D-109 — Lote final do V2: peças que faltavam no kit
- **Data:** 2026-11-14
- **Decisão:**
  - **gibão com `remendos`:** três retalhos chatos de outra cor (um no peito, dois nas costas), para o saqueador ler "roupa de várias origens";
  - **cartola do registrador:** copa sem subdivisão e mais alta. Com a subdivisão, o cilindro arredondava e virava um chapéu-coco, e a silhueta da cidade se perdia;
  - **hastes (lança, cajado, bastão, pá, enxada) e cabos (martelo, foice, machadinha) sem subdivisão:** cilindro longo e tampado, sem anéis no meio, vira charuto no subsurf. A lança da Vessa perdia a metade de cima, a ponta flutuava, e a trouxa do viajante ficava solta no ar;
  - **criança (Pipo):** sem esqueleto próprio. É o corpo comum com 1,22 m e a cabeça 1,45×, que já lê como criança a 48 px.
- **Alternativa descartada:** um esqueleto infantil com outras proporções de perna. Daria mais trabalho, e a diferença não aparece no tamanho do jogo.

## D-110 — Pixelização em paralelo
- **Data:** 2026-11-14
- **Pedido do diretor:** a máquina ficava parada depois do render ("pode colocar no máximo").
- **Decisão:**
  - o pixelizador roda vários objetos ao mesmo tempo, `pixel.threads` de cada vez (`local.properties`; padrão 3), com o heap do `renderArt` e do `pixelize` em 8 GB;
  - cada objeto monta as próprias entradas do manifesto, e elas entram na ordem da lista. A saída não depende de quem termina primeiro (VT-16);
  - cada Pixelorama escreve no próprio arquivo (`build/acabamento/<nome>.png`).
- **Resultado:** os 36 objetos, árvores incluídas, em 14 min.
- **Por que não 12 de cada vez:** uma árvore de 192 quadros passa de 2 GB de heap, e a máquina tem 16 GB. Com 12 de cada vez faltaria memória, como já aconteceu com 6 Blender (D-106).

## D-111 — Animação de trabalho, assento e cama só no lugar certo
- **Data:** 2026-11-15
- **Pedido do diretor:** havia NPC fazendo a animação de trabalho no meio da praça e dentro da taverna. Sugestão dele: só ativar quando o NPC estiver no lugar exato do trabalho; fora disso, andar ou parado.
- **Causa:** a animação saía só da atividade da agenda (`trabalhar`, `servir`, `varrer`...), sem olhar onde o NPC estava. Sentado e dormir procuravam banco e cama, mas, sem achar, sentavam no ar.
- **Decisão:**
  - `assets/config/animacoes.yaml` ganha `lugares`: cada animação que depende de um lugar diz de que props (até `alcance` m) ou de que terreno precisa:
    - martelo: bigorna ou forja;
    - bancada: bancada, balcão, banca ou mesa;
    - trabalho no chão: plantio, feno, sacos, mó, ervas, árvores de fruto...;
    - sentado: banco;
    - dormir: cama;
  - sem o lugar, o NPC fica em `parado`;
  - no lugar, ele se vira para o objeto;
  - parado e conversar valem em qualquer lugar;
  - `varrer` deixou de usar a animação de cavar e fica parado até ter animação própria.
- **Alternativa descartada:** mudar as agendas na simulação para o lugar exato. Mexeria no `core-sim` por causa de arte, o que a Fase V proíbe, e não resolveria quem chega ao lugar por outro caminho.

## D-112 — Render com GPU e CPU juntas: 5 Blender
- **Data:** 2026-11-15
- **Pedido do diretor:** "pode usar 100% do meu PC".
- **Medido** (personagens, 5 Blender: 3 na GPU e 2 na CPU):

  | Dispositivo | Por personagem | Por personagem, na prática |
  |---|---|---|
  | GPU, dividida entre 3 processos | 372 s | ~124 s |
  | CPU | 182 s | ~91 s |

  Nos personagens (quadros pequenos), a CPU rende mais que a GPU, ao contrário da árvore medida no D-106.
- **Decisão:** o padrão desta máquina passa a ser `blender.dispositivo=HIP,HIP,HIP,CPU,CPU` e `blender.processos=5`, no `local.properties`, fora do git.
- **Resultado:** os 19 personagens refeitos em 26 min.
- **Memória:** 5 Blender de personagem cabem nos 16 GB. Se faltar memória nas árvores, baixar para 4.

## D-113 — Ferramenta só no trabalho; pernas com anatomia
- **Data:** 2026-11-15
- **Pedido do diretor:**
  - os trabalhadores andavam com a ferramenta na mão (o ferreiro bebia na taverna de martelo na mão). Exceção: o cajado de quem precisa dele para andar;
  - reformular a skin do personagem principal, cujas pernas ainda pareciam cilindros da arte provisória.
- **Decisão:**
  - **ferramentas:**
    - o kit marca cada objeto de ferramenta com o tipo, e a tarefa do personagem o esconde fora das animações `trabalhar_*`;
    - ficam sempre na mão (`SEMPRE_NA_MAO`) a bengala do Anselmo, o cajado com trouxa do viajante, a lança da Vessa e a machadinha do saqueador. A lança é o papel da Vessa (guarda, a única armada) e a machadinha é a arma dele para o V3;
    - pasta, livro, carta, cesto, bandeja e caneca seguem a regra das ferramentas: só aparecem trabalhando;
  - **pernas (kit inteiro, todos os humanos):**
    - por seções, como o tronco: nádega, coxa que afina, joelho que marca na frente, panturrilha atrás, tornozelo fino, a calça folgando em cima da bota e a dobra no cano da bota;
    - no parado, os pés ficam um pouco mais abertos que o quadril, e de frente abre-se um V entre as pernas. Antes, as duas pernas encostavam e viravam uma coluna só;
  - **o jogador:** calça de lã escura (`tecido_cinza`), como no retrato. A calça e a bota marrons usavam os mesmos tons escuros e se fundiam.
- **Alternativa descartada:** mudar só a perna do jogador. Todos tinham as mesmas pernas de tubo, e a passada de render é a mesma.

## D-114 — Conferência dos retratos (V2, tarefa 8): nenhum refeito
- **Data:** 2026-11-15
- **Método:** os 24 retratos numa folha, lado a lado com a prancha "quem é quem".
- **Resultado:** todos batem no que identifica a pessoa de longe: cabelo, chapéu ou capuz, cor principal da roupa e o traço da ficha. Duas diferenças pequenas, mantidas:
  - **registrador:** sem cartola no retrato e com cartola no sprite. O retrato é de dentro, e na rua a cartola é a silhueta dele;
  - **Bento:** camisa marrom com lenço verde no retrato e camisa verde no sprite. Não atrapalha reconhecer.
- **Decisão:** nenhum retrato refeito; 0 créditos da Dreamina.

## D-115 — Transparência por pontilhado e nada de pixel solto em quem anda
- **Data:** 2026-11-15
- **Decisão:**
  - o material pode ter `opacidade` < 1. O pixelizador apaga parte dos pixels numa matriz de Bayer 4×4, fixa no quadro: pixel art de verdade, sem alfa parcial (VT-04);
  - no vulto, isso vale só para a fumaça. Os trapos pontilhados liam como rede e ficaram sólidos;
  - a opacidade entra pelo registro de materiais da própria tarefa (`criaturas/vulto.py`), sem mexer em `nucleo/materiais.py`. Mexer lá invalidaria o cache de render de todo objeto, árvores inclusive;
  - nas folhas de quem anda (gente, lobo, vulto), a montagem apaga o pixel sem nenhum vizinho: sobra do pontilhado e ponta de alça que a mão encobre (VT-15).
- **Alternativa descartada:** alfa parcial no PNG. Sai da paleta e borra.

## D-116 — Despacho genérico de tarefas e `renderArt -Prebase`
- **Data:** 2026-11-15
- **Problema:** o lobo e o vulto são tipos novos (`tipo: lobo`, `tipo: vulto`). O `entrada.py` tinha uma lista fixa de tipos e entra na chave do cache de todo objeto: acrescentar um tipo refaria o render de tudo.
- **Decisão:**
  - o `entrada.py` passa a carregar `tarefas/<tipo>.py` pelo nome. É a última vez que ele muda por causa de um tipo novo;
  - `renderArt -Prebase` regrava a chave dos renders que já existem, sem renderizar e sem pixelizar. Serve só para mudanças que não alteram imagem nenhuma, como esta. Usado uma vez: 43 objetos;
  - a montagem trata `lobo` e `vulto` como folha de personagem (`personagens/`, `spritesheet`), e não como sprite parado;
  - aviso novo na montagem: o desenho que encosta na borda do quadro de render pode estar cortado. Com ele, o quadro do lobo e o do vulto foram aumentados.

## D-117 — Combate no cliente (V3)
- **Data:** 2026-11-15
- **Decisão:** tudo é apresentação; o núcleo não muda.
  - **Golpe do jogador pela arma da mão principal** (`animacoes.yaml`, `golpes`):
    - faca e lança curta: estocada;
    - machadinha e punho: corte;
    - martelo e cajado: pesado;
    - o golpe forte é sempre o pesado.
  - **Inimigos:** usam o golpe da folha (corte do saqueador, bote do lobo, golpe do vulto). O preparo é o aviso, com um brilho curto quando começa a armar.
  - **Dano:** recuo de 3 quadros enquanto dura o lampejo.
  - **Inimigo abatido:** fica caído 25 s (`cair` → `caido`, `morrer` → `morto`). O vulto se desfaz e some.
  - **Depois da derrota:** quando a página fecha, o jogador se levanta (`levantar`).
  - **Camadas do jogador:** cabeça, mão principal e mão secundária, cada camada numa ficha própria (`aprendiz_c_*.yaml`, `so_camadas`), que renderiza em paralelo.
  - **A luz da lamparina:** sai 30 cm ao lado do corpo, do lado da mão esquerda. A luz é larga, e ler o ponto da mão em cada quadro não mudaria nada visível.
  - **Saqueador:** 3 variações de roupa e cor (`variantes`), uma por id: o bando não sai clonado.
  - **Efeitos** (`Particulas`, só na paleta): lampejo de impacto; gotas `#9C2A2A` (2 a 4 por golpe que acerta, no chão por 0,7 s), ou terra no caso do vulto; poeira de corrida e de queda; brilho do aviso.
  - **Console de depuração:** `puxar` traz os inimigos da área para 4 m do jogador. Serve para as capturas: o encontro forçado nasce nos pontos da área, longe demais para o inimigo perceber o jogador.
- **Dreamina:** 3 referências (lobo, poses do lobo, vulto), 90 créditos.

## D-118 — Árvores do V4: gerador próprio, sem Sapling
- **Data:** 2026-11-15
- **Decisão:** o spike da tarefa 1 do V4 fica com o gerador próprio (`natureza/arvore.py`, colonização do espaço, D-100).
  - Cada espécie nova é só um conjunto de parâmetros: castanheiro, freixo, pinheiro-silvestre e salgueiro.
  - Dois recursos novos:
    - o perfil `guarda`: copa que só existe no alto e termina chata (pinheiro);
    - `pendente`: o tufo desce em cortina mais estreita (salgueiro).
  - Os arbustos (zimbro, sabugueiro, amora) saem do mesmo gerador, com `rente`: um anel de tufos junto ao chão, senão a moita flutua sobre a sombra.
  - Pincéis novos no `folhas.pxo`: uma camada por espécie, acrescentada sem tocar nas que já existem.
- **Motivo:** o pintor de folhagem precisa saber onde está cada tufo (âncora por quadro, D-100), e o Sapling não dá isso. Instalar a extensão também traria uma dependência a mais para um resultado que o gerador próprio já alcança.
- **Descartado:** Sapling Tree Gen.
- **Dreamina:** nenhum crédito no V4. A casca sai do ruído por eixo, e a cor de cada espécie sai de rampas montadas só com cores que já estavam na paleta.

## D-119 — Espécie de árvore por área e por região
- **Data:** 2026-11-15
- **Decisão:** cada espécie nova tem o seu ID no manifesto (`natureza.castanheiro`, `natureza.freixo`, `natureza.pinheiro`, `natureza.salgueiro`).
  - O mapa continua dizendo só `prop.arvore` e `prop.arvore_b`.
  - A seção `especies` da `decoracao.yaml` troca o sprite por área (lista sorteada pelo hash da posição; repetir pesa mais) e por vizinhança (`perto: {de: casa_moinho, raio: 9}` → salgueiro). Quem faz a troca é o `Especies.java`.
  - Vila: carvalho, castanheiro e freixo, e salgueiro junto ao moinho. Estrada: mais pinheiro.
  - As árvores além da borda seguem a mesma regra.
- **Motivo:** dá variedade sem mexer nos mapas nem na simulação. Pegada e colisão são as do prop (regra da Fase V).
- **Descartado:** pôr todas as espécies como variantes de `prop.arvore`. Assim não haveria como fazer pinheiro só na estrada e salgueiro só no moinho.

## D-120 — A árvore de Nalia é peça única
- **Data:** 2026-11-15
- **Decisão:** `natureza/arvore_nalia.yaml`, com três IDs (`prop.arvore_nalia_morta`, `_viva`, `_cortada`) que o `props.yaml` usa nos estados de `arvore_velha`. Antes, os estados apontavam para a árvore genérica, e a "árvore velha" saía igual a qualquer outra.
  - É o mesmo esqueleto nos três estados (mesma semente): tronco grosso, torto e inclinado, maior que todas as outras árvores (8 m, copa de 6,8 m).
  - **Morta:** galho nu e cinza. **Viva:** a mesma árvore com folha rala (densidade 0,72). **Cortada:** o toco largo com o corte claro.
  - Nada brilha, nada é estranho na imagem. O arco revela o resto; a arte não adianta nada.
- **A aprovar pelo diretor:** os três estados.

## D-121 — Miúdos, arbustos e o sub-bosque além da borda
- **Data:** 2026-11-15
- **Decisão:**
  - **Decoração nova** (`decoracao/miudos.yaml`, `decoracao/pedras.yaml`, `natureza/arbustos.yaml`): capim alto com espiga, capim seco, juncos, samambaia, cinco flores do campo, cogumelos, folhas no chão (decalque), pedregulhos, arbustos e amora.
  - **`prop.pedras`** foi refeito com a mesma pegada.
  - **Troncos caídos:** só além da borda. Decoração não bloqueia, e um tronco que se atravessa andando fica errado.
  - **Distribuição:** vila e estrada, por terreno, em manchas, com distância mínima dos objetos. Nada a 1 m de porta (os pontos `*porta*` da área entram como obstáculo).
  - **Além da borda:** sub-bosque (`alem_da_borda`), cada vez mais fechado à medida que se afasta do mapa.

## D-122 — Terrenos pedra, plantio e tábuas; os três tons de grama ficam de fora
- **Data:** 2026-11-15
- **Decisão:**
  - `pedra` usa a textura de pedra do funil.
  - `plantio` (sulcos) e `tabuas` (assoalho) usam texturas feitas por script (`texturas/procedurais.py`), periódicas, em cinza. São geometria pura, e o gerador faz melhor que a Dreamina.
  - Com isso, nenhum terreno usado nos mapas tem arte do gerador antigo.
  - **Grama em três tons** (pisada, comum, alta) não entra: os mapas não têm terreno para isso, e criar um mudaria o mapa. A variação fica com a textura, com a decoração (o capim alto em manchas) e com o terreno de terra dos caminhos.

## D-123 — Vida ambiente e "Detalhes do ambiente"
- **Data:** 2026-11-15
- **Decisão:**
  - **Emissores** (`Ambiente.java`), sempre só na parte visível, com teto de 160:
    - folhas caem das árvores, mais nas rajadas do vento global;
    - de dia, bandos de 3 a 6 pássaros bem alto e borboletas em volta das flores;
    - à noite, vaga-lumes (cada um é um pontinho de luz na passada de luz) e mariposas em volta dos lampiões.
  - Cores só da paleta.
  - Opção nova: "Detalhes do ambiente: Alto/Baixo". Baixo desliga tudo isto.
  - A fumaça de chaminé fica para o V5, junto com as chaminés novas.

## D-124 — Kit de construção por parâmetros; a pegada é a colisão, o corpo pode ser menor
- **Data:** 2026-11-15
- **Decisão:**
  - Um kit só (`tools/blender/construcoes/casa.py`) faz as 9 construções a partir da ficha YAML: paredes de enxaimel, pedra ou tábua; telhado de duas águas, quatro águas ou meia-água, de colmo, tabuinha ou lousa; chaminés, alpendre, puxado, tabuleta, ervas, peças soltas e as pás do moinho.
  - **A pegada da ficha é a do `props.yaml`.** Quando a casa tem alpendre ou puxado, o corpo (`corpo`) encolhe dentro da pegada; nenhum mapa e nenhuma colisão mudaram.
  - Peças soltas pequenas (barril, lenha, vasos, ferramentas) podem sair até ~0,5 m da pegada: são desenho, não colidem.
  - Tabuinha e lousa são texturas por script (`texturas/procedurais.py`), como plantio e tábuas (D-122). Nenhum crédito da Dreamina.
- **Motivo:** uma ficha por construção, variedade por parâmetro, e a regra da Fase V (a arte não muda o `core-sim`).
- **Alternativa descartada:** modelar cada casa à mão (lento, sem variantes). Água-furtada e escada externa não entraram; ficam para o V8, se fizerem falta.

## D-125 — Variantes de casa pela posição; identidade das construções principais
- **Data:** 2026-11-15
- **Decisão:**
  - `casa_pequena` (3 variantes), `casa_media` (3) e `casa_estreita` (2) mudam telhado, reboco, desgaste, direção da cumeeira e puxado. O cliente escolhe pela posição (o mesmo mecanismo das árvores).
  - **Taverna:** a maior, colmo velho e escuro, corpo recuado com alpendre na frente, duas chaminés, tabuleta, barris.
  - **Oficina:** como no V1, mais ferramentas, lenha e barril do lado de fora.
  - **Moinho:** torre de pedra com empena para o sul, tabuinha, **pás de vento** que giram (16 quadros, um quarto de volta). Não tem roda de água: o mapa não tem água ali.
  - **Posto:** lousa de quatro águas, duas chaminés, tabuleta, alpendre de carga a leste. É a única construção "de fora".
  - **Curandeira:** baixa, colmo grosso, ervas sob o beiral e vasos. **Celeiro:** tábua, portão duplo, sem luz.
- **A aprovar pelo diretor:** a identidade da taverna, da oficina, do moinho e do Posto.

## D-126 — Luz das janelas e fumaça vêm do JSON da arte
- **Data:** 2026-11-15
- **Decisão:**
  - O Blender grava, por folha, os pontos em metros do jogo (`world`: `luzN` diante de cada janela, `fumacaN` na boca da chaminé e na forja).
  - O cliente usa esses pontos para a variante daquele lugar; o `props.yaml` continua dizendo raio e cor (a primeira luz do prop). Sem pontos, valem os do `props.yaml`.
  - A fumaça é da vida ambiente (`Ambiente.java`), desliga com "Detalhes do ambiente: Baixo" e não disputa o teto com folhas e bichos. Ao entrar na área, o ar já começa com 6 s simulados.
- **Motivo:** cada variante tem janelas e chaminé em lugares diferentes; pontos fixos no `props.yaml` ficariam errados.

## D-127 — Objetos de fora; atlas do menor para o maior
- **Data:** 2026-11-15
- **Decisão:**
  - 22 objetos por script (`construcoes/objetos.py`, fichas em `tools/blender/objetos/`): cercas, poço, lampião, banca, tenda, carroça, barril, caixote, feno, sacos, espantalho, lápides, marco, placa, estaca, bigorna, forja (brasa em laço), entulho, buraco, terra remexida, laje e o banco da praça. Mesmo ID e mesma pegada.
  - **Varal** não entra: não existe prop nem lugar no mapa para ele.
  - A laje tem sulcos que não se leem (anel, riscos, pontos): não confirma nada.
  - O atlas passa a empacotar do menor arquivo para o maior: casas, objetos e decoração ficam na primeira página e só as árvores ocupam as outras (276 → 158 chamadas por quadro).
- **Pendência:** `fibra` e `luz_buraco` (dentro da estrutura) ficam para o V6.

## D-128 — V6 feito por subagentes em paralelo
- **Data:** 2026-11-16
- **Decisão:** a pedido do diretor, o V6 foi dividido em 5 subagentes (móveis, paredes/pisos/corte, estrutura enterrada, itens, luz viva), cada um em arquivos próprios, com o orquestrador integrando, conferindo as imagens e fechando o marco. Ninguém além do orquestrador faz commit ou edita `PROGRESS.md`/`DECISIONS.md`.
- **Motivo:** tempo e atenção a cada parte. **Custo:** o limite de uso da conta acaba mais rápido (os cinco caíram juntos uma vez).

## D-129 — Casca dos cômodos: paredes por casa, frente cortada e piso pelo cliente
- **Data:** 2026-11-16
- **Decisão:**
  - Um conjunto de paredes por casa (`interiores/paredes.py`): oficina em enxaimel, taverna com lambril, moinho em pedra, curandeira caiada. O mapa mantém os IDs; o cliente troca o sprite pelo sufixo `_<casa>` (`assets/config/interiores.yaml`, `CascaInterior.java`).
  - Paredes da frente (sul e leste), pontas e canto são geradas no cliente a partir do retângulo do piso, cortadas a 0,4 m, na faixa de colisão da borda. Mapas e `core-sim` não mudam.
  - O piso do cômodo troca por dado, só no desenho (o som dos passos segue o terreno do mapa), com losango exato: borda reta, fim da pendência do M8.
  - Pisos feitos de geometria, sem PNG de textura novo (um PNG novo invalidaria o cache de render de tudo).
  - Paredes sem sombra projetada. A variante "reboco caído" saiu: de perto parecia símbolo gravado.
- **Alternativa descartada:** pôr as paredes da frente no mapa (mudaria colisão e conteúdo).

## D-130 — Móveis de interior
- **Data:** 2026-11-16
- **Decisão:**
  - Tipo `moveis` (`interiores/moveis.py`): mesa (3), mesa longa, balcão, lareira, bancada, mó, cama (3), baú, ervas. Mesmo ID e pegada.
  - Toda mesa e o balcão têm vela com ponto de luz; por isso ganharam `lights` no `props.yaml` (dado de apresentação).
  - Lareira sem fumaça (o cômodo é cortado). Fogo: tom claro só no pé da chama e brilho fraco; o laranja vem do sprite, não da soma do brilho.
  - `barril`, `caixote`, `sacos` e `banco` ficam com a arte do V5 (mesmo ID do lado de fora). Tear/fiandeira não entram: não há prop nem lugar no mapa.

## D-131 — Estrutura enterrada
- **Data:** 2026-11-16
- **Decisão:**
  - Blocos lisos de ardósia de 1 m em junta corrida, lajes de 2 × 2 m, só com rampas que já existiam (nenhuma cor nova).
  - Paredes desenhadas pelo cliente a partir da colisão (`ParedesEstrutura.java`): altas ao norte e a oeste só quando cobrem rocha; cortadas a 0,45 m no resto.
  - Chão próprio por área: `tile.<terreno>.<área>` no manifesto; o mapa continua dizendo "pedra".
  - Sem sombra projetada no subterrâneo. `luz_buraco` é um feixe frio pontilhado com poeira; `fibra` são fios de pedra escura que rompem as lajes.
  - Laje: arco aberto e cinco linhas paralelas. Nada de grade, painel, figura ou olho; não confirma V2/V3. `prop.entulho` e `prop.laje_simbolos` saem das fichas do V5 (só a estrutura os usa).
- **Pede aprovação do diretor** (V6.md).

## D-132 — Itens: ícones por modelo, câmera própria
- **Data:** 2026-11-16
- **Decisão:**
  - 43 modelos (`itens/modelos.py`), fichas por família (5 renders em vez de 43), ícone de 32×32 em `assets/arte/itens/`; `icone.<id>` no `arte.yaml` vence o provisório.
  - Câmera de ícone ortográfica de frente, com elevação por item e sem a correção vertical do mundo (o isométrico achatava papéis e roupas).
  - Ferramentas e armas copiam as proporções do kit humano sem importar `roupa.py` (não invalida o cache dos personagens).
  - `GameUi.ICON` de 40 para 32 px (1×, nítido); o tamanho final é do tema, no V7. Nenhum ícone brilha.

## D-133 — Luz viva
- **Data:** 2026-11-16
- **Decisão:**
  - Parâmetros em `assets/config/luz_viva.yaml` (o `daynight.yaml` é lista de horas e o `PropDef` é do núcleo).
  - Fogo que tremula por ruído suave, determinístico pela posição, preset pelo id do prop (ou prefixo).
  - Feixe de sol identificado pelos props `parede_x_janela`/`parede_y_janela`; sem estado (função da hora), então as capturas se repetem. "Detalhes: Baixo" tira só a poeira.
- **Limite conhecido:** o feixe não é bloqueado por móveis nem paredes.
