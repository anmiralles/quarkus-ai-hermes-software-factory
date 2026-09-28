import type { ProblemDetail } from '../api/types'

interface ProblemBannerProps {
  problem: ProblemDetail
}

/**
 * The non-field error region (§5.6): `role="alert"` carrying the problem `detail`.
 * Rendered as plain text only — nothing from a response is ever injected as HTML.
 */
export function ProblemBanner({ problem }: ProblemBannerProps) {
  const message = problem.detail ?? problem.title ?? 'Something went wrong.'
  return <div role="alert">{message}</div>
}
