import EditorSidebar from "../../../../src/components/inputs/EditorSidebar.vue";
import {useAxios} from "../../../../src/utils/axios";

const meta = {
    title: "inputs/EditorSidebar",
    component: EditorSidebar,
}

export default meta;

export const Default = {
    render: () => ({
        setup() {
            const axios = useAxios()

            axios.get = () => {
                    return  Promise.resolve({data: [
                        {fileName: "directory 1", type: "Directory"},
                        {fileName: "directory 2", type: "Directory"},
                        {fileName: "animals.txt", type: "File"},
                    ]
                })}


            return () => <div style="margin: 1rem;">
                <EditorSidebar currentNS="example"/>
            </div>
        }
    })
};