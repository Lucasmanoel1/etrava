# PROGRESS — ETRAVA

Última atualização: 2026-11-12

## Estado atual
- **Marco concluído:** M7 — Conteúdo completo.
- **Status:** **PARADO no fim do M7**, aguardando o usuário mandar continuar para o M8 (Polimento e release).
- Marcos concluídos: M0, M1, M2, M3, M4, M5, M6, M7.
- Aviso do usuário (2026-11-11): do M5 ao M7 ele usa o Opus; volta ao Fable no M8, para polir.

## Feito no M7

**Região (D-067, D-068).** Pedra-Mansa inteira: 15 construções (oficina de Avel, taverna, forja de Doran, moinho com a árvore de Nalia, casa da curandeira, casas, celeiro), praça com poço, cemitério cercado, pomar de Celi, horta, as lavouras de Bento e Gil com a divisa e os marcos. A estrada para leste, pelo bosque ralo, com a curva de má fama, até o **Posto da Encruzilhada** (casa, bancas, carroça, encruzilhada). Quatro **interiores** em corte, com luz própria: oficina (onde o jogo começa), taverna, moinho e casa da curandeira. Tudo gerado por `gradlew generateRegion` (abre no Tiled). Arte nova por código: assoalho, lajota, lavoura, paredes em corte, mesas, bancos, camas, balcão, lareira, bancada, baú, sacos, mó, ervas, forja, bigorna, cercas, lápides, estacas, marco, tenda, carroça, banca, espantalho, árvore de fruta, cinco casas novas e 14 personagens.

**Elenco.** 22 NPCs nomeados (8 de camada A) + viajante e estudioso. Retrato de todos pela Dreamina (17 novos), na paleta do jogo. Agendas que passam pelas portas; festa, arcos e estágios mudam a rotina.

**Histórias (todas andam sozinhas; o jogador pode mudar o fim):**
| | O que é | Desfechos | Sem o jogador |
|---|---|---|---|
| A1 | Nalia e a árvore (M4) | 5 | pai corta / Nalia piora / estabilizada |
| **A2** | O homem de fora procura alguém de Mir; a comitiva de Caldris mede as terras; Avel recebe uma carta | confessa · encontrado · partiu · averiguação | encontrado ou partiu |
| **A3** | A terra cede no campo de Bento durante a vala; Avel diz que não reconhece as marcas | tapado · registrado · mostrado | tapado ou registrado |
| H1 | O ladrão de frutas: é Pipo, alimentando um cachorro manco | Celi descobre · contado · segredo guardado | **leve** |
| H2 | A divisa: as estacas de Caldris pioram; o livro de enterros de Firmino resolve | acordo · Firmino resolve · piorou | piorou ou Firmino |
| H3 | Os apostadores (Cena 1 da lore); jogo de dados de verdade; o dado de Lauro tem chumbo | as pazes · Lauro barrado | **leve** |
| H4 | Trabalho no Posto (carga e vigia); saqueadores atacam o Posto numa noite | defendido · roubado | roubado |
| H5 | A festa da colheita; Anselmo diz que o trem vai passar por cima dos mortos — e dias depois as estacas cortam o cemitério | acabou bem | **leve** |
| E1–E5 | Já do M4; agora com conversas no Posto | | |

**Quantidades (AT-19):** 89 fatos, 48 conversas ambientes, 43 itens (10 documentos legíveis), 6 trabalhos, 2 comerciantes, 3 inimigos, 60 fragmentos de epílogo, 8 músicas, 16 arcos.

**Som (D-071).** Música por contexto (vila de dia/noite, casa, taverna, estrada, ruína, combate, menu, festa), ambiente por área e período, passos em 3 superfícies, combate, portas, interface, fenômenos (a árvore, o eco). Cinco canais de volume.

**Telas.** Menu principal com ilustração; novo jogo (origem, nome, ritmo); carregar e salvar com miniaturas (3 manuais + rápido + 3 automáticos); opções (volumes, tela cheia, V-Sync, FPS, pós-processamento, luz, tamanho do texto, tempo dos balões); pausa no Esc. **Mapa ilustrado** (M) com os lugares por onde se passou. **Caderno** com as cinco abas do PRD: Ouvi dizer, Vi e li, Pessoas, Lugares, Documentos. Documentos abrem para ler. Epílogo com painéis ilustrados. Portas destacadas sob o cursor.

**Núcleo.** Saídas com condição; lugares visitados; origem com local, conhecimento, opiniões e flags; nó de desvio nos diálogos (sorte, estado); condição `{here}`; contêineres com condição; documentos que não se gastam; NPCs atravessam portas; save v6 com migração da v5 (D-070).

## Testes de aceitação do marco
| Critério | Situação | Teste |
|---|---|---|
| AT-19 | passa: todas as quantidades da tabela 13.2 (NPCs, A, conversas, fatos, documentos, itens por categoria, comerciantes, trabalhos, inimigos, fragmentos, músicas, interiores, retrato por NPC) | `ContentM7Test.at19_quantidadesDoMvp`, `AssetsAndActorsTest.conteudoEAssetsDoRepositorioSaoValidos` |
| AT-20 | passa no automático (nenhum fato começa com verbo no imperativo; o caderno não tem objetivos). Conferência visual das telas: nenhum marcador | `ContentM7Test.at20_cadernoSemInstrucoes` + roteiro abaixo |
| AT-21 | passa: em 8 sementes sem o jogador, ≥ 2 histórias leves (H1, H3, H5, às vezes H2) e no máximo metade trágica | `ContentM7Test.at21_tomDosDesfechosPadrao` |

`gradlew build`: 125 testes passando, validador sem erros. `simulate --dias 30 --semente 42`: todos os arcos terminam e o epílogo cobre A1–A3, H1–H5 e E1–E5. `simulate --politica trabalhador --dias 10`: 144 moedas.

## Como rodar e testar
| Comando | Faz |
|---|---|
| `gradlew run` | abre o jogo **no menu principal** |
| `gradlew run --args="--area=vila"` | pula o menu (desenvolvimento) |
| `gradlew build` | compila, testa, confere cobertura e valida conteúdo e assets |
| `gradlew simulate --args="--dias 30 --semente 42"` | 30 dias sem o jogador: arcos, epílogo e crônica |
| `gradlew generateRegion` / `generateArt` / `generateAudio` / `generateEstrutura` | regeneram mapas, arte e som |
| `gradlew importPortraits` / `importIllustrations` | convertem a arte da Dreamina (pasta `arte-bruta`) |
| `gradlew distWin` | gera o `Etrava.exe` e o ZIP |

No IntelliJ: janela Gradle → `ETRAVA > Tasks > application > run`.

Controles novos: **M** mapa · **Esc** pausa (salvar, carregar, opções) · setas trocam a aba do caderno. Os de antes continuam (I mochila, C ficha, J caderno, T esperar, F5/F9, F1 console).

Roteiro para ver o M7 (uns 20 minutos):
1. `gradlew run`. Menu → Novo jogo → nome e ritmo → Começar. Você acorda na oficina; Avel está na bancada. Fale com ele ("Termino o eixo agora") e repare na música e no fogo da lareira.
2. Passe o mouse na porta (fundo da oficina): "Ir para: Pedra-Mansa". Saia. **M** mostra o mapa; **J** e as setas mostram as abas do caderno (Lugares já tem Pedra-Mansa).
3. Ande pela vila: taverna (entre à noite: Lauro e Ciro jogam dados; sente para jogar com Lauro), moinho, casa da curandeira, cemitério, pomar, lavouras. Conversas ambientes aparecem em balões.
4. Pelo console (F1), para acelerar as histórias: `arcos` lista tudo; `arco a2_avel comitiva` traz a comitiva (tenda no largo, estacas na divisa no dia seguinte); `arco a3_estrutura desabamento` abre o buraco no campo de Bento (ande até ele para descer); `arco h5_festa festa` começa a festa na praça; `arco h4_posto ataque` com você no Posto à noite faz os saqueadores aparecerem.
5. A2 pelo caminho do jogador: com a comitiva no vilarejo, abra o baú de Avel (de noite, com ele dormindo, ninguém vê), leia os papéis na aba Documentos, suba a opinião dele (trabalhando na oficina) e pergunte.
6. H1: no fim da tarde, procure Pipo atrás do feno no fundo do pomar.
7. Esc → Salvar → Jogo 1 (a miniatura aparece no Carregar). Opções: mexa nos volumes e no tamanho do texto.
8. `epilogo` no console mostra como o epílogo sairia agora, com os painéis.
9. Sem janela: `gradlew test --tests "*ContentM7Test*"`.

## Decisões que pedem aprovação
- **D-069 / Q-08 (narrativa, alto impacto):** o homem de fora é Oren, que perdeu um filho na fuga de Mir e procura o técnico que saiu com crianças.
- **D-073 (narrativa):** como A2 termina — o que Avel confessa (trabalhou em Mir, saiu com a criança, o nome Iria; nunca "não sou seu tio"), e a delação à comitiva como o único caminho trágico.
- **D-074:** A3 sem nenhum desfecho trágico; a vala como gatilho.
- **D-067:** elenco e nomes novos (todos provisórios, no glossário); Doran separado de Avel; Celi jovem (o retrato saiu assim).
- **D-071 (som):** o som é sintetizado e provisório. **Eu não consigo ouvir** — conferi níveis, picos e emendas dos loops numericamente, mas o diretor precisa escutar e dizer se serve de base ou se trocamos por trilha de verdade no M8.
- Texto novo de todas as conversas, fatos, documentos e epílogo: para a leitura de tom.
- Ilustrações do menu e do mapa (Dreamina): se o estilo serve.

## Créditos da Dreamina neste marco
17 retratos × 30 + 1 (Avel) × 30 = 540; 5 ilustrações × 60 = 300 (uma, `epilogo_estrada`, foi cobrada mas o arquivo ficou inacessível: a CLI passou a apontar para outro canvas). **Total ≈ 840.**

## Problemas conhecidos / pendências
- **Som não escutado por mim** (ver acima). Arquivos em WAV (38 MB); o PRD pede OGG — conversão no M8, sem mudar código.
- Slot de save sem "tempo jogado" (D-075).
- A fonte padrão do libGDX fica borrada no texto grande; uma fonte serifada própria seria polimento do M8.
- Combate ainda sem animação de golpe; personagens só têm parado/andando.
- O mapa ilustrado é de IA: as marcas das áreas foram postas à mão sobre a imagem (`mapPos`) e ficam aproximadas.
- Interiores: a borda da frente do cômodo fica aberta (corte), e o piso se esfuma um pouco além dela.
- O Posto ficou com árvores densas perto das bancas; ajuste fino de mapa no M8.
- O painel `epilogo_estrada` não veio; o epílogo usa dois painéis.
- Do M6 e antes, ainda valem: inimigos atravessam uns aos outros; o lobo de frente vira mancha; itens sem ícone; furto só pesa para quem vê; conferir os termos de uso comercial da Dreamina antes de um release; AT-18 testado só nesta máquina.

## Próximo passo — M8 (Polimento e release do MVP), só com o "continue" do usuário
PRD 18: ajuste de ritmo, correções, desempenho (AT-17), sessão de teste de 3 h, release. Inclui: ouvir e decidir o som, fonte própria, animações de golpe, ícones de itens, tempo jogado nos saves, conversão para OGG, AT-15 (portão visual com as capturas de `docs/capturas/m7/`), AT-16, AT-18 numa máquina limpa.

Para quem retomar (qualquer modelo): leia este arquivo, depois `CLAUDE.md`, as seções do PRD do marco e a `docs/SPEC.md`. O vocabulário de condições e efeitos está em `CONTENT.md`.

## Paradas obrigatórias (lembrete)
- Fim de cada marco (M0…M8).
- Antes de decisão irreversível ou de alto impacto narrativo/visual.
