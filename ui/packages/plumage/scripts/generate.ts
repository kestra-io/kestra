import {mkdirSync, writeFileSync} from "node:fs"
import {cssVariables, themes, toMonacoTheme, toTextMateTheme} from "../src/index.ts"
import {pebbleInjection} from "../src/pebble.ts"
import pkg from "../package.json" with {type: "json"}

const dist = new URL("../dist/", import.meta.url)

function write(path: string, content: string) {
    const url = new URL(path, dist)
    mkdirSync(new URL(".", url), {recursive: true})
    writeFileSync(url, content)
}

function writeModule(path: string, value: unknown, type: string) {
    write(`${path}.js`, `export default ${JSON.stringify(value)}\n`)
    write(`${path}.d.ts`, `declare const value: ${type}\nexport default value\n`)
}

const css: string[] = []
for (const theme of themes) {
    const textMate = toTextMateTheme(theme)
    writeModule(`monaco/${theme.name}`, toMonacoTheme(theme), "import(\"../index.js\").MonacoThemeData")
    writeModule(`shiki/${theme.name}`, textMate, "import(\"../index.js\").TextMateTheme")
    write(`vscode/${theme.name}.json`, JSON.stringify(textMate, null, 4) + "\n")
    const vars = Object.entries(cssVariables(theme)).map(([name, color]) => `    ${name}: ${color};`)
    css.push(`[data-code-theme="${theme.name}"] {\n${vars.join("\n")}\n}\n`)
}
write("css/themes.css", css.join("\n"))

write("vscode/pebble.tmLanguage.json", JSON.stringify(pebbleInjection, null, 4) + "\n")
write("vscode/package.json", JSON.stringify({
    name: "kestra-plumage",
    displayName: "Kestra Plumage",
    description: pkg.description,
    publisher: "kestra-io",
    version: pkg.version,
    engines: {vscode: "^1.80.0"},
    categories: ["Themes"],
    contributes: {
        themes: themes.map(theme => ({
            label: theme.displayName,
            uiTheme: theme.highContrast ? (theme.kind === "dark" ? "hc-black" : "hc-light") : (theme.kind === "dark" ? "vs-dark" : "vs"),
            path: `./${theme.name}.json`,
        })),
        grammars: [{scopeName: pebbleInjection.scopeName, path: "./pebble.tmLanguage.json", injectTo: pebbleInjection.injectTo}],
    },
}, null, 4) + "\n")
