import {computed, reactive} from "vue"
import {useI18n} from "vue-i18n"
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

    return reactive({
        theme: computed(() => {
            void miscStore.theme
            return getTheme()
        }),
        pluginIcons: computed((): Record<string, {icon: string; flowable: boolean}> => pluginsStore.icons),
        configureLanguage: (editor: monacoEditor.ICodeEditor | undefined, language: string, schemaType?: string) =>
            configureLanguageFn(flowStore, pluginsStore, t, editor, language, schemaType),
    })
}
