import {useI18n} from "vue-i18n"
import en from "./translations/en.json"

type Messages = {[key: string]: string | Messages}

const registered = new WeakSet<object>()

function missingFrom(messages: Messages, has: (key: string) => boolean, prefix = ""): Messages | undefined {
    const missing: Messages = {}
    for (const [key, value] of Object.entries(messages)) {
        const path = prefix + key
        const entry = typeof value === "string" ? (has(path) ? undefined : value) : missingFrom(value, has, `${path}.`)
        if (entry !== undefined) missing[key] = entry
    }
    return Object.keys(missing).length ? missing : undefined
}

/** Adds the graph's English labels to the app's i18n, for the keys the app does not translate itself. */
export function registerTopologyMessages() {
    const i18n = useI18n({useScope: "global"})
    if (registered.has(i18n)) return
    registered.add(i18n)
    const missing = missingFrom(en as Messages, (key) => i18n.te(key, "en"))
    if (missing) i18n.mergeLocaleMessage("en", missing)
}
