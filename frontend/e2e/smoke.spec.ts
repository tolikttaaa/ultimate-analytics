import { expect, test } from '@playwright/test'
import path from 'node:path'

/*
 * Smoke test of spec 12: upload a golden file → session opens → brush a window → metrics panel shows values → save as
 * segment. Runs against a fresh stack (infra/scripts/e2e.sh); a re-run on the same stack works too.
 */

const GOLDEN = path.resolve(import.meta.dirname, '../../fit-parser/src/test/resources/fit/22245986957_ACTIVITY.fit')

/** m:ss or h:mm:ss, as the app shows durations. */
function duration(seconds: number): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds % 60)}` : `${minutes}:${pad(seconds % 60)}`
}

test('upload, open, brush a window and save it as a segment', async ({ page, request }) => {
  const pageErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  page.on('dialog', (dialog) => dialog.accept())

  // Upload the golden file; a re-run finds it already uploaded.
  await page.goto('/')
  await page.getByRole('button', { name: 'Upload FIT files' }).first().click()
  await page.locator('input[type=file]').setInputFiles(GOLDEN)
  const result = page.locator('.upload-row', { hasText: '22245986957_ACTIVITY.fit' })
  await expect(result.locator('.status')).toHaveText(/created|already uploaded/)

  // The session opens with its charts and map.
  await result.getByRole('link', { name: 'Open' }).click()
  await expect(page).toHaveURL(/\/sessions\/[0-9a-f-]{36}$/)
  const sessionId = page.url().split('/').pop()!
  await expect(page.locator('.echart canvas')).toHaveCount(3)
  await expect(page.getByRole('region', { name: 'Metrics' })).toContainText('Elapsed')

  // Brush a window across the middle of the speed chart (plot area: 64 px from the left, 24 px from the right).
  const chart = (await page.locator('.echart').first().boundingBox())!
  const x = (share: number) => chart.x + 64 + (chart.width - 88) * share
  await page.mouse.move(x(0.3), chart.y + 140)
  await page.mouse.down()
  await page.mouse.move(x(0.45), chart.y + 140, { steps: 8 })
  await page.mouse.move(x(0.6), chart.y + 140, { steps: 8 })
  await page.mouse.up()
  await expect(page).toHaveURL(/\?from=\d+&to=\d+$/)
  const params = new URL(page.url()).searchParams
  const [from, to] = [Number(params.get('from')), Number(params.get('to'))]
  expect(to - from).toBeGreaterThan(60)

  // The metrics panel adds the window's column with values.
  const metrics = page.getByRole('region', { name: 'Metrics' })
  await expect(metrics.getByRole('columnheader', { name: 'Window' })).toBeVisible()
  await expect(metrics.getByRole('row', { name: /^Elapsed/ })).toContainText(duration(to - from))
  await expect(metrics.getByRole('row', { name: /^Efforts \d/ }).locator('td').nth(2)).toHaveText(/^\d+$/)

  // Save the window as a segment; anything in its way would be cut.
  const strip = page.getByLabel('Segments')
  await page.getByRole('button', { name: 'Save as segment' }).click()
  await page.getByLabel('Label').fill('Smoke test')
  await page.getByRole('button', { name: 'Save', exact: true }).click()
  await expect(strip.locator('.segment', { hasText: 'Smoke test' })).toBeVisible()

  const session = await (await request.get(`/api/sessions/${sessionId}`)).json()
  expect(session.segments).toContainEqual(expect.objectContaining({ startT: from, endT: to, label: 'Smoke test', source: 'MANUAL' }))
  expect(pageErrors).toEqual([])
})
