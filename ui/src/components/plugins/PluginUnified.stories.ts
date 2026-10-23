import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, waitFor, within} from "storybook/test"

import {mockStoryApiRoutes} from "../../../.storybook/apiMock"
import PluginUnified from "./PluginUnified.vue"

const DBT_GROUP = "io.kestra.plugin.dbt"

const meta: Meta<typeof PluginUnified> = {
    title: "Components/Plugins/PluginUnified",
    component: PluginUnified,
    beforeEach() {
        mockStoryApiRoutes({
            "GET /plugins/groups/subgroups": [
                {
                    name: "plugin-dbt",
                    title: "DBT",
                    description: "Integrate dbt data transformations into Kestra orchestration pipelines.",
                    longDescription: "# How to use the dbt plugin\n\n## Authentication\n\nUse `profiles`.",
                    group: DBT_GROUP,
                    tasks: [
                        {cls: "io.kestra.plugin.dbt.cli.DbtCLI"},
                        {cls: "io.kestra.plugin.dbt.cloud.CheckStatus"},
                    ],
                },
                {
                    name: "plugin-dbt",
                    title: "dbt CLI",
                    group: DBT_GROUP,
                    subGroup: "io.kestra.plugin.dbt.cli",
                    tasks: [{cls: "io.kestra.plugin.dbt.cli.DbtCLI"}],
                },
            ],
        })
    },
}

export default meta
type Story = StoryObj<typeof meta>

const fontSize = (element: Element) => parseFloat(getComputedStyle(element).fontSize)

function byDataTest(root: HTMLElement, name: string): HTMLElement {
    const element = root.querySelector<HTMLElement>(`[data-test='${name}']`)
    if (!element) throw new Error(`[data-test='${name}'] is not rendered`)
    return element
}

export const GroupWithLongDescription: Story = {
    args: {
        group: DBT_GROUP,
    },
    async play({canvasElement}) {
        const longDescription = await waitFor(() => byDataTest(canvasElement, "plugin-long-description"))
        const title = byDataTest(canvasElement, "plugin-title")
        const headings = within(longDescription).getAllByRole("heading")

        expect(headings.length).toBeGreaterThan(1)
        headings.forEach((heading) => expect(fontSize(heading)).toBeLessThanOrEqual(fontSize(title)))
    },
}
