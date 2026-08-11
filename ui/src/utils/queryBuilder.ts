import moment from "moment"

export function split(q: string): string[] {
    return q.split(/[^a-zA-Z0-9_.-]+/g)
        .filter(r => r !== "")
}

export function toLucene(q: string): string {
    const splitQuery = split(q)

    let query = "(*" + splitQuery.join("*") + "*)^3 OR (*" + splitQuery.join("* AND *") + "*)"

    if (splitQuery.length === 1 ) {
        query = `(${q})^5 OR ${query}`
    }

    return `(${query})`
}

export function toTextLucene(q: string): string {
    const splitQuery = split(q)

    return `(${splitQuery.join(" AND ") })`
}

export function iso(date: string | number): string {
    return moment(new Date(parseInt(String(date)))).toISOString(true)
}
