import moment from "moment"

export function split(q) {
    return q.split(/[^a-zA-Z0-9_.-]+/g)
        .filter(r => r !== "")
}

export function toLucene(q) {
    const splitted = split(q)

    let query = "(*" + splitted.join("*") + "*)^3 OR (*" + splitted.join("* AND *") + "*)"

    if (splitted.length === 1 ) {
        query = `(${q})^5 OR ${query}`
    }

    return `(${query})`
}

export function toTextLucene(q) {
    const splitted = split(q)

    return `(${splitted.join(" AND ") })`
}

export function iso(date) {
    return moment(new Date(parseInt(date))).toISOString(true)
}
