import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { ApiError, MALFORMED_ERROR_DETAIL, NETWORK_ERROR_DETAIL, requestJson } from './client'
import { deleteCoffee, getCoffee, listCoffees } from './coffees'
import { problem } from '../test/handlers'
import { server } from '../test/server'
import type { ProblemDetail } from './types'

/** Spec §6: the client turns every failure into an `ApiError`, and never throws while reporting one. */

async function failure<T>(call: Promise<T>): Promise<unknown> {
  return call.then(
    () => new Error('expected the request to reject'),
    (error: unknown) => error,
  )
}

describe('api client error model', () => {
  it('parses a problem+json failure into an ApiError carrying status and the field errors', async () => {
    server.use(
      http.get('/coffees', () =>
        problem(400, 'Request validation failed', [
          { field: 'name', message: 'name must not be blank' },
        ]),
      ),
    )

    const error = await failure(listCoffees(0, 20))

    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.status).toBe(400)
    expect(apiError.message).toBe('Request validation failed')
    expect(apiError.problem.errors?.[0]?.message).toBe('name must not be blank')
  })

  it('turns a non-problem body into a synthetic problem instead of throwing while parsing', async () => {
    server.use(
      http.get('/coffees', () =>
        new HttpResponse('<html>502 Bad Gateway</html>', {
          status: 502,
          headers: { 'Content-Type': 'text/html' },
        }),
      ),
    )

    const error = await failure(listCoffees(0, 20))

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(502)
    expect((error as ApiError).problem.detail).toBe(MALFORMED_ERROR_DETAIL)
  })

  it('reports a network failure as a fixed message, not a stack trace', async () => {
    server.use(http.get('/coffees', () => HttpResponse.error()))

    const error = await failure(listCoffees(0, 20))

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(0)
    expect((error as ApiError).problem.detail).toBe(NETWORK_ERROR_DETAIL)
  })

  it('maps a 404 on a single read to the problem detail', async () => {
    server.use(http.get('/coffees/:id', () => problem(404, 'Coffee 42 not found')))

    const error = await failure(getCoffee('42'))

    expect((error as ApiError).status).toBe(404)
    expect((error as ApiError).problem.detail).toBe('Coffee 42 not found')
  })

  it('resolves a 204 delete without trying to parse a body', async () => {
    server.use(http.delete('/coffees/:id', () => new HttpResponse(null, { status: 204 })))

    await expect(deleteCoffee('11111111-1111-4111-8111-111111111111')).resolves.toBeUndefined()
  })

  it('sends the request body as JSON with a JSON content type on POST/PUT-shaped calls', async () => {
    const received: { contentType: string | null; body: unknown } = {
      contentType: null,
      body: undefined,
    }
    server.use(
      http.post('/coffees', async ({ request }) => {
        received.contentType = request.headers.get('content-type')
        received.body = await request.json()
        return HttpResponse.json({}, { status: 201 })
      }),
    )

    await requestJson('/coffees', {
      method: 'POST',
      body: JSON.stringify({ name: 'x', roastLevel: 'LIGHT', origin: 'y', price: 1, stock: 0 }),
    })

    expect(received.contentType).toContain('application/json')
    expect(received.body).toEqual({
      name: 'x',
      roastLevel: 'LIGHT',
      origin: 'y',
      price: 1,
      stock: 0,
    })
  })
})

describe('problem detail contract', () => {
  it('keeps the problem detail shape the UI depends on', () => {
    const detail: ProblemDetail = {
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
      detail: 'Request validation failed',
      instance: '/coffees',
      errors: [{ field: 'price', message: 'price must be greater than 0' }],
    }

    expect(detail.errors?.[0]?.field).toBe('price')
  })
})
