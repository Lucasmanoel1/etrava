# ETRAVA — Instruções do projeto

Você está implementando **ETRAVA**, um RPG dark fantasy isométrico em Java.

## Documentos (leia antes de agir)
- `docs/PRD.md` — requisitos, escopo do MVP, marcos, testes de aceitação. **Fonte principal.**
- `docs/ponto-seguro.md` — visão e filosofia do projeto.
- `docs/lore-etrava.md` — lore provisória.
- `docs/SPEC.md` — especificação técnica (você cria no início).
- `PROGRESS.md` — estado atual do trabalho. **Leia primeiro em toda sessão.**
- `DECISIONS.md` — registro de decisões (data, decisão, motivo, alternativa descartada).

Hierarquia em conflito: instruções do usuário > PRD > Ponto Seguro > lore.

## Ciclo de trabalho (toda sessão)
1. Leia `PROGRESS.md` e identifique o marco atual e a próxima tarefa.
2. Releia no PRD apenas as seções relevantes para essa tarefa.
3. Trabalhe **somente no marco atual**. Nada de itens [PÓS]/[LP].
4. Rode os testes (`gradlew test`) e o validador de conteúdo antes de considerar algo pronto.
5. Ao concluir uma tarefa relevante: faça commit no git com mensagem descritiva.
6. Atualize `PROGRESS.md` (feito, próximo passo, problemas conhecidos, como testar).
7. **Ao concluir um marco: PARE.** Mostre ao usuário o que foi feito, como rodar/testar e quais decisões precisam de aprovação. Não comece o próximo marco sem o usuário mandar continuar.

Quando o usuário disser apenas "continue", siga este ciclo a partir do `PROGRESS.md`.

## Paradas obrigatórias
- Depois de escrever `docs/SPEC.md`, antes de escrever código.
- Fim de cada marco (M0…M8).
- **M1 (portão visual):** não produza assets em massa sem aprovação visual explícita do usuário.
- Antes de qualquer decisão irreversível ou de alto impacto narrativo/visual.

## Regras técnicas
- Java 21+, Gradle, libGDX (LWJGL3) salvo decisão registrada em contrário.
- Núcleo de simulação (`core-sim`) em Java puro, sem dependência de render, testável headless.
- Conteúdo (NPCs, diálogos, arcos, fatos, itens, locais) em arquivos de dados, não em código.
- Asset ausente → placeholder automático + aviso no log. Nunca travar por falta de asset.
- Preferir bibliotecas consolidadas a escrever do zero. Sem overengineering.
- Ambiguidade pequena: escolha a opção mais simples coerente com o PRD, registre em `DECISIONS.md` e siga.

## Regras narrativas
- O mundo não gira em torno do protagonista. Sem marcadores de missão.
- Nenhum fato de camada V2/V3 confirmado no Capítulo I.
- Todo arco tem desfecho padrão sem o jogador; todo fato de ramificação tem 2 fontes.
- Diálogos curtos, com subtexto, em português do Brasil natural.
