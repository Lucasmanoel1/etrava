# PROGRESS — ETRAVA

Última atualização: 2026-11-09

## Estado atual
- **Fase:** pré-M0 — especificação técnica concluída.
- **Status:** **PARADO, aguardando aprovação da SPEC pelo usuário.** Nenhum código foi escrito.

## Feito
- Lidos por completo: `CLAUDE.md`, `docs/PRD.md`, `docs/ponto-seguro.md`, `docs/lore-etrava.md`.
- `docs/SPEC.md` v0.1 escrita (arquitetura, módulos, formatos de dados, save, diálogos/arcos, assets, validador, testes, build).
- `DECISIONS.md` com D-001 a D-017.
- `ponto-seguro` e `lore-etrava` renomeados de `.txt` para `.md` (D-003).
- Repositório git iniciado, com commit inicial da documentação.
- Ambiente verificado: JDK 17, 21.0.12 e 25 em `C:\Program Files\Java`; `JAVA_HOME` = 25; git 2.54. Usuário usa IntelliJ.

## Próximo passo
1. Usuário aprova (ou corrige) a SPEC.
2. Com o "continue": iniciar o **M0 — Fundação** (PRD §18): projeto Gradle com os três módulos, janela, laço, câmera isométrica, área de teste com placeholders, clique para mover, console de debug, logging, `core-sim` com testes rodando, executável Windows com runtime embutido.
3. Parar ao fim do M0.

## Problemas conhecidos
- Versões de libGDX, gdx-gltf e Gradle ainda não fixadas nem testadas (primeira tarefa do M0). Também falta confirmar o Gradle rodando com o JDK 25 como padrão e compilando com o toolchain 21.
- `docs/lore-etrava.md` contém restos da conversa de origem (parágrafos iniciais de chat e três links de imagem na seção de Nácre). Não atrapalha; pode ser limpo se o usuário quiser.

## Como testar
Ainda não há código.

## Paradas obrigatórias (lembrete)
- Fim de cada marco (M0…M8).
- M1: portão visual — sem assets em massa sem aprovação explícita.
- Antes de decisão irreversível ou de alto impacto narrativo/visual.
