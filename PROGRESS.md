# PROGRESS — ETRAVA

Última atualização: 2026-11-11

## Estado atual
- **Marco concluído:** M3 — Conhecimento e diálogo.
- **Status:** **PARADO no fim do M3**, aguardando o usuário mandar continuar para o M4 (Mundo independente).
- Marcos concluídos: M0, M1, M2, M3.
- Direção visual trocada para **pixel art** (D-033), a pedido do usuário.

## Feito nesta etapa

### Arte
- Toda a arte gerada por código agora sai em pixel art, numa paleta única de 53 cores; o renderizador desenha com zoom inteiro e sprites alinhados à grade.
- Dreamina funcionando pela CLI `dreamina-canvas` (não é MCP). Seis retratos gerados (moleiro, ferreiro, taberneira, aldeã, viajante, aprendiz) e convertidos para a paleta com `gradlew importPortraits`.
- `STYLE.md` reescrito para pixel art (v2).

### M3
- **Fatos e conhecimento:** `content/facts.yaml` com camadas V0–V3, versões e veracidade oculta; jogador e NPCs sabem coisas com fonte, dia, lugar e grau de certeza.
- **Diálogos** em `content/dialogues/`, com condições e efeitos; o mundo para durante a conversa; abandonar a conversa pode ter consequência.
- **Conversas ambientes** em `content/ambient/`: tocam com os NPCs juntos e o jogador por perto; quem ouve até o fim anota como rumor.
- **Rumores:** NPCs próximos trocam fatos a cada hora, às vezes distorcidos.
- **Caderno** (J ou N): abas "Ouvi dizer" e "Vi e li", com fonte e dia; clicar marca uma anotação; fatos marcados viram "perguntar sobre" nas conversas.
- **Validador narrativo:** IDs inexistentes, nós inalcançáveis, fato de ramificação com menos de 2 fontes, V2/V3 no Capítulo I, V1 como certeza.
- **Interface:** clicar num NPC anda até ele e abre a conversa, com retrato; legendas sobre quem fala; nome ao passar o mouse.
- `CONTENT.md`: guia de como escrever fatos, NPCs, diálogos e conversas.

## Testes de aceitação do marco
| AT | Situação | Teste |
|---|---|---|
| AT-04 validador | passa (parte de arcos fica para o M4) | `validadorRejeitaNarrativaQuebradaApontandoArquivoEItem` |
| AT-08 rumor | passa em 10 sementes | `rumorPlantadoNaTavernaChegaATresOutrosNpcsEmTresDias` |
| Diálogo com 3 ramificações | passa | `caminhoDaMo…`, `caminhoDaArvore…`, `caminhoDaMenina…` |

`gradlew build`: 70 testes, cobertura do `core-sim` acima de 70%, validador sem erros.

## Como rodar e testar
| Comando | Faz |
|---|---|
| `gradlew run` | abre o jogo |
| `gradlew build` | compila, testa, confere cobertura e valida conteúdo e assets |
| `gradlew simulate --args="--dias 30 --semente 42 --politica parado"` | simulação headless |
| `gradlew generateArt` | regenera a arte por código (já em pixel art) |
| `gradlew importPortraits` | converte os retratos de `arte-bruta/retratos` para a paleta do jogo |
| `gradlew distWin` | gera o `Etrava.exe` e o ZIP |

Controles: clique anda ou fala com um NPC · WASD anda · roda do mouse muda o zoom · **J ou N abre o caderno** · na conversa: **1 a 9** escolhe, **Espaço** continua, **Esc** vai embora · T espera 1 h · F5/F9 salva/carrega · F1 console.
Console (novos): `falar NPC`, `escolher N`, `caderno`, `sabe NPC`.

Roteiro para ver o M3:
1. `gradlew run`, clique na aldeã (perto do poço de manhã) e pergunte "Alguma novidade?".
2. Abra o caderno (J): a anotação aparece com "Aldeã disse:". Clique nela para marcar.
3. Vá ao moinho e fale com o moleiro: agora existe a opção "Ouvi falar da árvore".
4. No console, `hora 14` e fique na praça: a aldeã e a taberneira conversam sozinhas.

## Créditos da Dreamina
Saldo inicial 8.757 → 8.540 agora. Foram 6 retratos de 30 créditos (180). Os outros 37 de diferença eu não consegui atribuir a uma operação minha. Dois nós de teste ficaram salvos no canvas sem rodar (não cobraram nada).

## Problemas conhecidos / pendências
- O texto dos diálogos de exemplo é meu e precisa da leitura de tom do diretor.
- Fonte da interface ainda é a padrão do libGDX (não tem travessão, por exemplo).
- Caderno só tem duas das cinco abas do PRD (faltam Pessoas, Lugares, Documentos).
- Sem escolhas por habilidade (as habilidades são do M5); o rótulo `tag` já existe.
- A checagem de "escolha relevante" só confere a declaração dos canais.
- Conferir os termos de uso comercial da Dreamina antes de um release.
- NPCs atravessam uns aos outros; troca de área só pelo console; sem tela de salvar/carregar.
- Personagens com só duas animações; bordas de mapa mostram o vazio.
- AT-18 segue parcial: executável testado só nesta máquina.

## Próximo passo — M4 (Mundo independente), só com o "continue" do usuário
Motor de arcos, eventos externos, variantes de locais, memória e opinião, reação a crimes, epílogo combinatório; A1 (Nalia e a árvore) como arco de referência. Critério: AT-01 e AT-02 para A1.

## Paradas obrigatórias (lembrete)
- Fim de cada marco (M0…M8).
- Antes de decisão irreversível ou de alto impacto narrativo/visual.
