import type {Meta, StoryObj} from "@storybook/vue3-vite";
import {provide} from "vue";
import {vueRouter} from "storybook-vue3-router";
import FileExplorer, {FILES_OPEN_TAB_INJECTION_KEY, FILES_CLOSE_TAB_INJECTION_KEY} from "./FileExplorer.vue";
import {setMockClient, type AxiosLikeClient} from "@kestra-io/kestra-sdk"
import {mockResponse, mockStoryApiRoutes} from "../../../.storybook/apiMock"

const FILES = [
    {fileName: "directory 1", type: "Directory"},
    {fileName: "directory 2", type: "Directory"},
    {fileName: "animals.txt", type: "File"},
]

const meta: Meta<typeof FileExplorer> = {
    title: "inputs/FileExplorer",
    component: FileExplorer,
    decorators: [
        vueRouter([
            {
                path: "/",
                component: {template: "<div></div>"}
            },
        ])
    ]
}

export default meta;

export const Default: StoryObj<typeof FileExplorer> = {
    render: () => ({
        setup() {
            const axios: Partial<AxiosLikeClient> = {}

            provide(FILES_OPEN_TAB_INJECTION_KEY, () => {})
            provide(FILES_CLOSE_TAB_INJECTION_KEY, () => false)


            axios.get = async <T,>() => mockResponse<T>(FILES)

            setMockClient(axios);
            mockStoryApiRoutes({"GET /namespaces/:namespace/files/directory": FILES});


            return () => <div style="margin: 1rem;">
                <FileExplorer currentNS="example"/>
            </div>
        }
    })
};
