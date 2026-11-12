# Testes manuais do MVP — roteiro

Os testes automáticos rodam em `gradlew build`. Este roteiro cobre o que só uma pessoa jogando decide
(PRD 16, AT-10 a AT-21) e a sessão de 3 horas da definição de pronto. Marque cada item; o que falhar vira
linha em "Problemas conhecidos" do `PROGRESS.md`.

**Como rodar:** `gradlew run` (desenvolvimento, com console no F1) ou `Etrava.exe` da pasta
`game-client/build/package/out/Etrava` (release, sem console). No IntelliJ: janela Gradle →
`ETRAVA > Tasks > application > run`.

**Console (só em desenvolvimento):** `ajuda` lista tudo. Os mais úteis: `arcos`, `arco ID ESTAGIO`,
`hora H`, `avancar HORAS`, `dar ITEM`, `dinheiro N`, `area ID`, `tp X Y`, `encontro ID`, `epilogo`, `vel N`.

## Apoio automático (já feito pelo build ou por um comando)

| O quê | Comando | O que confere |
|---|---|---|
| AT-01 a AT-09, AT-19, AT-20 (parte), AT-21 | `gradlew build` | 129 testes, cobertura ≥ 70 % no núcleo, validador |
| AT-13, AT-14 (regra) | `gradlew build` | `CombatTest`, `StoryTest` |
| AT-16 | `gradlew at16` (`-Psemente=N` troca os arquivos sorteados) | apaga 10 assets de uma cópia, joga 60 s sozinho, exige saída limpa e avisos no log |
| AT-17 (nesta máquina) | `gradlew run --args="--area=vila --cmd=\"hora 18\" --cmd=\"arco h5_festa festa\" --cmd=\"hora 20.5\" --cmd=\"tp 31 33\" --bench=15"` | grava no log média, 1 % pior, pior quadro e memória |
| Resistência | `Etrava.exe --hidden --bot=10800 --seed=101` | um jogador automático aperta de tudo por 3 h; o log termina com `BOT terminou` |
| Caos no núcleo | `gradlew build` (`ChaosTest`) | comandos ao acaso por 31 dias, 6 sementes, com save e load no meio |

Para o jogador automático não mexer nos seus saves, defina antes a variável de ambiente
`ETRAVA_USERDIR` com uma pasta vazia.

## AT-10 — Quem só trabalha vê o mundo mudar
1. Novo jogo, ritmo Normal. Por 30 dias: trabalhe na oficina de manhã, coma, durma. Não pergunte nada a ninguém.
2. [ ] Por volta do dia 2–3 a árvore do moinho aparece viva; no fim do arco, cortada, morta ou viva.
3. [ ] Por volta do dia 9 a tenda da comitiva está no largo; no dia seguinte há estacas na divisa.
4. [ ] Avel some da oficina ou é visto com o homem de fora (dias 19–22).
5. [ ] Há uma festa na praça (dia ~11) e o buraco no campo de Bento (dia ~11–13).
6. [ ] No dia 31 o epílogo aparece e fala do que aconteceu sem você. Nada nele supõe algo que você não fez.

## AT-11 — Três desfechos diferentes do A1 em três partidas
1. [ ] Partida 1: não faça nada → o pai corta a árvore, ou Nalia piora, ou o estudioso aparece.
2. [ ] Partida 2: fale com a aldeã, a curandeira e o moleiro; descubra que o custo pode ser repartido; no
   impasse, convença três voluntários → "três voluntários repartiram o que a árvore tirava de Nalia".
3. [ ] Partida 3: apoie o corte com o ferreiro, ou convença Nalia a soltar a ligação → outro desfecho.

## AT-12 — Escolha relevante muda dois canais
Para cada escolha marcada `relevant: true` em `content/dialogues` (liste com
`grep -rn "relevant: true" content/dialogues`):
- [ ] depois de escolher, pelo menos dois destes mudam de forma que se percebe: fala de um NPC, opinião
  (preço no comércio, cumprimento), cenário, caderno, crônica/epílogo.

## AT-13 — Nenhum arco exige vencer luta
- [ ] A1 pelo caminho dos voluntários, sem lutar.
- [ ] A2 pelo caminho da confissão (baú de Avel, papéis, opinião ≥ 40, perguntar na fase da comitiva).
- [ ] A3 descendo com lamparina, olhando e voltando; o vulto recua da luz. Mostre o que achou a alguém.

## AT-14 — Derrota e morte
1. [ ] Na estrada, à tarde (`encontro saqueadores_estrada` no console, se não aparecerem), perca ou
   renda-se: acorda horas depois, sem parte do dinheiro e dos itens, com o relógio adiantado.
2. [ ] Na estrutura, sem luz, deixe o vulto vencer: tela de morte, com a lista de saves para carregar.

## AT-15 — Portão visual (decisão do diretor)
Compare as capturas de `docs/capturas/m8/` (vila de dia, vila à noite, taverna, estrutura, Posto; mais
menu, diálogo e mochila) com o `STYLE.md`.
- [ ] Aprovado / [ ] Reprovado — o que trocar: ______

## AT-17 — Desempenho no hardware de referência e no mínimo
Rode o comando de medição da tabela em cada máquina, em tela cheia na resolução nativa.
- [ ] Referência (GTX 1050 / RX 560): média ≥ 60 FPS com luz e pós ligados.
- [ ] Mínimo (Intel Iris Xe): ≥ 30 FPS com `--cmd="luz off" --cmd="pos-fx off"`.
- Medido em 12/11/2026 numa RX 6650 XT, 1280×720: 503 FPS de média, 286 no 1 % pior (luz e pós ligados).
  **Não há medida no hardware de referência nem no mínimo.**

## AT-18 — Máquina limpa
1. Copie `game-client/build/package/Etrava-0.1.0-win.zip` para um Windows 10/11 sem Java.
2. [ ] Extraia, abra `Etrava.exe`: o menu aparece em até 15 s, com som.
3. [ ] Novo jogo, ande, salve, feche, abra, carregue.
- Conferido em 12/11/2026 nesta máquina com o `PATH` sem Java e sem `JAVA_HOME` (o executável usa o Java
  embutido). **Falta uma máquina de fato limpa.**

## AT-20 — Nada de marcador nem de ordem
- [ ] Percorra todas as telas (HUD, caderno nas cinco abas, mapa, mochila, ficha, comércio, diálogo, pausa):
  nenhuma seta, exclamação, ponto no mapa que diga "vá aqui", nem frase no imperativo no caderno.

## Sessão de 3 horas (definição de pronto)
Uma pessoa joga 3 horas seguidas no executável de release, do jeito que quiser. Anote:
- [ ] Nenhuma queda (se cair, guarde `%APPDATA%\Etrava\logs\crash-*.txt`).
- [ ] Nenhum travamento de conversa, comércio ou tela.
- [ ] Salvar e carregar em pelo menos 5 momentos diferentes, inclusive depois de dormir e de trocar de área.
- [ ] O som: a música e os ambientes servem de base? (O áudio é sintetizado e **nunca foi ouvido por quem
  o gerou** — ver D-071.)
- [ ] Texto: tamanho "Maior" legível em todas as telas, sem corte.
- [ ] Ritmo: houve algum trecho de mais de dois dias de jogo sem nada para ver ou ouvir?
- [ ] Três coisas que mais incomodaram, em ordem.

## Fase V — V0 (zoom, rolagem, depuração, visibilidade)
Automáticos: `gradlew vt01` (zoom depois de fechar painéis), `gradlew vt03` (visibilidade), `gradlew build`
(`UiStageTest`, `DebugModeTest`, `GameUiScrollTest`). À mão, pelo IntelliJ (`run`):
1. [ ] Abra e feche, em sequência, mochila (I), caderno (J), ficha, mapa, uma conversa e o comércio do Posto;
   depois de cada um, gire a roda fora de qualquer painel: o zoom muda **sempre**.
2. [ ] Com a mochila aberta, gire a roda em cima da lista: a lista rola e o zoom não muda; com o mouse fora
   dela, o zoom muda.
3. [ ] No comércio do Posto, com muitos itens (console F1: `dar pao 1` várias vezes, ou `dar` de vários itens),
   as duas colunas mostram a barra e rolam pela roda, pela barra e por PgUp/PgDn/Home/End.
4. [ ] Numa conversa com muitas perguntas marcadas no caderno, as opções rolam e a caixa não sai da tela.
5. [ ] Sem F3, nada de FPS, posição, semente ou velocidade na tela. F3 mostra; F3 de novo esconde.
   No `Etrava.exe` (release), F1 e F3 não fazem nada; com `Etrava.exe --debug`, fazem.
6. [ ] Vila às 6h, 19h30 e meia-noite (console: `hora 6`, `hora 19.5`, `hora 0`), zoom 1 e 4: o jogador e os
   NPCs se leem sem esforço; a noite continua com cara de noite. `presenca off` mostra como era.
