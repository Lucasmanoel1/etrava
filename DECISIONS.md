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
