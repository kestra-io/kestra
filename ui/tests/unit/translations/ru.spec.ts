import {describe, expect, it} from "vitest"
import translations from "../../../src/translations/ru.json"

interface RuTranslations {
    ru: {block_editor: {namespace_secrets: string}}
}

const ru: RuTranslations = translations

describe("ru.json block_editor labels", () => {
    // Regression: "namespace_secrets" shipped as "Cекреты" — a Latin "C" (U+0043) standing in for the
    // Cyrillic "С" (U+0421). It renders identically, so only a codepoint-level check catches it.
    it("has no Latin letter mixed into a Cyrillic-only label", () => {
        const namespaceSecrets = ru.ru.block_editor.namespace_secrets
        expect(namespaceSecrets).toMatch(/^[Ѐ-ӿ]+$/)
    })
})
