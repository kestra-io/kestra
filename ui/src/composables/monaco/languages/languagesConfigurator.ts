import {YamlLanguageConfigurator} from "./yamlLanguageConfigurator";
import {Store} from "vuex";
import {editor, IDisposable} from "monaco-editor/esm/vs/editor/editor.api";
import FilterLanguageConfigurator, {languages as filterLanguages} from "./filters/filterLanguageConfigurator";
import {FlowAutoCompletion} from "override/services/flowAutoCompletionProvider";
import {YamlAutoCompletion} from "../../../services/autoCompletionProvider";
import {useI18n} from "vue-i18n";
import {usePluginsStore} from "../../../stores/plugins";
import {ComputedRef} from "vue";

export default async function configure(
    store: Store<Record<string, any>>,
    pluginsStore: ReturnType<typeof usePluginsStore>,
    t: ReturnType<typeof useI18n>["t"],
    editorInstance: editor.ICodeEditor | undefined,
    language: string,
    domain?: string,
    flowYaml?: ComputedRef<string>
): Promise<() => void> {
    let disposables: IDisposable[] | undefined;
    if (language === "yaml") {
        const yamlAutoCompletion = domain === "flow" ? new FlowAutoCompletion(store, pluginsStore) : new YamlAutoCompletion();
        disposables = await new YamlLanguageConfigurator(yamlAutoCompletion, flowYaml).configure(store, t, editorInstance);
    } else if (filterLanguages.some(languageRegex => languageRegex.test(language))) {
        disposables = await new FilterLanguageConfigurator(language, domain).configure(store, t, editorInstance);
    }

    return () => disposables?.forEach(disposable => disposable.dispose());
}