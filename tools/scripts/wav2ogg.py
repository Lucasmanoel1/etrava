"""Converte os WAV de assets/audio em OGG Vorbis (RF-422, D-080) e apaga os WAV.

Uso:  python tools/scripts/wav2ogg.py assets/audio [qualidade 0..1, padrão 0.45]
Pede:  pip install soundfile   (traz a libsndfile, que codifica Vorbis; não precisa de ffmpeg)

Só é necessário para quem regenera o áudio (`gradlew generateAudio`): o repositório já guarda os OGG.
Sem este passo o jogo continua funcionando: o cliente toca o .wav de mesmo nome se o .ogg faltar.
"""
import os
import sys

try:
    import soundfile as sf
except ImportError:
    sys.exit("Falta o módulo soundfile. Instale com:  pip install soundfile")


CHUNK = 16384


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    root = sys.argv[1]
    quality = float(sys.argv[2]) if len(sys.argv) > 2 else 0.45
    before = after = count = 0
    for folder, _, files in os.walk(root):
        for name in sorted(files):
            if not name.lower().endswith(".wav"):
                continue
            src = os.path.join(folder, name)
            dst = src[:-4] + ".ogg"
            data, rate = sf.read(src, dtype="float32")
            channels = 1 if data.ndim == 1 else data.shape[1]
            with sf.SoundFile(dst, "w", samplerate=rate, channels=channels, format="OGG", subtype="VORBIS",
                              compression_level=quality) as out:
                for start in range(0, len(data), CHUNK):  # aos pedaços: a libsndfile cai com um bloco só, grande
                    out.write(data[start:start + CHUNK])
            check, check_rate = sf.read(dst, dtype="float32")
            if check_rate != rate or abs(len(check) - len(data)) > 64:
                sys.exit(f"{dst}: o OGG não bate com o WAV ({len(check)} x {len(data)} amostras)")
            before += os.path.getsize(src)
            after += os.path.getsize(dst)
            os.remove(src)
            count += 1
    print(f"{count} arquivo(s) convertidos: {before / 1e6:.1f} MB em WAV -> {after / 1e6:.1f} MB em OGG")


if __name__ == "__main__":
    main()
