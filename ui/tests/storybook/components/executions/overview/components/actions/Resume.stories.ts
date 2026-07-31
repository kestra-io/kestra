import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect} from "storybook/test"
import {createI18n} from "vue-i18n"
import {createPinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import Resume from "../../../../../../../src/components/executions/overview/components/actions/Resume.vue"
import {useExecutionsStore} from "../../../../../../../src/stores/executions"
import en from "../../../../../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en}})
const pinia = createPinia()

const execution = {
    id: "exec-id",
    flowId: "flow-id",
    namespace: "io.kestra.tests",
    state: {current: "PAUSED"},
}

const meta: Meta<typeof Resume> = {
    title: "executions/actions/Resume",
    component: Resume,
    decorators: [
        (story) => ({
            setup() {
                const executionsStore = useExecutionsStore()
                ;(executionsStore as any).loadFlowForExecution = () => Promise.resolve(undefined)
                ;(executionsStore as any).resume = () => Promise.resolve({})
            },
            components: {story},
            plugins: [i18n, KestraDesignSystem, pinia],
            template: "<div style=\"padding:24px\"><story /></div>",
        }),
    ],
    parameters: {
        docs: {
            description: {
                component:
                    "Resume.vue has multiple root nodes (the button + a KsDialog), so Vue does not auto-forward attributes like `type` to the button - it re-binds `$attrs` onto the rendered button itself. These stories guard that wiring.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof Resume>

/** ExecutionRootTopBar passes type="primary" when Resume is the execution's sole primary action. */
export const PrimaryAction: Story = {
    render: () => ({
        components: {Resume},
        setup() {
            return {execution}
        },
        template: "<Resume :execution=\"execution\" type=\"primary\" />",
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector("button.kel-button--primary")).toBeTruthy()
    },
}

/** Inside the "Actions" dropdown, no type is passed - the button keeps the default style. */
export const DropdownItem: Story = {
    render: () => ({
        components: {Resume},
        setup() {
            return {execution}
        },
        template: "<Resume :execution=\"execution\" />",
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector("button.kel-button")).toBeTruthy()
        await expect(canvasElement.querySelector("button.kel-button--primary")).toBeFalsy()
    },
}
