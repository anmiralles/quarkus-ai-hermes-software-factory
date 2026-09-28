import type { ProblemDetail } from './types'

/**
 * The single module that calls `fetch` (AC-F3, spec §6).
 *
 * Every non-2xx response becomes an `ApiError` carrying the HTTP status and a parsed
 * RFC 7807 problem detail. A body that is not a problem detail (a proxy error page,
 * an HTML error, no body at all) becomes a synthetic problem rather than an exception
 * thrown while reporting a failure; a network failure becomes one too.
 */
export const NETWORK_ERROR_DETAIL =
  'Cannot reach the server. Check that the API is running, then try again.'

export const MALFORMED_ERROR_DETAIL =
  'The server returned an unexpected error response.'

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

function syntheticProblem(status: number, detail: string): ProblemDetail {
  return { type: 'about:blank', title: 'Error', status, detail }
}

function isProblemJson(contentType: string): boolean {
  return contentType.includes('problem+json') || contentType.includes('application/json')
}

async function parseProblem(response: Response): Promise<ProblemDetail> {
  if (!isProblemJson(response.headers.get('content-type') ?? '')) {
    return syntheticProblem(response.status, MALFORMED_ERROR_DETAIL)
  }
  try {
    const body: unknown = await response.json()
    if (body === null || typeof body !== 'object') {
      return syntheticProblem(response.status, MALFORMED_ERROR_DETAIL)
    }
    return body as ProblemDetail
  } catch {
    return syntheticProblem(response.status, MALFORMED_ERROR_DETAIL)
  }
}

async function send(path: string, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers)
  if (init?.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  let response: Response
  try {
    response = await fetch(path, { ...init, headers })
  } catch {
    throw new ApiError(0, syntheticProblem(0, NETWORK_ERROR_DETAIL))
  }

  if (!response.ok) {
    throw new ApiError(response.status, await parseProblem(response))
  }
  return response
}

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await send(path, init)
  // The JSON boundary is the one place a cast to the declared response type is honest:
  // types.ts states the contract, and the contract is what the server is asserted against.
  const body: unknown = await response.json()
  return body as T
}

export async function requestVoid(path: string, init?: RequestInit): Promise<void> {
  await send(path, init)
}
