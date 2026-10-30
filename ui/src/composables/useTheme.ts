import {computed} from "vue"
import {useMiscStore} from "override/stores/misc"
import {getTheme} from "../utils/utils"

export const useTheme = () => {
    const miscStore = useMiscStore()
    return computed<"light" | "dark">(() => {
        void miscStore.theme
        return getTheme()
    })
}
