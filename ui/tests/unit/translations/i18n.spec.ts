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

describe("plural rules", () => {
    const pluralIn = (locale: "pl" | "ru", message: string) => {
        const i18n = setupI18n({locale, messages: {en: {}, [locale]: {message}}} as Parameters<typeof setupI18n>[0])
        return (count: number) => i18n.global.t("message", {count})
    }

    it("picks the Polish one | few | many form", () => {
        const files = pluralIn("pl", "{count} plik | {count} pliki | {count} plików")
        expect([1, 2, 4, 5, 12, 14, 21, 22, 25, 102, 112].map(files)).toEqual([
            "1 plik", "2 pliki", "4 pliki", "5 plików", "12 plików", "14 plików",
            "21 plików", "22 pliki", "25 plików", "102 pliki", "112 plików",
        ])
    })

    it("picks the Russian one | few | many form", () => {
        const files = pluralIn("ru", "{count} файл | {count} файла | {count} файлов")
        expect([0, 1, 2, 4, 5, 11, 12, 14, 21, 22, 25, 101, 111, 112].map(files)).toEqual([
            "0 файлов", "1 файл", "2 файла", "4 файла", "5 файлов", "11 файлов", "12 файлов",
            "14 файлов", "21 файл", "22 файла", "25 файлов", "101 файл", "111 файлов", "112 файлов",
        ])
    })

    it.each([
        ["pl", "brak plików | {count} plik | {count} pliki | {count} plików", ["brak plików", "1 plik", "3 pliki", "5 plików"]],
        ["ru", "нет файлов | {count} файл | {count} файла | {count} файлов", ["нет файлов", "1 файл", "3 файла", "5 файлов"]],
    ] as const)("reads four %s forms as zero | one | few | many", (locale, message, expected) => {
        expect([0, 1, 3, 5].map(pluralIn(locale, message))).toEqual(expected)
    })

    it.each(["pl", "ru"] as const)("falls back to one | other for a two-form %s message", (locale) => {
        expect([1, 2, 5].map(pluralIn(locale, "{count} x | {count} xs"))).toEqual(["1 x", "2 xs", "5 xs"])
    })
})
