import {computed} from "vue"
import {usePreferredDark} from "@vueuse/core"
import {useMiscStore} from "override/stores/misc"
import {getTheme} from "../utils/utils"

export const useTheme = () => {
    const miscStore = useMiscStore()
    const prefersDark = usePreferredDark()
    return computed<"light" | "dark">(() => {
        void miscStore.theme
        void prefersDark.value
        return getTheme()
    })
}
