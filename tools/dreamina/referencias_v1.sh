#!/usr/bin/env bash
# V1: referências (pranchas e espécies) e texturas de material na Dreamina (canvas "ETRAVA arte").
# Originais em arte-bruta/referencias e arte-bruta/texturas (fora do git); as texturas passam
# depois pelo funil (gradlew importTextures). Uso: bash tools/dreamina/referencias_v1.sh [id ...]
cd "$(dirname "$0")/../.."
REF="plain flat light grey background, clean concept art, muted earthy desaturated palette (ochre, rust, faded olive, warm grey, dark brown), soft even lighting, late medieval rural village, dark fantasy, no modern items, no text, no watermark."
TEX="seamless tileable texture, top-down orthographic view, flat even lighting, no shadows, no perspective, no objects, fills the whole frame edge to edge, muted desaturated natural colors, no text."
declare -A P K
K[ref_aprendiz]=ref; P[ref_aprendiz]="Character turnaround reference sheet: the same character shown full body in front view, side view and back view, standing straight with arms relaxed. A lean young man of about seventeen, apprentice toolmaker: messy dark brown hair, pale undyed linen shirt with sleeves rolled to the forearm, dark brown wool trousers, worn brown leather ankle boots, a thin leather belt with a small pouch, a leather satchel strap across his chest. $REF"
K[ref_avel]=ref; P[ref_avel]="Character turnaround reference sheet: the same character shown full body in front view, side view and back view, standing straight with arms relaxed. A man in his late fifties, village toolmaker: short grey beard, rust-brown wool cap, small round wire spectacles pushed up on the forehead, olive green shirt with grey undersleeves rolled up, heavy dark brown leather work apron with shoulder straps and a tool pocket, dark brown trousers, sturdy boots, slightly stooped shoulders. $REF"
K[ref_carvalho]=ref; P[ref_carvalho]="Botanical reference illustration of a mature European oak tree, whole tree from roots to crown, broad irregular rounded crown built of distinct dense leaf clusters with gaps showing branches, thick gnarled dark grey-brown trunk splitting into crooked limbs, side view. $REF"
K[ref_betula]=ref; P[ref_betula]="Botanical reference illustration of a silver birch tree, whole tree from roots to crown, slender white trunk with black horizontal marks and dark cracked base, light airy crown of small yellow-green leaves on thin drooping twigs, side view. $REF"
K[ref_casa]=ref; P[ref_casa]="Concept art of a late medieval village toolmaker's workshop house seen in three-quarter view from above (isometric angle): rectangular house, low rough fieldstone base, half-timbered walls with dark oak beams and pale lime plaster, steep thick thatched gable roof with soft rounded eaves and a bound ridge, stone chimney, small shuttered windows, heavy plank door, a few barrels by the wall. $REF"
K[tex_casca]=tex; P[tex_casca]="Oak tree bark texture, deep vertical furrows and ridges, grey-brown. $TEX"
K[tex_folha]=tex; P[tex_folha]="Dense mass of small oak leaves seen from above, overlapping lobed leaves, olive green with darker gaps. $TEX"
K[tex_colmo]=tex; P[tex_colmo]="Old thatched roof straw texture, long bundles of reed straw running in one direction, weathered ochre and grey-brown. $TEX"
K[tex_reboco]=tex; P[tex_reboco]="Old lime plaster wall texture, rough hand-applied plaster with small cracks and stains, pale warm off-white. $TEX"
K[tex_pedra]=tex; P[tex_pedra]="Rough fieldstone wall texture, irregular rounded stones with dark mortar joints, warm grey. $TEX"
K[tex_grama]=tex; P[tex_grama]="Short meadow grass texture seen from above, uneven tufts, a few dry blades, muted olive green. $TEX"
K[tex_terra]=tex; P[tex_terra]="Packed dirt path texture seen from above, trodden earth with small pebbles and cracks, brown. $TEX"
K[tex_lajota]=tex; P[tex_lajota]="Old irregular flagstone and cobblestone paving texture seen from above, worn rounded stones with dirt in the joints, warm grey. $TEX"
K[tex_linho]=tex; P[tex_linho]="Coarse undyed linen fabric texture, visible weave, slight wrinkles, pale beige. $TEX"
K[tex_couro]=tex; P[tex_couro]="Worn dark brown leather texture, creases and scuffs. $TEX"
IDS=("$@"); [ ${#IDS[@]} -eq 0 ] && IDS=(ref_aprendiz ref_avel ref_carvalho ref_betula ref_casa tex_casca tex_folha tex_colmo tex_reboco tex_pedra tex_grama tex_terra tex_lajota tex_linho tex_couro)
mkdir -p arte-bruta/referencias arte-bruta/texturas
for id in "${IDS[@]}"; do
  if [ "${K[$id]}" = tex ]; then out="arte-bruta/texturas/${id#tex_}.png"; ratio=1:1; res=1.5K
  else out="arte-bruta/referencias/${id#ref_}.png"; ratio=4:3; res=1.5K; fi
  [ -f "$out" ] && { echo "já existe: $id"; continue; }
  json=$(dreamina-canvas node create image --format json --non-interactive --model seedream_5.5 --mode t2i \
    --ratio $ratio --resolution $res --title "V1 $id" --prompt "${P[$id]}" --run --wait --credit-ceiling 35 2>&1)
  rid=$(echo "$json" | grep -o '"resourceId":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ -z "$rid" ]; then echo "FALHOU: $id"; echo "$json" | head -c 400; echo; continue; fi
  dreamina-canvas resource download "$rid" -o "$out" --format json > /dev/null && echo "ok: $id"
done
