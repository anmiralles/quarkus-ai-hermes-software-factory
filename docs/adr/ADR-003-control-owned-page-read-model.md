# ADR-003: Paging returns a control-owned `CoffeePage`, not Panache's `Page`

Status: accepted
Date: 2026-09-28
Deciders: architect (card `t_da7e20d9`)
Governs: `backend/src/main/java/com/example/coffeeshop/control/CoffeePage.java`,
`control/CoffeeService.listCoffees`,
`entity/CoffeeRepository.findAllPaged`,
`boundary/dto/CoffeePageResponse.java`

## Context

The specification's §5 and §6 (revision 1) printed the paging read path as `Page<Coffee>`:
`Page<Coffee> listCoffees(int page, int size)` in control and
`Page<Coffee> findAllPaged(int page, int size)` in the repository.

Implementing it (card `t_8046549a`) surfaced two hard facts:

- `io.quarkus.panache.common.Page` **carries no type parameter**. It is the *request* object
  describing an offset and a limit (`Page.of(page, size)`), not a result set. `Page<Coffee>`
  does not compile.
- §1.2 of the specification forbids `control` from importing `io.quarkus.panache.common.*`
  at all, so a Panache page type could not appear in a control signature even if it were
  generic.

The REST contract, though, needs three things a bare `List<Coffee>` cannot carry: the
effective `page` and `size` (a defaulted request must be visible to the client, §3.2) and
`totalElements` (to compute `totalPages`).

## Decision

We will model a page of the catalogue as a **control-owned record**,
`control/CoffeePage(List<Coffee> content, int page, int size, long totalElements)`, and have
`CoffeeService.listCoffees(int, int)` return it. `entity/CoffeeRepository.findAllPaged(int,
int)` returns the plain `List<Coffee>` for the requested page; the totals come from the
inherited `count()`.

## Alternatives considered

1. **`Page<Coffee>`, as revision 1 printed it.** Rejected: it does not compile (`Page` is not
   generic) and the import is forbidden to `control` by §1.2. This was a defect in the
   specification, not a design choice.
2. **A Spring-style generic `Page<T>` of our own** (`content`, `page`, `size`,
   `totalElements`, `totalPages`, `sort`). Rejected as premature: `totalPages` is a pure
   function of the other four fields and belongs in the boundary's response mapping, and the
   sort is fixed by the contract (there is no `sort` parameter). A generic page type would be
   a second public vocabulary to keep in sync, for one aggregate.
3. **Return `List<Coffee>` from control and let the boundary call `count()` itself.**
   Rejected: it either moves a persistence call into `boundary` or forces the resource to
   issue two use-case calls per endpoint, breaking the one-call-per-endpoint shape §1.1
   describes.
4. **Return Panache's `Page` wrapped in a tuple the boundary unwraps.** Rejected: it leaks a
   Panache type across the layer boundary, which is the thing §1.2 exists to prevent.

## Consequences

Easier: the paging contract is one small record that unit-tests without HTTP and without a
database; `totalPages` is computed in exactly one place; `control` imports nothing from
Panache, so the §1.2 grep stays green; and the same record is the natural input to any future
list endpoint.

Harder, and accepted: producing one page when a total is wanted costs two queries
(`findAllPaged` + `count()`) rather than one. On a catalogue table that is not a cost worth
leaking a Panache type for, but it is a real trade-off and it is recorded here rather than
hidden. `control/CoffeePage` also mentions `Coffee` in a control signature, which §1.1
already permits ("control returns entities and page objects").

## Revisit when

A page read costs measurably more than its row data (the second query shows up in
profiling), or a second list endpoint needs a materially different read model. Generalising
before either signal appears would be speculation.