import { type APIRequestContext, expect, type Locator, type Page, test } from '@playwright/test'
import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'

/*
 * Screenshots of docs/USER_GUIDE.md, with numbered marks on the elements the guide explains. Fills a fresh stack with
 * the golden sessions (the user's real trainings, already public in the repo), drill types, a geozone and segments.
 * Run with: ./infra/scripts/e2e.sh --config playwright.guide.config.ts
 */

const GOLDEN_DIR = path.resolve(import.meta.dirname, '../../fit-parser/src/test/resources/fit')
const OUT_DIR = path.resolve(import.meta.dirname, '../../docs/guide')
/** Uploaded in the guide itself, to show the upload dialog with a place to classify. */
const UPLOADED_LATER = '24421586382_ACTIVITY.fit'
/** Two laps, typed segments: the session the guide walks through. */
const MAIN_SESSION = '22296100401_ACTIVITY.fit'

type Side = 'left' | 'right' | 'top' | 'bottom'

interface Mark {
  n: number
  target: Locator
  /** A frame around the element as well as the number. */
  frame?: boolean
  /** Where the number sits, next to the element so it covers none of it: left (default), right, below or on its corner. */
  side?: Side
}

const BADGE = 24

/** Top-left corner of a number next to a box, kept on the screen. */
function badgeAt(box: { x: number; y: number; width: number; height: number }, side: Side) {
  const middle = box.y + box.height / 2 - BADGE / 2
  const [left, top] = {
    left: [box.x - BADGE - 6, middle],
    right: [box.x + box.width + 6, middle],
    top: [box.x - 10, box.y - 14],
    bottom: [box.x + box.width / 2 - BADGE / 2, box.y + box.height + 6],
  }[side]
  return { left: Math.max(2, left), top: Math.max(2, top) }
}

/** Puts numbered marks on the page, takes the screenshot and removes the marks again. */
async function shoot(page: Page, name: string, marks: Mark[] = [], clip?: { x: number; y: number; width: number; height: number }) {
  // Tooltips and hover states of the last click would show up: the mouse rests on an empty part of the header.
  await page.mouse.move(760, 26)
  const boxes = []
  for (const mark of marks) {
    const box = await mark.target.first().boundingBox()
    if (!box) throw new Error(`Mark ${mark.n} of ${name} is not visible`)
    boxes.push({ ...box, ...badgeAt(box, mark.side ?? 'left'), n: mark.n, frame: mark.frame ?? false })
  }
  await page.evaluate((items) => {
    // An open modal dialog is in the top layer, above any z-index: the marks go inside it.
    const host = document.querySelector('dialog[open]') ?? document.body
    const layer = document.createElement('div')
    layer.id = 'guide-marks'
    layer.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:2147483647'
    for (const item of items) {
      if (item.frame) {
        const frame = document.createElement('div')
        frame.style.cssText = `position:absolute;left:${item.x - 4}px;top:${item.y - 4}px;width:${item.width + 8}px;` +
          `height:${item.height + 8}px;border:2px solid #c2255c;border-radius:6px`
        layer.append(frame)
      }
      const badge = document.createElement('div')
      badge.textContent = String(item.n)
      badge.style.cssText = `position:absolute;left:${item.left}px;top:${item.top}px;width:24px;height:24px;` +
        'border-radius:50%;background:#c2255c;color:#fff;font:700 13px/24px Barlow,sans-serif;text-align:center;' +
        'box-shadow:0 0 0 2px #fff,0 2px 6px rgb(0 0 0 / 30%)'
      layer.append(badge)
    }
    host.append(layer)
  }, boxes)
  await page.screenshot({ path: path.join(OUT_DIR, `${name}.jpg`), type: 'jpeg', quality: 86, clip })
  await page.evaluate(() => document.getElementById('guide-marks')?.remove())
}

async function api<T>(request: APIRequestContext, method: 'get' | 'post' | 'patch', url: string, data?: unknown): Promise<T> {
  const response = await request[method](url, data === undefined ? undefined : { data })
  expect(response.ok(), `${method} ${url}: ${response.status()}`).toBe(true)
  return response.status() === 204 ? (undefined as T) : response.json()
}

interface Session { id: string; fileName: string; elapsedSec: number; surface: string }

/** The golden sessions, drill types, a geozone around the park, segments and a couple of manual surfaces. */
async function fillStack(request: APIRequestContext) {
  const form = new FormData()
  for (const file of readdirSync(GOLDEN_DIR).filter((name) => name.endsWith('.fit') && name !== UPLOADED_LATER)) {
    form.append('files', new Blob([readFileSync(path.join(GOLDEN_DIR, file))]), file)
  }
  expect((await request.post('/api/sessions/upload', { multipart: form })).ok()).toBe(true)

  const types: Record<string, string> = {}
  for (const [code, name, kind, color] of [
    ['WARM_UP', 'Warm-up', 'WARMUP', '#8d6e63'],
    ['SPRINT_LADDER', 'Sprint ladder', 'DRILL', '#e65100'],
    ['CUTTING', 'Cutting 1v1', 'DRILL', '#6a1b9a'],
    ['GAME', 'Game', 'GAME', '#00897b'],
  ]) {
    types[code] = (await api<{ id: string }>(request, 'post', '/api/drill-types', { code, name, kind, color })).id
  }

  const sessions = (await api<{ items: Session[] }>(request, 'get', '/api/sessions?size=50')).items
  const latest = await api<{ startPosition: { lat: number; lon: number } }>(request, 'get', `/api/sessions/${sessions[0].id}`)
  await api(request, 'post', '/api/geozones', {
    name: 'Park',
    surface: 'GRASS',
    shape: { type: 'circle', lat: latest.startPosition.lat, lon: latest.startPosition.lon, radiusM: 150 },
  })

  // Warm-up, a drill and a game in most sessions; the main session also gets a cutting drill.
  const main = sessions.find((session) => session.fileName === MAIN_SESSION)!
  for (const session of [main, ...sessions.filter((s) => s.id !== main.id).slice(0, 6)]) {
    const plan: [number, number, string][] = session.id === main.id
      ? [[0, 1500, 'WARM_UP'], [1500, 3000, 'SPRINT_LADDER'], [3000, 3994, 'CUTTING'], [3994, 7800, 'GAME']]
      : [[0, 1200, 'WARM_UP'], [1200, 2400, 'SPRINT_LADDER'], [2400, session.elapsedSec, 'GAME']]
    for (const [startT, endT, code] of plan) {
      await api(request, 'post', `/api/sessions/${session.id}/segments?overwrite=true`, { startT, endT, drillTypeId: types[code] })
    }
  }
  const unknown = (await api<{ items: Session[] }>(request, 'get', '/api/sessions?size=50&surface=UNKNOWN')).items
  for (const session of unknown.slice(0, 2)) {
    await api(request, 'patch', `/api/sessions/${session.id}`, { surface: 'SAND' })
  }
  return { main, types }
}

test('user guide screenshots', async ({ page, request }) => {
  const { main, types } = await fillStack(request)
  const settle = (ms = 600) => page.waitForTimeout(ms)

  // 1. Sessions list.
  await page.goto('/')
  await expect(page.locator('.sessions-table tbody tr')).toHaveCount(12)
  await shoot(page, 'sessions', [
    { n: 1, target: page.locator('.app-header nav'), frame: true, side: 'right' },
    { n: 2, target: page.getByRole('button', { name: 'Upload FIT files' }) },
    { n: 3, target: page.locator('.filters'), frame: true },
    { n: 4, target: page.getByRole('checkbox', { name: 'Select all sessions on this page' }) },
    { n: 5, target: page.locator('.sessions-table tbody tr').first().getByRole('link'), side: 'right' },
    { n: 6, target: page.locator('.sessions-table .chip').first(), side: 'right' },
    { n: 7, target: page.locator('.sessions-table .badge').first(), side: 'right' },
    { n: 8, target: page.getByRole('group', { name: 'Theme' }) },
  ])

  // 2. Upload dialog with a training at an unknown place.
  await page.getByRole('button', { name: 'Upload FIT files' }).click()
  await page.locator('input[type=file]').setInputFiles(path.join(GOLDEN_DIR, UPLOADED_LATER))
  await page.locator('.place-map .maplibregl-canvas').waitFor()
  await settle(3000)
  await shoot(page, 'upload', [
    { n: 1, target: page.locator('.dropzone'), frame: true },
    { n: 2, target: page.locator('.upload-row .status'), side: 'top' },
    { n: 3, target: page.locator('.surface-picker').getByRole('button', { name: 'Grass' }), side: 'bottom' },
    { n: 4, target: page.getByRole('button', { name: 'Create geozone from this session' }), side: 'right' },
    { n: 5, target: page.locator('.place-map'), frame: true },
  ])
  await page.keyboard.press('Escape')

  // 3. Session screen with a window selected.
  await page.goto(`/sessions/${main.id}?from=1500&to=2400`)
  await page.locator('.echart canvas').nth(2).waitFor()
  await page.locator('.maplibregl-canvas').waitFor()
  await settle(3500)
  await shoot(page, 'session', [
    { n: 1, target: page.locator('.session-title h1') },
    { n: 2, target: page.getByLabel('Surface'), side: 'top' },
    { n: 3, target: page.locator('.key-metrics'), frame: true },
    { n: 4, target: page.getByLabel('Segments'), side: 'right' },
    { n: 5, target: page.getByLabel('Activity'), side: 'right' },
    { n: 6, target: page.locator('.window-bar > span').first() },
    { n: 7, target: page.locator('.echart').first(), frame: true },
    { n: 8, target: page.locator('.session-map'), frame: true },
    { n: 9, target: page.getByRole('group', { name: 'Track shown' }), side: 'bottom' },
    { n: 10, target: page.getByRole('columnheader', { name: 'Window' }), side: 'right' },
    { n: 11, target: page.locator('.session-actions') },
  ])

  // 4. Segment menu on the strip.
  const strip = page.getByLabel('Segments')
  const stripBox = (await strip.boundingBox())!
  await strip.click({ button: 'right', position: { x: stripBox.width * 0.29, y: stripBox.height / 2 } })
  await page.getByRole('menu').waitFor()
  const menuBox = (await page.getByRole('menu').boundingBox())!
  await shoot(page, 'segment-menu', [
    { n: 1, target: page.getByRole('menuitem', { name: /^Split at/ }), side: 'right' },
    { n: 2, target: page.getByRole('menuitem', { name: 'Merge with next' }), side: 'right' },
    { n: 3, target: page.getByRole('group', { name: 'Drill type' }), side: 'right' },
    { n: 4, target: page.getByRole('menuitem', { name: 'Delete' }), side: 'right' },
    { n: 5, target: page.getByRole('menuitem', { name: 'Reset from laps…' }), side: 'right' },
  ], { x: stripBox.x - 80, y: stripBox.y - 20, width: Math.max(640, menuBox.x + menuBox.width - stripBox.x + 120), height: menuBox.y + menuBox.height - stripBox.y + 40 })
  await page.keyboard.press('Escape')

  // 5. Saving the window as a segment, inside the sprint ladder.
  await page.getByRole('button', { name: 'Save as segment' }).click()
  await page.getByLabel('Drill type').selectOption({ label: 'Cutting 1v1' })
  await page.getByLabel('Label').fill('Ladder repeats')
  const form = (await page.locator('.segment-form').boundingBox())!
  await shoot(page, 'save-segment', [
    { n: 1, target: page.locator('.segment-form label').first() },
    { n: 2, target: page.locator('.cut-note') },
    { n: 3, target: page.getByRole('button', { name: 'Save', exact: true }), side: 'top' },
  ], { x: stripBox.x - 80, y: stripBox.y - 20, width: 960, height: form.y + form.height - stripBox.y + 40 })
  await page.getByRole('button', { name: 'Cancel' }).click()

  // 6. Effort drawer: click the marker of the fastest effort.
  const efforts = await api<{ peakT: number; metrics: { peakSpeed: number } }[]>(request, 'get', `/api/sessions/${main.id}/efforts`)
  const fastest = efforts.reduce((a, b) => (b.metrics.peakSpeed > a.metrics.peakSpeed ? b : a))
  const chart = (await page.locator('.echart').first().boundingBox())!
  const plotHeight = 280 - 24 - 22
  const yMax = Math.ceil((Math.max(...efforts.map((e) => e.metrics.peakSpeed)) * 3.6) / 10) * 10
  await page.mouse.click(
    chart.x + 64 + ((chart.width - 88) * fastest.peakT) / main.elapsedSec,
    chart.y + 24 + plotHeight * (1 - (fastest.metrics.peakSpeed * 3.6) / yMax),
  )
  const drawer = page.getByRole('dialog')
  await drawer.waitFor()
  await drawer.locator('.maplibregl-canvas').waitFor()
  await settle(3000)
  await shoot(page, 'effort', [
    { n: 1, target: drawer.locator('.drawer-actions') },
    { n: 2, target: drawer.locator('.echart'), frame: true },
    { n: 3, target: drawer.locator('.effort-map'), frame: true },
    { n: 4, target: drawer.getByRole('table', { name: 'Effort metrics' }) },
  ])
  await page.keyboard.press('Escape')

  // 7. Geozones, drawing a polygon.
  await page.goto('/geozones')
  await page.locator('.maplibregl-canvas').waitFor()
  await settle(2500)
  await page.getByRole('button', { name: 'New polygon' }).click()
  const map = (await page.locator('.geozones-map').boundingBox())!
  for (const [dx, dy] of [[0.62, 0.62], [0.85, 0.6], [0.88, 0.85]]) await page.mouse.click(map.x + map.width * dx, map.y + map.height * dy)
  await page.getByLabel('Name').fill('Beach courts')
  await settle(800)
  await shoot(page, 'geozones', [
    { n: 1, target: page.locator('.geozone-new') },
    { n: 2, target: page.locator('.geozone-editor'), frame: true },
    { n: 3, target: page.locator('.geozone-row').first() },
    { n: 4, target: page.locator('.geozones-map'), side: 'right' },
  ])

  // 8. Drill types and one type's statistics.
  await page.goto('/drill-types')
  await page.getByRole('button', { name: 'New drill type' }).click()
  await page.getByLabel('Name').fill('Zone defence')
  await shoot(page, 'drill-types', [
    { n: 1, target: page.getByLabel('Name'), side: 'bottom' },
    { n: 2, target: page.getByLabel('Code'), side: 'bottom' },
    { n: 3, target: page.locator('.color-field'), frame: true, side: 'bottom' },
    { n: 4, target: page.locator('tbody tr').first().getByRole('link'), side: 'right' },
  ], { x: 0, y: 0, width: 1440, height: 520 })

  await page.goto(`/drill-types/${types.SPRINT_LADDER}`)
  await page.locator('.echart canvas').waitFor()
  await settle(1000)
  await shoot(page, 'drill-type', [
    { n: 1, target: page.locator('.filters'), frame: true },
    { n: 2, target: page.locator('.key-metrics'), frame: true },
    { n: 3, target: page.getByLabel('Trend'), side: 'right' },
    { n: 4, target: page.locator('.drill-type-main table') },
    { n: 5, target: page.getByRole('region', { name: 'Metrics' }) },
  ])

  // 9. The dark theme.
  await page.goto(`/sessions/${main.id}?from=1500&to=2400`)
  await page.getByRole('button', { name: 'Dark' }).click()
  await page.locator('.echart canvas').nth(2).waitFor()
  await page.locator('.maplibregl-canvas').waitFor()
  await settle(3500)
  await shoot(page, 'dark')
  await page.getByRole('button', { name: 'System' }).click()
})
