export interface TableSort {
    prop: string
    order: "ascending" | "descending"
}

/** The table's `defaultSort` for a `prop:asc|desc` URL sort, read as the API reads it, or `fallback` when the URL holds none. */
export function sortFromQuery(sort: unknown, fallback: TableSort): TableSort {
    const [prop, direction, ...rest] = typeof sort === "string" ? sort.split(":") : []
    if (!prop || !direction || rest.length > 0) {
        return fallback
    }
    return {prop, order: direction === "asc" ? "ascending" : "descending"}
}
