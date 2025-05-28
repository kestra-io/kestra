import {YamlLanguageConfigurator} from "./yamlLanguageConfigurator";
import {Store} from "vuex";
import {editor, IDisposable} from "monaco-editor/esm/vs/editor/editor.api";
import FilterLanguageConfigurator, {languages as filterLanguages} from "./filters/filterLanguageConfigurator";
import {FlowAutoCompletion} from "override/services/flowAutoCompletionProvider";
import {YamlAutoCompletion} from "../../../services/autoCompletionProvider";
import {useI18n} from "vue-i18n";

export default async function configure(store: Store<Record<string, any>>, t: ReturnType<typeof useI18n>["t"], editorInstance: editor.ICodeEditor | undefined, language: string, domain: string | undefined): Promise<() => void> {
    let disposables: IDisposable[] | undefined;
    if (language === "yaml") {
        const yamlAutoCompletion = domain === "flow" ? new FlowAutoCompletion(store) : new YamlAutoCompletion();
        disposables = await new YamlLanguageConfigurator(yamlAutoCompletion).configure(store, t, editorInstance);
    } else if (filterLanguages.some(languageRegex => languageRegex.test(language))) {
        disposables = await new FilterLanguageConfigurator(language, domain).configure(store, t, editorInstance);
    }

    return () => disposables?.forEach(disposable => disposable.dispose());
}