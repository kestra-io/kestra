import type {Meta, StoryObj} from "@storybook/vue3"
import {expect, userEvent, waitFor, within} from "storybook/test"

import {mockStoryApiRoutes} from "../../../../.storybook/apiMock"
import NamespaceSelect from "./NamespaceSelect.vue"

const NAMESPACES = Array.from({length: 300}, (_, index) => `company.ns${String(index + 1).padStart(3, "0")}`)

const meta: Meta<typeof NamespaceSelect> = {
    title: "Components/Namespaces/NamespaceSelect",
    component: NamespaceSelect,
    beforeEach() {
        mockStoryApiRoutes({
            "POST /namespaces/autocomplete": ({body}: {body?: unknown}) => {
                const {q} = JSON.parse(String(body)) as {q?: string}
                return NAMESPACES.filter(namespace => !q || namespace.includes(q)).slice(0, 50)
            },
        })
    },
}

export default meta
type Story = StoryObj<typeof meta>

export const FindsNamespaceBeyondFirstPage: Story = {
    args: {
        autoDefault: false,
    },
    async play({canvasElement}) {
        const combobox = await within(canvasElement).findByRole("combobox")

        await userEvent.click(combobox)
        await userEvent.type(combobox, "ns300")

        const body = within(canvasElement.ownerDocument.body)
        await waitFor(() => expect(body.getByRole("option", {name: "company.ns300"})).toBeVisible(), {timeout: 3000})
    },
}
