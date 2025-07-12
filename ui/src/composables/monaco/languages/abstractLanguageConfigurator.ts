import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import {IDisposable} from "monaco-editor/esm/vs/editor/editor.api";
import {Store} from "vuex";
import {useI18n} from "vue-i18n";
import {usePluginsStore} from "../../../stores/plugins.ts";

export default abstract class AbstractLanguageConfigurator {
    private readonly _language: string;
    protected static configuredLanguages: string[] = [];

    protected constructor(language: string) {
        this._language = language;
    }

    get language(): string {
        return this._language;
    }

    async configureLanguage(_: ReturnType<typeof usePluginsStore>) {
        monaco.languages.register({id: this.language});
    }

    abstract configureAutoCompletion(t: ReturnType<typeof useI18n>["t"], store: Store<Record<string, any>>, editorInstance: monaco.editor.ICodeEditor | undefined): IDisposable[];

    async configure(store: Store<Record<string, any>>, pluginsStore: ReturnType<typeof usePluginsStore>, t: ReturnType<typeof useI18n>["t"], editorInstance: monaco.editor.ICodeEditor | undefined): Promise<IDisposable[]> {
        if (!AbstractLanguageConfigurator.configuredLanguages.includes(this.language)) {
            AbstractLanguageConfigurator.configuredLanguages.push(this.language);
            await this.configureLanguage(pluginsStore);
            return this.configureAutoCompletion(t, store, editorInstance);
        }

        return []
    }
}
