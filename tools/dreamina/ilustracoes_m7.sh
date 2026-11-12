#!/usr/bin/env bash
# Ilustrações grandes do M7 (menu, mapa da região, painéis do epílogo) na Dreamina, com o modelo
# mais forte (Seedream 5.0 Pro). Originais em arte-bruta/ilustracoes; depois: gradlew importIllustrations.
# Uso: bash tools/dreamina/ilustracoes_m7.sh [id ...]
cd "$(dirname "$0")/../.."
mkdir -p arte-bruta/ilustracoes
STY="Style: detailed 16-bit pixel art, chunky clearly visible square pixels, limited muted palette of ochre, rust, faded olive green, warm grey, dark browns and deep night blue, flat shading with two or three tones per color, dark fantasy, late medieval rural, melancholic and quiet. No text, no letters, no watermark, no frame, no people in the foreground."
declare -A P R
P[menu]="A small dark fantasy village at dusk seen from a low hill: thatched half-timbered houses around a square with a stone well, warm amber lights in a few windows, a mill, an old dead tree in a yard, fields with stakes, a dirt road leaving east through sparse trees toward a distant crossroads, heavy grey clouds, the last light low on the horizon"
R[menu]="16:9"
P[mapa]="Top-down illustrated map on aged stained parchment of a small rural region: on the left a village of thatched houses around a central square with a well, a small fenced cemetery to the northwest, an orchard to the southwest, a mill with a single tree in its yard to the south, two tilled fields with a boundary strip between them to the southeast; a dirt road leaves the village to the east through sparse woods with a sharp bend, and ends at a crossroads trading post with a long house and stalls on the right side, where another road crosses north to south. Hand-inked outlines, faded colors, small simple icons, empty margins"
R[mapa]="16:9"
P[epilogo_vila]="Wide view of a quiet village square at dawn after a long month, a stone well, a bench, thatched roofs wet with dew, smoke rising from one chimney, an empty road leading out"
R[epilogo_vila]="16:9"
P[epilogo_arvore]="An old tree in the yard of a stone mill at twilight, a small wooden bench beneath it, a few new leaves on twisted branches, a lit window in the mill, fireflies"
R[epilogo_arvore]="16:9"
P[epilogo_estrada]="A dirt road at dusk running east through sparse woods toward a distant crossroads with a lantern on a post, wheel ruts, a lone signpost, long shadows, red and violet sky"
R[epilogo_estrada]="16:9"
IDS=("$@"); [ ${#IDS[@]} -eq 0 ] && IDS=(menu mapa epilogo_vila epilogo_arvore epilogo_estrada)
for id in "${IDS[@]}"; do
  out="arte-bruta/ilustracoes/$id.png"
  [ -f "$out" ] && { echo "já existe: $id"; continue; }
  json=$(dreamina-canvas node create image --format json --non-interactive --model seedream_5.0_pro --mode t2i \
    --ratio "${R[$id]}" --resolution 2K --title "ilustracao $id" --prompt "${P[$id]}. $STY" \
    --run --wait --credit-ceiling 80 2>&1)
  res=$(echo "$json" | grep -o '"resourceId":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ -z "$res" ]; then echo "FALHOU: $id"; echo "$json" | head -c 600; echo; continue; fi
  credits=$(echo "$json" | grep -o '"maxCredits":[0-9]*' | head -1)
  dreamina-canvas resource download "$res" -o "$out" --format json > /dev/null && echo "ok: $id ($credits)"
done
