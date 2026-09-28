interface PagerProps {
  page: number
  size: number
  totalPages: number
  previousDisabled: boolean
  nextDisabled: boolean
  onPrevious: () => void
  onNext: () => void
  onSizeChange: (size: number) => void
}

export const PAGE_SIZES: readonly number[] = [10, 20, 50, 100]

/**
 * Paging controls (§5.7). `page` is the 0-based wire index; the display is 1-based
 * (`Page N of M`, N = page + 1) — mixing the two is the classic off-by-one here (D-8).
 * `totalPages: 0` (an empty catalogue) renders as `Page 1 of 1` with both buttons
 * disabled, so the indicator never reads `Page 1 of 0`.
 */
export function Pager({
  page,
  size,
  totalPages,
  previousDisabled,
  nextDisabled,
  onPrevious,
  onNext,
  onSizeChange,
}: PagerProps) {
  const displayedTotalPages = Math.max(totalPages, 1)

  return (
    <div className="pager">
      <button type="button" onClick={onPrevious} disabled={previousDisabled}>
        Previous page
      </button>
      <span>{`Page ${page + 1} of ${displayedTotalPages}`}</span>
      <button type="button" onClick={onNext} disabled={nextDisabled}>
        Next page
      </button>
      <label htmlFor="rows-per-page">Rows per page</label>
      <select
        id="rows-per-page"
        value={String(size)}
        onChange={(event) => onSizeChange(Number(event.target.value))}
      >
        {PAGE_SIZES.map((value) => (
          <option key={value} value={String(value)}>
            {value}
          </option>
        ))}
      </select>
    </div>
  )
}
