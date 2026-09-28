import { http, HttpResponse } from 'msw'
import type { Coffee, CoffeePage, CoffeeRequest, ProblemFieldError } from '../api/types'

/**
 * Default happy-path handlers for the five endpoints, backed by a small in-memory store
 * so that a refetch after a mutation actually reflects the mutation — which is what makes
 * "the new row appears in the refetched list" a real assertion rather than a mock artefact.
 *
 * Every request is recorded in `captured`, so tests can assert on the *wire* shape of a
 * body (the `fail-on-unknown-properties` trap, D-5) rather than on a mock's wishful view.
 */

export const FIXED_TIMESTAMP = '2026-09-27T19:45:12.123456Z'

export interface CapturedRequest {
  method: string
  url: string
  body: unknown
}

export const captured: CapturedRequest[] = []

let store: Coffee[] = []
let sequence = 0

export function makeCoffee(overrides: Partial<Coffee> = {}): Coffee {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    name: 'Ethiopia Yirgacheffe',
    roastLevel: 'LIGHT',
    origin: 'Ethiopia',
    price: 12.3,
    stock: 40,
    createdAt: FIXED_TIMESTAMP,
    updatedAt: FIXED_TIMESTAMP,
    ...overrides,
  }
}

export function pageResponse(
  content: Coffee[],
  page: number,
  size: number,
  totalElements: number = content.length,
): CoffeePage {
  return {
    content,
    page,
    size,
    totalElements,
    totalPages: totalElements === 0 ? 0 : Math.ceil(totalElements / size),
  }
}

export function problem(status: number, detail: string, errors?: ProblemFieldError[]) {
  return HttpResponse.json(
    { type: 'about:blank', title: 'Error', status, detail, ...(errors ? { errors } : {}) },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

export function resetCoffeeStore(coffees: Coffee[] = [makeCoffee()]): void {
  store = coffees.map((coffee) => ({ ...coffee }))
  captured.length = 0
  sequence = 0
}

/** Record a request from a test-local handler override (the default handlers record their own). */
export function recordRequest(method: string, url: string, body: unknown): void {
  captured.push({ method, url, body })
}

// Seed the store so the very first test has the default happy-path catalogue available.
resetCoffeeStore()

export function currentStore(): Coffee[] {
  return store
}

function nextId(): string {
  sequence += 1
  return `22222222-2222-4222-8222-${String(sequence).padStart(12, '0')}`
}

export const handlers = [
  http.get('/coffees', ({ request }) => {
    const url = new URL(request.url)
    const page = Number(url.searchParams.get('page') ?? '0')
    const size = Number(url.searchParams.get('size') ?? '20')
    const content = store.slice(page * size, page * size + size)
    captured.push({ method: 'GET', url: `${url.pathname}${url.search}`, body: undefined })
    return HttpResponse.json(pageResponse(content, page, size, store.length))
  }),

  http.get('/coffees/:id', ({ params, request }) => {
    captured.push({ method: 'GET', url: new URL(request.url).pathname, body: undefined })
    const coffee = store.find((entry) => entry.id === params.id)
    if (coffee === undefined) {
      return problem(404, `Coffee ${String(params.id)} not found`)
    }
    return HttpResponse.json(coffee)
  }),

  http.post('/coffees', async ({ request }) => {
    const body = (await request.json()) as CoffeeRequest
    captured.push({ method: 'POST', url: '/coffees', body })
    const created = makeCoffee({ ...body, id: nextId() })
    store = [...store, created]
    return HttpResponse.json(created, { status: 201 })
  }),

  http.put('/coffees/:id', async ({ params, request }) => {
    const body = (await request.json()) as CoffeeRequest
    const path = new URL(request.url).pathname
    captured.push({ method: 'PUT', url: path, body })
    const index = store.findIndex((entry) => entry.id === params.id)
    if (index === -1) {
      return problem(404, `Coffee ${String(params.id)} not found`)
    }
    const updated: Coffee = { ...store[index]!, ...body, updatedAt: FIXED_TIMESTAMP }
    store = store.map((entry, position) => (position === index ? updated : entry))
    return HttpResponse.json(updated)
  }),

  http.delete('/coffees/:id', ({ params, request }) => {
    captured.push({ method: 'DELETE', url: new URL(request.url).pathname, body: undefined })
    if (!store.some((entry) => entry.id === params.id)) {
      return problem(404, `Coffee ${String(params.id)} not found`)
    }
    store = store.filter((entry) => entry.id !== params.id)
    return new HttpResponse(null, { status: 204 })
  }),
]
