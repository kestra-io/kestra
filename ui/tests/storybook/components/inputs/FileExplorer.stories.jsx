import {provide} from "vue";
import {vueRouter} from "storybook-vue3-router";
import FileExplorer, {FILES_OPEN_TAB_INJECTION_KEY, FILES_CLOSE_TAB_INJECTION_KEY} from "../../../../src/components/inputs/FileExplorer.vue";
import {useAxios} from "../../../../src/utils/axios";

const meta = {
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

export const Default = {
    render: () => ({
        setup() {
            const axios = useAxios()

            provide(FILES_OPEN_TAB_INJECTION_KEY, () => {})
            provide(FILES_CLOSE_TAB_INJECTION_KEY, () => {})


            axios.get = () => {
                    return  Promise.resolve({data: [
                        {fileName: "directory 1", type: "Directory"},
                        {fileName: "directory 2", type: "Directory"},
                        {fileName: "animals.txt", type: "File"},
                    ]
                })}


            return () => <div style="margin: 1rem;">
                <FileExplorer currentNS="example"/>
            </div>
        }
    })
};