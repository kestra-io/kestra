import fs from "fs"
import path from "path"
import paletteLight from "./palette-light.json" with {type: "json"}
import paletteDark from "./palette-dark.json" with {type: "json"}


function normalizeKey(key) {
    return key.replace(/ /g, "-").toLowerCase()
}
const __dirname = path.dirname(new URL(import.meta.url).pathname)

makePalettes(paletteLight, "light", ":root")
makePalettes(paletteDark, "dark", "html.dark")

function makePalettes(palette, paletteName, selector) {
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

    const cssVariableNames = {}

    function getVariableScss(tokenTheme, tokenName, level = 0) {
        const header = tokenName && level < 2 ? `\t/* ${tokenName} */\n` : ""
        return `${header}${Object.entries(tokenTheme).map(([key, value]) => {
            const normalizedKey = normalizeKey(key)
            const prefix = tokenName ? `${tokenName}-${normalizedKey}` : normalizedKey

            if(typeof value === "object") {
                return getVariableScss(value, prefix, level + 1)
            }

            if(tokenName){
                const cssVariableNamesTheme = cssVariableNames[tokenName] || []
                cssVariableNamesTheme.push(prefix)
                cssVariableNames[tokenName] = cssVariableNamesTheme
            }

            const colorVar = colorIndex[value] ? `$base-${colorIndex[value]}` : value
            return `\t#{--${prefix}}: ${colorVar};`

        }).join("\n")}\n`
    }

    const tokenScss = `\t${getVariableScss(tokens).trim()}`

    // write the scss file containing colors in the token palette
    fs.writeFileSync(path.resolve(__dirname, `../src/styles/layout/theme-${paletteName}.scss`), `@import "../color-palette.scss";\n\n${selector}{\n${tokenScss}\n}`, {encoding: "utf-8"})

    // write the css variables into an index for theme documentation
    fs.writeFileSync(path.resolve(__dirname, "../src/theme/css-variables.json"), JSON.stringify(cssVariableNames, null, 2), {encoding: "utf-8"})
}