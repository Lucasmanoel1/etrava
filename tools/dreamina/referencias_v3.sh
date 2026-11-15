#!/usr/bin/env bash
# V3: referências das criaturas na Dreamina (canvas "ETRAVA arte"), t2i com Seedream 5.5.
# Guiam a modelagem no Blender, nunca vão para o jogo. Originais em arte-bruta/referencias/v3.
# O vulto não pode confirmar nada da camada V2/V3: sem rosto, sem forma humana inteira, sem símbolo.
# Uso: bash tools/dreamina/referencias_v3.sh [id ...]
cd "$(dirname "$0")/../.."
REF="plain flat light grey background, clean concept art, muted earthy desaturated palette (warm grey, ochre, faded olive, dark brown), soft even lighting, dark fantasy, no text, no watermark."
declare -A P
P[lobo]="Creature turnaround reference sheet of a lean grey winter wolf, hungry and gaunt: the same animal shown full body in side view, front view and back view, standing. Long legs, narrow chest with visible ribs, thick ruff of fur at the neck and shoulders, ragged grey-brown coat darker along the back, pale belly and legs, long narrow muzzle, upright pointed ears, bushy tail hanging low. Realistic proportions. $REF"
P[lobo_poses]="Animation key poses sheet of the same lean grey winter wolf in side view: trotting, running at full gallop, crouched before a lunge, mid-air lunge with jaws open, recoiling from a hit, lying down curled. Ragged grey-brown coat, pale belly, bushy tail. $REF"
P[vulto]="Creature concept sheet of an unexplained thing that lives in a buried stone structure: a tall hunched mass of dark wet earth, tangled pale roots and plant fibres and torn grey cloth strips, with very thin long limbs like bundles of roots, no face, no eyes, no recognizable human shape, wisps of dark smoke trailing from it, its outline breaking apart at the edges. Shown in front view, side view and back view. Ominous, quiet, not gory. $REF"
IDS=("$@"); [ ${#IDS[@]} -eq 0 ] && IDS=(lobo lobo_poses vulto)
mkdir -p arte-bruta/referencias/v3
gasto=0
for id in "${IDS[@]}"; do
  out="arte-bruta/referencias/v3/$id.png"
  [ -f "$out" ] && { echo "já existe: $id"; continue; }
  json=$(dreamina-canvas node create image --format json --non-interactive --model seedream_5.5 --mode t2i \
    --ratio 4:3 --resolution 1.5K --title "V3 ref_$id" --prompt "${P[$id]}" --run --wait --credit-ceiling 35 2>&1)
  rid=$(echo "$json" | grep -o '"resourceId":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ -z "$rid" ]; then echo "FALHOU: $id"; echo "$json" | head -c 400; echo; continue; fi
  dreamina-canvas resource download "$rid" -o "$out" --format json > /dev/null && { echo "ok: $id"; gasto=$((gasto + 30)); }
done
echo "gasto estimado nesta rodada: $gasto créditos"
