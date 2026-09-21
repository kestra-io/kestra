import {afterEach, describe, expect, it, vi} from "vitest"
import {setMissingKeyPolicy, setupI18n} from "../../../src/translations/i18n"

const i18nWith = (messages: Record<string, unknown>) => setupI18n({locale: "en", messages: {en: messages}} as any)

describe("missing translation keys", () => {
    afterEach(() => {
        setMissingKeyPolicy("throw")
        vi.restoreAllMocks()
    })

    it("throws under test so a raw key fails the test that rendered it", () => {
        const i18n = i18nWith({present: "Present"})
        expect(i18n.global.t("present")).toBe("Present")
        expect(() => i18n.global.t("absent")).toThrow("[i18n] Missing translation key \"absent\"")
    })

    it("reports each missing key once and still renders the key", () => {
        setMissingKeyPolicy("report")
        const error = vi.spyOn(console, "error").mockImplementation(() => {})
        const i18n = i18nWith({})

        expect(i18n.global.t("absent")).toBe("absent")
        expect(i18n.global.t("absent")).toBe("absent")
        expect(i18n.global.t("other")).toBe("other")

        expect(error.mock.calls.map(([message]) => message)).toEqual([
            "[i18n] Missing translation key \"absent\" - it renders as its raw id",
            "[i18n] Missing translation key \"other\" - it renders as its raw id",
        ])
    })

    it("stays quiet when silenced", () => {
        setMissingKeyPolicy("silent")
        const error = vi.spyOn(console, "error").mockImplementation(() => {})
        expect(i18nWith({}).global.t("absent")).toBe("absent")
        expect(error).not.toHaveBeenCalled()
    })
})
