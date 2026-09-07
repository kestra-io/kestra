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
    messages: {en: {download: "Download", jsonl: "JSONL", open: "Open"}},
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
