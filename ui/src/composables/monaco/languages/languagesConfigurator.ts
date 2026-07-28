import {computed} from "vue"
import {useI18n} from "vue-i18n"
import type {Router} from "vue-router"
import {editor} from "monaco-editor/esm/vs/editor/editor.api"
import {YamlLanguageConfigurator} from "override/composables/monaco/languages/yamlLanguageConfigurator"
import {PebbleLanguageConfigurator} from "./pebbleLanguageConfigurator"
import {FlowAutoCompletion} from "override/services/flowAutoCompletionProvider"
import {YamlAutoCompletion} from "../../../services/autoCompletionProvider"
import {usePluginsStore} from "../../../stores/plugins"
import {useFlowStore} from "../../../stores/flow"
import {useMcpStore} from "../../../stores/mcp"
import {useDashboardStore} from "../../../stores/dashboard"
import {useNamespacesStore} from "override/stores/namespaces"

export default async function configure(
    flowStore: ReturnType<typeof useFlowStore>,
    pluginsStore: ReturnType<typeof usePluginsStore>,
    t: ReturnType<typeof useI18n>["t"],
    editorInstance: editor.ICodeEditor | undefined,
    language: string,
    domain?: string,
    router?: Router,
): Promise<void> {
    const namespacesStore = useNamespacesStore()
    const mcpStore = useMcpStore()
    const dashboardStore = useDashboardStore()
    let yamlAutocompletion
    if (language === "yaml") {
        if (domain === "flow" || domain === "testsuites" || domain === "reusableinputs") {
            // flow completion works for testsuites and the reusable-inputs block editor (so `{{ inputs.<sibling> }}`
            // suggests the block's own inputs); the reusable-inputs-only providers gate on the model URI anyway.
            yamlAutocompletion = new FlowAutoCompletion(flowStore, pluginsStore, namespacesStore, mcpStore, dashboardStore)
        } else {
            yamlAutocompletion = new YamlAutoCompletion()
        }
        await new YamlLanguageConfigurator(yamlAutocompletion, router, flowStore).configure(pluginsStore, t, editorInstance)
    } else if(language.endsWith("-pebble")) {
        const autoCompletion = new FlowAutoCompletion(flowStore, pluginsStore, namespacesStore, mcpStore, dashboardStore, computed(() => flowStore.flowYaml))
        await new PebbleLanguageConfigurator(language, autoCompletion, computed(() => flowStore.flowYaml))
            .configure(pluginsStore, t, editorInstance)
    }
}
