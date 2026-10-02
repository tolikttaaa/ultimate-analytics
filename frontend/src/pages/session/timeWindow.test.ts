import { describe, expect, it } from 'vitest'
import { parseWindow } from './timeWindow'

const parse = (query: string) => parseWindow(new URLSearchParams(query), 1800)

describe('parseWindow', () => {
  it('reads from and to of the URL', () => {
    expect(parse('from=600&to=1200')).toEqual([600, 1200])
    expect(parse('from=0&to=1800')).toEqual([0, 1800])
  })

  it('ignores missing, malformed, empty and out-of-session windows', () => {
    for (const query of ['', 'from=600', 'from=a&to=1200', 'from=-5&to=10', 'from=1.5&to=10', 'from=600&to=600', 'from=700&to=600', 'from=0&to=1801']) {
      expect(parse(query)).toBeNull()
    }
  })
})
