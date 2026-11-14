import {afterEach, describe, expect, it} from "vitest"
import {storageKeys} from "../../utils/constants"
import {getDefaultActiveTabs} from "./defaultActiveTabs"

describe("getDefaultActiveTabs", () => {
    afterEach(() => {
        localStorage.removeItem(storageKeys.EDITOR_OPEN_DOCS_TAB)
        localStorage.removeItem(storageKeys.EDITOR_VIEW_TYPE)
    })

    it("opens the docs tab next to the editor by default", () => {
        expect(getDefaultActiveTabs()).toEqual(["code", "doc"])
    })

    it("leaves the docs tab closed when it is disabled in the settings", () => {
        localStorage.setItem(storageKeys.EDITOR_OPEN_DOCS_TAB, "false")
        expect(getDefaultActiveTabs()).toEqual(["code"])
    })

    it("opens only the no-code editor when it is the default and the docs tab is disabled", () => {
        localStorage.setItem(storageKeys.EDITOR_VIEW_TYPE, "NO_CODE")
        localStorage.setItem(storageKeys.EDITOR_OPEN_DOCS_TAB, "false")
        expect(getDefaultActiveTabs()).toEqual(["nocode"])
    })
})
