import {readFileSync, readdirSync} from "node:fs"
import {join, relative} from "node:path"

type Messages = {[key: string]: string | Messages}

/** Column-picker description keys, e.g. `t("filter.table_column.flows.last modified")`. */
const KEY_PATTERN = /["'`](filter\.table_column\.[^"'`]+)["'`]/g

const sources = (dir: string): string[] =>
    readdirSync(dir, {withFileTypes: true}).flatMap((entry) => {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) return sources(full)
        return /\.(vue|ts|js)$/.test(entry.name) ? [full] : []
    })

/** Deep merge, mirroring how `registerDesignSystemI18n` merges each locale file into the app messages. */
export const mergeMessages = (target: Messages, source: Messages): Messages => {
    for (const [key, value] of Object.entries(source)) {
        const existing = target[key]
        target[key] =
            typeof value === "object" && typeof existing === "object"
                ? mergeMessages({...existing}, value)
                : value
    }
    return target
}

const resolves = (messages: Messages, key: string): boolean =>
    typeof key
        .split(".")
        .reduce<string | Messages | undefined>(
            (current, segment) =>
                current && typeof current === "object" ? current[segment] : undefined,
            messages,
        ) === "string"

/** Returns `path:line (key)` for every referenced key with no English message; empty when clean. */
export const findUnresolvedTableColumnKeys = (srcDir: string, messages: Messages): string[] =>
    sources(srcDir).flatMap((file) =>
        readFileSync(file, "utf8")
            .split("\n")
            .flatMap((line, i) =>
                [...line.matchAll(KEY_PATTERN)]
                    .map(([, key]) => key)
                    .filter((key) => !resolves(messages, key))
                    .map((key) => `${relative(srcDir, file)}:${i + 1} (${key})`),
            ),
    )
