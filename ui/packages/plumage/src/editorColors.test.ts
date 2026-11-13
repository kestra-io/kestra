import {describe, expect, it} from "vitest"
import {editorColors} from "./editorColors.ts"
import type {CodeTheme} from "./types.ts"
import {kestraLight, kestraDark, kestraDarkHighContrast} from "./themes/index.ts"

/** Build a synthetic CodeTheme where every colour field is a unique sentinel #RRGGBB value. */
function buildSentinelTheme(highContrast: boolean): {theme: CodeTheme; sentinels: Map<string, string>} {
    let counter = 1
    const next = () => `#${(counter++).toString(16).padStart(6, "0")}`
    const sentinels = new Map<string, string>()
    const field = (name: string) => {
        const val = next()
        sentinels.set(name, val)
        return val
    }

    const theme: CodeTheme = {
        name: "sentinel",
        displayName: "Sentinel",
        kind: "dark",
        highContrast,
        syntax: {
            text: field("syntax.text"),
            comment: field("syntax.comment"),
            punctuation: field("syntax.punctuation"),
            key: field("syntax.key"),
            value: field("syntax.value"),
            pebble: field("syntax.pebble"),
            constant: field("syntax.constant"),
            number: field("syntax.number"),
            string: field("syntax.string"),
            escape: field("syntax.escape"),
            keyword: field("syntax.keyword"),
            operator: field("syntax.operator"),
            type: field("syntax.type"),
            function: field("syntax.function"),
            parameter: field("syntax.parameter"),
            variable: field("syntax.variable"),
            builtin: field("syntax.builtin"),
            tag: field("syntax.tag"),
            attribute: field("syntax.attribute"),
            annotation: field("syntax.annotation"),
            regexp: field("syntax.regexp"),
            namespace: field("syntax.namespace"),
            heading: field("syntax.heading"),
            link: field("syntax.link"),
            invalid: field("syntax.invalid"),
        },
        editor: {
            background: field("editor.background"),
            lineHighlight: field("editor.lineHighlight"),
            selection: field("editor.selection"),
            selectionInactive: field("editor.selectionInactive"),
            selectionHighlight: field("editor.selectionHighlight"),
            findMatch: field("editor.findMatch"),
            findMatchHighlight: field("editor.findMatchHighlight"),
            cursor: field("editor.cursor"),
            lineNumber: field("editor.lineNumber"),
            lineNumberActive: field("editor.lineNumberActive"),
            indentGuide: field("editor.indentGuide"),
            indentGuideActive: field("editor.indentGuideActive"),
            whitespace: field("editor.whitespace"),
            bracketMatch: field("editor.bracketMatch"),
            widget: field("editor.widget"),
            widgetBorder: field("editor.widgetBorder"),
            scrollbar: field("editor.scrollbar"),
            scrollbarHover: field("editor.scrollbarHover"),
            inserted: field("editor.inserted"),
            removed: field("editor.removed"),
            error: field("editor.error"),
            warning: field("editor.warning"),
            info: field("editor.info"),
        },
    }

    return {theme, sentinels}
}

const TRANSPARENT = "#00000000"

describe("editorColors", () => {
    it("every returned value comes from the theme — no hard-coded colour other than the transparent inserted-text entry", () => {
        const {theme, sentinels} = buildSentinelTheme(false)
        const result = editorColors(theme)

        const sentinelSet = new Set(sentinels.values())
        for (const [key, value] of Object.entries(result)) {
            if (key === "diffEditor.insertedTextBackground") {
                expect(value, key).toBe(TRANSPARENT)
            } else {
                expect(sentinelSet.has(value), `${key}: "${value}" is not a sentinel and not the transparent constant`).toBe(true)
            }
        }
    })

    it("editor background, foreground and cursor map to the expected theme fields", () => {
        const {theme, sentinels} = buildSentinelTheme(false)
        const result = editorColors(theme)

        expect(result["editor.background"]).toBe(sentinels.get("editor.background"))
        expect(result["editor.foreground"]).toBe(sentinels.get("syntax.text"))
        expect(result["editorCursor.foreground"]).toBe(sentinels.get("editor.cursor"))
    })

    it("contrastBorder is present only when highContrast is set", () => {
        const {theme: hcTheme, sentinels} = buildSentinelTheme(true)
        const hcResult = editorColors(hcTheme)
        expect(hcResult).toHaveProperty("contrastBorder", sentinels.get("editor.widgetBorder"))

        const {theme: normalTheme} = buildSentinelTheme(false)
        const normalResult = editorColors(normalTheme)
        expect(normalResult).not.toHaveProperty("contrastBorder")
    })

    it("editor.lineHighlightBorder is widgetBorder under highContrast and lineHighlight otherwise", () => {
        const {theme: hcTheme, sentinels: hcSentinels} = buildSentinelTheme(true)
        const hcResult = editorColors(hcTheme)
        expect(hcResult["editor.lineHighlightBorder"]).toBe(hcSentinels.get("editor.widgetBorder"))

        const {theme: normalTheme, sentinels: normalSentinels} = buildSentinelTheme(false)
        const normalResult = editorColors(normalTheme)
        expect(normalResult["editor.lineHighlightBorder"]).toBe(normalSentinels.get("editor.lineHighlight"))
    })

    it("the three bracket highlight colours come from the syntax palette", () => {
        const {theme, sentinels} = buildSentinelTheme(false)
        const result = editorColors(theme)

        expect(result["editorBracketHighlight.foreground1"]).toBe(sentinels.get("syntax.key"))
        expect(result["editorBracketHighlight.foreground2"]).toBe(sentinels.get("syntax.type"))
        expect(result["editorBracketHighlight.foreground3"]).toBe(sentinels.get("syntax.number"))
    })

    it("two different themes produce different maps with the same set of keys", () => {
        const lightResult = editorColors(kestraLight)
        const darkResult = editorColors(kestraDark)

        const lightKeys = Object.keys(lightResult).sort()
        const darkKeys = Object.keys(darkResult).sort()
        expect(lightKeys).toEqual(darkKeys)

        expect(lightResult).not.toEqual(darkResult)
    })

    it("highContrast theme produces the same set of keys as a normal theme plus contrastBorder", () => {
        const normalResult = editorColors(kestraLight)
        const hcResult = editorColors(kestraDarkHighContrast)

        const normalKeys = Object.keys(normalResult).sort()
        const hcKeys = Object.keys(hcResult).sort()

        expect(hcKeys).toEqual([...normalKeys, "contrastBorder"].sort())
    })
})

