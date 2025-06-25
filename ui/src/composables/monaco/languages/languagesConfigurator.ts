import {computed} from "vue";
import {Store} from "vuex";
import {useI18n} from "vue-i18n";
import {editor} from "monaco-editor/esm/vs/editor/editor.api";
import {YamlLanguageConfigurator} from "./yamlLanguageConfigurator";
import {PebbleLanguageConfigurator} from "./pebbleLanguageConfigurator";
import FilterLanguageConfigurator, {languages as filterLanguages} from "./filters/filterLanguageConfigurator";
import {FlowAutoCompletion} from "override/services/flowAutoCompletionProvider";
import {YamlAutoCompletion} from "../../../services/autoCompletionProvider";
import {usePluginsStore} from "../../../stores/plugins";

export default async function configure(
    store: Store<Record<string, any>>,
    pluginsStore: ReturnType<typeof usePluginsStore>,
    t: ReturnType<typeof useI18n>["t"],
    editorInstance: editor.ICodeEditor | undefined,
    language: string,
    domain?: string
): Promise<void> {
    if (language === "yaml") {
        const yamlAutoCompletion = domain === "flow" ? new FlowAutoCompletion(store, pluginsStore) : new YamlAutoCompletion();
        await new YamlLanguageConfigurator(yamlAutoCompletion).configure(store, t, editorInstance);
    } else if(language === "plaintext-pebble") {
        const autoCompletion = new FlowAutoCompletion(store, pluginsStore);
        await new PebbleLanguageConfigurator(autoCompletion, computed(() => store.getters["flow/flowYaml"]))
            .configure(store, t, editorInstance);
    } else if (filterLanguages.some(languageRegex => languageRegex.test(language))) {
        await new FilterLanguageConfigurator(language, domain)
            .configure(store, t, editorInstance);
    }
}