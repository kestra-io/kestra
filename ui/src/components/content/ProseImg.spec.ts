import {describe, it, expect, beforeEach} from "vitest"
import {computed} from "vue"
import {createPinia, setActivePinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {useDocStore} from "../../stores/doc"
import {DOC_PATH_INJECTION_KEY} from "../docs/docsUtils"
import ProseImg from "./ProseImg.vue"

let pinia: ReturnType<typeof createPinia>

function mountImg(docPath?: string) {
    return i18nMount(ProseImg, {
        props: {src: "./main_page.png", alt: "Dashboard Main Page"},
        global: {
            plugins: [pinia],
            provide: docPath === undefined ? {} : {[DOC_PATH_INJECTION_KEY as symbol]: computed(() => docPath)},
        },
    })
}

describe("ProseImg", () => {
    beforeEach(() => {
        pinia = createPinia()
        setActivePinia(pinia)
        useDocStore().initResourceUrlTemplate("1.0.0")
    })

    it("resolves a relative image against the doc path the page renders, without the side panel's store state", () => {
        const wrapper = mountImg("docs/ui/dashboard")

        expect(wrapper.get("img").attributes("src")).toBe("https://api.kestra.io/v1/docs/docs/ui/dashboard/main_page.png/versions/1.0.0")
    })

    it("leaves the image untouched when no page provides a doc path", () => {
        const wrapper = mountImg()

        expect(wrapper.get("img").attributes("src")).toBe("https://api.kestra.io/v1/docs/./main_page.png/versions/1.0.0")
    })
})
