import {getCurrentInstance} from "vue"

export default function useDataComponent() {
    const instance = getCurrentInstance();
    let file = instance?.type?.__file?.substring(import.meta.env.__ROOT_DIR__.length + 1);
    file = file.substring(4, file.length - 4);

    return file;
}