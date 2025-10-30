import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import {IDisposable} from "monaco-editor/esm/vs/editor/editor.api";
import {useI18n} from "vue-i18n";
import {usePluginsStore} from "../../../stores/plugins";

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

    abstract configureAutoCompletion(t: ReturnType<typeof useI18n>["t"], editorInstance: monaco.editor.ICodeEditor | undefined): IDisposable[];

    async configure(pluginsStore: ReturnType<typeof usePluginsStore>, t: ReturnType<typeof useI18n>["t"], editorInstance: monaco.editor.ICodeEditor | undefined): Promise<IDisposable[]> {
        if (!AbstractLanguageConfigurator.configuredLanguages.includes(this.language)) {
            AbstractLanguageConfigurator.configuredLanguages.push(this.language);
            await this.configureLanguage(pluginsStore);
            return this.configureAutoCompletion(t, editorInstance);
        }

        return []
    }
}
