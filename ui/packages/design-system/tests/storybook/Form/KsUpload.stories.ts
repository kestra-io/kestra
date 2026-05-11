import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import {within, expect} from "storybook/test"
import KsUpload from "../../../src/components/Form/KsUpload.vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"

const meta: Meta<typeof KsUpload> = {
    title: "Components/Form/KsUpload",
    component: KsUpload,
    tags: ["autodocs"],
    argTypes: {
        drag: {control: "boolean"},
        multiple: {control: "boolean"},
        showFileList: {control: "boolean"},
        autoUpload: {control: "boolean"},
        accept: {control: "text"},
        limit: {control: "number"},
    },
    parameters: {
        docs: {
            description: {
                component:
                    "KsUpload is the Kestra design-system abstraction over `ElUpload` from Element Plus. " +
                    "Only the props, events and slots actually used across the Kestra UI are exposed.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsUpload>

/** Default upload button */
export const Default: Story = {
    render: () => ({
        components: {KsUpload, KsButton},
        template: `
            <div style="padding:24px">
                <ks-upload action="#">
                    <ks-button type="primary">Click to upload</ks-button>
                </ks-upload>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        const btn = canvas.getAllByRole("button")[0]
        await expect(btn).toBeTruthy()
    },
}

/** Drag and drop upload area */
export const DragAndDrop: Story = {
    render: () => ({
        components: {KsUpload},
        template: `
            <div style="padding:24px;width:360px">
                <ks-upload action="#" drag multiple accept=".jpg,.png,.pdf">
                    <div style="padding:32px;text-align:center">
                        <p style="font-size:14px;font-weight:500">Drop files here or click to upload</p>
                        <p style="font-size:12px;opacity:0.5;margin-top:4px">JPG, PNG, PDF accepted</p>
                    </div>
                    <template #tip>
                        <div style="font-size:12px;opacity:0.5;margin-top:8px">Max file size: 10 MB</div>
                    </template>
                </ks-upload>
            </div>
        `,
    }),
    async play({canvasElement}) {
        await expect(canvasElement.querySelector(".kel-upload-dragger")).toBeTruthy()
    },
}

/** With file list and limit */
export const WithFileList: Story = {
    render: () => ({
        components: {KsUpload, KsButton},
        setup() {
            const fileList = ref([
                {name: "report.pdf", url: ""},
                {name: "photo.png", url: ""},
            ])
            return {fileList}
        },
        template: `
            <div style="padding:24px;width:400px">
                <ks-upload action="#" :file-list="fileList" :limit="3" multiple>
                    <ks-button type="primary">Select Files</ks-button>
                    <template #tip>
                        <span style="font-size:12px;opacity:0.5">Up to 3 files</span>
                    </template>
                </ks-upload>
            </div>
        `,
    }),
}

/** Without file list display */
export const NoFileList: Story = {
    render: () => ({
        components: {KsUpload, KsButton},
        template: `
            <div style="padding:24px">
                <ks-upload action="#" :show-file-list="false">
                    <ks-button>Upload silently</ks-button>
                </ks-upload>
            </div>
        `,
    }),
}

/** Accept specific file types */
export const AcceptTypes: Story = {
    render: () => ({
        components: {KsUpload, KsButton},
        template: `
            <div style="padding:24px">
                <ks-upload action="#" accept=".csv,.json,.yaml">
                    <ks-button type="primary">Upload config file</ks-button>
                    <template #tip>
                        <span style="font-size:12px;opacity:0.5">Accepted: .csv, .json, .yaml</span>
                    </template>
                </ks-upload>
            </div>
        `,
    }),
}
