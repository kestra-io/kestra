import {describe, it, expect, beforeEach, vi} from "vitest"
import {computed} from "vue"
import {flushPromises} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {useDocStore} from "../../stores/doc"
import {DOC_PATH_INJECTION_KEY} from "../docs/docsUtils"
import ChildCard from "./ChildCard.vue"

let pinia: ReturnType<typeof createPinia>
let children: ReturnType<typeof vi.spyOn>

async function mountCard(docPath: string, props: Record<string, string> = {}) {
    const wrapper = i18nMount(ChildCard, {
        props,
        global: {
            plugins: [pinia],
            provide: {[DOC_PATH_INJECTION_KEY as symbol]: computed(() => docPath)},
        },
    })
    await flushPromises()
    return wrapper
}

const cardTitles = (wrapper: Awaited<ReturnType<typeof mountCard>>) => wrapper.findAll("h4").map(title => title.text())

describe("ChildCard", () => {
    beforeEach(() => {
        pinia = createPinia()
        setActivePinia(pinia)
        children = vi.spyOn(useDocStore(), "children").mockImplementation(async (prefix?: string) => ({
            [`${prefix}`]: {title: "Parent"},
            [`${prefix}/first`]: {title: "First"},
            [`${prefix}/second`]: {title: "Second"},
            [`${prefix}/second/nested`]: {title: "Nested"},
        }))
    })

    it("lists the direct children of the doc page that renders it", async () => {
        const wrapper = await mountCard("docs/tutorial")

        expect(children).toHaveBeenCalledWith("docs/tutorial")
        expect(cardTitles(wrapper)).toEqual(["First", "Second"])
    })

    it("lists the requested directory under its title instead of the current page", async () => {
        const wrapper = await mountCard("docs", {directory: "/docs/use-cases", title: "What’s possible with Kestra"})

        expect(children).toHaveBeenCalledWith("docs/use-cases")
        expect(wrapper.get("h2").text()).toBe("What’s possible with Kestra")
        expect(cardTitles(wrapper)).toEqual(["First", "Second"])
    })

    it("reverses the order when the content sets reverse={true}", async () => {
        const wrapper = await mountCard("docs/migration-guide", {reverse: "{true}"})

        expect(cardTitles(wrapper)).toEqual(["Second", "First"])
    })
})
