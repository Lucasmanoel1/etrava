# PONTO SEGURO — PROJETO DE JOGO
Versão 0.2 — Registro de ideias, pensamentos e possibilidades

**Estado do projeto:** Planejamento conceitual inicial.

**Objetivo deste documento:** Preservar o pensamento criativo desenvolvido durante as conversas, permitindo retomar, modificar, ampliar ou reorganizar o projeto sem perder ideias importantes.

**IMPORTANTE:** Nada neste documento deve ser interpretado como uma obrigação definitiva, exceto as preferências expressamente estabelecidas. Exemplos são possibilidades, não decisões finais. O objetivo é explorar a maior quantidade possível de caminhos criativos antes de iniciar o desenvolvimento.

---

# 1. VISÃO GERAL

O projeto é um RPG de mundo aberto, desenvolvido em Java, ambientado em um universo fictício extremamente rico em civilizações, culturas, religiões, conflitos políticos, fenômenos sobrenaturais e histórias independentes.

O jogo pretende combinar:

- A liberdade e autonomia do mundo de Kenshi.
- A profundidade política de Game of Thrones, Duna e Fullmetal Alchemist.
- A complexidade cultural, social e religiosa de Warhammer 40.000.
- O mistério sobrenatural de Supernatural, Castlevania e Evangelion.
- A brutalidade e insegurança social de Mad Max.
- A estética biomecânica de Scorn e H. R. Giger.
- A narrativa cinematográfica e os diálogos marcantes de Quentin Tarantino.
- As consequências pessoais e histórias secundárias de Cyberpunk.
- A exploração e o combate relativamente acessível de Diablo.
- O sentimento de descoberta de Star Wars.

Entretanto, essas obras são inspirações, não modelos para reprodução literal.

A intenção é construir um universo próprio, com sua identidade visual, suas regras, sua história e suas civilizações.

**Princípio fundamental: o mundo não existe para o protagonista. O protagonista existe dentro do mundo.**

O jogador poderá ser importante, insignificante, influente, desconhecido, respeitado ou odiado.

Independentemente disso, o mundo continuará existindo.

---

# 2. FILOSOFIA PRINCIPAL DO JOGO

O objetivo não é criar um mundo gigantesco apenas em tamanho.

O objetivo é criar um mundo gigantesco em possibilidades, informações, consequências e diversidade.

Cada região deve apresentar algo interessante para conhecer.

Isso não significa colocar uma missão em cada esquina. Significa que os habitantes precisam ter vidas, preocupações, relacionamentos, opiniões, histórias, tradições e conflitos próprios.

Um comerciante pode conhecer acontecimentos que nunca serão mencionados em nenhuma missão.

Um trabalhador pode esconder informações políticas.

Uma criança pode ter presenciado um fenômeno inexplicável.

Uma governante pode estar tentando impedir uma guerra sem que o jogador jamais descubra sua existência.

Uma religião pode estar mentindo sobre sua origem.

Uma sociedade inteira pode acreditar em uma explicação equivocada sobre o mundo.

O jogador não precisa descobrir tudo.

Na verdade, é desejável que uma única campanha revele apenas uma parte do universo.

A exploração deve produzir a sensação de estar descobrindo um mundo que existia muito antes de o personagem nascer.

---

# 3. TECNOLOGIA E DESENVOLVIMENTO

## 3.1 Linguagem e ferramentas

O jogo será desenvolvido inteiramente em Java.

Não existe interesse em utilizar uma engine tradicional como Unity, Unreal ou Godot.

Entretanto, bibliotecas gráficas e ferramentas de baixo nível são permitidas.

Possibilidade técnica:

- Java como linguagem principal.
- LWJGL para acessar funcionalidades gráficas e de sistema.
- OpenGL para renderização.
- Bibliotecas auxiliares quando realmente necessárias.
- Ferramentas externas para produção de modelos, animações, texturas e áudio.

Não existe intenção de construir um motor gráfico próprio completo.

O objetivo é desenvolver o jogo, não reinventar tecnologias que já existem.

## 3.2 Evitar overengineering

Evitar dedicar tempo excessivo a sistemas pequenos que não acrescentam valor proporcional.

Priorizar:

1. Experiência do jogador.
2. Identidade visual.
3. Qualidade dos ambientes.
4. Interação com personagens.
5. Exploração.
6. Narrativa e ramificações.
7. Funcionamento dos sistemas fundamentais.

Complexidade técnica só deve existir quando trouxer benefícios reais.

## 3.3 Possível estratégia

Ainda não foi decidido se haverá uma demonstração inicial ou desenvolvimento direto de um projeto maior.

Uma possibilidade é construir uma região inicial extremamente bem elaborada, com personagens, interações, ambientação, histórias e sistemas fundamentais.

Depois, ampliar o mundo reutilizando uma base técnica consistente.

A existência de muitos créditos de IA não elimina a necessidade de controlar a complexidade.

---

# 4. IDENTIDADE VISUAL

A beleza do jogo é uma das maiores prioridades de todo o projeto.

O objetivo é produzir ambientes visualmente impressionantes, memoráveis e ricos em personalidade.

Não necessariamente gráficos fotorrealistas.

A direção preferida é um 3D relativamente realista, com personagens de proporções naturais, lembrando Kenshi, mas com qualidade artística superior e maior diversidade.

Não se deseja a aparência exageradamente estilizada de determinadas animações.

## 4.1 Diferenças visuais entre civilizações

As diferenças devem aparecer em:

- Arquitetura.
- Vestuário.
- Materiais de construção.
- Ferramentas.
- Armas.
- Objetos domésticos.
- Organização das cidades.
- Monumentos.
- Religiões e símbolos.
- Iluminação.
- Cores.
- Tecnologia.
- Aparência dos habitantes.
- Costumes e comportamentos.

Uma civilização pode construir grandes estruturas de mármore.

Outra pode viver em cidades industriais cobertas de fuligem.

Outra pode habitar edifícios de carne e estruturas biomecânicas.

Outra pode viver em comunidades construídas com sucata.

Outra pode apresentar arquitetura elegante, jardins, bibliotecas e espaços públicos sofisticados.

**Não transformar todas as regiões em lugares sombrios ou destruídos.**

O contraste é fundamental.

Uma cidade extremamente bela pode esconder atrocidades.

Uma comunidade pobre pode ser acolhedora.

Uma civilização visualmente horrível pode possuir valores relativamente pacíficos.

A aparência não deve revelar automaticamente quem é bom ou mau.

---

# 5. CÂMERA E EXPLORAÇÃO

O jogo será tridimensional.

O jogador deverá ter liberdade para observar e explorar os ambientes.

São desejadas três perspectivas:

**Primeira pessoa:** aproximação com os habitantes, arquitetura e detalhes dos ambientes.

**Terceira pessoa:** exploração tradicional acompanhando o personagem.

**Câmera livre:** liberdade para observar o cenário de outros ângulos, com uma experiência próxima da câmera de Kenshi.

Essas perspectivas deverão compartilhar a maior parte dos sistemas de movimentação e interação, evitando construir três jogos diferentes.

É aceitável começar implementando apenas uma câmera, desde que a arquitetura permita acrescentar as demais.

---

# 6. O MUNDO

O mapa deve ser muito grande, dividido em diferentes regiões, territórios, ecossistemas e civilizações.

Não existe necessidade de transformar todos os ambientes em geração procedural.

Uma possibilidade é misturar:

- Regiões importantes construídas manualmente.
- Cidades cuidadosamente planejadas.
- Monumentos únicos.
- Ruínas com histórias próprias.
- Terrenos e vegetação gerados proceduralmente.
- Zonas menos importantes produzidas com ferramentas semiautomáticas.

O objetivo é equilibrar extensão territorial e riqueza de detalhes.

Um mapa enorme e vazio não atende à proposta.

## 6.1 Civilizações

A ambição inicial é possuir aproximadamente 30 civilizações ou culturas importantes.

Esse número não está definido.

Algumas podem apresentar semelhanças, origens compartilhadas ou influências mútuas.

Entretanto, cada uma deve possuir características reconhecíveis.

A diferença entre duas civilizações não pode ser apenas uma mudança de bandeira ou cor das construções.

Cada uma deveria possuir, conforme sua importância:

- Identidade histórica.
- Religião ou visão filosófica.
- Organização política.
- Sistema econômico.
- Estrutura social.
- Hábitos e tradições.
- Características arquitetônicas.
- Relação com magia.
- Relação com tecnologia.
- Relações diplomáticas.
- Interpretação dos acontecimentos históricos.
- Conflitos internos.

Alguns reinos podem reunir mais de uma cultura, mas grandes unificações multiculturais não precisam ser comuns.

A preferência é que existam muitas sociedades relativamente independentes.

## 6.2 Isolamento cultural

Algumas civilizações conhecem outras.

Algumas mantêm relações comerciais limitadas.

Algumas vivem em conflitos permanentes.

Outras simplesmente desconhecem a existência de grandes territórios além de suas fronteiras.

Uma sociedade industrial pode acreditar que representa o auge da civilização.

Enquanto isso, em outro continente, uma cultura biomecânica desenvolveu conhecimentos que seriam considerados impossíveis por ela.

Isso não precisa ser explicado ao jogador.

Ele descobrirá essas diferenças ao viajar, estudar, conversar e observar.

---

# 7. TECNOLOGIA DAS CIVILIZAÇÕES

O mundo terá níveis tecnológicos diferentes.

Não existe obrigação de estabelecer uma única era histórica.

Uma região pode lembrar a Grécia antiga.

Outra pode ter características do Império Romano.

Outra pode apresentar influências egípcias.

Outra pode lembrar sociedades medievais.

Outra pode ser industrial, com inspiração em Attack on Titan.

Outra pode apresentar condições de sobrevivência próximas às comunidades de Mad Max.

## 7.1 Limites tecnológicos

A tecnologia convencional não deve alcançar o padrão contemporâneo.

Evitar:

- Smartphones.
- Internet.
- Computação moderna generalizada.
- Carros modernos.
- Armas nucleares.
- Infraestrutura tecnológica equivalente à sociedade atual.

Veículos muito antigos podem existir em regiões específicas.

Por exemplo, máquinas e automóveis rudimentares semelhantes aos primeiros modelos do final do século XIX e começo do XX.

Não é obrigatório haver automóveis.

## 7.2 Exceções

O desenvolvimento biomecânico, sobrenatural e de outras formas não convencionais de tecnologia pode ultrapassar limites que seriam impossíveis para sociedades industriais comuns.

Isso não significa liberar qualquer invenção.

As tecnologias devem possuir coerência com a cultura, materiais, conhecimentos e limitações da sociedade que as desenvolveu.

Uma civilização pode dominar a manipulação de tecidos orgânicos, mas desconhecer motores simples.

Outra pode construir locomotivas rudimentares, mas considerar necromancia um mito.

Outra pode possuir conhecimentos extraordinários sobre medicina e praticamente nenhum desenvolvimento militar.

---

# 8. SOCIEDADES E VIDA COTIDIANA

Uma das maiores inspirações é a profundidade social de Warhammer 40.000, especialmente suas instituições, religiões, classes, hierarquias e formas de vida.

O interesse não se limita aos grandes líderes ou às guerras.

É necessário compreender como vivem as pessoas comuns.

Perguntas importantes para cada civilização:

Como seus habitantes trabalham?

O que comem?

Como produzem alimentos?

Como aprendem suas profissões?

Como é organizada a família?

Quem controla a propriedade?

Como funcionam os impostos?

Quem exerce autoridade?

Como resolvem disputas?

Como enterram seus mortos?

O que consideram pecado?

O que consideram um crime?

O que fazem para se divertir?

Como tratam estrangeiros?

Como lidam com doenças?

O que acontece quando alguém nasce pobre?

Existem escravos ou formas de trabalho compulsório?

Existem mecanismos de proteção social?

Como as pessoas enxergam seus governantes?

Nem todas as respostas precisam aparecer explicitamente.

Muitas devem ser percebidas pela arquitetura, comportamento, conversas e situações cotidianas.

---

# 9. POLÍTICA

A política terá grande importância narrativa.

Referências centrais:

- Game of Thrones.
- Duna.
- Fullmetal Alchemist.
- Warhammer 40.000.

O mundo poderá possuir:

- Monarquias.
- Cidades independentes.
- Governos militares.
- Conselhos.
- Oligarquias comerciais.
- Teocracias.
- Comunidades tribais.
- Sociedades descentralizadas.
- Estados expansionistas.
- Regiões anárquicas.
- Outros modelos originais.

As instituições não devem ser apenas decoração.

Deverão influenciar a vida das pessoas.

Uma decisão política pode modificar impostos, circulação comercial, segurança, religião, leis e relações diplomáticas.

Guerras podem começar independentemente do jogador.

Governantes podem morrer.

Golpes políticos podem acontecer.

Territórios podem mudar de controle.

Personalidades influentes podem conquistar ou perder poder.

Também deverão existir personagens capazes de transformar o mundo sem qualquer relação com o protagonista.

**O personagem principal não é a única pessoa com ambições.**

---

# 10. ECONOMIA E COMÉRCIO

O comércio ajuda a conectar civilizações.

Entretanto, nem todas devem participar de uma economia global integrada.

Algumas sociedades podem negociar apenas com comunidades vizinhas.

Outras podem depender de caravanas.

Algumas podem estabelecer postos comerciais próximos às fronteiras.

Outras podem proibir determinados produtos.

A ideia é permitir relações comerciais relativamente livres em determinadas regiões, mas mantendo diferenças políticas e culturais.

Mercadorias podem incluir:

- Alimentos.
- Animais.
- Ferramentas.
- Medicamentos.
- Livros.
- Materiais raros.
- Objetos religiosos.
- Máquinas rudimentares.
- Artefatos sobrenaturais.
- Matéria orgânica especial.
- Recursos relacionados à magia.

Os preços, disponibilidades e relações comerciais podem sofrer mudanças conforme os acontecimentos do mundo.

A economia precisa ser convincente, mas não necessariamente uma simulação perfeita.

---

# 11. SEGURANÇA E ATMOSFERA

Mad Max é uma inspiração importante, especialmente a sensação de insegurança em determinadas regiões.

Nem sempre existe uma instituição capaz de proteger os habitantes.

Algumas comunidades possuem infraestrutura precária.

Outras estão isoladas.

Algumas enfrentam escassez.

Em determinados lugares, viajar sozinho pode ser perigoso.

Entretanto, **a insegurança não deve dominar o jogo inteiro**.

Algumas regiões serão relativamente estáveis.

Outras extremamente sofisticadas e protegidas.

Algumas podem ser violentas, mas economicamente prósperas.

Outras podem ser pacíficas, mas pobres.

O mundo deve transmitir variedade emocional.

Beleza, conforto, medo, curiosidade, estranheza, admiração e horror precisam coexistir.

---

# 12. ESPÉCIES E FORMAS DE VIDA

Não existe obrigação de limitar o mundo aos humanos.

Dependendo da civilização, podem existir:

- Humanos comuns.
- Humanos modificados.
- Populações alteradas por magia.
- Seres biomecânicos.
- Criaturas sobrenaturais.
- Espécies inteligentes não humanas.
- Entidades de origem desconhecida.
- Outras formas de vida originais.

Porém, nem todas precisam conviver normalmente.

Uma espécie pode ser desconhecida para a maior parte das sociedades.

Outra pode ser considerada uma lenda.

Outra pode participar de comunidades humanas.

A existência de criaturas fantásticas precisa contribuir para a identidade do universo.

---

# 13. MAGIA E SOBRENATURAL

A magia será real.

Entretanto, sua compreensão será desigual.

Existe interesse particular em um sistema inspirado na troca equivalente de Fullmetal Alchemist.

Isso significa que fenômenos sobrenaturais podem exigir algum tipo de custo, transformação, compensação ou sacrifício.

Não é necessário que esse custo seja sempre material.

Ele pode envolver:

- Energia.
- Sangue.
- Memórias.
- Tempo de vida.
- Sensações.
- Sono.
- Matéria orgânica.
- Consciência.
- Outras consequências ainda desconhecidas.

Esses exemplos não são regras definidas.

## 13.1 Exceções

O sistema deverá possuir exceções misteriosas.

Determinados acontecimentos podem parecer contrariar suas próprias regras.

Até estudiosos experientes podem desconhecer suas causas.

Algumas exceções podem ser apenas fenômenos mal compreendidos.

Outras podem sugerir que o entendimento humano sobre a realidade é fundamentalmente incompleto.

## 13.2 O sobrenatural não deve ser uniforme

Uma civilização pode utilizar feitiços simples no cotidiano.

Outra pode considerar toda magia uma blasfêmia.

Outra pode praticar necromancia.

Outra pode utilizar rituais para preservar conhecimento.

Outra pode desconhecer completamente esses fenômenos.

Uma sociedade pode compreender apenas um tipo específico de magia.

Outra pode possuir teorias equivocadas.

O jogador começa com pouco conhecimento.

A descoberta do sobrenatural deve ocorrer progressivamente, sem transformar os primeiros minutos do jogo em uma apresentação de todas as regras do universo.

---

# 14. NECROMANCIA E CONSCIÊNCIA APÓS A MORTE

Este é um dos temas com maior potencial narrativo.

A referência inclui as práticas de ressurreição e criação de criaturas vistas em Castlevania.

Entretanto, o foco não precisa ser construir exércitos de mortos-vivos.

A proposta mais interessante é explorar as implicações humanas, religiosas, filosóficas e políticas da morte.

Questões possíveis:

É possível trazer alguém de volta?

A pessoa que retorna continua sendo a mesma?

Ela conserva suas memórias?

Uma consciência pode existir sem corpo?

Pessoas mortas podem testemunhar em julgamentos?

Uma religião teria interesse em impedir tais práticas?

Governantes poderiam tentar prolongar suas vidas?

Uma família aceitaria perder definitivamente alguém que poderia ser restaurado?

O que exatamente é sacrificado durante uma ressurreição?

Essas perguntas não devem receber uma resposta universal logo no começo.

---

# 15. RELIGIÕES E VERDADES FRAGMENTADAS

O universo possuirá diferentes religiões, cultos, sistemas espirituais e interpretações da realidade.

Algumas podem ser construídas sobre mentiras.

Outras podem apresentar explicações parcialmente verdadeiras.

Outras podem surgir de fenômenos sobrenaturais autênticos.

Outras podem desenvolver tradições sem qualquer evidência sobrenatural.

Algumas podem ser instrumentos políticos.

Outras podem representar instituições sinceramente comprometidas com o bem-estar de suas comunidades.

A religião não precisa estar automaticamente associada à opressão.

Ela pode produzir conhecimento, acolhimento, conflitos, solidariedade, violência ou estabilidade, dependendo de suas instituições e integrantes.

## 15.1 Exemplo central

Três civilizações encontram evidências de que certas consciências continuam existindo após a morte.

A primeira acredita que as almas foram aprisionadas.

A segunda acredita que a consciência pode ser conservada por processos desconhecidos.

A terceira, biomecânica, interpreta a morte como uma etapa natural da transformação da matéria viva.

As três apresentam evidências para defender suas interpretações.

Talvez todas estejam parcialmente certas.

Talvez estejam equivocadas.

Talvez suas lideranças conheçam fatos que escondem da população.

A verdade não deve ser revelada prematuramente.

**O jogador conhece versões da história, não necessariamente os fatos.**

---

# 16. PROTAGONISTA E LIBERDADE

A preferência inicial é controlar um personagem principal.

Porém, essa questão ainda não está completamente fechada.

Existe interesse na possibilidade de continuar a experiência depois da morte do protagonista, desde que isso possua fundamento narrativo.

A sucessão por um descendente foi citada apenas como exemplo.

Não está definida.

Por enquanto, o conceito central será um personagem controlado diretamente, com possibilidade de companheiros.

## 16.1 Protagonista comum

Uma origem particularmente interessante é começar como uma pessoa aparentemente insignificante.

Ela não conhece os grandes conflitos do mundo.

Não tem acesso a informações importantes.

Não possui influência política.

Não compreende a maior parte das culturas existentes.

Pode viver durante algum tempo sem abandonar sua comunidade.

Com o passar do tempo, suas experiências podem mudar sua situação.

Ela pode se tornar exploradora, comerciante, integrante de uma organização, governante ou simplesmente continuar sua vida.

Nada disso precisa ocorrer obrigatoriamente.

---

# 17. CINCO ORIGENS INICIAIS — PROPOSTAS

Existe interesse em oferecer aproximadamente cinco opções de origem no menu inicial.

Cada uma apresentaria uma experiência própria.

Essas cinco propostas são apenas possibilidades, ainda não aprovadas definitivamente.

## Origem 1 — O aprendiz do vilarejo

O personagem cresceu em uma comunidade pequena, com aproximadamente 100 habitantes.

Foi criado pelo tio.

Trabalha com ele em uma oficina ou profissão artesanal.

Desconhece detalhes importantes sobre seus pais.

Leva uma vida relativamente comum.

## Origem 2 — O trabalhador industrial

O personagem vive em uma cidade industrial.

Conhece as diferenças entre trabalhadores, comerciantes e autoridades.

Pode trabalhar com máquinas, produção, manutenção ou transporte.

Possui contato indireto com conflitos sociais e políticos.

## Origem 3 — O habitante da cidade erudita

O personagem cresce em uma cidade organizada, com mercados, escolas, bibliotecas, tradições filosóficas e instituições políticas.

Sua realidade pode parecer civilizada e relativamente segura.

Entretanto, conflitos econômicos e disputas por influência permanecem presentes.

## Origem 4 — O jovem de uma sociedade religiosa

O personagem cresce em uma comunidade profundamente ligada a tradições espirituais.

Seus habitantes podem ser acolhedores, disciplinados ou desconfiados de estrangeiros.

Os acontecimentos iniciais podem envolver diferenças entre crenças, instituições e práticas sobrenaturais.

## Origem 5 — O habitante da fronteira

O personagem vive em uma região relativamente isolada, atravessada por comerciantes, viajantes e grupos de diferentes culturas.

Conhece fragmentos de muitos lugares, mas quase nada com profundidade.

Pode viver em uma comunidade com pouca proteção e infraestrutura limitada.

**Regra importante:** essas origens não devem ser cinco versões da mesma história.

Cada uma precisa oferecer personagens, conhecimentos, oportunidades e conflitos próprios.

Algumas tramas poderão ser compartilhadas ou observadas de perspectivas diferentes.

---

# 18. ORIGEM DO TIO — DESENVOLVIMENTO INICIAL

Esta é a origem mais explorada até agora.

O protagonista cresceu em uma pequena cidade ou povoado isolado.

Foi criado pelo tio.

Seus pais morreram ou desapareceram em circunstâncias desconhecidas.

O tio evita falar sobre o assunto.

O personagem ajuda no trabalho familiar.

A comunidade possui habitantes com rotinas, profissões, relacionamentos e pequenos conflitos.

O início não deve parecer necessariamente o começo de uma grande aventura.

O jogador pode conversar, trabalhar, conhecer moradores, circular pelo povoado e observar acontecimentos.

A inspiração emocional é o cotidiano simples do jovem Luke Skywalker antes de compreender o tamanho do universo.

Porém, não existe obrigação de repetir essa estrutura narrativa.

O mistério familiar é pessoal.

**A história dos pais não é automaticamente o centro do universo.**

O tio pode esconder algo importante, mas isso não significa que toda a política mundial esteja conectada ao protagonista.

---

# 19. ABERTURAS NATURAIS

A narrativa deverá apresentar muitas possibilidades de entrada.

Uma entrada é um acontecimento que pode conduzir o jogador a uma história maior.

Não precisa aparecer uma mensagem anunciando uma missão.

Não precisa existir um personagem esperando permanentemente pelo jogador.

Uma conversa casual pode funcionar como entrada.

Um diálogo ouvido parcialmente pode revelar uma informação.

Uma notícia pode chegar distorcida.

Um comerciante pode comentar algo enquanto realiza uma venda.

Uma pessoa pode mencionar um acontecimento durante uma partida de sinuca.

Se o jogador não prestar atenção, pode perder aquela oportunidade.

Isso não significa obrigatoriamente perder a história inteira.

Ele pode encontrar outros vestígios posteriormente, dependendo de como os acontecimentos evoluírem.

## 19.1 Princípio narrativo

A informação deve parecer parte natural da vida das pessoas.

Evitar personagens dizendo coisas artificiais apenas para fornecer missões.

Uma notícia importante pode aparecer no meio de uma conversa banal.

Um acontecimento aparentemente insignificante pode apresentar consequências gigantescas.

Uma informação extraordinária pode simplesmente ser falsa.

O jogador não deve conseguir identificar imediatamente quais histórias serão importantes.

---

# 20. TRINTA POSSIBILIDADES DE GANCHOS NARRATIVOS

Estas são sementes de histórias, não missões definitivas.

Algumas pertencem à origem do tio. Outras podem aparecer em diferentes regiões e origens.

## 01. O homem que deveria estar morto

Um estrangeiro reconhece o tio do protagonista.

O tio finge não conhecê-lo.

Posteriormente, registros indicam que esse homem morreu décadas antes.

## 02. A cobrança

Soldados exigem impostos de um vilarejo que desconhecia pertencer a determinado governo.

O conflito começa como uma discussão administrativa.

## 03. O acidente subterrâneo

Durante um trabalho simples, o protagonista encontra uma estrutura enterrada.

O tio reconhece determinados símbolos, mas evita explicar.

## 04. A caravana

Mercadores oferecem trabalho para uma viagem.

A princípio, parece apenas uma oportunidade de ganhar dinheiro.

Os viajantes escondem conflitos e interesses próprios.

## 05. O funeral

Duas instituições religiosas discordam sobre o destino de um cadáver.

As pessoas comuns não compreendem o motivo da disputa.

## 06. Os refugiados

Pessoas chegam de uma região distante.

Suas explicações sobre o que ocorreu são contraditórias.

Algumas parecem esconder informações.

## 07. Absolutamente nada

Nenhum grande acontecimento é apresentado.

O protagonista simplesmente vive.

O jogador encontra diferentes possibilidades naturalmente, sem um evento inicial obrigatório.

## 08. A menina e a árvore

Uma criança consegue restaurar uma árvore que estava morrendo.

A comunidade considera o acontecimento maravilhoso.

Posteriormente, descobre-se que a criança começou a sofrer consequências inexplicáveis.

Ela pode perder a capacidade de dormir ou descobrir que precisa pagar algum custo.

O custo exato ainda não foi definido.

Possibilidades mencionadas incluem sacrificar animais, perder parte do próprio corpo, oferecer sangue ou desfazer o milagre.

## 09. A mulher que não queimou

Uma senhora sobrevive a um incêndio extraordinário.

Todos comentam que o fogo não conseguiu feri-la.

Porém, a informação divulgada omite que outras pessoas morreram durante o acontecimento.

A sobrevivente pode não compreender o que aconteceu.

## 10. O comerciante silencioso

Um comerciante aparentemente comum começa a comprar enormes quantidades de um produto sem valor aparente.

Meses depois, uma região inteira passa a depender daquele material.

## 11. A canção proibida

Uma música antiga começa a circular entre crianças.

Determinados adultos ficam profundamente perturbados ao ouvi-la.

## 12. O soldado que voltou

Um ex-soldado retorna à cidade depois de muitos anos.

Sua família o reconhece, mas ele demonstra recordar acontecimentos que ainda não ocorreram.

## 13. O templo abandonado

Uma comunidade pretende demolir um templo antigo para reaproveitar suas pedras.

Durante o trabalho, encontram registros que contradizem a história oficial da religião local.

## 14. A cidade sem funerais

Um viajante comenta que determinada cidade não realiza funerais há muitos anos.

Ninguém sabe explicar o motivo.

## 15. A doença dos sonhos

Diversas pessoas relatam sonhar com o mesmo lugar.

Inicialmente, o fenômeno é tratado como superstição.

Posteriormente, surgem relatos sobre acontecimentos semelhantes em outras regiões.

## 16. O aprendiz desaparecido

Um jovem desaparece depois de trabalhar com um artesão.

A comunidade suspeita de um crime comum.

O desaparecimento pode estar relacionado a uma descoberta técnica ou sobrenatural.

## 17. O julgamento

Uma pessoa é acusada de um crime grave.

Durante o processo, alguém afirma que a vítima morta poderia testemunhar.

Isso provoca um conflito entre leis, religião e práticas sobrenaturais.

## 18. A colheita impossível

Uma comunidade consegue produzir alimentos em uma terra considerada infértil.

A descoberta começa a atrair comerciantes e governantes.

A origem da fertilidade permanece desconhecida.

## 19. O homem dos mapas

Um cartógrafo vende mapas de territórios que ninguém conhece.

Alguns são comprovadamente precisos.

Outros parecem representar regiões que não deveriam existir.

## 20. A negociação fracassada

Dois comerciantes discutem o preço de uma mercadoria.

Uma negociação insignificante acaba contribuindo para um conflito entre grupos maiores.

## 21. O poço

Moradores afirmam ouvir vozes durante a noite perto de um poço antigo.

Algumas pessoas acreditam que se trata de um truque.

Outras reconhecem vozes de familiares falecidos.

## 22. O sacerdote desaparecido

Uma figura religiosa respeitada desaparece.

Os moradores apresentam explicações incompatíveis.

O desaparecimento pode estar relacionado a política, religião ou motivos pessoais.

## 23. A biblioteca destruída

Um incêndio atinge um arquivo histórico.

Documentos aparentemente sem importância desaparecem antes do fogo.

Uma pessoa comum percebe a ausência.

## 24. Os dois irmãos

Dois irmãos de uma família influente disputam uma herança.

A disputa pode desencadear conflitos políticos, mas o jogador pode conhecê-la apenas como uma fofoca familiar.

## 25. A ponte fechada

Uma importante passagem comercial é interditada.

A justificativa oficial envolve segurança.

Os habitantes locais suspeitam de interesses econômicos.

## 26. O animal que reconheceu alguém

Um animal apresenta comportamento estranho diante de um visitante.

O episódio pode não ter importância.

Ou pode indicar uma relação desconhecida entre aquele visitante e um acontecimento antigo.

## 27. O monumento incompleto

Uma cidade constrói um grande monumento há gerações.

Ninguém sabe explicar satisfatoriamente o motivo de a obra nunca terminar.

## 28. A moeda estrangeira

Uma moeda desconhecida surge em um mercado comum.

Ela apresenta símbolos associados a uma civilização que supostamente não mantém contato com aquela região.

## 29. O médico e o paciente

Um médico salva a vida de alguém com um método experimental.

O paciente recupera a saúde, mas começa a manifestar alterações inesperadas.

## 30. A festa interrompida

Durante uma comemoração local, uma pessoa faz uma afirmação aparentemente absurda.

Os presentes ignoram o comentário.

Alguns dias depois, acontecimentos externos fazem aquela declaração parecer muito mais importante.

---

# 21. COMO FUNCIONAM AS HISTÓRIAS SECUNDÁRIAS

As histórias precisam acontecer mesmo quando o jogador não está envolvido.

Exemplo:

Uma criança restaura uma árvore.

A comunidade comenta o acontecimento.

A criança começa a apresentar sintomas.

Um familiar procura ajuda.

Uma instituição religiosa toma conhecimento.

Um estudioso visita a região.

A história continua evoluindo.

Se o jogador estiver próximo e prestar atenção, poderá participar.

Se estiver longe, talvez receba notícias.

Se chegar tarde, poderá encontrar apenas as consequências.

Se nunca descobrir a história, o acontecimento ainda fará parte daquele mundo.

Essa é uma das principais ambições narrativas do projeto.

## 21.1 Histórias de diferentes tamanhos

Nem toda história deve mudar a política mundial.

Algumas podem afetar uma única família.

Outras podem transformar uma comunidade.

Outras podem causar uma crise regional.

Algumas podem desencadear consequências internacionais.

Uma discussão doméstica pode permanecer apenas uma discussão doméstica.

Nem todo detalhe precisa esconder um grande mistério.

---

# 22. EQUILÍBRIO EMOCIONAL

O jogo poderá apresentar situações perturbadoras.

Entretanto, não deve transformar todas as histórias em tragédias.

A inspiração em Cyberpunk diz respeito à possibilidade de histórias pequenas possuírem consequências dramáticas.

Mas isso precisa ser equilibrado.

Algumas histórias podem terminar bem.

Outras podem apresentar resoluções ambíguas.

Algumas podem terminar em perdas.

Outras podem ser engraçadas.

Algumas podem parecer horríveis e apresentar explicações surpreendentemente simples.

Outras podem começar como situações banais e revelar acontecimentos macabros.

A imprevisibilidade é importante.

**Se todas as histórias terminarem mal, o jogador começará a prever o resultado.**

---

# 23. DIÁLOGOS E CUTSCENES

As referências principais são Quentin Tarantino, especialmente Kill Bill, e a construção de tensão em conversas aparentemente comuns.

Os diálogos deverão apresentar:

- Personalidades distintas.
- Motivações próprias.
- Humor ocasional.
- Ironia.
- Tensões políticas.
- Conflitos pessoais.
- Informações incompletas.
- Mentiras.
- Subtextos.
- Silêncios significativos.

Evitar personagens que apenas explicam a história.

Uma pessoa pode contar uma versão dos acontecimentos para proteger sua reputação.

Outra pode estar sinceramente enganada.

Outra pode saber a verdade, mas não possuir motivo para revelá-la.

As cutscenes devem ser utilizadas quando melhorarem a narrativa.

Nem toda conversa precisa interromper o controle do jogador.

O mundo deve parecer natural.

---

# 24. CAPÍTULOS

Foi considerada a possibilidade de não utilizar capítulos.

Posteriormente, a ideia de capítulos foi aceita.

Entretanto, o sistema não deve obrigar o jogador a seguir uma única história.

A preferência é que os capítulos representem estágios de experiência, descoberta ou transformação do personagem.

Eles não precisam ser definidos exclusivamente por riqueza, nível ou posição política.

## Capítulo 1 — O mundo conhecido

O personagem ainda vive dentro de sua realidade inicial.

Conhece pouco sobre outras regiões.

Possui habilidades limitadas.

Começa a entrar em contato com histórias e informações externas.

Pode continuar vivendo normalmente.

Não existe necessidade de iniciar uma aventura épica imediatamente.

## Capítulo 2 — Além das fronteiras

O personagem já conhece parte do mundo.

Pode possuir novas habilidades, equipamentos, contatos ou companheiros.

Talvez tenha conquistado influência.

Talvez continue pobre, mas tenha adquirido conhecimento.

Talvez trabalhe para outra pessoa.

Talvez possua subordinados ou ajudantes.

A progressão não deve exigir uma condição social específica.

## Capítulos posteriores — Em aberto

Possibilidades:

- Expansão das relações políticas.
- Descoberta de outras civilizações.
- Contato com conhecimentos sobrenaturais.
- Participação em grandes disputas.
- Transformações sociais.
- Revelações sobre a história do mundo.

Não existe número definido de capítulos.

Um capítulo pode introduzir novas tramas independentes, sem encerrar automaticamente as anteriores.

---

# 25. RAMIFICAÇÕES E CONSEQUÊNCIAS

Esta é uma das maiores prioridades do jogo.

A liberdade deve produzir resultados concretos.

O jogador poderá:

- Ignorar histórias.
- Descobrir segredos.
- Ajudar pessoas.
- Prejudicar pessoas.
- Participar de conflitos.
- Trabalhar.
- Comercializar.
- Viajar.
- Estudar.
- Investigar acontecimentos.
- Conquistar influência.
- Se envolver com governos.
- Abandonar determinados objetivos.

Entretanto, nenhuma dessas atividades precisa transformar o jogador automaticamente em uma figura central.

## 25.1 Muitas possibilidades de resultado

Existe interesse em possuir uma quantidade extremamente grande de resultados possíveis.

Foi mencionada, de maneira ilustrativa, a possibilidade de bilhões de finais.

Não significa escrever literalmente um bilhão de roteiros.

Uma possibilidade técnica é construir desfechos combinatórios.

O estado final do mundo pode variar conforme:

- Quais personagens sobreviveram.
- Quais governos permanecem.
- Quais cidades prosperaram.
- Quais regiões foram destruídas.
- Quais mistérios foram descobertos.
- Quais relações foram construídas.
- Quais instituições conquistaram poder.
- Quais culturas mudaram.
- Quais acontecimentos foram ignorados.
- Quais escolhas pessoais foram realizadas.

Esses elementos podem combinar-se para produzir um número enorme de resultados diferentes.

O objetivo deve ser variedade significativa, e não apenas um número impressionante.

---

# 26. MUNDO INDEPENDENTE DO JOGADOR

O mundo deve continuar funcionando independentemente das decisões do protagonista.

Se ele permanecer trabalhando em uma comunidade durante anos, acontecimentos importantes continuarão ocorrendo.

Se viajar para uma região distante, outros territórios continuarão mudando.

Se ignorar determinado conflito, ele poderá ser resolvido sem sua intervenção.

Se um personagem importante morrer, sua ausência deverá produzir consequências.

## 26.1 Grandes eventos

Alguns acontecimentos podem possuir consequências irreversíveis.

Exemplos:

- Destruição de uma cidade.
- Queda de um governo.
- Morte de uma personalidade.
- Desaparecimento de uma instituição.
- Alteração de relações comerciais.
- Transformação de uma região.

Foi sugerida a possibilidade de oferecer configurações para reduzir ou desativar determinados eventos temporizados.

Isso permitiria que jogadores interessados principalmente em exploração tivessem mais liberdade de ritmo.

A configuração exata ainda não foi definida.

---

# 27. NPCs E PERSONAGENS IMPORTANTES

Os personagens não jogáveis devem possuir objetivos próprios.

Os mais relevantes poderão apresentar:

- Rotinas.
- Profissões.
- Relacionamentos.
- Memórias.
- Ambições.
- Preferências.
- Lealdades.
- Medos.
- Informações.
- Desentendimentos.
- Conflitos pessoais.
- Interesses políticos.

Nem todos precisam de simulação profunda.

Um morador comum pode ter comportamento relativamente simples.

Uma personalidade política importante pode participar de acontecimentos complexos.

Um governante pode ordenar o assassinato de outro por motivos políticos.

Um comerciante pode financiar uma rebelião.

Um religioso pode tentar impedir um conflito.

Um personagem desconhecido pode crescer em importância ao longo do tempo.

## 27.1 Simulação viável

Não existe necessidade de simular todos os habitantes do mundo, individualmente, a cada segundo.

Uma alternativa é utilizar diferentes escalas:

- Simulação detalhada perto do jogador.
- Simulação simplificada em comunidades distantes.
- Simulação política e econômica regional.
- Atualização de acontecimentos importantes conforme o tempo avança.

Isso permitiria criar a percepção de um mundo vivo sem exigir uma quantidade impossível de processamento.

---

# 28. COMBATE

O combate existirá, mas não será o foco principal.

A preferência é um sistema relativamente acessível, inspirado em Diablo.

Não existe interesse em exigir reflexos e precisão semelhantes aos jogos da série Dark Souls.

O combate pode ocorrer em tempo real.

A preparação do personagem, seus equipamentos, habilidades e decisões devem importar.

O jogador poderá enfrentar situações violentas, mas muitas histórias devem permitir caminhos que não dependam de combate.

O mundo não precisa ser um campo de batalha permanente.

---

# 29. PROGRESSÃO

A progressão poderá combinar:

- Habilidades.
- Equipamentos.
- Experiência.
- Conhecimento.
- Relações.
- Influência.
- Condições materiais.
- Decisões narrativas.

Uma pessoa pode ser extremamente influente e pouco habilidosa em combate.

Outra pode ser excelente em sobrevivência e não possuir qualquer relevância política.

Outra pode dominar conhecimentos sobrenaturais e continuar vivendo isolada.

O progresso não precisa significar apenas aumento de poder.

Aprender a compreender o mundo também deve representar evolução.

---

# 30. MORTE DO PROTAGONISTA

Existe interesse em consequências relevantes para a morte.

A possibilidade de morte permanente foi considerada.

Também foi levantada a ideia de continuar jogando com outra pessoa ligada ao personagem anterior.

Entretanto, isso ainda não foi definido.

A sucessão precisa fazer sentido.

Não deve surgir um personagem aleatório sem qualquer explicação.

Pode envolver relações familiares, sociais ou outros mecanismos narrativos.

Não existe decisão final sobre esse sistema.

O princípio central permanece:

**A morte do protagonista não precisa significar o fim do mundo.**

---

# 31. GRANDES MISTÉRIOS E PLOT TWISTS

Existe interesse em desenvolver revelações capazes de modificar profundamente a interpretação da história.

Referências:

- Clube da Luta.
- Evangelion.
- Duna.
- Mistérios sobrenaturais de Supernatural.
- Construção política de Game of Thrones.

O plot twist não precisa estar diretamente relacionado ao protagonista.

Pode envolver a natureza do mundo.

Pode envolver a origem de determinadas civilizações.

Pode envolver interpretações religiosas equivocadas.

Pode envolver o significado da morte.

Pode envolver processos de transformação da consciência.

Pode envolver uma civilização desconhecida.

Pode apresentar elementos cósmicos ou extraterrestres.

Existe interesse em conceitos estranhos e perturbadores, semelhantes à escala filosófica de Evangelion.

## 31.1 Uma possibilidade

Diversas civilizações possuem explicações diferentes para fenômenos que acreditam compreender.

Talvez essas explicações sejam fragmentos de um acontecimento muito maior.

Talvez o mundo inteiro esteja interpretando incorretamente sua própria história.

Não existe decisão definitiva.

## 31.2 Princípios do mistério

O mistério não deve depender exclusivamente de esconder informações arbitrariamente.

O ideal é que evidências apareçam ao longo da experiência.

Após a revelação, acontecimentos antigos podem adquirir novos significados.

Podem existir várias reviravoltas, mas a quantidade não é prioridade.

Uma grande revelação bem construída é melhor que muitas revelações artificiais.

---

# 32. REFERÊNCIAS CRIATIVAS

## Kenshi
Liberdade, exploração, independência do mundo, progressão e sensação de existir em uma realidade que não depende do jogador.

Não reproduzir sua aparência visual ou limitações.

A escravidão, agricultura e outras características de sua ambientação não são obrigatórias para este projeto.

## Diablo IV
Referência para combate acessível, exploração e evolução de personagem.

## Game of Thrones
Guerras, alianças, traições, disputas dinásticas, conflitos entre casas, estratégias políticas e personagens com interesses próprios.

Não há interesse especial em reproduzir elementos como o Corvo de Três Olhos.

## Duna
Política, religião, cultura, disputas de poder, interpretações proféticas e conflitos entre interesses pessoais e institucionais.

## Fullmetal Alchemist
Alquimia, troca equivalente, instituições políticas, segredos de Estado e consequências morais do conhecimento.

## Warhammer 40.000
Profundidade das civilizações, hierarquias, religiões, instituições militares, desigualdades, arquitetura monumental e histórias dos habitantes comuns.

A intenção é estudar profundamente essas características para criar culturas originais.

## Mad Max
Insegurança social, escassez, comunidades improvisadas, sobrevivência e diferentes níveis de organização.

Especial interesse em sociedades que utilizam soluções tecnológicas rudimentares e criativas.

## Attack on Titan
Arquitetura e tecnologia industrial relativamente antigas, estruturas políticas e sensação de viver em uma sociedade que desconhece partes importantes do mundo.

## Scorn
Biomecânica, arquitetura orgânica, fusão entre tecidos e máquinas, ambientes perturbadores e concepção visual inspirada em H. R. Giger.

## Star Wars
O começo relativamente simples de Luke Skywalker, a descoberta gradual de um universo maior e a ideia de poderes misteriosos que apresentam interpretações religiosas e filosóficas.

## Castlevania
Necromancia, criaturas sobrenaturais, conflitos entre humanos e outras entidades e possibilidades de manipulação da morte.

## Supernatural
Demônios, pactos, feitiços, tradições ocultas, criaturas e histórias sobrenaturais que podem existir paralelamente à vida cotidiana.

## Evangelion
Mistérios existenciais, transformação da humanidade, consciência coletiva, elementos cósmicos e revelações desconcertantes.

## Cyberpunk
Histórias paralelas, consequências pessoais, dilemas morais e acontecimentos inicialmente pequenos que podem terminar de maneira inesperada.

## Quentin Tarantino / Kill Bill
Diálogos, tensão, personalidade, construção de cenas e linguagem cinematográfica.

## Clube da Luta
Reviravolta narrativa capaz de alterar a interpretação dos acontecimentos anteriores.

## The Walking Dead e narrativas semelhantes
Relações entre comunidades, insegurança, trocas comerciais, alianças limitadas e a maneira como grupos diferentes convivem em um mundo perigoso.

Todas essas referências devem servir como base de estudo e inspiração, não como obrigação de reprodução.

---

# 33. PRIORIDADES DO PROJETO

As prioridades criativas identificadas até agora são:

**1. Identidade visual marcante.**

O jogo precisa ser bonito, original e memorável.

**2. Civilizações profundas.**

Diferenças reais na vida cotidiana, política, cultura e religião.

**3. Mundo independente.**

Acontecimentos continuam sem a participação do protagonista.

**4. Narrativa ramificada.**

Escolhas, informações e oportunidades podem alterar resultados.

**5. Exploração e descoberta.**

Conhecimento gradual de um mundo vasto.

**6. Personagens convincentes.**

NPCs com interesses, relações e objetivos.

**7. Sobrenatural misterioso.**

Magia, necromancia e fenômenos com regras e exceções.

**8. Liberdade de experiência.**

O jogador não precisa seguir um destino predeterminado.

**9. Combate funcional.**

Importante, mas não central.

**10. Tecnologia viável.**

Utilizar Java, bibliotecas existentes e evitar complexidade desnecessária.

---

# 34. QUESTÕES AINDA NÃO DEFINIDAS

Este documento não deve fechar prematuramente as seguintes decisões:

- Nome do jogo.
- Nome do mundo.
- Formato definitivo do mapa.
- Quantidade exata de regiões.
- Quantidade final de civilizações.
- Idiomas e sistemas de escrita.
- Calendário.
- História antiga do universo.
- Origem da magia.
- Limitações definitivas da magia.
- Natureza das entidades sobrenaturais.
- Espécies inteligentes.
- Origem definitiva do protagonista.
- Relação entre os pais e o tio.
- Quantidade final de origens jogáveis.
- Sistema de capítulos.
- Critérios para avançar entre capítulos.
- Sistema definitivo de NPCs.
- Sistema de combate.
- Sistema de habilidades.
- Consequências da morte do personagem.
- Possibilidade de sucessão.
- Organização econômica detalhada.
- Sistema de construção.
- Administração de propriedades.
- Sistema de companheiros.
- Direção visual definitiva.
- Mecânica de troca de câmeras.
- Sistema de eventos.
- Número e estrutura dos finais.
- Plot twist principal.
- Possibilidade de continuação após o final.
- Escopo da primeira versão jogável.

Esses assuntos devem ser discutidos e desenvolvidos posteriormente.

---

# 35. O QUE NÃO QUEREMOS

Evitar:

1. Um mundo enorme, porém vazio.
2. Civilizações que diferem apenas visualmente.
3. Narrativa centralizada artificialmente no protagonista.
4. Missões que parecem existir apenas para o jogador.
5. Personagens que não possuem vida própria.
6. Um universo permanentemente sombrio e deprimente.
7. Todas as histórias terminando de maneira trágica.
8. Magia sem identidade ou consequências.
9. Revelações sobrenaturais excessivamente antecipadas.
10. Combate extremamente difícil como principal atração.
11. Tecnologia moderna convencional.
12. Construir uma engine completa desnecessariamente.
13. Sistemas tecnicamente complexos sem benefício perceptível.
14. Mistérios que não apresentam desenvolvimento coerente.
15. Finais que mudam apenas uma frase.
16. Escolhas aparentemente importantes que não produzem consequências.
17. Exposição excessiva da história através de diálogos artificiais.
18. Obrigação de transformar o protagonista em governante, herói ou salvador.
19. Comprometer a qualidade dos ambientes apenas para aumentar o tamanho do mapa.
20. Tomar exemplos improvisados como decisões definitivas.

---

# 36. FILOSOFIA DE DESENVOLVIMENTO DA HISTÓRIA

A história precisa ser construída a partir do mundo.

Primeiro, desenvolver sociedades, personagens, instituições, relações e conflitos.

Depois, permitir que histórias apareçam naturalmente a partir dessas relações.

A narrativa não precisa possuir uma única trama principal.

Pode existir um grande mistério estrutural sobre o universo.

Também podem existir tramas políticas, familiares, comerciais, religiosas e sobrenaturais independentes.

Uma história pode cruzar com outra.

Uma decisão tomada em determinada região pode alterar acontecimentos distantes.

Entretanto, nem todas as tramas precisam estar conectadas.

**O mundo deve possuir acontecimentos sem qualquer relação com o jogador e sem qualquer relação com o grande mistério.**

A história do tio e dos pais é um exemplo de arco pessoal.

Ela não determina necessariamente o significado do universo.

É possível que o jogador nunca resolva esse mistério.

Mesmo assim, sua experiência deve continuar interessante.

---

# 37. DIRETRIZ PARA FUTURAS CONVERSAS

Ao retomar este projeto, considerar que estamos trabalhando em um conceito criativo, não em um roteiro finalizado.

Devemos:

- Desenvolver as ideias progressivamente.
- Apresentar alternativas.
- Fazer perguntas relevantes.
- Evitar decisões precipitadas.
- Preservar possibilidades interessantes.
- Distinguir sugestões de escolhas confirmadas.
- Não transformar todo exemplo em regra.
- Manter a viabilidade técnica em consideração.
- Evitar overengineering.
- Priorizar narrativa, civilizações e identidade visual.
- Manter respostas objetivas durante a discussão.

O objetivo é construir gradualmente uma visão suficientemente clara para orientar uma IA de programação, minimizando ambiguidades e desperdício de créditos.

Não iniciar automaticamente a implementação apenas porque uma ideia foi discutida.

Antes do desenvolvimento, será necessário elaborar especificações mais concretas sobre arquitetura do jogo, mapas, sistemas, interface, arte e funcionalidades.

---

# 38. RESUMO ESSENCIAL

Queremos criar um jogo em Java, sem engine tradicional, utilizando bibliotecas gráficas como OpenGL por meio de LWJGL.

Será um RPG tridimensional, com exploração livre, diferentes câmeras, personagens relativamente realistas e direção artística extremamente cuidadosa.

O mundo será grande, dividido em regiões e civilizações com diferenças profundas de cultura, arquitetura, tecnologia, religião, política e comportamento.

Algumas sociedades serão industriais, outras antigas, outras primitivas, outras biomecânicas e outras completamente originais.

A tecnologia convencional será limitada, mas o sobrenatural e a biomecânica poderão apresentar desenvolvimentos extraordinários.

O jogo terá magia, necromancia, religiões, disputas políticas, comércio, exploração, relacionamentos e acontecimentos independentes do jogador.

As histórias surgirão naturalmente, inclusive por meio de rumores e conversas aparentemente insignificantes.

O jogador poderá perder oportunidades, descobrir segredos ou alterar acontecimentos.

Existirão diferentes origens jogáveis, com histórias e possibilidades próprias.

Os capítulos representarão fases de descoberta ou transformação sem obrigar uma trajetória única.

A quantidade de resultados possíveis será extremamente grande, construída por consequências e combinações significativas.

O protagonista poderá começar como alguém comum, sem conhecimento sobre a verdadeira extensão do mundo.

Um de seus possíveis arcos envolve ter sido criado pelo tio e desconhecer o destino dos pais.

O mundo não girará em torno desse mistério.

O maior objetivo é fazer com que o jogador sinta que está vivendo dentro de um universo extraordinariamente rico, onde cada cultura possui identidade, cada pessoa pode possuir uma história e praticamente qualquer acontecimento pode ter consequências inesperadas.

**FRASE QUE DEFINE O PROJETO:**

"Não queremos criar uma aventura em que o mundo espera pelo jogador. Queremos criar um mundo que possui suas próprias aventuras, e permitir que o jogador faça parte de algumas delas."

---

FIM DO PONTO SEGURO — VERSÃO 0.2

Este documento registra o pensamento desenvolvido até agora. Não constitui uma especificação técnica completa, um roteiro definitivo nem uma lista de funcionalidades obrigatórias. Todas as propostas podem ser revisadas conforme a visão criativa evoluir.