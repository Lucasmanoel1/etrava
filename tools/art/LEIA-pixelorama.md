# Pixelorama no pipeline de arte (D-100)

O Pixelorama (1.2.x) entra no pipeline de três formas.

## 1. Pincéis de folha: `pinceis/folhas.pxo`
- Cada camada é uma espécie (`carvalho`, `betula`, `macieira`, `pereira`).
- Cada pincel ocupa uma célula de 12×12 px.
- Os pincéis são pintados em cinza, por nível de tom:

  | Cor no pincel | Vira no sprite |
  |---|---|
  | escuro (~#404040) | sombra do cacho, um tom abaixo |
  | médio (~#808080) | corpo do cacho, o tom da luz local |
  | claro (~#C0C0C0) | luz, um tom acima |
  | branco | brilho |

Para mudar o desenho da folha: abra o `.pxo` no Pixelorama, pinte, salve e rode `gradlew pixelize -Pso=carvalho` (não precisa do Blender).

## 2. Folhas em camadas: `acabamento/<pasta>/<folha>.pxo` (árvores e personagens)
- São geradas pelo `renderArt` ou pelo `pixelize`.
- Camadas:
  - árvores: `sombra`, `tronco`, `folhagem`, `contorno` e `retoque`;
  - personagens: `corpo`, `contorno` e `retoque`.
- Cada quadro da folha é um quadro do projeto, direção por direção.
- Cada animação de cada direção tem uma tag (`vento`, ou `parado_d0`…`correr_d7` nos personagens), para tocar no Pixelorama.

## 3. Retoque à mão
- Abra o `.pxo` no Pixelorama e pinte **só na camada `retoque`**, usando cores da paleta (`paleta.yaml`). Depois salve.
- Na próxima geração, o pipeline refaz as outras camadas e mantém o retoque.
- Se o tamanho ou o número de quadros mudar, o retoque antigo é guardado em `<folha>.retoque-antigo.pxo` e o pipeline avisa.
- O retoque só acrescenta ou cobre pixels; não apaga o que está nas camadas de baixo.

## Exportação
- A folha final (`assets/arte/...png`) é exportada pelo Pixelorama na linha de comando (`--headless --export --spritesheet`).
- O pipeline procura o executável:
  1. na variável `PIXELORAMA`;
  2. em `pixelorama.path` no `local.properties`;
  3. na pasta padrão `%LOCALAPPDATA%/Programs/Pixelorama`.
- Sem o Pixelorama, as camadas são compostas em Java e o pipeline avisa.
