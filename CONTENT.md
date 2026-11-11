# CONTENT — como escrever conteúdo para o ETRAVA

Tudo o que é história mora em `content/`, em YAML. Adicionar NPC, fato, diálogo ou conversa não exige código. Depois de editar, rode `gradlew validateContent`: ele aponta arquivo e item de cada problema.

Regras que o validador cobra (PRD 20.3):
- Todo ID citado precisa existir.
- Fato que abre um caminho de diálogo precisa de **duas fontes** ao alcance do jogador.
- Nada de camada **V2/V3** chega ao jogador no Capítulo I; **V1** só como pista (nunca `verified`).
- Nenhum nó de diálogo inalcançável.
- Escolha marcada `relevant` declara pelo menos 2 canais.
- Agenda de NPC cobre as 24 horas.

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

## Condições (`when`)

| Condição | Exemplo |
|---|---|
| flag | `{flag: festa}` · `{flag: {name: festa, value: false}}` |
| variável | `{var: {name: voluntarios, min: 3}}` |
| alguém sabe | `{knows: {who: player, fact: arvore_voltou}}` (`who`: `player`, `npc` = o da conversa, ou um ID) |
| opinião | `{opinion: {npc: moleiro, min: 10}}` |
| tempo | `{time: {dayMin: 2, hourFrom: 18, hourTo: 20}}` |
| NPC numa área | `{npc: {id: viajante, area: vila_ref}}` |
| sorte | `{chance: 0.3}` |
| combinar | `{all: [...]}` · `{any: [...]}` · `{not: {...}}` |

## Efeitos (`effects`)

| Efeito | Exemplo |
|---|---|
| flag / variável | `{setFlag: festa}` · `{setVar: {name: x, value: 2}}` · `{addVar: {name: x, value: 1}}` |
| ensinar um fato | `{learn: {who: player, fact: arvore_voltou, version: exagero, certainty: claim}}` |
| opinião | `{opinion: {npc: npc, add: -5}}` |
| crônica | `{chronicle: {type: nota, text: "…"}}` |
| mais tarde | `{schedule: {hours: 48, effects: [...]}}` |

Graus de certeza: `rumor` (ouvi dizer), `claim` (me afirmaram), `witnessed` (vi), `read` (li), `verified` (conferi).

## Outros arquivos

- `content/textos.yaml` — falas genéricas da simulação. `content/i18n/ui_pt_BR.properties` — textos da interface.
- `content/glossario.yaml` — todo nome próprio novo entra aqui, como provisório.
- `content/props.yaml` e `content/areas/` — objetos e mapas (ver `STYLE.md` e SPEC 4.7).
