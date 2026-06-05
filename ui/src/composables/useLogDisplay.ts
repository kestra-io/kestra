import {useStorage} from "@vueuse/core"

export type LogDensity = "compact" | "normal" | "expanded";

export const DENSITY_PADDING: Record<LogDensity, string> = {
    compact: "2px",
    normal: "5px",
    expanded: "12px",
}

export const logsFontSize = useStorage<number>("logsFontSize", 14)
export const logsDensity = useStorage<LogDensity>("logsDensity", "normal")
export const logsBodyClamp = useStorage<number>("logsBodyClamp", 0)
export const logsPrettyJson = useStorage<boolean>("logsPrettyJson", true)
export const logsExpandByDefault = useStorage<boolean>("logsExpandByDefault", false)
