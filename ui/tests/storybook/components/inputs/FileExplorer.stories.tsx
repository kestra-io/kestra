import {vi} from "vitest";

// namespacesStore.readDirectory() calls FilesAPI.listNamespaceDirectoryFiles() directly, which
// goes through the SDK's own internal client rather than the axios instance setMockClient()
// swaps - so it has to be intercepted at the submodule level.
vi.mock("@kestra-io/kestra-sdk/files", () => ({
    listNamespaceDirectoryFiles: async () => ([
        {fileName: "directory 1", type: "Directory"},
        {fileName: "directory 2", type: "Directory"},
        {fileName: "animals.txt", type: "File"},
    ]),
}))

import type {Meta, StoryObj} from "@storybook/vue3-vite";
import {provide} from "vue";
import {vueRouter} from "storybook-vue3-router";
import FileExplorer, {FILES_OPEN_TAB_INJECTION_KEY, FILES_CLOSE_TAB_INJECTION_KEY} from "../../../../src/components/inputs/FileExplorer.vue";
import {setMockClient} from "@kestra-io/kestra-sdk"

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
            const axios: any = {}

            provide(FILES_OPEN_TAB_INJECTION_KEY, () => {})
            provide(FILES_CLOSE_TAB_INJECTION_KEY, () => false)


            axios.get = () => {
                    return  Promise.resolve({data: [
                        {fileName: "directory 1", type: "Directory"},
                        {fileName: "directory 2", type: "Directory"},
                        {fileName: "animals.txt", type: "File"},
                    ]
                })}

            setMockClient(axios);


            return () => <div style="margin: 1rem;">
                <FileExplorer currentNS="example"/>
            </div>
        }
    })
};
