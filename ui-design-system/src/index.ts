import type {App} from "vue"
import {KsSelect} from "./components/KsSelect"
import {KsOption} from "./components/KsOption"
import {KsButton} from "./components/KsButton"

// ─── Named exports (tree-shakeable) ──────────────────────────────────────────
export {KsSelect, KsOption, KsButton}

// ─── Vue plugin (auto-registers all components) ──────────────────────────────
const KestraDesignSystem = {
    install(app: App) {
        app.component("KsSelect", KsSelect)
        app.component("KsOption", KsOption)
        app.component("KsButton", KsButton)
    },
}

export default KestraDesignSystem

// ─── Global component type augmentation (Volar / IntelliJ IDE support) ───────
declare module "vue" {
    export interface GlobalComponents {
        KsSelect: typeof KsSelect
        KsOption: typeof KsOption
        KsButton: typeof KsButton
    }
}
