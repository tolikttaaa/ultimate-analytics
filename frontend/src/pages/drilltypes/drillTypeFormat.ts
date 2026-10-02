import type { DrillType } from '../../api/types'

export type DrillKind = DrillType['kind']

export const KIND_LABELS: Record<DrillKind, string> = { DRILL: 'Drill', GAME: 'Game', WARMUP: 'Warm-up', REST: 'Rest' }

/** Distinct colours offered for new drill types; any `#RRGGBB` works. */
export const PALETTE = ['#e65100', '#00897b', '#1565c0', '#6a1b9a', '#8d6e63', '#c62828', '#2e7d32', '#f9a825']

/** A code suggested by a name: upper case letters, digits and `_`, e.g. `Cutting 1v1` → `CUTTING_1V1`. */
export function codeFromName(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
}

/** The first palette colour no drill type uses yet. */
export function nextColor(drillTypes: DrillType[]): string {
  const used = new Set(drillTypes.map((type) => type.color.toLowerCase()))
  return PALETTE.find((color) => !used.has(color)) ?? PALETTE[drillTypes.length % PALETTE.length]
}
