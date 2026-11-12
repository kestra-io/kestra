import {describe, test, expect, vi} from "vitest"
import {shallowMount, flushPromises} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

const fileMetaMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    fileMetadatasFromExecution: (...args: unknown[]) => fileMetaMock(...args),
}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "http://localhost:8080/api/v1/main",
}))

vi.mock("../../../../src/composables/useEditorBindings", () => ({
    useEditorBindings: () => ({}),
}))

import VarValue from "../../../../src/components/executions/VarValue.vue"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {en: {download: "Download", jsonl: "JSONL", open: "Open", email: "Email"}},
    missingWarn: false,
    fallbackWarn: false,
})

describe("VarValue download link", () => {
    // Storage rewrites spaces in output file names to "+" and percent-encodes URI-special
    // characters, so an unencoded ?path= query double-decodes server-side and 422s.
    test("URL-encodes the storage path in the download link", async () => {
        fileMetaMock.mockResolvedValue({size: 6})

        const wrapper = shallowMount(VarValue, {
            props: {value: "kestra:///company/e1/abc-a%23b+c.txt", execution: {id: "exec-1"}},
            global: {
                plugins: [i18n],
                stubs: {
                    KsButtonGroup: {template: "<div><slot /></div>"},
                    KsButton: {inheritAttrs: false, template: "<a v-bind=\"$attrs\"><slot /></a>"},
                },
            },
        })
        await flushPromises()

        expect(wrapper.find("a[href]").attributes("href"))
            .toBe("http://localhost:8080/api/v1/main/executions/exec-1/file?path=kestra%3A%2F%2F%2Fcompany%2Fe1%2Fabc-a%2523b%2Bc.txt")
    })
})

describe("VarValue URI-like values", () => {
    const mountValue = (value: string) => shallowMount(VarValue, {
        props: {value},
        global: {
            plugins: [i18n],
            stubs: {KsButton: {template: "<a><slot /></a>"}},
        },
    })

    // new URL() accepts any `scheme:` prefix; such identifiers must stay readable text (Pylon 2622).
    test.each([
        "iso6523-actorid-upis::0088:5798000000001",
        "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
        "arn:aws:s3:::my-bucket",
        "mailto:someone@example.com",
    ])("shows %s as text without an Open link", (value) => {
        const wrapper = mountValue(value)

        expect(wrapper.text()).toContain(value)
        expect(wrapper.text()).not.toContain("Open")
    })

    // mailto: hands off to the mail client; target=_blank would leave an empty tab behind.
    test("shows a mailto value as text next to an Email link in the same tab", () => {
        const value = "mailto:ops@example.com?subject=Invoice%20rejected"
        const wrapper = shallowMount(VarValue, {
            props: {value},
            global: {
                plugins: [i18n],
                stubs: {KsButton: {inheritAttrs: false, template: "<a v-bind=\"$attrs\"><slot /></a>"}},
            },
        })

        expect(wrapper.text()).toContain(value)
        const link = wrapper.find("[data-test=var-value-mailto]")
        expect(link.attributes("href")).toBe(value)
        expect(link.attributes("target")).toBeUndefined()
    })

    test("shows a web link as text next to its Open button", () => {
        const wrapper = mountValue("https://kestra.io/docs")

        expect(wrapper.text()).toContain("https://kestra.io/docs")
        expect(wrapper.text()).toContain("Open")
    })
})
