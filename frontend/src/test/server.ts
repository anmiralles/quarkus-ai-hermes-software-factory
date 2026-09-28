import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/**
 * MSW v2 intercepts at the `fetch` boundary, so `api/client.ts` and `api/coffees.ts`
 * run for real in every test (spec §7). Only the server is fake.
 */
export const server = setupServer(...handlers)
