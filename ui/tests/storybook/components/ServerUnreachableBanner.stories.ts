import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, waitFor, within} from "storybook/test"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import ServerUnreachableBanner from "../../../src/components/ServerUnreachableBanner.vue"
import {markServerReachable, markServerUnreachable, UNREACHABLE_DELAY} from "../../../src/composables/useServerReachability"
import en from "../../../src/translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en}})

const meta: Meta<typeof ServerUnreachableBanner> = {
    title: "components/ServerUnreachableBanner",
    component: ServerUnreachableBanner,
    decorators: [
        (story) => ({
            components: {story},
            plugins: [i18n, KestraDesignSystem],
            template: "<story />",
        }),
    ],
}
export default meta
type Story = StoryObj<typeof ServerUnreachableBanner>

export const Unreachable: Story = {
    async play({canvasElement}) {
        markServerUnreachable()
        await expect(await within(canvasElement).findByText("Cannot reach the Kestra server", {}, {timeout: UNREACHABLE_DELAY + 1_000})).toBeVisible()
    },
}

export const IgnoresALoneFailure: Story = {
    async play({canvasElement}) {
        markServerUnreachable()
        markServerReachable()

        await new Promise((resolve) => setTimeout(resolve, UNREACHABLE_DELAY + 200))

        await expect(within(canvasElement).queryByText("Cannot reach the Kestra server")).toBeNull()
    },
}

export const ClearsWhenTheServerResponds: Story = {
    async play({canvasElement}) {
        markServerUnreachable()
        const canvas = within(canvasElement)
        await canvas.findByText("Cannot reach the Kestra server", {}, {timeout: UNREACHABLE_DELAY + 1_000})

        markServerReachable()

        await waitFor(() => expect(canvas.queryByText("Cannot reach the Kestra server")).toBeNull())
    },
}
