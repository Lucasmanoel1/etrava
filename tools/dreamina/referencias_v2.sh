#!/usr/bin/env bash
# V2: pranchas de referência (frente/lado/costas) dos humanos, na Dreamina (canvas "ETRAVA arte").
# Cada prancha parte do retrato do personagem (i2i, Seedream 5.5) para não divergir dele; a
# descrição repete a ficha (tools/blender/personagens/<id>.yaml). Guiam a modelagem, nunca vão
# para o jogo. Originais em arte-bruta/referencias/v2 (fora do git).
# Uso: bash tools/dreamina/referencias_v2.sh [id ...]
cd "$(dirname "$0")/../.."
PID=118e2115-5fcb-4843-9839-dad8999ea1ba
REF="plain flat light grey background, clean concept art, muted earthy desaturated palette (ochre, rust, faded olive, warm grey, dark brown), soft even lighting, late medieval rural village, dark fantasy, no modern items, no text, no watermark."
declare -A P R
R[agrimensora]=agrimensora; P[agrimensora]="A lean woman of about thirty, land surveyor from the city: dark hair tied back, brown tunic to mid-thigh, short red cloak over the shoulders, grey trousers, tall leather boots, leather belt with a satchel, a coil of knotted rope over one shoulder, holding a wooden measuring staff taller than her head."
R[aldea]=aldea; P[aldea]="A slender young widow of about twenty-six who keeps an orchard: rust-red hood always up, long brown braid, undyed linen blouse, brown laced bodice, long brown skirt to the ankles, small leather shoes, carrying a wicker basket on her arm."
R[anselmo]=anselmo; P[anselmo]="A very old man of about eighty-four, bald on top with long white hair on the sides and a long white beard, deeply stooped, a red plaid wool blanket over his shoulders, brown tunic, grey trousers, worn boots, leaning on a wooden walking stick."
R[bento]=bento; P[bento]="A broad heavy farmer of about forty-five with a red face: wide-brimmed straw hat, olive shirt, brown vest, red neckerchief, brown trousers, leather boots, chest pushed forward, holding a hoe."
R[ciro]=ciro; P[ciro]="A thin man of about thirty-eight with hunched shoulders and tired eyes: flat grey-blue cap, faded red tunic, brown hood fallen on his back, grey trousers, worn boots, hands close to the body."
R[comerciante]=comerciante_posto; P[comerciante]="A stout merchant of about forty-eight with dark skin and a moustache: red bandana on his head, linen shirt with rolled sleeves, olive padded vest with many pockets, brown neckerchief, brown trousers, boots, leather belt and pouch, a little belly, confident."
R[curandeira]=curandeira; P[curandeira]="An old village healer woman of about sixty, slightly stooped: olive green headscarf, red plaid shawl, olive blouse, long brown skirt, small boots, a leather belt with bundles of dried herbs hanging from it."
R[estranho]=estranho; P[estranho]="A tall thin tired man of about fifty, a stranger from far away: short grey hair, grey stubble beard, long dark grey travel coat down to the calves, brown shirt, grey trousers, tall boots, holding a folded letter with a red wax seal."
R[estudioso]=estudioso; P[estudioso]="A scholar of about fifty-five: dark wide-brimmed hat, grey full beard, grey travelling cape to the knees, brown tunic, brown trousers, tall boots, leather belt and book satchel, holding an open book."
R[firmino]=firmino; P[firmino]="A tall thin stooped gravedigger of about sixty-six: thin long grey hair, grey stubble, black tunic and black trousers, a pale undyed wool scarf wrapped around the neck, leather boots, carrying a wooden spade on his shoulder."
R[gil]=gil; P[gil]="A thin farmer of about forty with drooping shoulders: short dark hair, dark moustache, rust-red tunic, pale neckerchief, brown trousers, leather belt, boots, holding a short sickle."
R[lauro]=lauro; P[lauro]="A very fat cheerful man of about forty-four with a red nose and stubble: small red skullcap, olive tunic over a big belly, red neckerchief, brown trousers, short boots, a ring on his finger, holding dice."
R[lina]=lina; P[lina]="A young woman of about seventeen who serves at the tavern: long red braid, linen blouse with puffed sleeves, rust-red laced bodice, long brown skirt, short linen apron at the waist, small boots, holding a wooden tray."
R[moleiro]=moleiro; P[moleiro]="A miller of about fifty with heavy shoulders: pale cloth band tied around the forehead, short grey hair and short grey beard, brown tunic and grey trousers dusted with flour, rust-red hood fallen on his back, boots."
R[nalia]=nalia; P[nalia]="A quiet slender young woman, the miller's daughter, shy posture with the head slightly lowered: long loose black hair, plain undyed wool dress to the ankles, brown hood resting on her back, small leather boots."
R[pipo]=pipo; P[pipo]="A small boy of about eight with a big head for his body: messy blond hair, brown hooded tunic too big for him with the hood down, grey trousers, little boots, a small leather pouch, pockets stuffed with things. Child proportions."
R[registrador]=registrador; P[registrador]="A thin pale city official of about forty-two, rigid posture: black hair combed flat, small round spectacles, tall black hat, long black coat buttoned up to the neck down to the knees, black trousers, polished boots, a leather document case under his arm."
R[taberneira]=taberneira; P[taberneira]="A tavern keeper woman of about forty-two: pale linen headscarf over red hair, dusty rose shawl, olive blouse with rolled sleeves, long brown skirt, long pale linen apron, small boots, holding a pewter tankard, one hand on her hip."
R[tobias]=tobias; P[tobias]="A huge bald porter of about thirty, the tallest man in the village, thick neck and big arms but shy posture: olive shirt with rolled sleeves, rust-red hood fallen on his back, brown trousers, leather belt, boots, a coil of rope over one shoulder."
R[vessa]=vessa; P[vessa]="A woman guard of about thirty-two, upright and alert: dark hair tied back, brown leather jerkin with leather shoulder pads, short grey cloak, grey trousers, tall leather boots, belt, holding a spear taller than herself."
R[viajante]=viajante; P[viajante]="A wandering traveller of about forty-five: deep brown hood shading the face, brown full beard, long brown travel cloak with a bronze clasp, brown tunic, grey trousers, tall boots, carrying a walking staff with a cloth bundle tied to its top."
R[saqueador]=; P[saqueador]="A road bandit of about thirty, leaning forward, ready to run: black hood, a dark cloth covering the mouth and nose, patched brown leather jerkin with mismatched red patches, olive tunic, brown trousers, boots, belt, holding a hatchet."
R[aprendiz]=aprendiz; P[aprendiz]="A lean young man of about seventeen, apprentice toolmaker: messy dark brown hair, pale undyed linen tunic to mid-thigh with sleeves rolled up, dark brown wool trousers, worn brown leather boots, a thin belt, a leather satchel strap across his chest."
IDS=("$@"); [ ${#IDS[@]} -eq 0 ] && IDS=(agrimensora aldea anselmo bento ciro comerciante curandeira estranho estudioso firmino gil lauro lina moleiro nalia pipo registrador taberneira tobias vessa viajante saqueador)
mkdir -p arte-bruta/referencias/v2
gasto=0
for id in "${IDS[@]}"; do
  out="arte-bruta/referencias/v2/$id.png"
  [ -f "$out" ] && { echo "já existe: $id"; continue; }
  if [ -n "${R[$id]}" ]; then
    up=$(dreamina-canvas resource upload --file "arte-bruta/retratos/${R[$id]}.png" --project-id $PID --format json --non-interactive --yes 2>&1)
    rr=$(echo "$up" | grep -o '"resourceId":"[^"]*"' | head -1 | cut -d'"' -f4)
    [ -z "$rr" ] && { echo "FALHOU upload: $id"; continue; }
    prompt="Character turnaround reference sheet of the person in {{res:$rr}}: the same person shown full body, head to boots, in front view, side view and back view, standing straight with arms relaxed. Keep the face, hair and clothing colors from the reference portrait. ${P[$id]} $REF"
    args=(--mode i2i --ref "res:$rr")
  else
    prompt="Character turnaround reference sheet: the same person shown full body, head to boots, in front view, side view and back view, standing straight with arms relaxed. ${P[$id]} $REF"
    args=(--mode t2i)
  fi
  json=$(dreamina-canvas node create image --format json --non-interactive --model seedream_5.5 "${args[@]}" \
    --ratio 4:3 --resolution 1.5K --title "V2 ref_$id" --prompt "$prompt" --run --wait --credit-ceiling 35 2>&1)
  rid=$(echo "$json" | grep -o '"resourceId":"[^"]*"' | grep -v "${rr:-x}" | head -1 | cut -d'"' -f4)
  if [ -z "$rid" ]; then echo "FALHOU: $id"; echo "$json" | head -c 400; echo; continue; fi
  dreamina-canvas resource download "$rid" -o "$out" --format json > /dev/null && { echo "ok: $id"; gasto=$((gasto + 30)); }
done
echo "gasto estimado nesta rodada: $gasto créditos"
