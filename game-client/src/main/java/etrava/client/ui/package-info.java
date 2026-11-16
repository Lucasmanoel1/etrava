/**
 * Interface do cliente (V7). Como montar uma tela:
 * <pre>
 * // 1. Escala: k = max(1, altura/540). Um único ponto, {@link etrava.client.ui.Escala}.
 * //    No resize do Stage:  ui.escala(Escala.aplicar(stage.getViewport(), w, h));
 * //    A fonte vem de {@link etrava.client.ui.Fonts}{@code .text(escala, k)} (gerada em k vezes, reduzida a 1/k);
 * //    quando k muda, o EtravaGame refaz fontes e telas. Medidas de layout são em unidades do Stage.
 * //
 * // 2. Tema e utilitários (o GameUi já tem {@code tema} e {@code ui}):
 * //    Tema tema = new Tema(assetsDir, manifest, fonte);  Ui ui = new Ui(tema);
 * Table p = ui.painel("Título");              // madeira + pergaminho + divisória; segue acrescentando linhas
 * p.add(ui.rolagem(lista)).grow().row();      // toda lista rola assim (barra, roda sob o cursor)
 * p.add(ui.botao("Fechar", this::fechar));    // estados normal/sobre/pressionado/desativado
 * ui.dica(ator, "texto");                     // tooltip do tema, com atraso curto
 * new Label(texto, tema.tinta);               // tintas: tinta, tintaSuave, ferrugem, tintaClara, desativado
 * root.add(p).width(ui.largura(stage, 0.9f, 30)).height(ui.altura(stage, 0.9f, 24)); // fração da tela, máx. em linhas
 * //
 * // 3. Esconder e mostrar painéis: sempre {@code Ui.esconder(ator)} / {@code Ui.mostrar(ator, visivel)}
 * //    (setVisible + stage.unfocus; nunca setVisible(false) direto).
 * //
 * // 4. Teclado: Foco foco = ui.foco().aoFechar(this::fechar);  foco.adicionar(ator, ativar);
 * //    painel.addActor(foco);  // último filho; o UiStage entrega Tab/setas/Enter/Esc ao Foco visível mais alto.
 * //    Refez o painel (clearChildren)? Crie outro Foco junto.
 * //
 * // 5. Cursor: ui.cursor(Ui.Tipo.APONTAR) (padrão); FALAR, ENTRAR, PEGAR, ATACAR, ESPERAR.
 * </pre>
 * Peças ausentes viram placeholder com aviso no log (nunca exceção); {@code Tema#peca(id)} e {@code Tema#pad(id)}
 * dão acesso direto às peças e aos recuos. A ficha ({@code GameUi#buildCharacter}) é a tela de exemplo.
 * Depuração: {@code ETRAVA_ESCALA=N} fixa o k (janelas de captura são menores que a tela pedida).
 */
package etrava.client.ui;
