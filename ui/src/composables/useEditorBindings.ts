import {reactive} from "vue"
import {useI18n} from "vue-i18n"
import {useRouter} from "vue-router"
import {usePluginsStore} from "../stores/plugins"
import {useFlowStore} from "../stores/flow"
import {useCodeTheme} from "./useTheme"
import type {editor as monacoEditor} from "monaco-editor/editor/editor.api"

export function useEditorBindings() {
    const pluginsStore = usePluginsStore()
    const flowStore = useFlowStore()
    const {t} = useI18n()
    const router = useRouter()

    return reactive({
        theme: useCodeTheme(),
        loadTaskIcon: pluginsStore.loadIcon,
        // Imported here rather than at module scope: forty-odd components use
        // these bindings, and statically this put Monaco on all of their graphs.
        configureLanguage: async (editor: monacoEditor.ICodeEditor | undefined, language: string, schemaType?: string) => {
            const {default: configureLanguageFn} = await import("./monaco/languages/languagesConfigurator")
            return configureLanguageFn(flowStore, pluginsStore, t, editor, language, schemaType, router)
        },
    })
}
