import {computed, reactive} from "vue"
import {useI18n} from "vue-i18n"
import {useRouter} from "vue-router"
import {useMiscStore} from "override/stores/misc"
import {getTheme} from "../utils/utils"
import {usePluginsStore} from "../stores/plugins"
import {useFlowStore} from "../stores/flow"
import configureLanguageFn from "./monaco/languages/languagesConfigurator"
import type {editor as monacoEditor} from "monaco-editor/esm/vs/editor/editor.api"

export function useEditorBindings() {
    const miscStore = useMiscStore()
    const pluginsStore = usePluginsStore()
    const flowStore = useFlowStore()
    const {t} = useI18n()
    const router = useRouter()

    // Code editors resolve arbitrary task types as the user types (autocomplete, hover docs), so
    // they need the full plugin-icons catalog rather than a handful of known classes. Triggered
    // here instead of eagerly at app boot so it only downloads for sessions that open an editor.
    pluginsStore.fetchIcons()

    return reactive({
        theme: computed(() => {
            void miscStore.theme
            return getTheme()
        }),
        pluginIcons: computed((): Record<string, {icon: string; flowable: boolean}> => pluginsStore.icons),
        configureLanguage: (editor: monacoEditor.ICodeEditor | undefined, language: string, schemaType?: string) =>
            configureLanguageFn(flowStore, pluginsStore, t, editor, language, schemaType, router),
    })
}
