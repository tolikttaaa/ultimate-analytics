import { describe, expect, it } from 'vitest'
import { geozone } from '../../test/data'
import {
  addPoint,
  describeShape,
  type Draft,
  draftFeatures,
  draftShape,
  finishPolygon,
  geozoneFeatures,
  geozonesBounds,
  shapeDraft,
  undoPoint,
} from './draft'

const open = (...points: number[][]): Draft => ({ kind: 'polygon', points, closed: false })

describe('drawing a circle', () => {
  it('needs a centre, which every click moves', () => {
    let draft: Draft = { kind: 'circle', center: null, radiusM: 150 }
    expect(draftShape(draft)).toBeNull()
    draft = addPoint(addPoint(draft, [33.1, 34.7]), [33.2, 34.8])
    expect(draftShape(draft)).toEqual({ type: 'circle', lon: 33.2, lat: 34.8, radiusM: 150 })
  })

  it('needs a positive radius', () => {
    expect(draftShape({ kind: 'circle', center: [33.1, 34.7], radiusM: 0 })).toBeNull()
  })
})

describe('drawing a polygon', () => {
  it('collects corners until it is finished, then closes the ring', () => {
    let draft = addPoint(addPoint(addPoint(open(), [1, 1]), [2, 1]), [2, 2])
    expect(draftShape(draft)).toBeNull()
    draft = finishPolygon(draft)
    expect(draftShape(draft)).toEqual({ type: 'Polygon', coordinates: [[[1, 1], [2, 1], [2, 2], [1, 1]]] })
    expect(addPoint(draft, [3, 3])).toBe(draft)
  })

  it('drops the repeated corner of a finishing double-click and needs three corners', () => {
    expect(finishPolygon(open([1, 1], [2, 1], [2, 2], [2, 2]))).toEqual({ kind: 'polygon', points: [[1, 1], [2, 1], [2, 2]], closed: true })
    expect(finishPolygon(open([1, 1], [2, 1], [2, 1]))).toMatchObject({ closed: false })
  })

  it('undoes the last corner', () => {
    expect(undoPoint(open([1, 1], [2, 1]))).toEqual(open([1, 1]))
  })
})

describe('editing', () => {
  it('starts from the saved shape', () => {
    expect(shapeDraft({ type: 'circle', lat: 34.7, lon: 33.1, radiusM: 200 })).toEqual({ kind: 'circle', center: [33.1, 34.7], radiusM: 200 })
    expect(shapeDraft({ type: 'Polygon', coordinates: [[[1, 1], [2, 1], [2, 2], [1, 1]]] }))
      .toEqual({ kind: 'polygon', points: [[1, 1], [2, 1], [2, 2]], closed: true })
  })
})

describe('map features', () => {
  it('show an open polygon as its corners and the line through them', () => {
    const features = draftFeatures(open([1, 1], [2, 1])).features
    expect(features.map((feature) => feature.geometry.type)).toEqual(['LineString', 'Point', 'Point'])
  })

  it('show a finished shape as an area with its corners', () => {
    const features = draftFeatures(finishPolygon(open([1, 1], [2, 1], [2, 2]))).features
    expect(features.map((feature) => feature.geometry.type)).toEqual(['Polygon', 'Point', 'Point', 'Point'])
    expect(draftFeatures(null).features).toEqual([])
  })

  it('carry id, name and surface of every geozone and bound them', () => {
    const zones = [
      geozone({ id: 'a', name: 'Field', shape: { type: 'Polygon', coordinates: [[[33, 34], [33.2, 34], [33.2, 34.1], [33, 34]]] } }),
      geozone({ id: 'b', name: 'Beach', surface: 'SAND', shape: { type: 'Polygon', coordinates: [[[33.5, 34.5], [33.6, 34.5], [33.6, 34.6], [33.5, 34.5]]] } }),
    ]
    expect(geozoneFeatures(zones).features.map((feature) => feature.properties))
      .toEqual([{ id: 'a', name: 'Field', surface: 'GRASS' }, { id: 'b', name: 'Beach', surface: 'SAND' }])
    expect(geozonesBounds(zones)).toEqual([[33, 34], [33.6, 34.6]])
    expect(geozonesBounds([])).toBeNull()
  })
})

describe('describeShape', () => {
  it('names the kind and size', () => {
    expect(describeShape({ type: 'circle', lat: 1, lon: 1, radiusM: 149.6 })).toBe('Circle, 150 m')
    expect(describeShape({ type: 'Polygon', coordinates: [[[1, 1], [2, 1], [2, 2], [1, 2], [1, 1]]] })).toBe('Polygon, 4 corners')
  })
})
