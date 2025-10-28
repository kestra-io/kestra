import FileExplorer from "../../../../src/components/inputs/FileExplorer.vue";
import {useAxios} from "../../../../src/utils/axios";

const meta = {
    title: "inputs/FileExplorer",
    component: FileExplorer,
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
                <FileExplorer currentNS="example"/>
            </div>
        }
    })
};