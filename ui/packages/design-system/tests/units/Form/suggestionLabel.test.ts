import {describe, expect, it} from "vitest"
import {suggestionLabel} from "../../../src/composables/useSuggestWidgetIcons"

/** A Monaco suggest row, as the widget renders it for a string label or for a `{label, description}` one. */
function row({name, description, stringLabel}: {name: string; description?: string; stringLabel: boolean}): HTMLElement {
    const element = document.createElement("div")
    element.className = stringLabel ? "monaco-list-row string-label" : "monaco-list-row"
    element.innerHTML = `
        <span class="monaco-icon-name-container">${name}</span>
        <span class="details-label">${description ?? ""}</span>
    `
    return element
}

describe("suggestionLabel", () => {
    it("rejoins the package and the class name of a plugin type", () => {
        expect(suggestionLabel(row({
            name: "CreateExecutionButton",
            description: "io.kestra.plugin.ee.apps.execution.blocks",
            stringLabel: false,
        }))).toBe("io.kestra.plugin.ee.apps.execution.blocks.CreateExecutionButton")
    })

    it("keeps a string label as it is, even when a detail is rendered next to it", () => {
        expect(suggestionLabel(row({name: "namespace", description: "required", stringLabel: true}))).toBe("namespace")
    })

    it("keeps the rendered label when there is no description to rejoin", () => {
        expect(suggestionLabel(row({name: "SUCCESS", stringLabel: false}))).toBe("SUCCESS")
    })

    it("falls back to the aria-label when nothing is rendered yet", () => {
        const element = document.createElement("div")
        element.className = "monaco-list-row"
        element.setAttribute("aria-label", "RUNNING, value")
        expect(suggestionLabel(element)).toBe("RUNNING")
    })
})
