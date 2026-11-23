import {kestraDark} from "../src/index.ts"
import type {CodeTheme, SyntaxRole} from "../src/index.ts"

const roles = Object.keys(kestraDark.syntax) as SyntaxRole[]
const syntax = {...kestraDark.syntax}
roles.forEach((role, i) => {
    syntax[role] = `#${(i + 1).toString(16).padStart(2, "0")}A0B0`
})

/** A theme whose roles all have distinct colors, so a rendered token's color tells which role it resolved to. */
export const probeTheme: CodeTheme = {...kestraDark, name: "probe", syntax}

const byColor = new Map(roles.map(role => [probeTheme.syntax[role].toUpperCase(), role]))

export function roleOf(color: string | undefined): SyntaxRole | undefined {
    return color ? byColor.get(color.toUpperCase()) : undefined
}
