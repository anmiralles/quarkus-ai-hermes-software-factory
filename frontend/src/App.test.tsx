import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import {
  captured,
  makeCoffee,
  pageResponse,
  problem,
  recordRequest,
  resetCoffeeStore,
} from './test/handlers'
import { renderApp } from './test/renderApp'
import { server } from './test/server'

/**
 * Behaviour-level suite for spec §7 (tests 1–15). The real components, the real API client
 * and the real TanStack Query run; only the network is stubbed (MSW), and every query goes
 * through a role and an accessible name (D-10).
 */

const EXISTING_ID = '11111111-1111-4111-8111-111111111111'

function lastRequest(method: string) {
  const found = [...captured].reverse().find((entry) => entry.method === method)
  if (found === undefined) {
    throw new Error(`Expected a ${method} request, none was captured`)
  }
  return found
}

function bodyOf(method: string): Record<string, unknown> {
  return lastRequest(method).body as Record<string, unknown>
}

async function fillCreateForm(
  user: ReturnType<typeof userEvent.setup>,
  values: { name: string; origin: string; price: string; stock: string; roastLevel?: string },
) {
  await user.click(screen.getByRole('button', { name: 'New coffee' }))
  await user.type(screen.getByRole('textbox', { name: 'Name' }), values.name)
  if (values.roastLevel !== undefined) {
    await user.selectOptions(screen.getByRole('combobox', { name: 'Roast level' }), values.roastLevel)
  }
  await user.type(screen.getByRole('textbox', { name: 'Origin' }), values.origin)
  await user.type(screen.getByRole('spinbutton', { name: 'Price' }), values.price)
  await user.type(screen.getByRole('spinbutton', { name: 'Stock' }), values.stock)
}

describe('list, loading, empty, error', () => {
  it('1. renders a row from the stubbed page response, under the page heading', async () => {
    renderApp()

    expect(await screen.findByRole('cell', { name: 'Ethiopia Yirgacheffe' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Coffee catalogue')
    expect(screen.getByRole('columnheader', { name: 'Roast' })).toBeInTheDocument()
    // The list request is self-describing: an explicit 0-based page and the API's default size.
    expect(captured[0]?.url).toBe('/coffees?page=0&size=20')
  })

  it('2. shows the loading state before the first list response resolves', async () => {
    let release: (() => void) | undefined
    server.use(
      http.get('/coffees', async () => {
        await new Promise<void>((resolve) => {
          release = resolve
        })
        return HttpResponse.json(pageResponse([makeCoffee()], 0, 20))
      }),
    )

    renderApp()

    expect(screen.getByText('Loading coffees…')).toBeInTheDocument()
    // Wait until the request is actually in flight before releasing it: React Query
    // issues the fetch asynchronously, so calling release() too early would be a no-op.
    await waitFor(() => expect(release).toBeDefined())
    release?.()
    expect(await screen.findByRole('cell', { name: 'Ethiopia Yirgacheffe' })).toBeInTheDocument()
    expect(screen.queryByText('Loading coffees…')).not.toBeInTheDocument()
  })

  it('3. shows the empty state with both pager buttons disabled for an empty page', async () => {
    server.use(http.get('/coffees', () => HttpResponse.json(pageResponse([], 0, 20))))

    renderApp()

    expect(await screen.findByText('No coffees yet.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    // totalPages is 0 on the wire; the indicator must not read "Page 1 of 0".
    expect(screen.getByText('Page 1 of 1')).toBeInTheDocument()
  })

  it('4. shows the problem detail in the alert region when the list fails', async () => {
    server.use(http.get('/coffees', () => problem(500, 'Unexpected error')))

    renderApp()

    expect(await screen.findByRole('alert')).toHaveTextContent('Unexpected error')
  })
})

describe('create', () => {
  it('5. posts exactly the five request fields and shows the refetched row', async () => {
    const user = userEvent.setup()
    renderApp()
    await screen.findByRole('cell', { name: 'Ethiopia Yirgacheffe' })

    await fillCreateForm(user, {
      name: 'Kenya Nyeri',
      origin: 'Kenya',
      price: '14.25',
      stock: '12',
      roastLevel: 'DARK',
    })
    await user.click(screen.getByRole('button', { name: 'Save coffee' }))

    await waitFor(() => expect(captured.some((entry) => entry.method === 'POST')).toBe(true))
    const body = bodyOf('POST')
    expect(Object.keys(body).sort()).toEqual(['name', 'origin', 'price', 'roastLevel', 'stock'])
    expect(body['roastLevel']).toBe('DARK')
    expect(body['name']).toBe('Kenya Nyeri')

    expect(await screen.findByRole('cell', { name: 'Kenya Nyeri' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Coffee created')
    expect(screen.queryByRole('heading', { level: 2 })).not.toBeInTheDocument()
  })

  it('6. sends price as a JSON number, never as a string', async () => {
    const user = userEvent.setup()
    renderApp()
    await screen.findByRole('cell', { name: 'Ethiopia Yirgacheffe' })

    await fillCreateForm(user, { name: 'Kenya Nyeri', origin: 'Kenya', price: '14.25', stock: '12' })
    await user.click(screen.getByRole('button', { name: 'Save coffee' }))

    await waitFor(() => expect(captured.some((entry) => entry.method === 'POST')).toBe(true))
    const body = bodyOf('POST')
    expect(typeof body['price']).toBe('number')
    expect(body['price']).toBe(14.25)
    expect(typeof body['stock']).toBe('number')
  })
})

describe('read and update', () => {
  it('7. puts to /coffees/{id} with exactly the five fields and no identity or timestamps', async () => {
    const user = userEvent.setup()
    renderApp()
    await screen.findByRole('cell', { name: 'Ethiopia Yirgacheffe' })

    await user.click(screen.getByRole('button', { name: 'Edit Ethiopia Yirgacheffe' }))
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(
      'Edit coffee: Ethiopia Yirgacheffe',
    )

    const price = screen.getByRole('spinbutton', { name: 'Price' })
    await user.clear(price)
    await user.type(price, '15.50')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(captured.some((entry) => entry.method === 'PUT')).toBe(true))
    const put = lastRequest('PUT')
    expect(put.url).toBe(`/coffees/${EXISTING_ID}`)
    const body = bodyOf('PUT')
    expect(Object.keys(body).sort()).toEqual(['name', 'origin', 'price', 'roastLevel', 'stock'])
    expect(body).not.toHaveProperty('id')
    expect(body).not.toHaveProperty('createdAt')
    expect(body).not.toHaveProperty('updatedAt')

    expect(await screen.findByRole('cell', { name: '15.50' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Coffee updated')
  })

  it('14. renders the server price at exactly two decimals', async () => {
    resetCoffeeStore([makeCoffee({ price: 12.3 })])

    renderApp()

    expect(await screen.findByRole('cell', { name: '12.30' })).toBeInTheDocument()
  })
})

describe('delete', () => {
  it('8. deletes only after Confirm delete, and the row disappears', async () => {
    const user = userEvent.setup()
    renderApp()
    await screen.findByRole('cell', { name: 'Ethiopia Yirgacheffe' })

    await user.click(screen.getByRole('button', { name: 'Delete Ethiopia Yirgacheffe' }))

    expect(
      screen.getByText('Delete Ethiopia Yirgacheffe? This cannot be undone.'),
    ).toBeInTheDocument()
    expect(captured.some((entry) => entry.method === 'DELETE')).toBe(false)

    await user.click(screen.getByRole('button', { name: 'Confirm delete' }))

    await waitFor(() =>
      expect(screen.queryByRole('cell', { name: 'Ethiopia Yirgacheffe' })).not.toBeInTheDocument(),
    )
    expect(lastRequest('DELETE').url).toBe(`/coffees/${EXISTING_ID}`)
    expect(screen.getByRole('status')).toHaveTextContent('Coffee deleted')
  })

  it('9. issues no request at all when the delete is cancelled', async () => {
    const user = userEvent.setup()
    renderApp()
    await screen.findByRole('cell', { name: 'Ethiopia Yirgacheffe' })

    await user.click(screen.getByRole('button', { name: 'Delete Ethiopia Yirgacheffe' }))
    await user.click(screen.getByRole('button', { name: 'Cancel delete' }))

    expect(screen.getByRole('button', { name: 'Delete Ethiopia Yirgacheffe' })).toBeInTheDocument()
    expect(captured.some((entry) => entry.method === 'DELETE')).toBe(false)
  })

  it('15. surfaces a 404 on delete and drops the stale row after the refetch', async () => {
    const user = userEvent.setup()
    let removed = false
    server.use(
      http.get('/coffees', () =>
        HttpResponse.json(
          pageResponse(removed ? [] : [makeCoffee()], 0, 20, removed ? 0 : 1),
        ),
      ),
      http.delete('/coffees/:id', () => {
        removed = true
        return problem(404, `Coffee ${EXISTING_ID} not found`)
      }),
    )

    renderApp()
    await screen.findByRole('cell', { name: 'Ethiopia Yirgacheffe' })

    await user.click(screen.getByRole('button', { name: 'Delete Ethiopia Yirgacheffe' }))
    await user.click(screen.getByRole('button', { name: 'Confirm delete' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(`Coffee ${EXISTING_ID} not found`)
    await waitFor(() =>
      expect(screen.queryByRole('cell', { name: 'Ethiopia Yirgacheffe' })).not.toBeInTheDocument(),
    )
  })
})

describe('server field errors', () => {
  it('10. renders a 400 field message next to its field and marks the field invalid', async () => {
    const user = userEvent.setup()
    server.use(
      http.post('/coffees', async ({ request }) => {
        recordRequest('POST', '/coffees', await request.json())
        return problem(400, 'Request validation failed', [
          { field: 'price', message: 'price must have at most 2 decimal places' },
        ])
      }),
    )

    renderApp()
    await screen.findByRole('cell', { name: 'Ethiopia Yirgacheffe' })

    // The client does not re-implement the scale rule: it sends 12.345 as typed (D-4, D-6).
    await fillCreateForm(user, { name: 'Kenya Nyeri', origin: 'Kenya', price: '12.345', stock: '5' })
    await user.click(screen.getByRole('button', { name: 'Save coffee' }))

    expect(
      await screen.findByText('price must have at most 2 decimal places'),
    ).toBeInTheDocument()
    const price = screen.getByRole('spinbutton', { name: 'Price' })
    expect(price).toHaveAttribute('aria-invalid', 'true')
    expect(price).toHaveAttribute('aria-describedby', 'coffee-price-error')
    expect(bodyOf('POST')['price']).toBe(12.345)
  })

  it('11. shows a 409 detail in the alert region and keeps the typed values', async () => {
    const user = userEvent.setup()
    server.use(http.post('/coffees', () => problem(409, "Coffee name 'Kenya Nyeri' already exists")))

    renderApp()
    await screen.findByRole('cell', { name: 'Ethiopia Yirgacheffe' })

    await fillCreateForm(user, { name: 'Kenya Nyeri', origin: 'Kenya', price: '14.25', stock: '12' })
    await user.click(screen.getByRole('button', { name: 'Save coffee' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      "Coffee name 'Kenya Nyeri' already exists",
    )
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('Kenya Nyeri')
    expect(screen.getByRole('textbox', { name: 'Origin' })).toHaveValue('Kenya')
  })
})

describe('paging', () => {
  it('12. requests page=1 on Next and disables each button at its bound', async () => {
    resetCoffeeStore(
      Array.from({ length: 25 }, (_, index) =>
        makeCoffee({ id: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`, name: `Coffee ${String(index + 1).padStart(2, '0')}` }),
      ),
    )
    const user = userEvent.setup()

    renderApp()
    await screen.findByRole('cell', { name: 'Coffee 01' })

    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeEnabled()
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Next page' }))

    expect(await screen.findByRole('cell', { name: 'Coffee 21' })).toBeInTheDocument()
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    expect(captured.some((entry) => entry.url.includes('page=1'))).toBe(true)
  })

  it('13. changing Rows per page sends size=50 and resets to page 0', async () => {
    resetCoffeeStore(
      Array.from({ length: 25 }, (_, index) =>
        makeCoffee({ id: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`, name: `Coffee ${String(index + 1).padStart(2, '0')}` }),
      ),
    )
    const user = userEvent.setup()

    renderApp()
    await screen.findByRole('cell', { name: 'Coffee 01' })
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    await screen.findByRole('cell', { name: 'Coffee 21' })

    await user.selectOptions(screen.getByRole('combobox', { name: 'Rows per page' }), '50')

    await waitFor(() =>
      expect(captured.some((entry) => entry.url === '/coffees?page=0&size=50')).toBe(true),
    )
    expect(screen.getByText('Page 1 of 1')).toBeInTheDocument()
  })
})
