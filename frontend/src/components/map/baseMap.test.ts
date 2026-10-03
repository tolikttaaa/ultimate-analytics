import { describe, expect, it } from 'vitest'
import { baseMapStyle } from './baseMap'

const vector = 'https://tiles.openfreemap.org/styles/liberty'
const dark = 'https://tiles.openfreemap.org/styles/dark'
const satellite = { tilesUrl: 'https://tiles.example.com/{z}/{x}/{y}.jpg?key=k', attribution: '© Example', maxZoom: 20, tileSize: 512 }

describe('baseMapStyle', () => {
  it('uses the vector style by URL', () => {
    expect(baseMapStyle({ vectorStyleUrl: vector, vectorStyleUrlDark: dark, satellite }, 'map')).toBe(vector)
  })

  it('wraps the satellite tiles in a raster style with their attribution', () => {
    expect(baseMapStyle({ vectorStyleUrl: vector, vectorStyleUrlDark: dark, satellite }, 'satellite')).toEqual({
      version: 8,
      sources: {
        satellite: { type: 'raster', tiles: [satellite.tilesUrl], tileSize: 512, maxzoom: 20, attribution: '© Example' },
      },
      layers: [{ id: 'satellite', type: 'raster', source: 'satellite' }],
    })
  })

  it('takes the dark vector style in the dark theme; satellite imagery has none', () => {
    expect(baseMapStyle({ vectorStyleUrl: vector, vectorStyleUrlDark: dark, satellite }, 'map', 'dark')).toBe(dark)
    expect(baseMapStyle({ vectorStyleUrl: vector, vectorStyleUrlDark: dark, satellite }, 'satellite', 'dark')).toMatchObject({ version: 8 })
  })

  it('falls back to the vector style without satellite tiles', () => {
    expect(baseMapStyle({ vectorStyleUrl: vector, vectorStyleUrlDark: dark, satellite: null }, 'satellite')).toBe(vector)
  })
})
