import type {App, Component} from "vue"
import * as designSystem from "./index"

/**
 * Registers every `Ks*` component on the app.
 *
 * Only for environments that compile templates at runtime — Storybook stories written as
 * `template: "<ks-select …>"` strings, where no build step ever sees the tag and the
 * auto-import resolver therefore cannot resolve it.
 *
 * Application code must not call this. It is a static reference to the entire library, and
 * removing exactly that reference is what lets a page ship only the components it renders.
 */
export function registerComponents(app: App) {
    for (const [name, exported] of Object.entries(designSystem)) {
        // Components are objects; the `Ks*` exports that are not (KsMessage, KsMessageBox,
        // KsNotification) are imperative services with nothing to register.
        if (name.startsWith("Ks") && typeof exported === "object" && exported !== null) {
            app.component(name, exported as Component)
        }
    }
}
