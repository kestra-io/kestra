import {describe, it, expect, vi, afterEach} from "vitest"
import type {VueWrapper} from "@vue/test-utils"
import en from "../../translations/en.json"

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "secrets", path: "/secrets", query: {}}),
    useRouter: () => ({push: vi.fn()}),
    RouterLink: {
        props: ["to"],
        template: "<a><slot /></a>",
    },
}))

vi.mock("@kestra-io/kestra-sdk/secrets", () => ({
    listSecrets: vi.fn().mockResolvedValue({
        results: [{key: "MY_SECRET", namespace: "my.namespace"}],
        total: 1,
        readOnly: true,
    }),
    createSecret: vi.fn(),
    updateSecret: vi.fn(),
    deleteSecret: vi.fn(),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

vi.mock("override/stores/namespaces", () => ({
    useNamespacesStore: () => ({loadNamespaces: vi.fn()}),
}))

vi.mock("../../stores/api", () => ({
    useApiStore: () => ({posthogEvents: vi.fn()}),
}))

import {defineComponent, type PropType} from "vue"
import SecretsTable from "./SecretsTable.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import {flushPromises} from "@vue/test-utils"

let wrapper: VueWrapper | undefined

const stubs = {
    KsDataTable: defineComponent({
        props: {
            loadData: {
                type: Function as PropType<(arg: {page: number; size: number}) => void>,
                default: undefined,
            },
        },
        mounted() {
            this.loadData?.({page: 1, size: 25})
        },
        template: `
            <div class="ks-data-table-stub">
                <slot name="top" />
                <slot />
            </div>
        `,
    }),
    KsTableColumn: {
        props: ["prop", "columnKey"],
        template: `
            <div class="ks-table-column-stub">
                <slot :row="{key: 'MY_SECRET', namespace: 'my.namespace'}" />
            </div>
        `,
    },
    KsTooltip: {template: "<div><slot /><slot name=\"content\" /></div>"},
    KsIcon: {template: "<span class=\"ks-icon\"><slot /></span>"},
    Lock: {template: "<svg class=\"lock-icon-stub\" />"},
    Labels: {template: "<div />"},
    NamespaceSelect: {template: "<div />"},
    KsDialog: {template: "<div><slot /><slot name=\"footer\" /></div>"},
    KsForm: {template: "<form><slot /></form>"},
    KsFormItem: {template: "<div><slot /><slot name=\"label\" /></div>"},
    KsFilter: {props: ["prefix"], template: "<div />"},
    KsEntityLink: {props: ["value", "to"], template: "<a><slot /></a>"},
    KsId: {template: "<span><slot /></span>"},
    KsIconButton: {template: "<button><slot /></button>"},
    KsButton: {template: "<button><slot /></button>"},
    KsInput: {template: "<input />"},
    ContentCopy: {template: "<svg />"},
    RouterLink: {props: ["to"], template: "<a><slot /></a>"},
}

const mountSecretsTable = (props = {}) => {
    wrapper = i18nMount(SecretsTable, {
        locales: en,
        props: {
            filterable: false,
            ...props,
        },
        global: {stubs},
    })
    return wrapper
}

describe("SecretsTable.vue", () => {
    afterEach(() => {
        wrapper?.unmount()
    })

    it("renders design system classes and no Bootstrap classes", async () => {
        const w = mountSecretsTable()
        await flushPromises()

        const lockedIcon = w.find(".locked-icon")
        expect(lockedIcon.exists()).toBe(true)
        expect(lockedIcon.classes()).not.toContain("d-flex")
        expect(lockedIcon.classes()).not.toContain("justify-content-center")
    })
})
