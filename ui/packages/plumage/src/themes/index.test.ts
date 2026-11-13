import {describe, expect, it} from "vitest"
import {themes} from "./index.ts"
import {PEBBLE_ROLES} from "../pebble.ts"

function luminance(hex: string) {
    const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
        .map(v => v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)
    return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!
}

function contrast(a: string, b: string) {
    const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
    return (hi! + 0.05) / (lo! + 0.05)
}

/** CIE76 color difference: about 2 is the smallest a viewer notices, above 25 reads as a different color. */
function difference(first: string, second: string) {
    const lab = (hex: string) => {
        const [r, g, b] = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255)
            .map(v => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)
        const [x, y, z] = [(r! * 0.4124 + g! * 0.3576 + b! * 0.1805) / 0.95047, r! * 0.2126 + g! * 0.7152 + b! * 0.0722, (r! * 0.0193 + g! * 0.1192 + b! * 0.9505) / 1.08883]
            .map(t => t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116)
        return [116 * y! - 16, 500 * (x! - y!), 200 * (y! - z!)]
    }
    const [l1, a1, b1] = lab(first), [l2, a2, b2] = lab(second)
    return Math.hypot(l1! - l2!, a1! - a2!, b1! - b2!)
}

describe("contrast", () => {
    it.each(themes.map(theme => [theme.name, theme] as const))("%s keeps every syntax role readable on its background", (_, theme) => {
        const minimum = theme.highContrast ? 7 : 4.5
        const failing = Object.entries(theme.syntax)
            .map(([role, color]) => [role, Number(contrast(color, theme.editor.background).toFixed(2))] as const)
            .filter(([, ratio]) => ratio < minimum)
        expect(failing).toEqual([])
    })

    it.each(themes.map(theme => [theme.name, theme] as const))("%s keeps function names and parameters apart from plain code", (_, theme) => {
        const {syntax} = theme
        const failing = (["function", "parameter"] as const).flatMap(role => (["text", "value"] as const)
            .map(plain => [role, plain, Math.round(difference(syntax[role], syntax[plain]))] as const))
            .filter(([, , delta]) => delta < 25)
        expect(failing).toEqual([])
    })

    it.each(themes.map(theme => [theme.name, theme] as const))("%s keeps the code inside a Pebble block apart from the block color", (_, theme) => {
        const {syntax} = theme
        const failing = PEBBLE_ROLES.filter(role => !["pebble", "constant", "operator", "punctuation"].includes(role))
            .map(role => [role, Math.round(difference(syntax[role], syntax.pebble))] as const)
            .filter(([, delta]) => delta < 25)
        expect(failing).toEqual([])
    })
})
