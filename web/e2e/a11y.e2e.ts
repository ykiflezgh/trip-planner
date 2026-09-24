import AxeBuilder from '@axe-core/playwright'
import { expect, test } from './fixtures'

type Page = import('@playwright/test').Page
type TestInfo = import('@playwright/test').TestInfo

/**
 * Known findings, each a decision rather than a surprise: the rule id and the node it hits, with the app-side fix.
 * Anything else fails the test.
 */
const KNOWN: { id: string; target: RegExp; reason: string }[] = [
  // Agenda.tsx renders DndContext inside the <ol>, so dnd-kit's role="status" live region becomes a non-<li> child of the list.
  { id: 'list', target: /^\.divide-y$/, reason: 'Agenda.tsx: move DndContext outside the <ol>, or pass dnd-kit an accessibility container' },
  // DayView.tsx hour labels: 11 px text-stone-400 on white is about 2.5:1; stone-500/600 clears 4.5:1.
  { id: 'color-contrast', target: /^\.left-1:nth-child\(\d+\)$/, reason: 'DayView.tsx: hour axis text-stone-400 -> text-stone-600' },
  // Trip view: a day with no stops is a role="row" column with no gridcell (Day 2 in the seed).
  { id: 'aria-required-children', target: /\[role="row"\]/, reason: 'DayView.tsx TimeGrid: render an empty-state gridcell or role="presentation" for an empty column' },
]

/** WCAG 2.x A/AA per route; the full report is attached to the test so a failure names the nodes. */
const scan = async (page: Page, info: TestInfo, label: string) => {
  const { violations } = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()
  await info.attach(`axe ${label}`, { body: JSON.stringify(violations, null, 2), contentType: 'application/json' })
  const unexpected = violations.flatMap((v) => v.nodes.map((n) => ({ id: v.id, target: n.target.join(' ') }))).filter((f) => !KNOWN.some((k) => k.id === f.id && k.target.test(f.target)))
  expect.soft(unexpected.map((f) => `${label} ${f.id}: ${f.target}`), label).toEqual([]) // soft: one run reports every route
}

test.describe('Accessibility (axe)', () => {
  test('trip list, signed out and in', async ({ page, users, trip, signIn }, info) => {
    await page.goto('/app/')
    await expect(page.getByText('Sign in to see your trips.')).toBeVisible()
    await scan(page, info, 'signed out')
    await signIn(users.owner)
    await expect(page.locator(`a[href="/app/t/${trip.tripId}"]`)).toBeVisible()
    await scan(page, info, 'trip list')
  })

  test('trip views and dialogs', async ({ page, users, trip, signIn }, info) => {
    await signIn(users.owner)
    for (const [q, label] of [['', 'agenda'], ['?view=day', 'day view'], ['?view=trip', 'trip view']] as const) {
      await page.goto(`/app/t/${trip.tripId}${q}`)
      await expect(page.getByRole('heading', { name: 'Rome weekend' })).toBeVisible()
      await expect(page.locator('main section ol > li, [role=gridcell]').first()).toBeVisible()
      await scan(page, info, label)
    }
    await page.goto(`/app/t/${trip.tripId}`)
    await page.getByRole('button', { name: 'Add stop' }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await scan(page, info, 'add stop dialog')
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toBeHidden()
    await page.getByRole('button', { name: 'More ▾' }).click()
    await page.getByRole('menuitem', { name: /^Settings/ }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await scan(page, info, 'settings dialog')
  })

  test('join page', async ({ page }, info) => {
    await page.goto('/join/abc123')
    await expect(page.getByRole('heading', { name: "You've been invited to a trip" })).toBeVisible()
    await scan(page, info, 'join')
  })
})
