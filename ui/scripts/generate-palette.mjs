import fs from "fs"
import path from "path"
import palette from "./palette-light.json" with {type: "json"}

function normalizeKey(key) {
    return key.replace(/ /g, "-").toLowerCase()
}

const __dirname = path.dirname(new URL(import.meta.url).pathname)

const baseColorNames = Object.keys(palette["Base Color Palette"])
const colorIndex = {}

const scss = baseColorNames.map(colorName => {
    const colorTheme = palette["Base Color Palette"][colorName]
    return Object.entries(colorTheme).map(([key, value]) => {
        colorIndex[value] = normalizeKey(key)
        return `$base-${normalizeKey(key)}: ${value};`
    }).join("\n")
}).join("\n")

// write the scss file containing colors in the base palette
fs.writeFileSync(path.resolve(__dirname, "../src/styles/color-palette.scss"), scss, {encoding: "utf-8"})

const tokens = palette["Tokens"]

function getVariableScss(tokenTheme, tokenName) {
    return Object.entries(tokenTheme).map(([key, value]) => {
        const normalizedKey = normalizeKey(key)
        const prefix = tokenName ? `${tokenName}-${normalizedKey}` : normalizedKey
        if(typeof value === "object") {
            return getVariableScss(value, prefix)
        }
        const colorVar = colorIndex[value] ? `$base-${colorIndex[value]}` : value
        return `\t#{--${prefix}}: ${colorVar};`

    }).join("\n")
}

const tokenScss = getVariableScss(tokens)

// write the scss file containing colors in the token palette
fs.writeFileSync(path.resolve(__dirname, "../src/styles/layout/theme-light.scss"), `@import "../color-palette.scss";\n\n:root{\n${tokenScss}\n}`, {encoding: "utf-8"})