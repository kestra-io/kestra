import {describe, it, expect, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import en from "../../../src/translations/en.json"

const {KsNotification} = vi.hoisted(() => ({
    KsNotification: vi.fn((_options: any) => ({close: vi.fn()})),
}))
vi.mock("@kestra-io/design-system", () => ({KsNotification}))
vi.mock("vue-router", () => ({useRoute: () => ({name: "flows/list", params: {}, query: {}})}))
vi.mock("../../../src/stores/api", () => ({useApiStore: () => ({events: vi.fn()})}))

import ErrorToast from "../../../src/components/ErrorToast.vue"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: en})

describe("ErrorToast", () => {
    it("names the failing request for a 404, since its only message is the bare status", () => {
        KsNotification.mockClear()

        mount(ErrorToast, {
            props: {
                message: {
                    variant: "error",
                    response: {
                        status: 404,
                        config: {method: "get", url: "/api/v1/main/flows/io.kestra/missing"},
                    },
                    content: {message: "404 Not Found"},
                },
            },
            global: {plugins: [i18n]},
        })

        const [notification] = KsNotification.mock.calls[0]
        expect(notification.title).toBe("Not found")
        expect(notification.message.props.message.content.message).toBe(
            "The request GET /api/v1/main/flows/io.kestra/missing returned 404 Not Found.",
        )
    })
})
