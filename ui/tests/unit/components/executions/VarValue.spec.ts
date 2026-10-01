import {describe, test, expect, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nShallowMount} from "../../i18nMount"

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

describe("VarValue download link", () => {
    // Storage rewrites spaces in output file names to "+" and percent-encodes URI-special
    // characters, so an unencoded ?path= query double-decodes server-side and 422s.
    test("URL-encodes the storage path in the download link", async () => {
        fileMetaMock.mockResolvedValue({size: 6})

        const wrapper = i18nShallowMount(VarValue, {
            messages: {download: "Download", jsonl: "JSONL", open: "Open"},
            props: {value: "kestra:///company/e1/abc-a%23b+c.txt", execution: {id: "exec-1"}},
            global: {
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
