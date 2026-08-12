import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect} from "storybook/test"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import Pause from "../../../../../../../src/components/executions/overview/components/actions/Pause.vue"
import en from "../../../../../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en}})
const pinia = createPinia()

const execution = {
    id: "exec-id",
    flowId: "flow-id",
    namespace: "io.kestra.tests",
    state: {current: "RUNNING"},
}

const meta: Meta<typeof Pause> = {
    title: "executions/actions/Pause",
    component: Pause,
    decorators: [
        (story) => ({
            components: {story},
            plugins: [i18n, KestraDesignSystem, pinia],
            template: "<div style=\"padding:24px\"><story /></div>",
        }),
    ],
    parameters: {
        docs: {
            description: {
                component:
                    "Pause.vue has multiple root nodes (the button + a KsDialog), so Vue does not auto-forward attributes like `type` to the button - it re-binds `$attrs` onto the rendered button itself. These stories guard that wiring.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof Pause>

/** ExecutionRootTopBar passes type="primary" when Pause is the execution's sole primary action. */
export const PrimaryAction: Story = {
    render: () => ({
        components: {Pause},
        setup() {
            return {execution}
        },
        template: "<Pause :execution=\"execution\" type=\"primary\" />",
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector("button.kel-button--primary")).toBeTruthy()
    },
}

/** Inside the "Actions" dropdown, no type is passed - the button keeps the default style. */
export const DropdownItem: Story = {
    render: () => ({
        components: {Pause},
        setup() {
            return {execution}
        },
        template: "<Pause :execution=\"execution\" />",
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector("button.kel-button")).toBeTruthy()
        await expect(canvasElement.querySelector("button.kel-button--primary")).toBeFalsy()
    },
}
