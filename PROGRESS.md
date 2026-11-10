# PROGRESS — ETRAVA

Última atualização: 2026-11-10

## Estado atual
- **Marco em andamento:** M1 — Fatia visual.
- **Status:** **PARADO no portão visual (AT-15).** A parte técnica do M1 está pronta; a arte é provisória (gerada por código). Falta a avaliação visual do usuário.
- Marcos concluídos: M0.

## Feito no M1
- **Pipeline de assets:** manifesto por ID (`assets/manifest/*.yaml`), placeholder automático com aviso no log, arte definitiva substitui a provisória só repetindo o ID em outro manifesto. `gradlew licenses` gera `ASSETS_LICENSES.md`; o empacotamento falha se houver asset sem origem/licença.
- **Renderizador 2D isométrico:** chão em tiles com mistura entre terrenos, sprites ordenados de trás para a frente, sombras, objetos que cobrem o jogador ficam translúcidos.
- **Luz:** ciclo dia/noite por tabela editável (`assets/config/daynight.yaml`), luzes de janelas e lamparinas à noite, brilho próprio das vidraças, pós-processamento (saturação, contraste, vinheta).
- **Personagens animados:** folhas de sprites em 8 direções (parado e andando); jogador + uma figurante que anda pela praça.
- **Arte provisória por código** (`gradlew generateArt`): 3 terrenos, 4 construções, árvores, poço, lampião, barril, caixote, feno, 2 personagens.
- **Cena de referência:** `content/areas/vila_ref.tmj` (praça com poço, oficina, taverna, casa, moinho) — é a área que abre por padrão.
- **`STYLE.md` v1:** regras, paleta, especificação dos arquivos, modelos de prompt para IA e como trocar a arte provisória.
- Validador passou a conferir referências a assets; 36 testes no `core-sim`.

## Como rodar e testar
| Comando | Faz |
|---|---|
| `gradlew run` | abre o jogo na cena de referência |
| `gradlew run --args="--area=area_teste"` | abre a área de teste do M0 (mostra placeholders) |
| `gradlew build` | compila, testa, confere cobertura e valida conteúdo e assets |
| `gradlew generateArt` | regenera a arte provisória |
| `gradlew generateRefMap` | regenera o mapa da cena de referência (sobrescreve edições feitas no Tiled) |
| `gradlew licenses` | atualiza `ASSETS_LICENSES.md` |
| `gradlew distWin` | gera o `Etrava.exe` e o ZIP |

Controles: clique esquerdo anda · WASD anda · roda do mouse dá zoom · Espaço pausa · F1 console · F12 captura · Esc sai.
Console (novos): `hora H` pula para a hora H (ex.: `hora 22`), `luz on/off`, `pos-fx on/off`, `colisao`, `hud`, `zoom VALOR`.

Capturas da cena: `docs/capturas/m1/` (`dia`, `tarde`, `noite`, `noite_taverna`).

## Verificado
- `gradlew build`: 36 testes passando, cobertura do `core-sim` acima de 70%, validador sem erros (2 avisos esperados de imagens pendentes).
- Jogo rodando na cena de referência de dia, ao entardecer e à noite; figurante andando; placeholders funcionando na área de teste.
- `Etrava.exe` do ZIP roda sem Java no PATH, nas duas áreas.

## Portão visual — o que o usuário precisa decidir
1. O **clima** (luz, paleta, noite com janelas acesas) está no caminho certo?
2. A **arte provisória** basta como base para seguir, com a definitiva entrando aos poucos, ou é preciso arte definitiva antes de continuar?
3. **Personagens:** aceitar o boneco atual como provisório e melhorá-lo depois, ou investir agora?

## Problemas conhecidos / pendências
- Toda a arte é provisória. Nada é definitivo ainda.
- Personagens têm cara de manequim e só duas animações (parado, andar); faltam as outras do RF-424.
- Bordas entre terrenos ainda aparecem em degraus.
- A luz atravessa objetos e não projeta sombras (D-026).
- Um objeto grande fica translúcido quando o jogador passa atrás da área do sprite, mesmo sem estar de fato coberto.
- AT-15 pede 5 capturas (vilarejo dia/noite, taverna, estrutura enterrada, Posto); taverna por dentro, estrutura e Posto ainda não existem como áreas. Este portão cobre o vilarejo.
- Não medi FPS com muitos NPCs nem no hardware mínimo (AT-17 é do fim do MVP).
- Fonte da interface ainda é a padrão do libGDX.
- AT-18 segue parcial: executável testado só nesta máquina.
- Os mapas gerados ainda não foram abertos no Tiled para conferir.
- Camada `lights` dos mapas não implementada (as luzes vêm dos props).

## Próximo passo
- Com a aprovação do portão: **M2 — Simulação básica** (tempo, esperar/dormir, estado do mundo, save/load versionado, NPCs com agenda, simulação headless; AT-03, AT-05, AT-06, AT-07).
- Se o portão for reprovado: iterar no visual antes de seguir.

## Paradas obrigatórias (lembrete)
- Fim de cada marco (M0…M8).
- M1: portão visual — sem assets em massa sem aprovação explícita.
- Antes de decisão irreversível ou de alto impacto narrativo/visual.
