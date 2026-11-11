#!/usr/bin/env bash
# Gera os retratos do M7 na Dreamina (canvas "ETRAVA arte") e baixa os originais para arte-bruta/retratos.
# Depois: gradlew importPortraits. Uso: bash tools/dreamina/retratos_m7.sh [id ...]
cd "$(dirname "$0")/../.."
STY="Style: 16-bit pixel art game portrait, chunky clearly visible square pixels, limited muted palette of ochre, rust, faded olive green, warm grey and dark browns, strong readable shapes, flat shading with two or three tones per color, dark outline, warm lantern light from the upper left. No text, no watermark, no frame."
TAIL="bust, three-quarter view, plain flat dark brown background. Dark fantasy rural village, late medieval, no modern items."
declare -A D
D[nalia]="a thin girl of about fourteen, miller's daughter, flour-dusted linen dress, long dark hair loosely tied, dark circles under her eyes but a small stubborn smile"
D[curandeira]="a woman in her fifties, village herb healer, grey hair under a faded olive headscarf, bundles of dried herbs hanging from her shawl, sharp appraising eyes"
D[estranho]="a weathered man in his forties, traveller from far away, dusty dark travelling coat with high collar, close-cropped greying hair, a folded paper held against his chest, polite but searching expression"
D[registrador]="a clean-shaven man in his fifties, official registrar from an industrial city, buttoned dark grey wool coat with brass buttons, small round spectacles, thin lips, ledger under his arm, faint condescension"
D[agrimensora]="a woman in her thirties, land surveyor from an industrial city, practical brown coat with leather straps, short dark hair, a measuring cord coiled over her shoulder, curious and quietly troubled look"
D[comerciante_posto]="a broad man in his forties, salt-road merchant of a trading post, sun-darkened skin, faded rust-red headwrap, salt-stained vest with many pockets, shrewd friendly half-smile"
D[bento]="a heavyset farmer in his fifties, straw hat pushed back, sunburnt face, stubbly jaw, coarse olive tunic, arms crossed, stubborn scowl"
D[gil]="a lean farmer in his thirties, thin moustache, patched ochre tunic, hands calloused, nervous defensive look"
D[pipo]="a boy of about nine, freckled, messy straw-colored hair, oversized patched tunic, scraped knees, guilty innocent eyes"
D[lauro]="a jovial heavy man in his forties, gambler at the village tavern, red nose, loose collar, a pair of bone dice between his fingers, teasing grin"
D[ciro]="a sour skinny man in his forties, gambler at the village tavern, hollow cheeks, worn grey cap, suspicious squint"
D[anselmo]="a very old man, bald with long white side hair, deep wrinkles, wool blanket over his shoulders, cloudy but amused eyes, as if he knows a joke no one else does"
D[lina]="a young woman around twenty, tavern helper, auburn hair in a braid, rolled-up linen sleeves, apron, tired but warm eyes"
D[tobias]="a big young porter around twenty-five at a trading post, shaved head, thick neck, rope over his shoulder, simple friendly face"
D[vessa]="a hard-faced woman in her thirties, hired guard of a salt-road trading post, scar across her eyebrow, padded leather jerkin, short spear shaft over her shoulder, watchful eyes"
D[firmino]="a gaunt man in his sixties, village gravedigger, dirt under his nails, hunched shoulders, faded black shawl, gentle melancholy face"
D[estudioso]="a scholarly man in his fifties, travelling learned man, long grey coat with ink stains, satchel of books, trimmed beard, gentle attentive eyes"
IDS=("$@"); [ ${#IDS[@]} -eq 0 ] && IDS=("${!D[@]}")
for id in "${IDS[@]}"; do
  out="arte-bruta/retratos/$id.png"
  [ -f "$out" ] && { echo "já existe: $id"; continue; }
  json=$(dreamina-canvas node create image --format json --non-interactive --model seedream_5.5 --mode t2i \
    --ratio 3:4 --resolution 1.5K --title "retrato $id" --prompt "Portrait of ${D[$id]}, $TAIL $STY" \
    --run --wait --credit-ceiling 35 2>&1)
  res=$(echo "$json" | grep -o '"resourceId":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ -z "$res" ]; then echo "FALHOU: $id"; echo "$json" | head -c 400; echo; continue; fi
  dreamina-canvas resource download "$res" -o "$out" --format json > /dev/null && echo "ok: $id"
done
