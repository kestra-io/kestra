export function afterLastDot(str: string): string | undefined {
    return str.split(".").pop()
}
