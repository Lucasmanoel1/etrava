# PROGRESS — ETRAVA

Última atualização: 2026-11-10

## Estado atual
- **Marco concluído:** M2 — Simulação básica.
- **Status:** **PARADO no fim do M2**, aguardando o usuário mandar continuar para o M3 (Conhecimento e diálogo).
- Marcos concluídos: M0, M1 (portão visual do vilarejo aprovado com arte provisória — D-028), M2.

## Feito no M2
- **Tempo:** esperar (1–12 h) e dormir (até 06:00) saltam o tempo processando tudo em ordem; eventos podem interromper.
- **Eventos agendados:** fila por (tick, ordem de criação), com efeitos `setFlag`, `setVar` e `chronicle`.
- **Estado do mundo:** flags, variáveis, crônica interna, NPCs de todas as áreas.
- **NPCs com agenda:** 5 NPCs provisórios (`content/npcs/`) com blocos de horário; andam na área carregada, são posicionados direto fora dela; entram em casa (somem) e trocam de área pelo ponto `entrada`.
- **Save/load versionado:** arquivo `.etrava` (ZIP com `meta.json` e `state.json`) em `%APPDATA%\Etrava\saves`, escrita atômica, hash de estado, migrações em cadeia, erros tratados.
- **Cliente:** F5/F9 (salvar/carregar rápido), T (esperar 1 h), salvamento automático rotativo em 3 arquivos ao dormir e ao trocar de área, avisos na tela.
- **Simulação headless:** `gradlew simulate`.
- Os figurantes de ronda do M1 saíram; o validador passou a conferir agendas.

## Testes de aceitação do marco
| AT | Situação | Teste |
|---|---|---|
| AT-03 determinismo | passa | `mesmaSementeEMesmosComandosDaoCronicaEEstadoIdenticos` |
| AT-05 salvar/carregar | passa | `salvarECarregarPreservaOEstadoEOFuturo` |
| AT-06 versão antiga e corrompido | passa | `saveDeVersaoAntigaEhMigradoOuRecusado`, `saveCorrompidoGeraErroTratado` |
| AT-07 evento durante o sono | passa | `eventoDas3DaManhaAconteceMesmoDormindo` |

`gradlew build`: 52 testes, cobertura do `core-sim` acima de 70%, validador sem erros (2 avisos esperados de imagens pendentes).
30 dias headless rodam em menos de 1 segundo; duas execuções com a mesma semente dão o mesmo hash.

## Como rodar e testar
| Comando | Faz |
|---|---|
| `gradlew run` | abre o jogo na cena de referência |
| `gradlew build` | compila, testa, confere cobertura e valida conteúdo e assets |
| `gradlew simulate --args="--dias 30 --semente 42 --politica parado"` | simulação headless com relatório |
| `gradlew distWin` | gera o `Etrava.exe` e o ZIP |
| `gradlew generateArt` / `generateRefMap` / `licenses` | arte provisória, mapa de referência, relatório de licenças |

Controles: clique esquerdo anda · WASD anda · roda do mouse dá zoom · Espaço pausa · **T espera 1 h** · **F5 salva** · **F9 carrega** · F1 console · F12 captura · Esc sai.
Console (novos): `esperar H`, `dormir`, `area ID`, `salvar [NOME]`, `carregar [NOME]`, `saves`, `npcs`, `cronica`, `agendar HORAS TEXTO`.

Roteiro rápido para ver o M2: `gradlew run` → F1 → `npcs` → `hora 17.9` → fechar o console e ver os moradores irem para a taverna às 18:00 → F5 → `dormir` → F9 (volta para antes do sono).

## Problemas conhecidos / pendências
- **Arte definitiva:** o MCP da Dreamina não está acessível ao Claude Code nesta máquina. Toda a arte segue provisória.
- Personagens com cara de manequim e só duas animações.
- NPCs atravessam uns aos outros e o jogador (não há colisão entre personagens).
- Troca de área só pelo console (`area ID`); não há portas nem saída pela estrada ainda (D-031).
- Sem tela de salvar/carregar e sem miniatura no save (M7, D-032).
- Só existe a política headless `parado`.
- A crônica ainda registra pouco (virada de dia, sono, troca de área, eventos agendados).
- Bordas entre terrenos em degraus; luz atravessa objetos.
- AT-18 segue parcial: executável testado só nesta máquina.
- Mapas gerados ainda não foram abertos no Tiled.
- `CONTENT.md` (como criar NPCs, fatos, diálogos, arcos) ainda não existe; começa no M3.

## Próximo passo — M3 (Conhecimento e diálogo), só com o "continue" do usuário
Fatos e conhecimento, caderno, diálogos com condições e efeitos, conversas ambientes, propagação de rumores, validador narrativo, retratos. Critério: AT-04 e AT-08 passando e um diálogo de exemplo com 3 ramificações.

## Paradas obrigatórias (lembrete)
- Fim de cada marco (M0…M8).
- Sem arte em massa antes de uma amostra de arte definitiva aprovada.
- Antes de decisão irreversível ou de alto impacto narrativo/visual.
