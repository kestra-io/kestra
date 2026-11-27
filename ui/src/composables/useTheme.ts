import {computed} from "vue"
import {usePreferredDark, useStorage} from "@vueuse/core"
import {themes, type ThemeKind} from "@kestra-io/plumage"
import {useMiscStore} from "override/stores/misc"
import {storageKeys} from "../utils/constants"
import {getTheme} from "../utils/utils"

/** The code theme picked in Settings for each app color mode; `null` follows the app theme. */
export const editorDarkTheme = useStorage<string | null>(storageKeys.EDITOR_DARK_THEME, null)
export const editorLightTheme = useStorage<string | null>(storageKeys.EDITOR_LIGHT_THEME, null)

const isCodeTheme = (name: string | null, kind: ThemeKind): name is string =>
    themes.some(theme => theme.name === name && theme.kind === kind)

export const useTheme = () => {
    const miscStore = useMiscStore()
    const prefersDark = usePreferredDark()
    return computed<"light" | "dark">(() => {
        void miscStore.theme
        void prefersDark.value
        return getTheme()
    })
}

/** The `@kestra-io/plumage` theme for KsEditor: the one picked in Settings for the current color mode, or Kestra Dark / Kestra Light. */
export const useCodeTheme = () => {
    const miscStore = useMiscStore()
    const prefersDark = usePreferredDark()
    return computed(() => {
        void miscStore.theme
        void prefersDark.value
        if (getTheme() === "light") {
            return isCodeTheme(editorLightTheme.value, "light") ? editorLightTheme.value : "kestra-light"
        }
        return isCodeTheme(editorDarkTheme.value, "dark") ? editorDarkTheme.value : "kestra-dark"
    })
}
