import {computed} from "vue"
import {useStorage} from "@vueuse/core"
import {APP_FONT_SIZE_KEY, BASE_PX, type AppFontSizeMode} from "../utils/appFontSize"

export type LogDensity = "compact" | "normal" | "expanded";

export const DENSITY_PADDING = {
    compact: "2px",
    normal: "5px",
    expanded: "12px",
} as const

const MIGRATION_FLAG = "_fontMigratedV2"
const LEGACY_LOGS_DEFAULT = 14
const LEGACY_EDITOR_DEFAULT = 12

function runMigrationOnce() {
    if (localStorage.getItem(MIGRATION_FLAG)) return

    const rawLogs = localStorage.getItem("logsFontSize")
    if (rawLogs !== null) {
        const n = Number(rawLogs)
        if (n === LEGACY_LOGS_DEFAULT) localStorage.removeItem("logsFontSize")
    }

    const rawEditor = localStorage.getItem("editorFontSize")
    if (rawEditor !== null) {
        const n = Number(rawEditor)
        if (n === LEGACY_EDITOR_DEFAULT) localStorage.removeItem("editorFontSize")
    }

    localStorage.setItem(MIGRATION_FLAG, "1")
}

runMigrationOnce()

export const appFontSizeMode = useStorage<AppFontSizeMode>(APP_FONT_SIZE_KEY, "medium")

export const logsFontSizeOverride = useStorage<number | null>("logsFontSize", null, localStorage, {
    serializer: {
        read: (v) => {
            if (v === null || v === "null" || v === "") return null
            const n = Number(v)
            return isNaN(n) ? null : n
        },
        write: (v) => (v === null ? "null" : String(v)),
    },
})

export const editorFontSizeOverride = useStorage<number | null>("editorFontSize", null, localStorage, {
    serializer: {
        read: (v) => {
            if (v === null || v === "null" || v === "") return null
            const n = Number(v)
            return isNaN(n) ? null : n
        },
        write: (v) => (v === null ? "null" : String(v)),
    },
})

export const logsFontSize = computed({
    get: () => logsFontSizeOverride.value ?? BASE_PX[appFontSizeMode.value],
    set: (v: number) => { logsFontSizeOverride.value = v },
})

export const effectiveEditorFontSize = computed(
    () => editorFontSizeOverride.value ?? BASE_PX[appFontSizeMode.value],
)

export const logsDensity = useStorage<LogDensity>("logsDensity", "normal")
export const logsBodyClamp = useStorage<number>("logsBodyClamp", 0)
export const logsPrettyJson = useStorage<boolean>("logsPrettyJson", true)
export const logsExpandByDefault = useStorage<boolean>("logsExpandByDefault", false)
