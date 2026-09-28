import { requestJson, requestVoid } from './client'
import type { Coffee, CoffeePage, CoffeeRequest } from './types'

/**
 * The five use cases of `coffee-bce.md` §3.1. Relative, same-origin paths only — the
 * Vite dev/preview server proxies `/coffees` to the backend (ADR-003, D-3).
 */

export function listCoffees(page: number, size: number): Promise<CoffeePage> {
  return requestJson<CoffeePage>(`/coffees?page=${page}&size=${size}`)
}

export function getCoffee(id: string): Promise<Coffee> {
  return requestJson<Coffee>(`/coffees/${encodeURIComponent(id)}`)
}

export function createCoffee(body: CoffeeRequest): Promise<Coffee> {
  return requestJson<Coffee>('/coffees', { method: 'POST', body: JSON.stringify(body) })
}

export function updateCoffee(id: string, body: CoffeeRequest): Promise<Coffee> {
  return requestJson<Coffee>(`/coffees/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export function deleteCoffee(id: string): Promise<void> {
  return requestVoid(`/coffees/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
