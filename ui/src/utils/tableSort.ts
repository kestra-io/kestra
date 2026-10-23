import type {LocationQuery} from "vue-router"

export interface TableSort {
    prop: string
    order: "ascending" | "descending"
}

export const sortFromQuery = (sort: LocationQuery[string] | undefined, fallback: TableSort): TableSort => {
    const parts = typeof sort === "string" ? sort.split(":") : []
    if (parts.length !== 2 || !parts[0] || !parts[1]) {
        return fallback
    }
    // Read the direction as the API does: "asc" is ascending, anything else descending.
    return {prop: parts[0], order: parts[1] === "asc" ? "ascending" : "descending"}
}
