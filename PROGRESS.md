# PROGRESS — ETRAVA

Última atualização: 2026-11-11

## Estado atual
- **Marco concluído:** M4 — Mundo independente.
- **Status:** **PARADO no fim do M4**, aguardando o usuário mandar continuar para o M5 (Vida e economia).
- Marcos concluídos: M0, M1, M2, M3, M4.
- Aviso do usuário (2026-11-11): do M5 ao M7 ele pretende usar outro modelo (Opus) e voltar ao Fable no M8, para polir. Este arquivo, o `DECISIONS.md`, a `docs/SPEC.md` e o `CONTENT.md` estão atualizados para essa troca.

## Feito no M4

- **Motor de arcos** (`content/arcs/`): histórias como máquinas de estado em dados. Todo estágio tem prazo, então todo arco chega a um desfecho sozinho. Avaliado a cada hora de jogo e logo depois de qualquer escolha do jogador.
- **A1 — Nalia e a árvore**, do começo ao fim: árvore morta → brota (≈ dia 2) → insônia → vozes → impasse (≈ dia 10) → desfecho (≈ dias 17–19). Cinco desfechos:
  - sem o jogador: um estudioso estabiliza a árvore (só se ele passar pela região e o rumor tiver chegado à estrada), o pai corta a árvore, ou Nalia piora;
  - com o jogador: juntar 3 voluntários para repartir o custo (curandeira, moleiro, ferreiro, taberneira ou ele mesmo), convencer Nalia a soltar a árvore, ou apoiar o ferreiro no corte.
- **Eventos externos E1–E5** (ferrovia, sal, sacerdote, a mulher do incêndio em três versões, o homem da estrada): acontecem fora de tela em datas sorteadas e chegam pelo viajante; daí em diante correm como rumor.
- **Cenário que muda:** a árvore do quintal do moinho tem três variantes (morta, viva, cortada) e troca na tela quando o arco manda.
- **NPCs novos:** Nalia, a curandeira e o homem de fora (só aparece num dos desfechos e parte três dias depois). Ferreiro, taberneira e viajante ganharam conversa.
- **Agenda que reage ao arco:** Nalia passa as noites debaixo da árvore enquanto não dorme; a curandeira vai ao moinho no impasse; se Nalia piora, não sai mais de casa.
- **Memória e opinião:** NPCs guardam lembranças datadas (até 20); parentes e amigos contam mais um ao outro, rivais menos.
- **Atos do jogador** (`content/deeds.yaml`): só pesam para quem viu; viram rumor e pesam menos para quem só ouviu; quem detesta o jogador (opinião ≤ −40) se recusa a conversar.
- **Novidades:** perguntar "o que há de novo?" ao viajante ou à taberneira traz a notícia mais recente que eles sabem.
- **Epílogo combinatório** (`content/epilogues/cap1.yaml`, 27 fragmentos em 12 espaços): aparece ao fim do dia 30, numa tela de papel; o jogo continua depois.
- **Ritmo Contemplativo** (prazos ×2) na simulação e no `simulate`; a escolha no menu vem no M7.
- **Validador:** arco sem prazo, sem desfecho, só com desfecho trágico ou que anda em círculo; estágio, chave de prop, variante, ato ou situação inexistentes; arco maior sem trecho garantido no epílogo.
- Save na versão 3 (saves antigos são recusados com mensagem).

## Testes de aceitação do marco
| AT | Situação | Teste |
|---|---|---|
| AT-01 (para A1) | passa | `at01_jogadorParado30Dias_arcoEEventosExternosChegamAoFim` |
| AT-02 | passa: em 20 sementes, 7 estabilizada · 7 árvore cortada · 6 Nalia piora | `at02_vinteSementes_desfechosDiferentesEPeloMenosUmNaoTragico` |
| AT-04 (parte de arcos) | passa | `validadorRejeitaArcoSemDesfechoEEpilogoSemGarantia` |
| AT-05 com arco em andamento | passa | `mesmaSementeDaAMesmaHistoria_eOSaveNoMeioDoArcoNaoMudaNada` |

AT-01 pede também A2, que é do M7; os 5 eventos externos já ocorrem.
`gradlew build`: 88 testes, cobertura do `core-sim` em 92%, validador sem erros.

## Como rodar e testar
| Comando | Faz |
|---|---|
| `gradlew run` | abre o jogo |
| `gradlew build` | compila, testa, confere cobertura e valida conteúdo e assets |
| `gradlew simulate --args="--dias 30 --semente 42"` | 30 dias sem janela: arcos, cenário, epílogo e crônica |
| `gradlew simulate --args="--semente 7 --resumo"` | só a situação final de cada arco |
| `gradlew simulate --args="--ritmo contemplativo"` | prazos dobrados |
| `gradlew generateArt` / `generateRefMap` / `importPortraits` | regeneram arte, mapa de referência e retratos |
| `gradlew distWin` | gera o `Etrava.exe` e o ZIP |

No IntelliJ: janela Gradle → `ETRAVA > Tasks > application > run` (ou `verification > simulate`, com os argumentos em "Arguments").

Controles: clique anda ou fala com um NPC · WASD anda · roda do mouse muda o zoom · J ou N abre o caderno · na conversa: 1 a 9 escolhe, Espaço continua, Esc vai embora · T espera 1 h · F5/F9 salva/carrega · F1 console.
Console (novos): `arcos`, `arco ID ESTAGIO`, `prop CHAVE ESTADO`, `status NPC SITUACAO`, `ato ID`, `epilogo`; `sabe NPC` agora mostra lembranças.

Roteiro para ver o M4 (uns 5 minutos):
1. `gradlew run`. Ande até o sudeste da praça (perto de `ir 31 41` no console): a árvore do quintal do moinho está seca.
2. Console: `avancar 40`. A árvore brotou. Fale com Nalia ("Viu? Tem folha.").
3. Console: `arco a1_nalia insonia`, depois `hora 23`: Nalia está acordada debaixo da árvore.
4. Console: `arco a1_nalia vozes`, `arco a1_nalia impasse`, `hora 10`. A curandeira está no quintal: pergunte o que a menina tem e se tem jeito. Depois convença dois outros (moleiro, ferreiro, taberneira) ou entre você mesmo falando com Nalia. No terceiro voluntário o arco termina.
5. Console: `epilogo` mostra o texto que sairia agora. Para outro final, recomece e, no impasse, diga "Eu ajudo." ao ferreiro: a árvore vira toco.
6. Sem fazer nada: `gradlew simulate --args="--semente 42"` e leia a crônica e o epílogo.

## Decisões que pedem aprovação
- **D-041 (narrativo):** como A1 se resolve sem o jogador, os dois caminhos exclusivos do jogador e o texto dos desfechos e do epílogo são proposta minha.
- **D-039:** o jogo começa com a árvore morta (ela brota por volta do dia 2).
- Os nomes: a curandeira segue sem nome próprio; o estudioso aparece como "Homem de fora".

## Problemas conhecidos / pendências
- **Retratos de Nalia e da curandeira não foram gerados** (aparecem como placeholder na conversa). São 2 imagens na Dreamina, cerca de 60 créditos; deixei fora por não ser trabalho do M4.
- Todo texto de diálogo, fato e epílogo é meu e precisa da leitura de tom do diretor.
- Furto, invasão e agressão ainda não existem como ações do jogador (M5/M6); o efeito `deed` e o console `ato` já funcionam. Confronto e expulsão ficam para o M6 (D-042).
- A fonte padrão do libGDX não tem travessão; evitei o caractere nas falas.
- A árvore viva encobre quem fica atrás dela (fica translúcida, como os outros objetos).
- Caderno só tem duas das cinco abas do PRD; sem escolhas por habilidade (M5).
- A checagem de "escolha relevante" só confere a declaração dos canais.
- NPCs atravessam uns aos outros; troca de área só pelo console; sem tela de salvar/carregar nem menu (logo, sem escolha de ritmo na interface).
- Capítulo I só termina pelo dia 30; a saída pela estrada entra no M7 (D-045).
- Conferir os termos de uso comercial da Dreamina antes de um release.
- AT-18 segue parcial: executável testado só nesta máquina.

## Próximo passo — M5 (Vida e economia), só com o "continue" do usuário
SPEC 4.8: itens e inventário, economia e comerciantes, trabalhos, habilidades. E2 (sal) já marca a flag `sal_subiu`; falta ligá-la ao preço (AT-09). Os gatilhos de furto usam o efeito `deed`.

Para quem retomar (qualquer modelo): leia este arquivo, depois `CLAUDE.md`, as seções do PRD do marco e a `docs/SPEC.md`. O vocabulário de condições e efeitos está em `CONTENT.md`; cada tipo novo precisa de leitura e escrita em `Rules` (os efeitos agendados vão para o save) e de um caso no `NarrativeValidator`.

## Paradas obrigatórias (lembrete)
- Fim de cada marco (M0…M8).
- Antes de decisão irreversível ou de alto impacto narrativo/visual.
