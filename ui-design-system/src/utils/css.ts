export function cssVar(name: string, opacity?: number): string {
    if (typeof window === "undefined") {
        return ""
    }

    const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()

    if (opacity === undefined) {
        return value
    }

    const hex = value.replace("#", "")
    const r = parseInt(hex.substring(0, 2), 16)
    const g = parseInt(hex.substring(2, 4), 16)
    const b = parseInt(hex.substring(4, 6), 16)

    return `rgba(${r}, ${g}, ${b}, ${opacity})`
}