import {computed} from "vue";
import {useI18n} from "vue-i18n";
import {editor} from "monaco-editor/esm/vs/editor/editor.api";
import {YamlLanguageConfigurator} from "./yamlLanguageConfigurator";
import {PebbleLanguageConfigurator} from "./pebbleLanguageConfigurator";
import {FlowAutoCompletion} from "override/services/flowAutoCompletionProvider";
import {YamlAutoCompletion} from "../../../services/autoCompletionProvider";
import {usePluginsStore} from "../../../stores/plugins";
import {useFlowStore} from "../../../stores/flow";
import {useNamespacesStore} from "override/stores/namespaces";

export default async function configure(
    flowStore: ReturnType<typeof useFlowStore>,
    pluginsStore: ReturnType<typeof usePluginsStore>,
    t: ReturnType<typeof useI18n>["t"],
    editorInstance: editor.ICodeEditor | undefined,
    language: string,
    domain?: string
): Promise<void> {
    const namespacesStore = useNamespacesStore();
    let yamlAutocompletion;
    if (language === "yaml") {
        if (domain === "flow" || domain === "testsuites") {
            // flow completion seems to work fine for testsuites, quickwin
            yamlAutocompletion = new FlowAutoCompletion(flowStore, pluginsStore, namespacesStore);
        } else {
            yamlAutocompletion = new YamlAutoCompletion();
        }
        await new YamlLanguageConfigurator(yamlAutocompletion).configure(pluginsStore, t, editorInstance);
    } else if(language.endsWith("-pebble")) {
        const autoCompletion = new FlowAutoCompletion(flowStore, pluginsStore, namespacesStore, computed(() => flowStore.flowYaml));
        await new PebbleLanguageConfigurator(language, autoCompletion, computed(() => flowStore.flowYaml))
            .configure(pluginsStore, t, editorInstance);
    }
}
