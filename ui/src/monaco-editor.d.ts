declare module "*.vue" {
    import {DefineComponent} from "vue";
    const component: DefineComponent<object, object, any>;
    export default component;
}
declare module "monaco-editor/esm/vs/editor/common/services/languageFeatures" {
    export {ILanguageFeaturesService} from "monaco-editor/esm/vs/editor/common/services/languageFeatures";
}

declare module "monaco-editor/esm/vs/editor/standalone/browser/standaloneServices" {
    export {StandaloneServices} from "monaco-editor/esm/vs/editor/standalone/browser/standaloneServices"
}