import fs from "fs"
import path from "path"
import paletteLight from "./Sytem color-Default_LIGHT.json" with {type: "json"}
import paletteDark from "./Sytem color-Default_DARK.json" with {type: "json"}


function normalizeKey(key) {
    return key.replace(/ /g, "-").toLowerCase()
}
const __dirname = path.dirname(new URL(import.meta.url).pathname)

makePalettes(paletteLight, "light", ":root")
makePalettes(paletteDark, "dark", "html.dark")

function makePalettes(palette, paletteName, selector) {
    const baseColorNames = Object.keys(palette["base-color-palette"])
    const colorIndex = {}

    const scss = baseColorNames.map(colorName => {
        const colorTheme = palette["base-color-palette"][colorName]
        return Object.entries(colorTheme).map(([key, value]) => {
            colorIndex[value] = normalizeKey(key)
            return `$base-${normalizeKey(key)}: ${value};`
        })
        // sort the colors by length then by name
        .sort((color1, color2) => {
            const [color1Name] = color1.split(":")
            const [color2Name] = color2.split(":")
            return color1Name.length === color2Name.length ? color1Name.localeCompare(color2Name) : color1Name.length - color2Name.length
        })
        .join("\n")
    }).join("\n")

    // write the scss file containing colors in the base palette
    fs.writeFileSync(path.resolve(__dirname, "../src/styles/color-palette.scss"), scss, {encoding: "utf-8"})

    const tokens = palette["ks"]

    const cssVariableNames = {}

    function getVariableScss(tokenTheme, tokenName, level = 0) {
        // TODO: need to bring back the sections headers
        const content = Object.entries(tokenTheme).map(([key, value]) => {
            const normalizedKey = normalizeKey(key)
            const prefix = tokenName ? `${tokenName}-${normalizedKey}` : normalizedKey

            if(typeof value === "object") {
                return getVariableScss(value, prefix, level + 1)
            }

            const tokenRoot = prefix.split("-")[1]
            const cssVariableNamesTheme = cssVariableNames[tokenRoot] || []
            cssVariableNamesTheme.push(prefix)
            cssVariableNames[tokenRoot] = cssVariableNamesTheme

            const colorVar = colorIndex[value] ? `$base-${colorIndex[value]}` : value
            return `\t#{--${prefix}}: ${colorVar};`
        }).sort()

        if(level > 0) {
            return content.join("\n")
        }

        // add categories comments to make it clearer to find
        const contentWithCategoriesHeaders = []
        let previousCategory = null
        for (const line of content) {
            const currentCategory = line.match(/#{--\w+-(\w+)/)?.[1] || "Other"
            if (previousCategory !== currentCategory) {
                contentWithCategoriesHeaders.push(`\n\t/* ${currentCategory} */`)
            }
            previousCategory = currentCategory

            contentWithCategoriesHeaders.push(line)
        }

        return `${contentWithCategoriesHeaders.join("\n")}\n`
    }

    const tokenScss = `\t${getVariableScss(tokens, "ks").trim()}`

    // write the scss file containing colors in the token palette
    fs.writeFileSync(path.resolve(__dirname, `../src/styles/layout/theme-${paletteName}.scss`), `@import "../color-palette.scss";\n\n${selector}{\n${tokenScss}\n}`, {encoding: "utf-8"})

    // write the css variables into an index for theme documentation
    fs.writeFileSync(path.resolve(__dirname, "../src/theme/css-variables.json"), JSON.stringify(cssVariableNames, null, 2), {encoding: "utf-8"})
}