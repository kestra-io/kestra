import type {App} from "vue"
import ElementPlus from "element-plus"

import KsSelect from "./components/KsSelect/KsSelect.vue"
import KsOption from "./components/KsSelect/KsOption.vue"
import KsButton from "./components/KsButton/KsButton.vue"
import KsButtonGroup from "./components/KsButton/KsButtonGroup.vue"

// ─── Named exports (tree-shakeable) ──────────────────────────────────────────
export {KsSelect, KsOption, KsButton, KsButtonGroup}

// ─── Vue plugin (auto-registers all components) ──────────────────────────────
const KestraDesignSystem = {
    install(app: App) {
        app.use(ElementPlus)

        app.component("KsSelect", KsSelect)
        app.component("KsOption", KsOption)
        app.component("KsButton", KsButton)
        app.component("KsButtonGroup", KsButtonGroup)
    },
}

export default KestraDesignSystem

// ─── Global component type augmentation (Volar / IntelliJ IDE support) ───────
declare module "vue" {
    export interface GlobalComponents {
        KsSelect: typeof KsSelect
        KsOption: typeof KsOption
        KsButton: typeof KsButton
        KsButtonGroup: typeof KsButtonGroup
    }
}
