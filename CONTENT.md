# CONTENT — como escrever conteúdo para o ETRAVA

Tudo o que é história mora em `content/`, em YAML. Adicionar NPC, fato, diálogo ou conversa não exige código. Depois de editar, rode `gradlew validateContent`: ele aponta arquivo e item de cada problema.

Regras que o validador cobra (PRD 20.3):
- Todo ID citado precisa existir.
- Fato que abre um caminho de diálogo precisa de **duas fontes** ao alcance do jogador.
- Nada de camada **V2/V3** chega ao jogador no Capítulo I; **V1** só como pista (nunca `verified`).
- Nenhum nó de diálogo inalcançável.
- Escolha marcada `relevant` declara pelo menos 2 canais.
- Agenda de NPC cobre as 24 horas (contando só os blocos sem condição).
- Todo estágio de arco que não é desfecho tem prazo, com uma saída sem condição; todo arco tem um desfecho não trágico.
- Todo arco maior (`major: true`) tem um espaço no epílogo com fragmento sem condição.

Tom: ver PRD 20.4 — falas curtas, subtexto, gente que fala de si e não do enredo.

---

## Fatos — `content/facts.yaml`

```yaml
- id: arvore_voltou
  layer: V0            # V0 superfície · V1 suspeita · V2/V3 proibidos no Capítulo I
  truth: true          # oculto do jogador: true | false | partial | unknown
  spread: local        # vira rumor entre NPCs? none | local | wide
  tags: [arvore, moinho]
  versions:            # a primeira é a padrão
    - {id: base, text: "A árvore velha do moinho brotou de novo."}
    - {id: exagero, text: "A filha do moleiro encostou na árvore morta e ela voltou."}
```

O texto vai para o caderno depois de "Fulano disse:" — escreva uma **afirmação**, nunca uma instrução ("Vá até…").

## NPCs — `content/npcs/<id>.yaml`

```yaml
id: moleiro
name: Moleiro
layer: B                       # A personagem · B morador · C figurante
sprite: personagem.moleiro     # folha de sprites
dialogue: moleiro              # opcional; sem isso o NPC só resmunga
opinion: 10                    # opinião inicial sobre o jogador (-100 a 100)
knows: [arvore_voltou, "sal_subiu:exagero"]   # o que já sabe: fato ou fato:versão
schedule:                      # precisa cobrir o dia inteiro
  - {from: "21:00", to: "05:00", do: dormir,    at: vila_ref/moinho_porta, hidden: true}
  - {from: "05:00", to: "11:00", do: trabalhar, at: vila_ref/moinho_porta}
```

`at` é `área/ponto` (pontos são os objetos da camada `spots` do mapa). `hidden: true` = dentro de uma construção: o NPC vai até a porta e some. O retrato é o asset `retrato.<id>`.

Campos opcionais:

```yaml
status: away                               # present (padrão) | away | departed | dead
relations: [{npc: moleiro, kind: familia}] # familia | amizade | rivalidade — muda a quem conta rumores
schedule:
  # bloco com `when` vem ANTES e sobrepõe a rotina enquanto a condição valer
  - {from: "21:00", to: "05:00", do: vigiar, at: vila_ref/quintal_moinho,
     when: {arc: {id: a1_nalia, stage: [insonia, vozes, impasse]}}}
  - {from: "21:00", to: "06:00", do: dormir, at: vila_ref/moinho_porta, hidden: true}
```

## Diálogos — `content/dialogues/<id>.yaml`

```yaml
id: moleiro
start:                                   # vale a primeira entrada cuja condição passa
  - {when: {flag: moleiro_fechou}, goto: seco}
  - {goto: inicio}
nodes:
  inicio:
    text: "Se é farinha, só amanhã. A mó está parada."
    effects: [{learn: {who: player, fact: mo_parada, certainty: claim}}]
    choices:
      - {text: "Parada, com trigo esperando?", goto: mo}
      - text: "Ouvi falar da árvore."
        when: {knows: {who: player, fact: arvore_voltou}}   # só aparece para quem sabe
        goto: arvore
      - {text: "Volto amanhã, então."}                      # sem goto: encerra
  mo:
    text: "Pedra também cansa."
    goto: outro_no                       # sem escolhas: segue quando o jogador avança
    onLeave: [{opinion: {npc: npc, add: -4}}]   # se o jogador virar as costas aqui
```

Campos de uma escolha: `text`, `when`, `tag` (rótulo entre colchetes, ex.: `Lábia`), `effects`, `goto`, `relevant` + `channels`.

**Nó de novidades** — o NPC conta o fato mais recente que ele sabe e o jogador não (é assim que as notícias de fora chegam):

```yaml
  novidades:
    gossip: nada                 # para onde ir quando ele não tem nada de novo
    text: "Na estrada, dizem:"   # introdução; o texto do fato vem em seguida
    choices:
      - {text: "Mais alguma coisa?", goto: novidades}
  nada:
    text: "Nada que valha a viagem."
```

## Conversas ambientes — `content/ambient/*.yaml`

NPCs falando entre si, sem pausar o jogo. Só tocam com os participantes juntos e o jogador a até 9 m.

```yaml
- id: arvore_na_praca
  participants: [aldea, taberneira]
  when: {time: {hourFrom: 14, hourTo: 15}}
  cooldownHours: 0                 # 0 = toca uma vez só
  lines:
    - {speaker: aldea, text: "Passou no moinho hoje?"}
    - {speaker: taberneira, text: "Passei longe. Por quê?"}
  facts: [arvore_voltou]           # quem ouvir até o fim anota como rumor
```

## Arcos de mundo — `content/arcs/*.yaml`

Uma história que anda sozinha. Um arquivo pode ter um arco ou uma lista deles.

```yaml
id: a1_nalia
name: Nalia e a árvore
major: true                      # arco maior: precisa de espaço garantido no epílogo
start: antes
stages:
  antes:
    chronicle: a árvore está morta há anos        # o que a crônica registra
    timeout: {after: {days: 1, jitterHours: 12}, to: restauracao}
  restauracao:
    onEnter:                                       # efeitos ao chegar (há também onExit)
      - {setProp: {key: arvore_nalia, state: viva}}
      - {learn: {who: moleiro, fact: arvore_voltou, certainty: witnessed}}
    timeout: {after: {days: 2}, to: impasse}
  impasse:
    transitions:                                   # alguém mudou o mundo: vale a primeira que passar
      - {when: {var: {name: voluntarios, min: 3}}, to: custo_dividido}
    timeout:                                       # ninguém fez nada
      after: {days: 7, jitterHours: 24}
      choose:
        - {when: {flag: estudioso_na_regiao}, to: estabilizada}   # com condição e sem peso: prioridade
        - {weight: 2, to: pai_corta_arvore}                        # o resto vai a sorteio
        - {weight: 1, to: nalia_piora}
  custo_dividido: {terminal: true, outcome: leve}
  nalia_piora:    {terminal: true, outcome: tragico}
```

- `outcome`: `leve`, `ambiguo`, `tragico` (ou `neutro`, para notícia de fora).
- Não use `chance` em `transitions` (seria sorteada a cada hora); sorteio é no `choose`.
- Evento externo é um arco curto que só deixa um fato com quem viaja (`arcs/mundo.yaml`).
- Para testar: no console, `arcos` lista tudo; `arco a1_nalia impasse` pula para um estágio.

## Cenário que muda — `props.yaml` + mapa

```yaml
- id: arvore_velha
  footprint: [0.8, 0.8]
  blocks: true
  states: {morta: prop.arvore_morta, viva: prop.arvore, cortada: prop.toco}   # o primeiro é o inicial
```

No Tiled, o objeto ganha a propriedade `key` (ex.: `arvore_nalia`). Muda com `{setProp: {key: arvore_nalia, state: viva}}`.

## Epílogo — `content/epilogues/cap1.yaml`

```yaml
endDay: 30
slots: [abertura, {id: a1_nalia, arc: a1_nalia}, fecho]   # a ordem do texto
fragments:
  - slot: a1_nalia
    priority: 10
    when: {arc: {id: a1_nalia, stage: custo_dividido}}
    text: "Nalia dorme. …"
  - slot: a1_nalia                # sem `when`: garante texto mesmo sem o jogador
    text: "No moinho, a história ainda não acabou."
```

Em cada espaço entra um fragmento: o de maior `priority` cuja condição passa. No console, `epilogo` mostra como sairia agora.

## Atos do jogador — `content/deeds.yaml`

```yaml
- {id: roubo, name: Roubo, fact: jogador_roubou, witness: -30, hearsay: -12}
```

Disparado por `{deed: roubo}`. Quem viu perde `witness` de opinião e passa a contar; quem ouve o rumor perde `hearsay`. Quem não soube não muda.

## Itens — `content/items.yaml`

```yaml
- id: pao
  name: Pão de centeio
  description: "Duro por fora, quase duro por dentro."   # aparece na mochila
  category: consumivel     # arma | armadura | ferramenta | consumivel | material | mercadoria | documento | especial
  tags: [sal]              # marcas livres: eventos mexem no preço por elas (E2 sobe tudo com `sal`)
  weight: 0.4              # kg
  value: 2                 # preço-base, antes de opinião, Comércio e eventos
  food: 30                 # quanto a fome cai ao comer
  slot: mainHand           # mainHand | offHand | body | head | accessory — só para o que se veste ou empunha
  use: [{learn: {who: player, fact: x, certainty: read}}]   # efeitos ao usar (opcional)
  stats: {dano: 3, protecao: 1, luz: 6, carga: 6}          # números do M6; `carga` já aumenta a capacidade
```

## Comerciantes — `content/merchants.yaml`

```yaml
- id: taverna
  npc: taberneira          # quem atende: só negocia acordado, à vista e na área do jogador
  cash: 40                 # caixa; volta a esse valor na reposição
  restockDays: 1
  when: {reputation: {faction: pedra_mansa, min: -25}}   # quem tem má fama não compra
  stock:
    - {item: pao, min: 3, max: 10}   # na reposição, fora de [min, max] volta ao máximo
  buys: [horta, moinho]    # categorias ou marcas que ele compra do jogador
```

Abre-se o comércio pela conversa: uma escolha com `effects: [{startTrade: taverna}]` (sem `goto`: a conversa fecha e o balcão abre).

## Trabalhos — `content/jobs.yaml`

```yaml
- id: oficina
  name: Serviço na oficina
  giver: ferreiro          # quem dá: presente, acordado e na área do jogador
  at: vila_ref/oficina_fora
  from: "06:00"            # janela em que dá para COMEÇAR
  to: "11:00"
  hours: 4
  pay: 9
  paySkill: oficio         # até +50% com 100
  train: {oficio: 0.8, vigor: 0.2}   # ganho-base por hora
  when: {opinion: {npc: npc, min: -20}}   # "npc" = quem dá o trabalho
  onFinish: [{opinion: {npc: ferreiro, add: 1}}]
  gossip: 0.5              # chance de ele contar uma novidade durante o trabalho
```

Na conversa: escolha com `when: {job: oficina}` (só aparece se o trabalho pode começar agora) e `effects: [{startJob: oficina}]`.

## Contêineres — `content/containers.yaml`

```yaml
- id: sacos_moinho
  name: Sacos de farinha
  at: vila_ref/feno        # ponto do mapa; o jogador clica perto para abrir
  owner: moleiro           # pegar do que tem dono é roubo — se alguém vir
  restockDays: 2
  items: [{item: farinha, qty: 3}]
```

## Habilidades e origem

- `content/skills.yaml`: as seis habilidades, com nome, descrição e a `curve` do ganho decrescente.
- `content/origins/pedra_mansa.yaml`: dinheiro, itens, equipamento e habilidades com que o personagem começa.
- Uma escolha de diálogo com condição `skill` mostra o rótulo (`tag: Lábia`) e treina a habilidade quando escolhida.
- NPC ganha `faction: pedra_mansa` para contar na reputação da comunidade.

## Condições (`when`)

| Condição | Exemplo |
|---|---|
| flag | `{flag: festa}` · `{flag: {name: festa, value: false}}` |
| variável | `{var: {name: voluntarios, min: 3}}` |
| alguém sabe | `{knows: {who: player, fact: arvore_voltou}}` (`who`: `player`, `npc` = o da conversa, ou um ID) |
| opinião | `{opinion: {npc: moleiro, min: 10}}` |
| tempo | `{time: {dayMin: 2, hourFrom: 18, hourTo: 20}}` |
| NPC numa área | `{npc: {id: viajante, area: vila_ref}}` |
| situação de NPC | `{npc: {id: estudioso, status: departed}}` |
| arco | `{arc: {id: a1_nalia, stage: impasse}}` · `stage: [insonia, vozes]` · `{arc: {id: a1_nalia, outcome: tragico}}` |
| cenário | `{prop: {key: arvore_nalia, state: viva}}` |
| lembrança | `{remembers: {npc: nalia, type: ajuda, subject: player}}` |
| sorte | `{chance: 0.3}` |
| jogador tem | `{has: {item: pao, qty: 2}}` · `{has: {money: 20}}` |
| habilidade | `{skill: {name: labia, min: 30}}` |
| reputação | `{reputation: {faction: pedra_mansa, min: -25}}` |
| trabalho disponível agora | `{job: oficina}` |
| combinar | `{all: [...]}` · `{any: [...]}` · `{not: {...}}` |

## Efeitos (`effects`)

| Efeito | Exemplo |
|---|---|
| flag / variável | `{setFlag: festa}` · `{setVar: {name: x, value: 2}}` · `{addVar: {name: x, value: 1}}` |
| ensinar um fato | `{learn: {who: player, fact: arvore_voltou, version: exagero, certainty: claim}}` |
| opinião | `{opinion: {npc: npc, add: -5}}` |
| crônica | `{chronicle: {type: nota, text: "…"}}` |
| mais tarde | `{schedule: {hours: 48, effects: [...]}}` |
| cenário | `{setProp: {key: arvore_nalia, state: cortada}}` |
| arco | `{arc: {id: a1_nalia, to: impasse}}` |
| situação de NPC | `{npcStatus: {id: estudioso, status: present}}` |
| lembrança | `{remember: {npc: npc, type: ajuda, subject: player}}` |
| ato | `{deed: roubo}` |
| dar / tirar | `{give: {item: pao, qty: 2}}` · `{give: {money: 5}}` · `{take: {item: pao}}` |
| preço | `{priceMod: {tag: sal, factor: 1.5}}` (multiplica o atual) |
| treinar | `{train: {skill: vigor, amount: 2}}` |
| trabalho / comércio | `{startJob: oficina}` · `{startTrade: taverna}` (começam quando a conversa acabar) |

Graus de certeza: `rumor` (ouvi dizer), `claim` (me afirmaram), `witnessed` (vi), `read` (li), `verified` (conferi).

## Outros arquivos

- `content/textos.yaml` — falas genéricas da simulação. `content/i18n/ui_pt_BR.properties` — textos da interface.
- `content/glossario.yaml` — todo nome próprio novo entra aqui, como provisório.
- `content/props.yaml` e `content/areas/` — objetos e mapas (ver `STYLE.md` e SPEC 4.7).
