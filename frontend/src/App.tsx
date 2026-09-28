import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from './api/client'
import { createCoffee, deleteCoffee, listCoffees, updateCoffee } from './api/coffees'
import type { Coffee, CoffeeRequest, ProblemDetail } from './api/types'
import { CoffeeForm } from './components/CoffeeForm'
import { CoffeeTable } from './components/CoffeeTable'
import { Pager } from './components/Pager'
import { ProblemBanner } from './components/ProblemBanner'

/** The list page always asks for an explicit size, so the request is self-describing (§5.1). */
const DEFAULT_PAGE_SIZE = 20

type FormState = { mode: 'closed' } | { mode: 'create' } | { mode: 'edit'; coffee: Coffee }

type FieldName = 'name' | 'roastLevel' | 'origin' | 'price' | 'stock'

function toProblem(error: unknown): ProblemDetail {
  if (error instanceof ApiError) {
    return error.problem
  }
  return { type: 'about:blank', title: 'Error', detail: 'Something went wrong.' }
}

/**
 * The single page: form panel + list + pager (§4, §5). No router and no client-state
 * library (D-2) — `page`, `size`, the open form and the message are `useState`; the
 * catalogue itself is server state and lives in TanStack Query.
 */
export function App() {
  const queryClient = useQueryClient()

  const [page, setPage] = useState(0)
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE)
  const [form, setForm] = useState<FormState>({ mode: 'closed' })
  const [statusMessage, setStatusMessage] = useState('')
  const [problem, setProblem] = useState<ProblemDetail | null>(null)
  const [confirmingId, setConfirmingId] = useState<string | null>(null)

  const listQuery = useQuery({
    queryKey: ['coffees', page, size],
    queryFn: () => listCoffees(page, size),
    retry: false,
  })

  const invalidateList = () => queryClient.invalidateQueries({ queryKey: ['coffees'] })

  const createMutation = useMutation({
    mutationFn: createCoffee,
    onSuccess: () => {
      setProblem(null)
      setForm({ mode: 'closed' })
      setStatusMessage('Coffee created')
      void invalidateList()
    },
    onError: (error) => setProblem(toProblem(error)),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, request }: { id: string; request: CoffeeRequest }) =>
      updateCoffee(id, request),
    onSuccess: () => {
      setProblem(null)
      setForm({ mode: 'closed' })
      setStatusMessage('Coffee updated')
      void invalidateList()
    },
    onError: (error) => setProblem(toProblem(error)),
  })

  const deleteMutation = useMutation({
    mutationFn: deleteCoffee,
    onSuccess: () => {
      setProblem(null)
      setConfirmingId(null)
      setStatusMessage('Coffee deleted')
      void invalidateList()
    },
    onError: (error) => {
      setProblem(toProblem(error))
      setConfirmingId(null)
      // A 404 means the row is stale (deleted elsewhere): refetching is what makes it
      // disappear (§5.5). Any other failure refetches too — it costs one request and
      // keeps the table honest.
      void invalidateList()
    },
  })

  const fieldErrors = useMemo<Partial<Record<FieldName, string>>>(() => {
    const map: Partial<Record<FieldName, string>> = {}
    for (const entry of problem?.errors ?? []) {
      map[entry.field as FieldName] = entry.message
    }
    return map
  }, [problem])

  // Field failures are rendered against their field (§5.6); the alert region carries the
  // problem `detail` for everything else: 404, 409, 5xx, malformed body, network failure.
  const alertProblem =
    problem !== null && (problem.errors === undefined || problem.errors.length === 0)
      ? problem
      : null

  const listProblem =
    listQuery.isError && listQuery.error instanceof ApiError ? listQuery.error.problem : null

  const submitting = createMutation.isPending || updateMutation.isPending
  const data = listQuery.data
  const coffees = data?.content ?? []

  function handleSubmit(request: CoffeeRequest) {
    if (form.mode === 'edit') {
      updateMutation.mutate({ id: form.coffee.id, request })
    } else if (form.mode === 'create') {
      createMutation.mutate(request)
    }
  }

  function openCreateForm() {
    setProblem(null)
    setConfirmingId(null)
    setForm({ mode: 'create' })
  }

  function openEditForm(coffee: Coffee) {
    setProblem(null)
    setConfirmingId(null)
    setForm({ mode: 'edit', coffee })
  }

  function closeForm() {
    setProblem(null)
    setForm({ mode: 'closed' })
  }

  return (
    <main>
      <h1>Coffee catalogue</h1>

      <p role="status">{statusMessage}</p>

      {alertProblem !== null ? <ProblemBanner problem={alertProblem} /> : null}

      <p>
        <button type="button" onClick={openCreateForm}>
          New coffee
        </button>
      </p>

      {form.mode !== 'closed' ? (
        <CoffeeForm
          key={form.mode === 'edit' ? form.coffee.id : 'create'}
          mode={form.mode}
          coffee={form.mode === 'edit' ? form.coffee : undefined}
          fieldErrors={fieldErrors}
          submitting={submitting}
          onSubmit={handleSubmit}
          onCancel={closeForm}
        />
      ) : null}

      {listQuery.isPending ? <p>Loading coffees…</p> : null}

      {!listQuery.isPending && listProblem !== null ? (
        <ProblemBanner problem={listProblem} />
      ) : null}

      {!listQuery.isPending && listProblem === null && data !== undefined && coffees.length === 0 ? (
        <p>No coffees yet.</p>
      ) : null}

      {data !== undefined && coffees.length > 0 ? (
        <CoffeeTable
          coffees={coffees}
          confirmingId={confirmingId}
          onEdit={openEditForm}
          onRequestDelete={(coffee) => setConfirmingId(coffee.id)}
          onConfirmDelete={(coffee) => deleteMutation.mutate(coffee.id)}
          onCancelDelete={() => setConfirmingId(null)}
        />
      ) : null}

      {data !== undefined ? (
        <Pager
          page={data.page}
          size={data.size}
          totalPages={data.totalPages}
          previousDisabled={data.page <= 0}
          nextDisabled={data.totalPages === 0 || data.page + 1 >= data.totalPages}
          onPrevious={() => setPage((current) => Math.max(0, current - 1))}
          onNext={() => setPage((current) => current + 1)}
          onSizeChange={(nextSize) => {
            setSize(nextSize)
            setPage(0)
          }}
        />
      ) : null}
    </main>
  )
}
