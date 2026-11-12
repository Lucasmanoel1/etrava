package etrava.tools.audio;

import static etrava.tools.audio.Synth.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gera o áudio PROVISÓRIO do jogo por síntese ({@code gradlew generateAudio}, D-071): música
 * (RF-401), ambiente por área e período (RF-400) e efeitos (RF-402), em WAV, mais o manifesto
 * {@code assets/manifest/audio.yaml}. Qualquer arquivo pode ser trocado pelo definitivo
 * mantendo o ID (RF-423). Tudo com semente fixa: rodar de novo dá o mesmo som.
 */
public final class GenerateAudio {
    private static final String SOURCE = "Gerado por código (tools: GenerateAudio) — áudio provisório";
    private final Path assets;
    private final StringBuilder manifest = new StringBuilder();

    private GenerateAudio(Path assets) { this.assets = assets; }

    public static void main(String[] args) throws IOException {
        new GenerateAudio(Path.of(args.length > 0 ? args[0] : "assets")).run();
    }

    private void run() throws IOException {
        manifest.append("# GERADO por `gradlew generateAudio` — não edite à mão.\n")
                .append("# Áudio provisório feito por síntese (WAV, mono, 22 kHz). Para trocar pelo definitivo,\n")
                .append("# repita o ID noutro arquivo de manifesto desta pasta, apontando para o novo arquivo.\n\n");
        // ---- música (RF-401)
        music("musica.vila", "vila", vila());
        music("musica.noite", "noite", noite());
        music("musica.casa", "casa", casa());
        music("musica.taverna", "taverna", taverna());
        music("musica.estrada", "estrada", estrada());
        music("musica.ruina", "ruina", ruina());
        music("musica.combate", "combate", combate());
        music("musica.menu", "menu", menu());
        // ---- ambiente por área e período (RF-400)
        ambience("amb.vila.dia", "vila_dia", amb(new Random(1), 40, 0.5, 6, 0, 0, 2));
        ambience("amb.vila.noite", "vila_noite", amb(new Random(2), 40, 0.35, 0, 1, 1, 0));
        ambience("amb.estrada.dia", "estrada_dia", amb(new Random(3), 40, 0.8, 3, 0, 0, 0));
        ambience("amb.estrada.noite", "estrada_noite", amb(new Random(4), 40, 0.6, 0, 1, 2, 0));
        ambience("amb.interior", "interior", interior(new Random(5), false));
        ambience("amb.taverna", "taverna", interior(new Random(6), true));
        ambience("amb.ruina", "ruina", ruinaAmb(new Random(7)));
        // ---- efeitos (RF-402)
        sfxSet("sfx.passo.terra", "passo_terra", r -> step(r, 180, 0.06, 0));
        sfxSet("sfx.passo.pedra", "passo_pedra", r -> step(r, 900, 0.04, 0.35));
        sfxSet("sfx.passo.madeira", "passo_madeira", r -> step(r, 320, 0.07, 0.6));
        sfx("sfx.ui.clique", "ui_clique", click(new Random(10)));
        sfx("sfx.ui.pagina", "ui_pagina", paper(new Random(11), 0.25));
        sfx("sfx.ui.anotado", "ui_anotado", pen(new Random(12)));
        sfx("sfx.porta", "porta", door(new Random(13)));
        sfx("sfx.moedas", "moedas", coins(new Random(14)));
        sfx("sfx.comer", "comer", crunch(new Random(15)));
        sfx("sfx.item", "item", paper(new Random(16), 0.12));
        sfx("sfx.trabalho", "trabalho", hammer(new Random(17)));
        sfx("sfx.combate.golpe", "golpe", whoosh(new Random(18), 0.22, 900, 2400));
        sfx("sfx.combate.esquiva", "esquiva", whoosh(new Random(19), 0.16, 1500, 3500));
        sfx("sfx.combate.acerto", "acerto", hit(new Random(20), 110));
        sfx("sfx.combate.dano", "dano", hit(new Random(21), 75));
        sfx("sfx.combate.lobo", "lobo", growl(new Random(22)));
        sfx("sfx.combate.uivo", "uivo", howl(new Random(23)));
        sfx("sfx.fenomeno.arvore", "arvore", shimmer(new Random(24)));
        sfx("sfx.fenomeno.eco", "eco", echoVoice(new Random(25)));
        sfx("sfx.derrota", "derrota", fall(new Random(26)));

        Path file = assets.resolve("manifest/audio.yaml");
        Files.writeString(file, manifest.toString(), StandardCharsets.UTF_8);
        System.out.println("Áudio provisório gerado em " + assets.resolve("audio").toAbsolutePath());
    }

    // ================================================================ música

    /** Escala modal: graus a partir da tônica. */
    private static final int[] DORIAN = {0, 2, 3, 5, 7, 9, 10}, AEOLIAN = {0, 2, 3, 5, 7, 8, 10},
            PHRYGIAN = {0, 1, 3, 5, 7, 8, 10}, MIXO = {0, 2, 4, 5, 7, 9, 10};

    private static double note(int root, int[] scale, int degree) {
        int oct = Math.floorDiv(degree, scale.length), d = Math.floorMod(degree, scale.length);
        return midi(root + oct * 12 + scale[d]);
    }

    /** Melodia que anda pela escala por graus próximos, com respiros; mesma semente = mesma frase. */
    private static int[] phrase(Random rnd, int len, int start, int lo, int hi) {
        int[] out = new int[len];
        int d = start;
        for (int i = 0; i < len; i++) {
            int step = rnd.nextDouble() < 0.7 ? (rnd.nextBoolean() ? 1 : -1) : (rnd.nextBoolean() ? 2 : -2);
            if (rnd.nextDouble() < 0.15) step = 0;
            d = Math.max(lo, Math.min(hi, d + step));
            out[i] = rnd.nextDouble() < 0.12 && i % 4 != 0 ? Integer.MIN_VALUE : d; // pausa
        }
        out[len - 1] = start;
        return out;
    }

    private float[] vila() {
        Random rnd = new Random(101);
        int root = 50; // ré
        double beat = 60.0 / 72, bars = 24;
        float[] mixBuf = buffer(bars * 4 * beat + 4);
        int[][] chords = {{0, 2, 4}, {-1, 1, 3}, {3, 5, 7}, {0, 2, 4}, {-2, 0, 2}, {-1, 1, 3}, {4, 6, 8}, {0, 2, 4}};
        for (int bar = 0; bar < bars; bar++) {
            int[] ch = chords[bar % chords.length];
            double t0 = bar * 4 * beat;
            mix(mixBuf, drone(note(root - 12, DORIAN, ch[0]), 4 * beat + 1, 500), at(t0), 0.18, false);
            int[] arp = {ch[0], ch[1], ch[2], ch[1] + 7, ch[2], ch[1], ch[0] + 7, ch[2]};
            for (int i = 0; i < 8; i++)
                mix(mixBuf, pluck(note(root, DORIAN, arp[i]), 1.6, 0.35, rnd), at(t0 + i * beat / 2), 0.35, false);
        }
        int[] a = phrase(new Random(7), 16, 7, 4, 11), b = phrase(new Random(8), 16, 9, 5, 12);
        int[][] form = {a, a, b, a};
        for (int s = 0; s < form.length; s++) {
            double t0 = (8 + s * 4) * 4 * beat;
            for (int i = 0; i < 16; i++) {
                if (form[s][i] == Integer.MIN_VALUE) continue;
                mix(mixBuf, flute(note(root, DORIAN, form[s][i]), beat * 0.95, rnd), at(t0 + i * beat), 0.22, false);
            }
        }
        return finish(mixBuf, 0.35, 0.6);
    }

    private float[] noite() {
        Random rnd = new Random(102);
        int root = 57; // lá
        double bar = 6.0;
        int bars = 13;
        float[] buf = buffer(bars * bar + 4);
        int[][] chords = {{0, 2, 4}, {-2, 0, 2}, {3, 5, 7}, {4, 6, 8}};
        for (int b = 0; b < bars; b++) {
            int[] ch = chords[b % chords.length];
            for (int n : ch) mix(buf, pad(note(root - 12, AEOLIAN, n), bar + 1.5, 700), at(b * bar), 0.12, false);
            for (int k = 0; k < 3; k++) {
                if (rnd.nextDouble() < 0.4) continue;
                double t = b * bar + rnd.nextDouble() * bar;
                mix(buf, bell(note(root + 12, AEOLIAN, ch[rnd.nextInt(3)] + (rnd.nextBoolean() ? 7 : 0)), 4.0), at(t), 0.16, false);
            }
        }
        return finish(buf, 0.55, 0.85);
    }

    private float[] casa() {
        Random rnd = new Random(103);
        int root = 53; // fá
        double beat = 60.0 / 58;
        int bars = 16;
        float[] buf = buffer(bars * 4 * beat + 4);
        int[][] chords = {{0, 2, 4}, {3, 5, 7}, {1, 3, 5}, {4, 6, 8}};
        for (int b = 0; b < bars; b++) {
            int[] ch = chords[b % chords.length];
            double t0 = b * 4 * beat;
            int[] arp = {ch[0], ch[2], ch[1] + 7, ch[2]};
            for (int i = 0; i < 4; i++)
                mix(buf, pluck(note(root, DORIAN, arp[i]), 2.2, 0.25, rnd), at(t0 + i * beat), 0.32, false);
            mix(buf, pluck(note(root - 12, DORIAN, ch[0]), 3.0, 0.2, rnd), at(t0), 0.3, false);
        }
        return finish(buf, 0.4, 0.5);
    }

    private float[] taverna() {
        Random rnd = new Random(104);
        int root = 55; // sol
        double beat = 60.0 / 104;
        int bars = 32;
        float[] buf = buffer(bars * 4 * beat + 3);
        int[][] chords = {{0, 2, 4}, {0, 2, 4}, {-3, -1, 1}, {4, 6, 8}, {3, 5, 7}, {0, 2, 4}, {4, 6, 8}, {0, 2, 4}};
        for (int b = 0; b < bars; b++) {
            int[] ch = chords[b % chords.length];
            double t0 = b * 4 * beat;
            mix(buf, pluck(note(root - 12, MIXO, ch[0]), 1.2, 0.3, rnd), at(t0), 0.4, false);
            mix(buf, pluck(note(root - 12, MIXO, ch[0] + 4), 1.0, 0.3, rnd), at(t0 + 2 * beat), 0.35, false);
            for (int k : new int[] {1, 3}) // batida no contratempo
                for (int n = 0; n < 3; n++)
                    mix(buf, pluck(note(root, MIXO, ch[n]), 0.6, 0.5, rnd), at(t0 + k * beat + n * 0.012), 0.22, false);
            // tambor: bum . tc bum tc .
            mix(buf, drum(80, 0.35, 0.2, rnd), at(t0), 0.5, false);
            mix(buf, drum(200, 0.12, 0.8, rnd), at(t0 + beat * 1.5), 0.25, false);
            mix(buf, drum(80, 0.35, 0.2, rnd), at(t0 + beat * 2), 0.45, false);
            mix(buf, drum(200, 0.12, 0.8, rnd), at(t0 + beat * 3), 0.25, false);
        }
        int[] a = phrase(new Random(21), 16, 7, 4, 11), b2 = phrase(new Random(22), 16, 9, 6, 13);
        int[][] form = {a, a, b2, a, a, b2, a, a};
        for (int s = 0; s < form.length; s++) {
            double t0 = s * 4 * 4 * beat;
            for (int i = 0; i < 16; i++) {
                if (form[s][i] == Integer.MIN_VALUE) continue;
                double len = (i % 4 == 3) ? beat * 1.8 : beat * 0.9;
                mix(buf, flute(note(root, MIXO, form[s][i]), len, rnd), at(t0 + i * beat), 0.2, false);
            }
        }
        return finish(buf, 0.25, 0.3);
    }

    private float[] estrada() {
        Random rnd = new Random(105);
        int root = 52; // mi
        double beat = 60.0 / 64;
        int bars = 20;
        float[] buf = buffer(bars * 4 * beat + 4);
        for (int b = 0; b < bars; b += 2) {
            mix(buf, drone(midi(root - 12), 8 * beat + 1.5, 450), at(b * 4 * beat), 0.2, false);
            mix(buf, drone(midi(root - 5), 8 * beat + 1.5, 450), at(b * 4 * beat), 0.14, false);
        }
        int[] a = phrase(new Random(31), 8, 7, 3, 11), b2 = phrase(new Random(32), 8, 4, 2, 9);
        int[][] form = {a, b2, a, b2, a};
        for (int s = 0; s < form.length; s++) {
            double t0 = s * 16 * beat + 2 * beat;
            for (int i = 0; i < 8; i++) {
                if (form[s][i] == Integer.MIN_VALUE) continue;
                mix(buf, flute(note(root, DORIAN, form[s][i]), beat * 1.9, rnd), at(t0 + i * 2 * beat), 0.24, false);
            }
        }
        for (int b = 0; b < bars; b++)
            if (rnd.nextDouble() < 0.6)
                mix(buf, pluck(note(root, DORIAN, rnd.nextInt(5)), 2.5, 0.3, rnd), at(b * 4 * beat + 3 * beat), 0.25, false);
        return finish(buf, 0.45, 0.7);
    }

    private float[] ruina() {
        Random rnd = new Random(106);
        int root = 38; // ré grave
        double len = 72;
        float[] buf = buffer(len + 5);
        for (double t = 0; t < len; t += 12) {
            mix(buf, drone(midi(root), 14, 260), at(t), 0.3, false);
            mix(buf, drone(midi(root + 6), 14, 220), at(t + 6), 0.1, false); // trítono, baixinho
        }
        for (int k = 0; k < 14; k++) {
            double t = rnd.nextDouble() * len;
            mix(buf, bell(note(root + 12, PHRYGIAN, rnd.nextInt(7)), 6.0), at(t), 0.14, false);
        }
        return finish(buf, 0.7, 0.95);
    }

    private float[] combate() {
        Random rnd = new Random(107);
        int root = 38;
        double beat = 60.0 / 128;
        int bars = 32;
        float[] buf = buffer(bars * 4 * beat + 2);
        int[] ost = {0, 0, 1, 0, -1, 0, 1, 2};
        for (int b = 0; b < bars; b++) {
            double t0 = b * 4 * beat;
            for (int i = 0; i < 8; i++)
                mix(buf, pluck(note(root + 12, PHRYGIAN, ost[i] + (b % 8 >= 6 ? 2 : 0)), 0.5, 0.6, rnd), at(t0 + i * beat / 2), 0.32, false);
            mix(buf, drum(55, 0.4, 0.25, rnd), at(t0), 0.7, false);
            mix(buf, drum(55, 0.4, 0.25, rnd), at(t0 + 2 * beat), 0.6, false);
            mix(buf, drum(55, 0.3, 0.25, rnd), at(t0 + 2.75 * beat), 0.4, false);
            mix(buf, drum(140, 0.15, 0.9, rnd), at(t0 + beat), 0.3, false);
            mix(buf, drum(140, 0.15, 0.9, rnd), at(t0 + 3 * beat), 0.3, false);
            if (b % 4 == 0) mix(buf, drone(midi(root), 4 * 4 * beat + 0.5, 600), at(t0), 0.2, false);
        }
        return finish(buf, 0.2, 0.35);
    }

    private float[] menu() {
        Random rnd = new Random(108);
        int root = 57;
        double beat = 60.0 / 60;
        int bars = 16;
        float[] buf = buffer(bars * 4 * beat + 5);
        int[][] chords = {{0, 2, 4}, {-2, 0, 2}, {3, 5, 7}, {-3, -1, 1}};
        for (int b = 0; b < bars; b++) {
            int[] ch = chords[b % chords.length];
            for (int n : ch) mix(buf, pad(note(root - 12, AEOLIAN, n), 4 * beat + 1.5, 800), at(b * 4 * beat), 0.1, false);
            mix(buf, pluck(note(root - 12, AEOLIAN, ch[0]), 3.5, 0.2, rnd), at(b * 4 * beat), 0.3, false);
        }
        int[] theme = {4, 3, 2, 0, 2, 3, 4, 7, 6, 4, 3, 4, Integer.MIN_VALUE, 2, 1, 0};
        for (int s = 1; s < 4; s++) {
            double t0 = s * 16 * beat;
            for (int i = 0; i < theme.length; i++) {
                if (theme[i] == Integer.MIN_VALUE) continue;
                float[] n = s == 2 ? flute(note(root, AEOLIAN, theme[i]), beat * 0.95, rnd) : bell(note(root + 12, AEOLIAN, theme[i]), 2.5);
                mix(buf, n, at(t0 + i * beat), s == 2 ? 0.22 : 0.15, false);
            }
        }
        return finish(buf, 0.5, 0.8);
    }

    /** Reverb, normalização e emenda de loop. */
    private static float[] finish(float[] buf, double wet, double room) {
        float[] out = reverb(buf, wet, room);
        lowpass(out, 7000);
        normalize(out, 0.8);
        return seam(out, 3.0);
    }

    // ================================================================ ambientes

    /** Vento, pássaros, grilos, coruja e martelo ao longe, na proporção pedida. */
    private static float[] amb(Random rnd, double len, double wind, int birds, int crickets, int owls, int hammers) {
        float[] buf = buffer(len);
        float[] w = brown(len, rnd);
        for (int i = 0; i < w.length; i++) {
            double t = i / (double) SR;
            w[i] *= (float) (0.6 + 0.4 * Math.sin(2 * Math.PI * t / 9.0) * Math.sin(2 * Math.PI * t / 23.0));
        }
        lowpass(w, 600);
        mix(buf, w, 0, wind * 0.7, true);
        for (int k = 0; k < birds * 4; k++) mix(buf, chirp(rnd), at(rnd.nextDouble() * len), 0.12, true);
        if (crickets > 0) mix(buf, crickets(len, rnd), 0, 0.08 * crickets, true);
        for (int k = 0; k < owls; k++) mix(buf, owl(rnd), at(rnd.nextDouble() * len), 0.18, true);
        for (int k = 0; k < hammers * 3; k++) {
            double t = rnd.nextDouble() * len;
            for (int j = 0; j < 3; j++) mix(buf, clank(rnd), at(t + j * 0.55), 0.05, true);
        }
        float[] out = reverb(buf, 0.25, 0.5);
        normalize(out, 0.55);
        return seam(out, 2.0);
    }

    private static float[] chirp(Random rnd) {
        double dur = 0.08 + rnd.nextDouble() * 0.1, f0 = 2500 + rnd.nextDouble() * 1800;
        float[] out = buffer(dur * 3);
        int notes = 1 + rnd.nextInt(3);
        for (int n = 0; n < notes; n++) {
            int off = at(n * dur * 1.1);
            double ph = 0;
            for (int i = 0; i < at(dur) && off + i < out.length; i++) {
                double t = i / (double) at(dur);
                ph += f0 * (1 + 0.3 * Math.sin(Math.PI * t)) / SR;
                out[off + i] += (float) (Math.sin(2 * Math.PI * ph) * Math.sin(Math.PI * t));
            }
        }
        return out;
    }

    private static float[] crickets(double len, Random rnd) {
        float[] out = buffer(len);
        for (int c = 0; c < 3; c++) {
            double f = 4200 + c * 350, rate = 28 + c * 3, phase = rnd.nextDouble();
            for (int i = 0; i < out.length; i++) {
                double t = i / (double) SR;
                double pulse = Math.max(0, Math.sin(2 * Math.PI * (rate * t + phase)));
                double gate = Math.sin(2 * Math.PI * (t / (1.3 + c * 0.4) + phase)) > 0.1 ? 1 : 0;
                out[i] += (float) (Math.sin(2 * Math.PI * f * t) * pulse * pulse * gate * 0.3);
            }
        }
        return out;
    }

    private static float[] owl(Random rnd) {
        float[] out = buffer(1.6);
        double f = 320 + rnd.nextDouble() * 40;
        for (int h = 0; h < 2; h++) {
            int off = at(h * 0.7);
            double dur = h == 0 ? 0.35 : 0.55;
            for (int i = 0; i < at(dur); i++) {
                double t = i / (double) SR;
                double env = Math.sin(Math.PI * t / dur);
                out[off + i] += (float) (Math.sin(2 * Math.PI * f * (1 - 0.05 * t) * t) * env * env);
            }
        }
        return out;
    }

    private static float[] clank(Random rnd) {
        float[] out = bell(900 + rnd.nextDouble() * 200, 0.4);
        return out;
    }

    private static float[] interior(Random rnd, boolean tavern) {
        double len = 36;
        float[] buf = buffer(len);
        float[] room = brown(len, rnd);
        lowpass(room, 180);
        mix(buf, room, 0, 0.5, true);
        // lareira: estalos e um chiado baixo
        float[] hiss = noise(len, rnd);
        bandpass(hiss, 2500, 0.7);
        for (int i = 0; i < hiss.length; i++) hiss[i] *= (float) (0.3 + 0.7 * Math.abs(Math.sin(i / (double) SR * 1.7)));
        mix(buf, hiss, 0, 0.03, true);
        for (int k = 0; k < 120; k++) {
            float[] crack = noise(0.01 + rnd.nextDouble() * 0.02, rnd);
            highpass(crack, 1500);
            envelope(crack, 0.0005, crack.length / (double) SR, 0.008);
            mix(buf, crack, at(rnd.nextDouble() * len), 0.15 + rnd.nextDouble() * 0.25, true);
        }
        if (tavern) {
            // murmúrio de vozes: ruído com formantes e sílabas
            for (int v = 0; v < 6; v++) {
                float[] voice = noise(len, rnd);
                bandpass(voice, 350 + rnd.nextDouble() * 400, 4);
                double rate = 3 + rnd.nextDouble() * 2.5, ph = rnd.nextDouble() * 10;
                for (int i = 0; i < voice.length; i++) {
                    double t = i / (double) SR;
                    double syll = Math.max(0, Math.sin(2 * Math.PI * (rate * t + ph))) * (Math.sin(2 * Math.PI * (t / 7 + ph)) > -0.3 ? 1 : 0);
                    voice[i] *= (float) syll;
                }
                mix(buf, voice, 0, 0.5, true);
            }
            for (int k = 0; k < 14; k++) mix(buf, bell(1800 + rnd.nextDouble() * 900, 0.25), at(rnd.nextDouble() * len), 0.05, true);
        }
        float[] out = reverb(buf, tavern ? 0.3 : 0.2, 0.35);
        normalize(out, 0.5);
        return seam(out, 2.0);
    }

    private static float[] ruinaAmb(Random rnd) {
        double len = 40;
        float[] buf = buffer(len);
        float[] rumble = brown(len, rnd);
        lowpass(rumble, 90);
        mix(buf, rumble, 0, 1.0, true);
        for (int k = 0; k < 22; k++) {
            float[] drip = buffer(0.15);
            double f = 900 + rnd.nextDouble() * 900;
            for (int i = 0; i < drip.length; i++) {
                double t = i / (double) SR;
                drip[i] = (float) (Math.sin(2 * Math.PI * f * (1 + 2 * Math.exp(-t * 60)) * t) * Math.exp(-t * 35));
            }
            mix(buf, drip, at(rnd.nextDouble() * len), 0.2, true);
        }
        float[] out = reverb(buf, 0.6, 0.95);
        normalize(out, 0.55);
        return seam(out, 2.0);
    }

    // ================================================================ efeitos

    private interface Maker { float[] make(Random rnd); }

    private static float[] step(Random rnd, double tone, double dur, double knock) {
        float[] n = noise(dur, rnd);
        lowpass(n, tone * 3);
        envelope(n, 0.002, dur, dur * 0.8);
        if (knock > 0) {
            float[] k = noise(dur, rnd);
            bandpass(k, tone, 6);
            envelope(k, 0.001, dur, dur * 0.7);
            mix(n, k, 0, knock * 3, false);
        }
        normalize(n, 0.6);
        return n;
    }

    private static float[] click(Random rnd) {
        float[] c = noise(0.03, rnd);
        bandpass(c, 2200, 3);
        envelope(c, 0.0005, 0.03, 0.025);
        normalize(c, 0.5);
        return c;
    }

    private static float[] paper(Random rnd, double dur) {
        float[] p = noise(dur, rnd);
        highpass(p, 1800);
        for (int i = 0; i < p.length; i++) p[i] *= (float) Math.sin(Math.PI * i / p.length);
        normalize(p, 0.45);
        return p;
    }

    private static float[] pen(Random rnd) {
        float[] p = noise(0.35, rnd);
        bandpass(p, 3200, 2);
        for (int i = 0; i < p.length; i++) {
            double t = i / (double) SR;
            p[i] *= (float) (Math.abs(Math.sin(2 * Math.PI * 9 * t)) * Math.sin(Math.PI * t / 0.35));
        }
        normalize(p, 0.4);
        return p;
    }

    private static float[] door(Random rnd) {
        float[] creak = buffer(0.7);
        double ph = 0;
        for (int i = 0; i < creak.length; i++) {
            double t = i / (double) SR;
            ph += (180 + 90 * Math.sin(t * 6) + rnd.nextDouble() * 30) / SR;
            creak[i] = (float) ((ph % 1) * 2 - 1);
        }
        bandpass(creak, 900, 5);
        envelope(creak, 0.05, 0.6, 0.2);
        float[] thud = drum(70, 0.25, 0.4, rnd);
        mix(creak, thud, at(0.55), 1.2, false);
        normalize(creak, 0.6);
        return creak;
    }

    private static float[] coins(Random rnd) {
        float[] out = buffer(0.6);
        for (int k = 0; k < 5; k++) mix(out, bell(2600 + rnd.nextDouble() * 1400, 0.25), at(k * 0.06 + rnd.nextDouble() * 0.03), 0.5, false);
        normalize(out, 0.5);
        return out;
    }

    private static float[] crunch(Random rnd) {
        float[] out = buffer(0.45);
        for (int k = 0; k < 3; k++) {
            float[] c = noise(0.07, rnd);
            bandpass(c, 1400, 1.5);
            envelope(c, 0.002, 0.07, 0.05);
            mix(out, c, at(k * 0.13), 1, false);
        }
        normalize(out, 0.5);
        return out;
    }

    private static float[] hammer(Random rnd) {
        float[] out = buffer(1.4);
        for (int k = 0; k < 3; k++) {
            mix(out, bell(1300 + rnd.nextDouble() * 100, 0.4), at(k * 0.45), 0.6, false);
            mix(out, drum(150, 0.1, 0.9, rnd), at(k * 0.45), 0.5, false);
        }
        normalize(out, 0.55);
        return out;
    }

    private static float[] whoosh(Random rnd, double dur, double f0, double f1) {
        float[] w = noise(dur, rnd);
        float[] out = buffer(dur);
        int seg = at(0.01);
        for (int s = 0; s * seg < w.length; s++) {
            float[] part = java.util.Arrays.copyOfRange(w, s * seg, Math.min(w.length, (s + 1) * seg + seg));
            double t = s * seg / (double) w.length;
            bandpass(part, f0 + (f1 - f0) * Math.sin(Math.PI * t), 1.2);
            for (int i = 0; i < seg && s * seg + i < out.length; i++) out[s * seg + i] = part[i];
        }
        for (int i = 0; i < out.length; i++) out[i] *= (float) Math.sin(Math.PI * i / out.length);
        normalize(out, 0.55);
        return out;
    }

    private static float[] hit(Random rnd, double pitch) {
        float[] out = drum(pitch, 0.3, 0.6, rnd);
        float[] crack = noise(0.04, rnd);
        highpass(crack, 2000);
        envelope(crack, 0.0005, 0.04, 0.03);
        mix(out, crack, 0, 0.5, false);
        normalize(out, 0.7);
        return out;
    }

    private static float[] growl(Random rnd) {
        float[] g = noise(0.8, rnd);
        bandpass(g, 220, 3);
        for (int i = 0; i < g.length; i++) {
            double t = i / (double) SR;
            g[i] *= (float) ((0.5 + 0.5 * Math.sin(2 * Math.PI * 32 * t)) * Math.sin(Math.PI * t / 0.8));
        }
        normalize(g, 0.6);
        return g;
    }

    private static float[] howl(Random rnd) {
        float[] out = buffer(2.6);
        double ph = 0;
        for (int i = 0; i < out.length; i++) {
            double t = i / (double) SR;
            double f = 420 + 160 * Math.sin(Math.PI * Math.min(1, t / 1.2)) - 80 * Math.max(0, t - 1.6);
            f *= 1 + 0.01 * Math.sin(2 * Math.PI * 5.5 * t);
            ph += f / SR;
            out[i] = (float) ((Math.sin(2 * Math.PI * ph) + 0.3 * Math.sin(4 * Math.PI * ph)) * Math.sin(Math.PI * t / 2.6));
        }
        float[] r = reverb(out, 0.5, 0.8);
        normalize(r, 0.45);
        return r;
    }

    private static float[] shimmer(Random rnd) {
        float[] out = buffer(3.0);
        for (int k = 0; k < 9; k++) mix(out, bell(midi(84 + new int[] {0, 3, 7, 10, 12, 15}[rnd.nextInt(6)]), 2.0), at(k * 0.2), 0.3, false);
        float[] r = reverb(out, 0.6, 0.9);
        envelope(r, 0.5, 3.0, 1.2);
        normalize(r, 0.45);
        return r;
    }

    /** A voz longe, chamando um nome que não é o seu (o "eco" da estrutura, D-063). */
    private static float[] echoVoice(Random rnd) {
        double dur = 2.4;
        float[] src = buffer(dur);
        double ph = 0;
        for (int i = 0; i < src.length; i++) {
            double t = i / (double) SR;
            double f = 190 + 30 * Math.sin(Math.PI * t / 1.1) - 25 * Math.max(0, t - 1.2);
            ph += f / SR;
            double saw = (ph % 1) * 2 - 1;
            double syll = Math.max(0, Math.sin(Math.PI * Math.min(1, t / 0.9))) * (t < 1.0 ? 1 : 0)
                    + Math.max(0, Math.sin(Math.PI * Math.max(0, (t - 1.2) / 1.1))) * (t > 1.2 ? 1 : 0);
            src[i] = (float) (saw * syll);
        }
        float[] a = src.clone(), b = src.clone();
        bandpass(a, 700, 6);
        bandpass(b, 1150, 6);
        for (int i = 0; i < src.length; i++) src[i] = a[i] + b[i] * 0.7f;
        lowpass(src, 1600);
        float[] r = reverb(src, 0.85, 0.98);
        normalize(r, 0.4);
        return r;
    }

    private static float[] fall(Random rnd) {
        float[] out = drum(60, 0.6, 0.5, rnd);
        mix(out, step(rnd, 180, 0.12, 0), at(0.15), 1.0, false);
        normalize(out, 0.6);
        return out;
    }

    // ================================================================ arquivos e manifesto

    private void music(String id, String name, float[] data) throws IOException {
        write("audio/musica/" + name + ".wav", data);
        entry(id, "music", "audio/musica/" + name + ".ogg", null);
    }

    private void ambience(String id, String name, float[] data) throws IOException {
        write("audio/ambiente/" + name + ".wav", data);
        entry(id, "music", "audio/ambiente/" + name + ".ogg", null);
    }

    private void sfx(String id, String name, float[] data) throws IOException {
        write("audio/efeitos/" + name + ".wav", data);
        entry(id, "sound", "audio/efeitos/" + name + ".ogg", null);
    }

    private void sfxSet(String id, String name, Maker maker) throws IOException {
        List<String> files = new ArrayList<>();
        for (int v = 1; v <= 3; v++) {
            String file = "audio/efeitos/" + name + "_" + v + ".wav";
            write(file, maker.make(new Random(name.hashCode() + v)));
            files.add(file.replace(".wav", ".ogg"));
        }
        entry(id, "sound", null, files);
    }

    private void entry(String id, String type, String file, List<String> files) {
        manifest.append("- id: ").append(id).append('\n').append("  type: ").append(type).append('\n');
        if (file != null) manifest.append("  file: ").append(file).append('\n');
        if (files != null) manifest.append("  files: [").append(String.join(", ", files)).append("]\n");
        manifest.append("  source: \"").append(SOURCE).append("\"\n").append("  license: proprio\n\n");
    }

    private void write(String relative, float[] data) throws IOException {
        writeWav(assets.resolve(relative), data);
    }
}
