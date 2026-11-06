import {describe, expect, test} from "vitest"
import NamespaceFilesEmpty from "./NamespaceFilesEmpty.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const messages = {
    empty: {
        namespaceFiles: {
            title: "No files in this namespace yet",
            content: "Namespace Files work like a project folder for this namespace. Store Python, SQL, dbt or Terraform code, then reference any file in your flows with",
        },
    },
    "namespace files": {
        new_file: "New File",
    },
    ks_empty_state: {
        learn_more: "Learn more",
    },
}

function mountEmpty() {
    return i18nMount(NamespaceFilesEmpty, {messages})
}

describe("NamespaceFilesEmpty", () => {
    test("renders the title and description text", () => {
        const wrapper = mountEmpty()

        expect(wrapper.text()).toContain("No files in this namespace yet")
        expect(wrapper.text()).toContain(
            "Namespace Files work like a project folder for this namespace. Store Python, SQL, dbt or Terraform code, then reference any file in your flows with",
        )
    })

    test("renders the read('path/to/file') snippet", () => {
        const wrapper = mountEmpty()

        expect(wrapper.find(".namespace-files-empty-snippet").text()).toBe("read('path/to/file')")
    })

    test("renders the namespace files image and learn-more link", () => {
        const wrapper = mountEmpty()

        const img = wrapper.find(".ks-empty-state__artwork img")
        expect(img.exists()).toBe(true)
        expect(img.attributes("src")).toMatch(/namespaceFiles/)

        const learnMore = wrapper.find(".ks-empty-state__actions a")
        expect(learnMore.exists()).toBe(true)
        expect(learnMore.attributes("href")).toBe(
            "https://kestra.io/docs/concepts/namespace-files?utm_source=app&utm_medium=referral&utm_campaign=namespace-files",
        )
        expect(learnMore.attributes("target")).toBe("_blank")
        expect(learnMore.text()).toBe("Learn more")
    })

    test("clicking the action emits newFile exactly once", async () => {
        const wrapper = mountEmpty()

        await wrapper.get("button").trigger("click")

        expect(wrapper.emitted("newFile")).toHaveLength(1)
    })
})
