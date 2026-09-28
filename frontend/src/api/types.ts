/**
 * D-9: the single declaration of the API types. Mirrors `docs/architecture/coffee-bce.md`
 * §3.2 field for field. Deliberately hand-written and checked in — not generated from a
 * live `/q/openapi`, so the frontend build does not depend on a running backend.
 */

/** `entity/RoastLevel` — case-sensitive on the wire (D-7). */
export type RoastLevel = 'LIGHT' | 'MEDIUM' | 'DARK'

export const ROAST_LEVELS: readonly RoastLevel[] = ['LIGHT', 'MEDIUM', 'DARK']

/** `boundary/dto/CoffeeResponse`. */
export interface Coffee {
  id: string
  name: string
  roastLevel: RoastLevel
  origin: string
  price: number
  stock: number
  createdAt: string
  updatedAt: string
}

/** `boundary/dto/CoffeeRequest` — exactly five fields; no `id`, no timestamps (D-5). */
export interface CoffeeRequest {
  name: string
  roastLevel: RoastLevel
  origin: string
  price: number
  stock: number
}

/** `boundary/dto/CoffeePageResponse`. `page` is 0-based on the wire (D-8). */
export interface CoffeePage {
  content: Coffee[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** One entry of the RFC 7807 `errors[]` array (`coffee-bce.md` §3.5). */
export interface ProblemFieldError {
  field: string
  message: string
}

/** The RFC 7807 problem detail returned for every error response. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errors?: ProblemFieldError[]
}
