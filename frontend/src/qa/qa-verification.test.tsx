import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { captured, makeCoffee, pageResponse, problem, recordRequest, resetCoffeeStore } from '../test/handlers'
import { renderApp } from '../test/renderApp'
import { server } from '../test/server'

/**
 * Card `t_62a65b0b` — independent verification (spec §9, AC-Q3).
 *
 * These tests are authored by the verifier, not the implementer, and are written from
 * `docs/architecture/coffee-frontend.md` §5/§6 before reading the app's own suite. They
 * share only the test harness (MSW server + handlers), never an assertion.
 *
 * Overlap with the implementation's suite is recorded honestly in FRONTEND-VERIFICATION.md:
 * author tests 3 (empty page) and 15 (404 on delete) already cover similar ground; these
 * are independent re-derivations, not novel coverage. AC-Q3(d) has no author counterpart.
 */

const QA_ID = '33333333-3333-4333-8333-333333333333'

/** The five keys D-5 permits on the wire — no more, no fewer. */
const FIVE_KEYS = ['name', 'origin', 'price', 'roastLevel', 'stock']

function last(method: string) {
  const found = [...captured].reverse().find((entry) => entry.method === method)
  if (found === undefined) {
    throw new Error(`Expected a ${method} request, none was captured`)
  }
  return found
}

function bodyOf(method: string): Record<string, unknown> {
  return last(method).body as Record<string, unknown>
}

function getCount(): number {
  return captured.filter((entry) => entry.method === 'GET').length
}

describe('AC-Q3a — an empty page (totalElements 0 / totalPages 0)', () => {
  it('shows the empty state and disables both pager buttons', async () => {
    resetCoffeeStore([])

    renderApp()

    expect(await screen.findByText('No coffees yet.')).toBeInTheDocument()
    // Both pager buttons are disabled at the bound of an empty catalogue.
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    // `totalPages: 0` on the wire must never render as "Page 1 of 0".
    expect(screen.getByText('Page 1 of 1')).toBeInTheDocument()
    // No table at all — an empty catalogue is not an empty table.
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    // The request is still the self-describing default (D-8: 0-based page on the wire).
    expect(captured[0]?.url).toBe('/coffees?page=0&size=20')
  })
})

describe('AC-Q3b — a 404 on delete', () => {
  it('surfaces the problem detail and drops the stale row after the refetch', async () => {
    resetCoffeeStore([makeCoffee({ id: QA_ID, name: 'QA Stale Row' })])
    const user = userEvent.setup()
    let removed = false
    server.use(
      http.delete('/coffees/:id', ({ request }) => {
        removed = true
        recordRequest('DELETE', new URL(request.url).pathname, undefined)
        return problem(404, `Coffee ${QA_ID} not found`)
      }),
      // The row is already gone server-side, so the refetch after the 404 comes back empty.
      http.get('/coffees', ({ request }) => {
        recordRequest('GET', new URL(request.url).pathname + new URL(request.url).search, undefined)
        return HttpResponse.json(
          pageResponse(removed ? [] : [makeCoffee({ id: QA_ID, name: 'QA Stale Row' })], 0, 20),
        )
      }),
    )

    renderApp()
    await screen.findByRole('cell', { name: 'QA Stale Row' })
    const getsBefore = getCount()

    await user.click(screen.getByRole('button', { name: 'Delete QA Stale Row' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(`Coffee ${QA_ID} not found`)
    await waitFor(() =>
      expect(screen.queryByRole('cell', { name: 'QA Stale Row' })).not.toBeInTheDocument(),
    )
    expect(last('DELETE').url).toBe(`/coffees/${QA_ID}`)
    // §5.5 requires a refetch specifically; the author's test only observes that the row
    // vanished, so this asserts the mechanism, not just the outcome.
    expect(getCount()).toBeGreaterThan(getsBefore)
  })
})

describe('AC-Q3c — the wire body carries exactly the five contract keys', () => {
  it('POST /coffees and PUT /coffees/{id} send name, roastLevel, origin, price, stock and nothing else', async () => {
    resetCoffeeStore([makeCoffee({ id: QA_ID, name: 'QA Existing' })])
    const user = userEvent.setup()

    renderApp()
    await screen.findByRole('cell', { name: 'QA Existing' })

    // --- create ---
    await user.click(screen.getByRole('button', { name: 'New coffee' }))
    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'QA Created')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Roast level' }), 'MEDIUM')
    await user.type(screen.getByRole('textbox', { name: 'Origin' }), 'Peru')
    await user.type(screen.getByRole('spinbutton', { name: 'Price' }), '9.99')
    await user.type(screen.getByRole('spinbutton', { name: 'Stock' }), '3')
    await user.click(screen.getByRole('button', { name: 'Save coffee' }))

    await waitFor(() => expect(captured.some((entry) => entry.method === 'POST')).toBe(true))
    const post = bodyOf('POST')
    expect(Object.keys(post).sort()).toEqual(FIVE_KEYS)
    expect(Object.keys(post)).toHaveLength(5)
    expect(post).not.toHaveProperty('id')
    expect(post).not.toHaveProperty('createdAt')
    expect(post).not.toHaveProperty('updatedAt')
    expect(post['roastLevel']).toBe('MEDIUM')

    // --- edit ---
    await user.click(await screen.findByRole('button', { name: 'Edit QA Existing' }))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(captured.some((entry) => entry.method === 'PUT')).toBe(true))
    const put = bodyOf('PUT')
    expect(Object.keys(put).sort()).toEqual(FIVE_KEYS)
    expect(Object.keys(put)).toHaveLength(5)
    expect(put).not.toHaveProperty('id')
    expect(put).not.toHaveProperty('createdAt')
    expect(put).not.toHaveProperty('updatedAt')
    expect(last('PUT').url).toBe(`/coffees/${QA_ID}`)
  })
})

describe('AC-Q3d — an edit round-trip of a 12.30 row', () => {
  it('sends price as a JSON number, never a string', async () => {
    resetCoffeeStore([makeCoffee({ id: QA_ID, name: 'QA Priced', price: 12.3 })])
    const user = userEvent.setup()

    renderApp()
    // The row displays 12.30 (D-6).
    await screen.findByRole('cell', { name: '12.30' })

    await user.click(screen.getByRole('button', { name: 'Edit QA Priced' }))
    // Save without touching the prefilled price: the round-trip must stay numeric.
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(captured.some((entry) => entry.method === 'PUT')).toBe(true))
    const put = bodyOf('PUT')
    expect(typeof put['price']).toBe('number')
    expect(put['price']).toBe(12.3)
    // The raw JSON text must not carry a quoted price either.
    expect(JSON.stringify(put)).not.toContain('"price":"')
  })
})
