"""Entrada do pipeline de arte do ETRAVA (Fase V, SPEC-visual §2).

Uso (sempre em segundo plano, nunca com as preferências do usuário):

    blender -b --factory-startup -P tools/blender/entrada.py -- <trabalho.json> [--force]

O trabalho é um JSON escrito pelo `gradlew renderArt` (tools/RenderArt.java) a partir dos
YAML de tools/blender/. Este script confere a versão do Blender (RV7), semeia o RNG e
despacha para a tarefa do tipo pedido. A saída são passes em PNG de 16 bits e um
`render.json` com materiais e pontos por quadro; quem decide os pixels é o pixelizador.
"""
import json
import os
import random
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
if AQUI not in sys.path:
    sys.path.insert(0, AQUI)

import bpy  # noqa: E402

VERSAO = (5, 3)


def main():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if not argv:
        raise SystemExit("uso: blender -b --factory-startup -P entrada.py -- <trabalho.json> [--force]")
    if tuple(bpy.app.version[:2]) != VERSAO and "--force" not in argv:
        raise SystemExit("Blender %s; o pipeline do ETRAVA exige %d.%d (use --force por sua conta)"
                         % (bpy.app.version_string, *VERSAO))
    with open(argv[0], encoding="utf-8") as f:
        trabalho = json.load(f)
    random.seed(trabalho.get("semente", 1))
    # caminhos relativos ao diretório de trabalho (o Blender resolveria contra a raiz do disco)
    for chave in ("saida", "texturas"):
        if chave in trabalho:
            trabalho[chave] = os.path.abspath(trabalho[chave])

    from nucleo import cena
    cena.limpar()
    tipo = trabalho["tipo"]
    if tipo == "cubo":
        from tarefas import cubo as tarefa
    elif tipo == "personagem":
        from tarefas import personagem as tarefa
    elif tipo == "arvore":
        from tarefas import arvore as tarefa
    elif tipo == "casa":
        from tarefas import casa as tarefa
    elif tipo == "chao":
        from tarefas import chao as tarefa
    elif tipo == "decoracao":
        from tarefas import decoracao as tarefa
    else:
        raise SystemExit("tipo de trabalho desconhecido: " + tipo)
    tarefa.executar(trabalho)
    print("ETRAVA: ok", trabalho["id"])


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception:  # o Blender engole exceções de -P; sai com erro para o Gradle ver
        import traceback
        traceback.print_exc()
        sys.stdout.flush()
        os._exit(1)
    sys.stdout.flush()
    os._exit(0)
