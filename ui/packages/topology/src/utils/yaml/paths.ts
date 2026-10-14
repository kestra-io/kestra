// Dotted flow paths (`tasks[0].then`) to and from segment arrays. No yaml involved.

export function parsePath(path: string): (string | number)[] {
    const segments: (string | number)[] = []
    let i = 0
    while (i < path.length) {
        const ch = path[i]
        if (ch === ".") {
            i++
        } else if (ch === "[") {
            const quote = path[i + 1]
            if (quote === "\"" || quote === "'") {
                let key = ""
                let j = i + 2
                while (j < path.length && path[j] !== quote) {
                    if (path[j] === "\\" && j + 1 < path.length) {
                        key += path[j + 1]
                        j += 2
                    } else {
                        key += path[j]
                        j++
                    }
                }
                i = path[j + 1] === "]" ? j + 2 : j + 1
                segments.push(key)
            } else {
                const close = path.indexOf("]", i)
                const inner = path.slice(i + 1, close)
                segments.push(/^\d+$/.test(inner) ? parseInt(inner, 10) : inner)
                i = close + 1
            }
        } else {
            let j = i
            while (j < path.length && path[j] !== "." && path[j] !== "[") j++
            segments.push(path.slice(i, j))
            i = j
        }
    }
    return segments
}

function keyNeedsQuoting(key: string): boolean {
    return key === "" || /[.[\]"'\\]/.test(key)
}

function quoteKey(key: string): string {
    return `["${key.replace(/\\/g, "\\\\").replace(/"/g, "\\\"")}"]`
}

export function appendKeyToPath(basePath: string, key: string): string {
    if (keyNeedsQuoting(key)) return `${basePath}${quoteKey(key)}`
    return basePath ? `${basePath}.${key}` : key
}

export function joinPath(segments: (string | number)[]): string {
    let out = ""
    for (const seg of segments) {
        if (typeof seg === "number") out += `[${seg}]`
        else if (keyNeedsQuoting(seg)) out += quoteKey(seg)
        else out += out ? `.${seg}` : seg
    }
    return out
}

export function hasQuotedSegment(path: string): boolean {
    return path.includes("[\"") || path.includes("['")
}

export function lastSegmentKey(path: string): string {
    return hasQuotedSegment(path)
        ? String(parsePath(path).at(-1))
        : (path.split(".").pop() as string)
}
