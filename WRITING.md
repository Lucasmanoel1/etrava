# WRITING — guia de escrita do ETRAVA

Para quem escreve falas, fatos, documentos, crônicas e epílogo. Vale para gente e para agente de IA.
O formato dos arquivos está em `CONTENT.md`; aqui é o tom. O validador (`gradlew build`) cobra parte destas
regras; o resto é leitura humana — a escrita é o maior risco do projeto (PRD, R3).

## 1. O que o texto precisa fazer

O jogador não recebe missão. Ele ouve gente falando da própria vida e, se prestar atenção, percebe que
alguma coisa está acontecendo. Todo texto serve a isso: **dar o mundo, não dar a tarefa**.

- **Personagem fala de si, não do enredo.** O moleiro fala da mó e da filha; a curandeira, de erva e de
  quem não paga; o carregador, do peso.
- **A informação entra de lado.** O que importa aparece no meio de outra conversa, nunca anunciado.
- **Ninguém espera o jogador.** Nenhum NPC "tem um problema e precisa de alguém". Eles têm o problema e
  tocam a vida.
- **Gente mente, erra e omite** — por vaidade, medo, lealdade ou ignorância. Um fato pode ter versões
  diferentes em bocas diferentes (`versions` em `facts.yaml`).

## 2. Regras de tamanho e forma

- Fala de diálogo: **até 3 frases**. Se precisar de mais, é outro nó — ou é demais.
- Balão de conversa ambiente: **uma frase**, duas no máximo. Troca de 3 a 5 balões.
- Opção do jogador: curta, com voz. "Pedra não cansa." em vez de "Perguntar sobre a mó".
- Fato do caderno: uma frase no indicativo, do jeito que o personagem anotaria. **Nunca no imperativo**
  ("Vá…", "Fale com…", "Descubra…") — o teste `at20_cadernoSemInstrucoes` reprova.
- Crônica de arco: minúscula, sem ponto final, como entrada de diário do mundo.
- Epílogo: frases que se sustentam sozinhas; cada fragmento pode aparecer sem os vizinhos.

## 3. Voz

Português do Brasil, natural, sem gíria moderna e sem palavra de escritório. O registro muda com a pessoa:

| Quem | Como fala |
|---|---|
| Lavradores, carregadores | frase curta, concreta, coisa e peso; ironia seca |
| Taverna | provocação, apelido, conta de quem deve a quem |
| Avel | ofício e peça; desconversa com trabalho ("termina o eixo") |
| Comitiva de Caldris | período completo, termo de registro, cortesia que é ordem |
| Crianças | pergunta direta, lógica própria |
| Velhos | frase que parece fora do lugar e depois faz sentido |

Humor seco e humano, como na Cena 1 da lore. Exemplos que estão no jogo e dão a medida:

> — Quantos fardos hoje?
> — Dezoito.
> — Eu contei dezessete.
> — Eu contei o que eu carreguei.

> — Se é farinha, só amanhã. A mó está parada.
> — Parada, com trigo esperando?
> — Pedra também cansa.
> — Pedra não cansa.
> — Então sou eu.

Repare: ninguém explica nada. A segunda troca diz que o moleiro não dorme, sem dizer.

## 4. O que não se escreve

- **Nada de V2/V3 confirmado no Capítulo I.** Mir é "o acidente"; o que Avel fez é suspeita; os símbolos
  não têm tradução. Fato com `layer: V2` ou `V3` é barrado pelo validador.
- **Nada de explicação da magia, da Testemunha ou da estrutura.** Lacuna grande fica lacuna; registre a
  dúvida em `DECISIONS.md` em vez de inventar cânone.
- **Nada de anacronismo:** "ok", "tipo", "estresse", "foco", "processo", porcentagem em boca de lavrador.
- **Nada de adjetivo no lugar do fato.** "Estranho", "misterioso", "sombrio" não informam; diga o que se vê.
- **Nada de protagonista escolhido.** Ninguém acha o aprendiz especial. No máximo, útil.
- **Nada de fala que resume a cena** ("Então você quer dizer que…").

## 5. Estrutura que o texto precisa respeitar

- **Todo arco anda sozinho.** Escreva primeiro o desfecho sem o jogador (a crônica dele tem de ser boa de
  ler); depois os desvios. Pelo menos um desfecho possível não é trágico.
- **Fato de ramificação tem duas fontes** (`branching: true`): duas pessoas, ou uma pessoa e um papel, ou
  uma conversa ouvida. O jogador distraído ainda tem uma segunda chance.
- **Escolha relevante muda dois canais** (`relevant: true` + `channels`): fala de alguém, opinião, cenário,
  caderno, preço. Se só muda um número escondido, não é relevante.
- **Tom do conjunto (AT-21):** das histórias do capítulo, ao menos duas terminam leves sozinhas e no máximo
  metade termina mal. O mundo é duro, não é castigo.
- **Nomes novos** vão para `content/glossario.yaml`, marcados como provisórios.

## 6. Antes de entregar um texto

1. Leia em voz alta. Se soa a narrador, reescreva como gente.
2. Corte a primeira frase. Quase sempre a fala começa melhor na segunda.
3. Procure a informação "de frente" e vire-a de lado.
4. Confira quem sabe o quê: o NPC só diz o que conhece (`knows`), na versão que conhece.
5. Rode `gradlew build` (validador) e `gradlew simulate --args="--dias 30 --semente 42"`: a crônica e o
   epílogo têm de continuar fazendo sentido sem o jogador.

Referência de tom: as três cenas da seção 11 de `docs/lore-etrava.md`.
