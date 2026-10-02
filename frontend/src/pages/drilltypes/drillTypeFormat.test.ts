import { describe, expect, it } from 'vitest'
import { drillType } from '../../test/data'
import { codeFromName, nextColor, PALETTE } from './drillTypeFormat'

describe('codeFromName', () => {
  it('suggests an upper case code of letters, digits and _', () => {
    expect(codeFromName('Cutting 1v1')).toBe('CUTTING_1V1')
    expect(codeFromName('  Warm-up / Mobility ')).toBe('WARM_UP_MOBILITY')
    expect(codeFromName('Déjà vu')).toBe('DEJA_VU')
    expect(codeFromName('—')).toBe('')
  })
})

describe('nextColor', () => {
  it('takes the first unused palette colour', () => {
    expect(nextColor([])).toBe(PALETTE[0])
    expect(nextColor([drillType({ color: PALETTE[0].toUpperCase() })])).toBe(PALETTE[1])
  })
})
