# PROGRESS — ETRAVA

Última atualização: 2026-11-11

## Estado atual
- **Marco concluído:** M5 — Vida e economia.
- **Status:** **PARADO no fim do M5**, aguardando o usuário mandar continuar para o M6 (Combate e perigo).
- Marcos concluídos: M0, M1, M2, M3, M4, M5.
- Aviso do usuário (2026-11-11): do M5 ao M7 ele usa o Opus; volta ao Fable no M8, para polir.

## Feito no M5

- **Mochila e corpo:** itens em dados (`content/items.yaml`, 29 itens), mochila por categoria com peso, 5 encaixes. Carga demais deixa o personagem lento. Tecla **I**.
- **Fome** (D-046): sobe de 0 a 100 em um dia; comer não gasta tempo; faminto rende e aprende pela metade. Aparece no HUD.
- **Habilidades** (`content/skills.yaml`): as seis do PRD, 0–100, ganho decrescente. Ofício e Vigor sobem trabalhando, Comércio negociando, Observação escutando conversas (e aumenta a distância de escuta), Lábia usando falas com rótulo [Lábia]. Tecla **C**.
- **Comércio** (`content/merchants.yaml`): taverna (vilarejo) e Posto. Preço = valor × modificador regional × opinião × Comércio; o comerciante tem caixa e repõe o estoque. Tela de duas colunas com o efeito da opinião. Relógio parado enquanto negocia.
- **AT-09:** E2 multiplica por 1,5 o preço de tudo com a marca `sal`; o comerciante do Posto comenta.
- **Trabalhos** (`content/jobs.yaml`): oficina (ferreiro), colheita (aldeã), carga no Posto. Oferecidos na conversa; o tempo salta, paga, treina e às vezes quem dá o trabalho conta uma novidade.
- **Contêineres com dono** (`content/containers.yaml`): sacos do moinho e cesto da horta. Pegar é roubo se alguém vir (ato `roubo`, D-042 agora tem gatilho de jogo).
- **Reputação:** NPCs têm comunidade (`faction`); reputação = média das opiniões. Má fama fecha a cozinha da taverna; quem detesta o jogador não oferece trabalho.
- **Origem** (`content/origins/pedra_mansa.yaml`): 8 moedas, pão, maçã, camisa e martelo, habilidades iniciais.
- **NPC novo:** comerciante do Posto (na área de teste até o M7). A taberneira serve do lado de fora à tarde.
- **Epílogo:** espaço novo "vida", com trecho sobre trabalho e dinheiro.
- **Política headless `trabalhador`** e `simulate --politica trabalhador`.
- Validador: itens, comerciantes, trabalhos, contêineres, origem, habilidades e as condições/efeitos novos. Save na versão 4.

## Testes de aceitação do marco
| Critério | Situação | Teste |
|---|---|---|
| AT-09 | passa | `at09_depoisDeE2OSalSobeNoPostoEAReposicaoVoltaOEstoqueAosLimites` |
| Viver 10 dias só trabalhando e comerciando | passa: semente 42 termina com 134 moedas, fome máxima 78 (nunca faminto), Ofício 20 → 39, Vigor 15 → 34 | `m5_dezDiasSoTrabalhandoEComerciando` |
| AT-05 com economia | passa (save no meio, mesmo hash depois) | `politicaTrabalhadorEDeterministicaESobreviveAoSave` |

`gradlew build`: 107 testes, cobertura do `core-sim` em 91%, validador sem erros.

## Como rodar e testar
| Comando | Faz |
|---|---|
| `gradlew run` | abre o jogo |
| `gradlew build` | compila, testa, confere cobertura e valida conteúdo e assets |
| `gradlew simulate --args="--dias 10 --semente 42 --politica trabalhador --resumo"` | 10 dias de um jogador que só trabalha e come |
| `gradlew simulate --args="--dias 30 --semente 42"` | 30 dias sem o jogador: arcos, cenário, epílogo e crônica |
| `gradlew generateArt` / `generateRefMap` / `importPortraits` | regeneram arte, mapa de referência e retratos |
| `gradlew distWin` | gera o `Etrava.exe` e o ZIP |

No IntelliJ: janela Gradle → `ETRAVA > Tasks > application > run` (ou `verification > simulate`, com os argumentos em "Arguments").

Controles: clique anda, fala com um NPC ou abre um contêiner · WASD anda · roda do mouse muda o zoom · **I** mochila · **C** habilidades · J ou N caderno · na conversa: 1 a 9 escolhe, Espaço continua, Esc vai embora · no comércio: clique compra/vende, Esc fecha · T espera 1 h · F5/F9 salva/carrega · F1 console.
Console (novos): `trabalhar ID`, `comercio ID`, `abrir CONTEINER`, `mochila`, `ficha`, `dar ITEM [N]`, `dinheiro N`, `fome N`, `vida` (dinheiro, habilidades, reputação), `precos`.

Roteiro para ver o M5 (uns 5 minutos):
1. `gradlew run`. No alto: "8 moedas". **I** abre a mochila: clique na maçã para comer. **C** mostra as habilidades.
2. Fale com o ferreiro (na frente da oficina, 6 h–11 h): "Precisa de mão na oficina?" → "Começo agora." Passam 4 horas e aparece quanto você ganhou. Ofício subiu (**C**).
3. Entre 9 h e 11h30, fale com a aldeã no feno: "Precisa de ajuda na colheita?". Você ganha moedas e legumes.
4. Depois das 15 h (console `hora 16`), a taberneira serve na frente da taverna: "O que tem no caldeirão?". Compre pão; venda legumes. Repare no caixa dela e no seu dinheiro.
5. Clique nos sacos de farinha, ao lado do feno, e pegue um. Se a aldeã ou o moleiro estiverem perto e acordados, aparece "Alguém viu." (console `sabe aldea` mostra a opinião e a lembrança). De madrugada ninguém vê.
6. Console `area area_teste`: o comerciante do Posto. Depois `arco e2_sal subiu` e fale com ele de novo: ele comenta o sal, e `precos` mostra o sal 50% mais caro.
7. Sem janela: `gradlew simulate --args="--dias 10 --politica trabalhador --resumo"`.

## Decisões que pedem aprovação
- **D-046 (design):** a fome como única necessidade, e o que ela faz (faminto rende metade; ninguém morre de fome).
- **D-049:** trabalho oferecido na conversa, uma vez por dia; o ferreiro faz as vezes de Avel na oficina até o M7.
- **D-050:** a taberneira passa a servir do lado de fora das 15 h às 23 h; o comerciante do Posto mora na área de teste até o M7.
- **Equilíbrio:** quem só trabalha junta ~13 moedas por dia (134 em 10 dias). Está generoso de propósito; dá para apertar em `jobs.yaml` e `items.yaml` sem mexer em código.
- Texto novo: falas do comerciante do Posto, ofertas de trabalho, balcão da taverna, descrições de itens e habilidades, trechos "vida" do epílogo — tudo proposta minha, para a leitura de tom do diretor.

## Problemas conhecidos / pendências
- **Retratos que faltam** (placeholder na conversa): Nalia, curandeira e, agora, o comerciante do Posto. São 3 imagens na Dreamina, cerca de 90 créditos.
- Itens aparecem só como texto: os ícones (`icone.<id>`) ainda não existem. Arma equipada não aparece no personagem (RF-203, MVP-S).
- Equipamento ainda não faz nada além de ocupar o encaixe e pesar (exceto a bolsa de cinto, que aumenta a carga). Dano, proteção e a luz da lamparina entram no M6.
- O Posto só se alcança pelo console (`area area_teste`); troca de área no mapa entra com a estrada (M7).
- Furto: o dono não descobre depois sozinho; só quem vê (D-051). Invasão e agressão chegam com o combate (M6).
- A fonte padrão do libGDX não tem travessão nem "×"; evitei os dois na interface.
- Do M4, ainda valem: NPCs atravessam uns aos outros; sem tela de salvar/carregar nem menu; Capítulo I só termina pelo dia 30; conferir os termos de uso comercial da Dreamina antes de um release; AT-18 testado só nesta máquina.

## Próximo passo — M6 (Combate e perigo), só com o "continue" do usuário
SPEC 4.8 (combate) e 6 (escuridão): combate em tempo real com vida e fôlego, 3 inimigos, fuga e rendição, derrota não letal e morte, a estrutura enterrada e o raio de luz (a lamparina já existe como item, com `stats: {luz: 6}`). Os `stats` de dano e proteção dos itens já estão nos dados. AT-13 e AT-14.

Para quem retomar (qualquer modelo): leia este arquivo, depois `CLAUDE.md`, as seções do PRD do marco e a `docs/SPEC.md`. O vocabulário de condições e efeitos está em `CONTENT.md`; cada tipo novo precisa de leitura e escrita em `Rules` (os efeitos agendados vão para o save), de um caso no `Story` (teste/aplicação) e de um caso no `NarrativeValidator`.

## Paradas obrigatórias (lembrete)
- Fim de cada marco (M0…M8).
- Antes de decisão irreversível ou de alto impacto narrativo/visual.
