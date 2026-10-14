import {describe, expect, it, vi} from "vitest"

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => `translated:${key}`}),
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({name: undefined, query: {}}),
    useRouter: () => ({
        getRoutes: () => [],
        resolve: () => ({fullPath: "/"}),
    }),
}))

import {useLeftMenu} from "./useLeftMenu"

describe("useLeftMenu", () => {
    it("should translate the section headers", () => {
        const sections = useLeftMenu().menu.value

        expect(sections.find((section) => section.id === "workspace")?.title).toBe("translated:workspace")
        expect(sections.find((section) => section.id === "resources")?.title).toBe("translated:resources")
    })
})
