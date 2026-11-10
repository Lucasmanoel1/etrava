# PROGRESS — ETRAVA

Última atualização: 2026-11-09

## Estado atual
- **Marco concluído:** M0 — Fundação.
- **Status:** **PARADO no fim do M0**, aguardando o usuário mandar continuar para o M1 (Fatia visual).
- SPEC aprovada pelo usuário. Visual definido como **2D isométrico** (D-018), por delegação do usuário.

## Feito no M0
- Projeto Gradle com três módulos (`core-sim`, `game-client`, `tools`), Java 21 por toolchain, wrapper 9.9.0.
- `core-sim` (Java puro): RNG com semente, relógio em ticks, grade de navegação + A*, leitor de mapas do Tiled, carregador de conteúdo, validador (props e áreas), fachada `Simulation` com comandos gravados.
- `game-client` (libGDX): janela, laço em passo fixo com interpolação, câmera isométrica com zoom e suavização, área de teste desenhada com placeholders, clique para mover, WASD, pausa, objetos na frente do jogador ficam translúcidos, HUD com dia/hora, console de depuração (F1), captura de tela (F12), log em arquivo e relatório de crash.
- `tools`: validador de conteúdo ligado ao build.
- Conteúdo: `content/props.yaml`, `content/areas/area_teste.tmj` (40×40 m).
- Pacote Windows: pasta com `Etrava.exe` + Java embutido, e ZIP.

## Como rodar e testar
Na raiz do projeto (terminal do IntelliJ serve):

| Comando | Faz |
|---|---|
| `gradlew run` | abre o jogo em modo desenvolvimento |
| `gradlew build` | compila, roda os testes, a cobertura e o validador |
| `gradlew test` | só os testes |
| `gradlew validateContent` | só o validador de conteúdo |
| `gradlew distWin` | gera `game-client/build/package/out/Etrava/Etrava.exe` e o ZIP em `game-client/build/package/` |

No IntelliJ: abrir a pasta como projeto Gradle; a tarefa `run` fica em Gradle → etrava → game-client → Tasks → application.

Controles: clique esquerdo anda (segurar acompanha o mouse) · WASD anda · roda do mouse dá zoom · Espaço pausa · F1 console · F12 captura de tela · Esc sai.
Console: `ajuda`, `pos`, `tp X Y`, `ir X Y`, `hora`, `avancar HORAS`, `vel FATOR`, `semente`, `validar`, `sair`.

Arquivos do usuário: `%APPDATA%\Etrava\` (`logs\etrava.log`, `screenshots\`).

## Verificado
- `gradlew build`: 25 testes passando, cobertura do `core-sim` acima de 70%, validador sem erros.
- Jogo rodando a 60 FPS na área de teste; personagem contorna obstáculos e entra na construção pela porta.
- `Etrava.exe` do ZIP roda sem Java no PATH e sem `JAVA_HOME`.

## Problemas conhecidos / pendências
- AT-18 está **parcial**: o executável foi testado nesta máquina sem Java no PATH, não numa máquina Windows limpa.
- O mapa de teste foi gerado por script e ainda não foi aberto no Tiled para conferir (D-022).
- Paredes altas escondem o interior das construções; telhado e paredes da frente que somem ao entrar são trabalho do M1.
- A fonte do HUD/console é a padrão do libGDX; a fonte definitiva entra com o guia de estilo (M1).
- `gradlew simulate` e `gradlew licenses`, citados na SPEC, ainda não existem (M2 e M1).
- `fastForward` só avança o relógio; blocos de hora e eventos agendados entram no M2.
- `docs/lore-etrava.md` contém restos da conversa de origem (parágrafos de chat e links de imagem).

## Próximo passo — M1 (Fatia visual), só com o "continue" do usuário
1. `STYLE.md` v1 e pipeline de assets com manifesto e placeholders.
2. Testar as duas rotas de sprites de personagem (D-019) e a consistência do cenário gerado por IA.
3. Cena de referência do vilarejo: terreno, 3–4 construções, 2 personagens animados, dia/noite, luzes, pós-processamento, oclusão.
4. **Portão visual (AT-15):** 5 capturas para aprovação explícita. Sem assets em massa antes disso.

Depende do usuário no M1: gerar imagens na ferramenta de IA dele (ex.: Dreamina) a partir dos prompts do `STYLE.md`, ou indicar outra fonte de arte.

## Paradas obrigatórias (lembrete)
- Fim de cada marco (M0…M8).
- M1: portão visual — sem assets em massa sem aprovação explícita.
- Antes de decisão irreversível ou de alto impacto narrativo/visual.
