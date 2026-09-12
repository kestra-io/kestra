const keys = new WeakMap<object, string>()
let counter = 0

/** Stable `v-for` key for an editable row that has no id of its own, tied to the row's identity. */
export function rowKey(row: object): string {
    let key = keys.get(row)
    if (key === undefined) {
        key = `row-${++counter}`
        keys.set(row, key)
    }
    return key
}
