package etrava.tools.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Síntese de áudio mínima para o som provisório do jogo (D-071): cordas dedilhadas
 * (Karplus-Strong), sinos aditivos, pads, flauta, percussão, ruídos filtrados e um
 * reverb de Schroeder. Tudo em mono, 22 050 Hz, float de -1 a 1.
 */
final class Synth {
    static final int SR = 22050;

    private Synth() {}

    static float[] buffer(double seconds) {
        return new float[(int) Math.ceil(seconds * SR)];
    }

    static int at(double seconds) { return (int) Math.round(seconds * SR); }

    /** Soma {@code src} em {@code dst} a partir de {@code offset} amostras; o que passar do fim dá a volta (loop). */
    static void mix(float[] dst, float[] src, int offset, double gain, boolean wrap) {
        for (int i = 0; i < src.length; i++) {
            int j = offset + i;
            if (j >= dst.length) {
                if (!wrap) break;
                j %= dst.length;
            }
            if (j >= 0) dst[j] += (float) (src[i] * gain);
        }
    }

    static double midi(double note) { return 440.0 * Math.pow(2, (note - 69) / 12.0); }

    // ---------------------------------------------------------------- instrumentos

    /** Corda dedilhada (alaúde): Karplus-Strong com um filtro que escurece o timbre. */
    static float[] pluck(double freq, double dur, double bright, Random rnd) {
        float[] out = buffer(dur);
        int n = Math.max(2, (int) Math.round(SR / freq));
        float[] ring = new float[n];
        for (int i = 0; i < n; i++) ring[i] = (float) (rnd.nextDouble() * 2 - 1);
        // suaviza o ataque (menos "bip" de computador)
        for (int k = 0; k < 2; k++) for (int i = 1; i < n; i++) ring[i] = (ring[i] + ring[i - 1]) * 0.5f;
        double decay = 0.994 + 0.004 * Math.min(1, 200 / freq);
        int idx = 0;
        for (int i = 0; i < out.length; i++) {
            int next = (idx + 1) % n;
            float v = ring[idx];
            ring[idx] = (float) ((v * bright + (v + ring[next]) * 0.5 * (1 - bright)) * decay);
            out[i] = v;
            idx = next;
        }
        envelope(out, 0.002, dur * 0.9, 0.08);
        return out;
    }

    /** Sino: parciais inarmônicas que somem em tempos diferentes. */
    static float[] bell(double freq, double dur) {
        float[] out = buffer(dur);
        double[] ratio = {1, 2.0, 2.76, 4.07, 5.4};
        double[] amp = {1, 0.45, 0.35, 0.2, 0.12};
        double[] tau = {1.0, 0.6, 0.45, 0.3, 0.2};
        for (int i = 0; i < out.length; i++) {
            double t = i / (double) SR, s = 0;
            for (int p = 0; p < ratio.length; p++)
                s += amp[p] * Math.sin(2 * Math.PI * freq * ratio[p] * t) * Math.exp(-t / (tau[p] * dur * 0.5));
            out[i] = (float) (s * 0.35);
        }
        envelope(out, 0.004, dur, 0.05);
        return out;
    }

    /** Pad: três dentes-de-serra desafinados, filtrados, com ataque lento. */
    static float[] pad(double freq, double dur, double cutoff) {
        float[] out = buffer(dur);
        double[] det = {0.996, 1.0, 1.004};
        double[] ph = new double[3];
        for (int i = 0; i < out.length; i++) {
            double s = 0;
            for (int k = 0; k < 3; k++) {
                ph[k] += freq * det[k] / SR;
                ph[k] -= Math.floor(ph[k]);
                s += ph[k] * 2 - 1;
            }
            out[i] = (float) (s / 3);
        }
        lowpass(out, cutoff);
        lowpass(out, cutoff * 1.4);
        envelope(out, Math.min(1.5, dur * 0.3), dur, Math.min(1.5, dur * 0.3));
        return out;
    }

    /** Bordão de arco: pad grave com tremor lento. */
    static float[] drone(double freq, double dur, double cutoff) {
        float[] out = pad(freq, dur, cutoff);
        for (int i = 0; i < out.length; i++) {
            double t = i / (double) SR;
            out[i] *= (float) (0.75 + 0.25 * Math.sin(2 * Math.PI * 0.13 * t));
        }
        return out;
    }

    /** Flauta de madeira: seno com um pouco de segundo harmônico, vibrato e sopro. */
    static float[] flute(double freq, double dur, Random rnd) {
        float[] out = buffer(dur);
        double ph = 0;
        for (int i = 0; i < out.length; i++) {
            double t = i / (double) SR;
            double vib = 1 + 0.006 * Math.sin(2 * Math.PI * 5.2 * t) * Math.min(1, t * 3);
            ph += freq * vib / SR;
            double s = Math.sin(2 * Math.PI * ph) + 0.18 * Math.sin(4 * Math.PI * ph) + 0.06 * Math.sin(6 * Math.PI * ph);
            s += (rnd.nextDouble() * 2 - 1) * 0.06;
            out[i] = (float) (s * 0.5);
        }
        lowpass(out, 3500);
        envelope(out, 0.06, dur, 0.12);
        return out;
    }

    /** Tambor de mão: seno que cai de tom e um pouco de pele (ruído). */
    static float[] drum(double pitch, double dur, double noise, Random rnd) {
        float[] out = buffer(dur);
        double ph = 0;
        for (int i = 0; i < out.length; i++) {
            double t = i / (double) SR;
            double f = pitch * (1 + 1.5 * Math.exp(-t * 40));
            ph += f / SR;
            double env = Math.exp(-t * 14 / dur * 0.25);
            out[i] = (float) ((Math.sin(2 * Math.PI * ph) * env + (rnd.nextDouble() * 2 - 1) * noise * Math.exp(-t * 60)) * 0.8);
        }
        envelope(out, 0.001, dur, 0.02);
        return out;
    }

    // ---------------------------------------------------------------- ruídos e filtros

    static float[] noise(double dur, Random rnd) {
        float[] out = buffer(dur);
        for (int i = 0; i < out.length; i++) out[i] = (float) (rnd.nextDouble() * 2 - 1);
        return out;
    }

    /** Ruído marrom (grave, como vento ao longe). */
    static float[] brown(double dur, Random rnd) {
        float[] out = buffer(dur);
        double v = 0;
        for (int i = 0; i < out.length; i++) {
            v = (v + (rnd.nextDouble() * 2 - 1) * 0.02) * 0.998;
            out[i] = (float) (v * 3);
        }
        return out;
    }

    static void lowpass(float[] x, double cutoff) {
        double a = Math.exp(-2 * Math.PI * cutoff / SR), y = 0;
        for (int i = 0; i < x.length; i++) {
            y = (1 - a) * x[i] + a * y;
            x[i] = (float) y;
        }
    }

    static void highpass(float[] x, double cutoff) {
        double a = Math.exp(-2 * Math.PI * cutoff / SR), y = 0, prev = 0;
        for (int i = 0; i < x.length; i++) {
            y = a * (y + x[i] - prev);
            prev = x[i];
            x[i] = (float) y;
        }
    }

    /** Passa-banda ressonante (biquad), para vozes, madeira e assobios. */
    static void bandpass(float[] x, double freq, double q) {
        double w = 2 * Math.PI * freq / SR, alpha = Math.sin(w) / (2 * q), cos = Math.cos(w);
        double b0 = alpha, b2 = -alpha, a0 = 1 + alpha, a1 = -2 * cos, a2 = 1 - alpha;
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        for (int i = 0; i < x.length; i++) {
            double y = (b0 * x[i] + b2 * x2 - a1 * y1 - a2 * y2) / a0;
            x2 = x1;
            x1 = x[i];
            y2 = y1;
            y1 = y;
            x[i] = (float) y;
        }
    }

    // ---------------------------------------------------------------- envelopes e efeitos

    static void envelope(float[] x, double attack, double dur, double release) {
        int a = Math.max(1, at(attack)), end = Math.min(x.length, at(dur)), r = Math.max(1, at(release));
        for (int i = 0; i < x.length; i++) {
            double g = 1;
            if (i < a) g = i / (double) a;
            if (i > end - r) g *= Math.max(0, (end - i) / (double) r);
            x[i] *= (float) g;
        }
    }

    static void gain(float[] x, double g) {
        for (int i = 0; i < x.length; i++) x[i] *= (float) g;
    }

    /** Reverb de Schroeder: quatro pentes e dois passa-tudo. {@code size} de 0 a 1. */
    static float[] reverb(float[] in, double mix, double size) {
        int[] combs = {1116, 1188, 1277, 1356};
        int[] alls = {556, 441};
        double fb = 0.70 + 0.26 * size;
        float[] wet = new float[in.length];
        for (int c : combs) {
            int len = (int) (c * (0.6 + size));
            float[] buf = new float[len];
            int idx = 0;
            double damp = 0;
            for (int i = 0; i < in.length; i++) {
                float out = buf[idx];
                damp = out * 0.6 + damp * 0.4;
                buf[idx] = (float) (in[i] + damp * fb);
                wet[i] += out * 0.25f;
                idx = (idx + 1) % len;
            }
        }
        for (int c : alls) {
            float[] buf = new float[c];
            int idx = 0;
            for (int i = 0; i < wet.length; i++) {
                float b = buf[idx];
                float y = -wet[i] + b;
                buf[idx] = (float) (wet[i] + b * 0.5);
                wet[i] = y;
                idx = (idx + 1) % c;
            }
        }
        float[] out = new float[in.length];
        for (int i = 0; i < in.length; i++) out[i] = (float) (in[i] * (1 - mix) + wet[i] * mix);
        return out;
    }

    /** Normaliza o pico para {@code peak}. */
    static void normalize(float[] x, double peak) {
        double max = 1e-9;
        for (float v : x) max = Math.max(max, Math.abs(v));
        gain(x, peak / max);
    }

    /**
     * Emenda o fim no começo para o loop não estalar: os últimos {@code seconds} entram em
     * fade sobre o começo, e o arquivo encurta esse tanto. A última amostra continua na primeira.
     */
    static float[] seam(float[] x, double seconds) {
        int n = Math.min(x.length / 4, at(seconds));
        float[] out = java.util.Arrays.copyOf(x, x.length - n);
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            out[i] = (float) (x[i] * t + x[x.length - n + i] * (1 - t));
        }
        return out;
    }

    // ---------------------------------------------------------------- arquivo

    /** WAV PCM 16 bits mono. */
    static void writeWav(Path file, float[] x) throws IOException {
        Files.createDirectories(file.getParent());
        ByteBuffer data = ByteBuffer.allocate(x.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : x) data.putShort((short) Math.round(Math.max(-1, Math.min(1, v)) * 32767));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()).putInt(36 + x.length * 2).put("WAVE".getBytes());
        h.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1).putInt(SR).putInt(SR * 2)
                .putShort((short) 2).putShort((short) 16);
        h.put("data".getBytes()).putInt(x.length * 2);
        out.write(h.array());
        out.write(data.array());
        Files.write(file, out.toByteArray());
    }
}
