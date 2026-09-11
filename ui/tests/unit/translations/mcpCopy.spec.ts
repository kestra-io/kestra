import {createI18n} from "vue-i18n"
import {describe, expect, it} from "vitest"

import en from "../../../src/translations/en.json"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: en,
})

describe("MCP copy", () => {
    it("uses the singular server label for a count of one", () => {
        expect(i18n.global.t("mcp.servers_count", {count: 1})).toBe("1 MCP Server")
    })

    it("uses the plural server label for a count of two", () => {
        expect(i18n.global.t("mcp.servers_count", {count: 2})).toBe("2 MCP Servers")
    })
})
