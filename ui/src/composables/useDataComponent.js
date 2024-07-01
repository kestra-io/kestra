import {getCurrentInstance} from "vue"

export default function useDataComponent() {
    const instance = getCurrentInstance();
    return instance?.type?.__file?.substring(import.meta.env.__ROOT_DIR__.length - 1);
}