/**
 * Registers a global Ctrl+Shift+L keyboard shortcut that cycles through
 * the three available themes: light → dark → dark-2 → light.
 *
 * Call once from the root App component, passing the misc store instance.
 */
import {onMounted, onUnmounted} from "vue"
import {switchTheme} from "../utils/utils"
import type {SelectedTheme} from "../utils/utils"

const THEMES: SelectedTheme[] = ["dark-2", "dark", "light"]

export function useThemeCycle(miscStore: {theme: SelectedTheme}) {
    function cycleTheme(e: KeyboardEvent) {
        if (e.ctrlKey && e.shiftKey && e.key.toLowerCase() === "l") {
            const current = miscStore.theme ?? "light"
            const next = THEMES[(THEMES.indexOf(current) + 1) % THEMES.length]
            switchTheme(miscStore, next)
        }
    }

    onMounted(() => window.addEventListener("keydown", cycleTheme))
    onUnmounted(() => window.removeEventListener("keydown", cycleTheme))
}
