export interface ExplorerSearchItem {
    label: string;
    value: unknown;
    searchText?: string;
}

export function serializeExplorerValue(value: unknown): string {
    if (value === undefined) {
        return ""
    }

    if (value === null) {
        return "null"
    }

    if (typeof value === "string") {
        return value
    }

    try {
        return JSON.stringify(value)
    } catch {
        return String(value)
    }
}

export function matchesExplorerItem(item: ExplorerSearchItem, rawQuery: string): boolean {
    const query = rawQuery.trim().toLowerCase()
    if (!query) {
        return true
    }

    if (item.label.toLowerCase().includes(query)) {
        return true
    }

    if (item.searchText?.toLowerCase().includes(query)) {
        return true
    }

    return serializeExplorerValue(item.value).toLowerCase().includes(query)
}

export function taskOutputLabel(taskId: string, iterationValue?: string | number | null): string {
    if (iterationValue === undefined || iterationValue === null || iterationValue === "") {
        return taskId
    }

    return `${taskId} - ${iterationValue}`
}
