import {describe, it, expect, beforeEach, vi} from "vitest"
import {defineComponent} from "vue"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

const mockFeeds: {value: Array<{publicationDate: string}>} = {value: []}
vi.mock("../../../src/stores/api", () => ({
    useApiStore: () => ({feeds: mockFeeds.value}),
}))

vi.mock("@vueuse/core", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@vueuse/core")>()
    return {...actual, useNetwork: () => ({isOnline: {value: true}})}
})

import {useContextButtons} from "../../../src/override/composables/contextButtons"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {en: {contextBar: {news: "News", docs: "Docs", help: "Help", issue: "Issue", demo: "Demo", star: "Star"}}},
})

function mountButtons() {
    let api: ReturnType<typeof useContextButtons>
    const Comp = defineComponent({
        setup() {
            api = useContextButtons()
            return () => null
        },
    })
    mount(Comp, {global: {plugins: [i18n]}})
    return api!
}

describe("useContextButtons news unread", () => {
    beforeEach(() => {
        localStorage.clear()
        mockFeeds.value = []
    })

    it("is unread when no read date has been stored yet", () => {
        mockFeeds.value = [{publicationDate: "2024-01-01T00:00:00Z"}]

        const {buttons} = mountButtons()

        expect(buttons.news.unread?.value).toBe(true)
    })

    it("is unread when the latest feed is newer than the last read date", () => {
        localStorage.setItem("feeds", "2024-01-01T00:00:00Z")
        mockFeeds.value = [{publicationDate: "2024-06-01T00:00:00Z"}]

        const {buttons} = mountButtons()

        expect(buttons.news.unread?.value).toBe(true)
    })

    it("is read once the last read date is at or after the latest feed", () => {
        localStorage.setItem("feeds", "2024-06-01T00:00:00Z")
        mockFeeds.value = [{publicationDate: "2024-01-01T00:00:00Z"}]

        const {buttons} = mountButtons()

        expect(buttons.news.unread?.value).toBe(false)
    })

    it("is read when there are no feeds at all", () => {
        localStorage.setItem("feeds", "2024-01-01T00:00:00Z")
        mockFeeds.value = []

        const {buttons} = mountButtons()

        expect(buttons.news.unread?.value).toBe(false)
    })
})
