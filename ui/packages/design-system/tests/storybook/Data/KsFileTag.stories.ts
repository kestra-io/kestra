import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, waitFor, within} from "storybook/test"
import KsFileTag from "../../../src/components/Data/KsFileTag.vue"

const meta: Meta<typeof KsFileTag> = {
    title: "Components/Data/KsFileTag",
    component: KsFileTag,
    tags: ["autodocs"],
    argTypes: {
        uri: {control: "text"},
        name: {control: "text"},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsFileTag renders a storage URI as a file reference: a symbol picked from the extension, plus a readable name. Storage URIs end with a generated segment, so pass `name` — typically the output key the file is stored under — and keep `uri` for the icon and the tooltip.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsFileTag>

const render = (args: Record<string, unknown>) => ({
    components: {KsFileTag},
    setup() { return {args} },
    template: "<div style=\"padding:24px\"><ks-file-tag v-bind=\"args\" /></div>",
})

export const NamedByKey: Story = {
    render,
    args: {uri: "kestra:///company/team/6Yd2A/outputs/8f2c1d.txt", name: "abc"},
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement)
        await waitFor(() => expect(canvas.getByText("abc")).toBeTruthy())
        expect(canvasElement.textContent).not.toContain("kestra:///")
    },
}

export const NameFromUri: Story = {
    render,
    args: {uri: "kestra:///company/team/6Yd2A/outputs/quarterly-report.csv"},
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        await waitFor(() => expect(within(canvasElement).getByText("quarterly-report.csv")).toBeTruthy())
    },
}

export const ExtensionSymbols: Story = {
    render: () => ({
        components: {KsFileTag},
        setup() {
            return {
                uris: [
                    "kestra:///out/report.csv",
                    "kestra:///out/data.ion",
                    "kestra:///out/payload.json",
                    "kestra:///out/values.yaml",
                    "kestra:///out/notes.md",
                    "kestra:///out/invoice.pdf",
                    "kestra:///out/chart.png",
                    "kestra:///out/clip.mp4",
                    "kestra:///out/theme.mp3",
                    "kestra:///out/bundle.zip",
                    "kestra:///out/main.py",
                    "kestra:///out/sheet.xlsx",
                    "kestra:///out/run.log",
                    "kestra:///out/abc",
                ],
            }
        },
        template: `
            <div style="display:flex;flex-wrap:wrap;gap:8px;padding:24px">
                <ks-file-tag v-for="uri in uris" :key="uri" :uri="uri" />
            </div>`,
    }),
}

export const LongName: Story = {
    render: (args) => ({
        components: {KsFileTag},
        setup() { return {args} },
        template: "<div style=\"width:180px;padding:24px\"><ks-file-tag v-bind=\"args\" /></div>",
    }),
    args: {
        uri: "kestra:///company/team/6Yd2A/outputs/8f2c1d.parquet",
        name: "extremely-long-output-file-name-that-has-to-be-clipped",
    },
}
