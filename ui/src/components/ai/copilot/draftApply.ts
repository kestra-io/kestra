import {KsMessageBox} from "@kestra-io/design-system"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import {asProblem, isProblemType, ProblemTypes} from "@kestra-io/kestra-sdk"

/** Shared with the EE `override/` draft actions, so an EE artefact confirms and fails exactly like an OSS one. */

/** Minimal shape of vue-i18n's `t`, so callers pass their own bound translator. */
type Translate = (key: string, named?: Record<string, unknown>) => string

/** Opts out of the global error toast: an already-exists create is an expected fallback step, and real failures get their own alert. */
export const SILENT_REQUEST = {showMessageOnError: false}

/** Confirm an apply; resolves false when the user dismisses the dialog. */
export function confirmApply(t: Translate, message: string, title: string): Promise<boolean> {
    return KsMessageBox.confirm(message, title, {
        type: "warning",
        confirmButtonText: t("ai.copilot.draft.apply"),
        cancelButtonText: t("cancel"),
    }).then(() => true).catch(() => false)
}

/** Report a failed apply, preferring the server's message over the caller's fallback. */
export async function alertError(e: unknown, fallback: string, title: string): Promise<void> {
    const message = asProblem(e)?.detail ?? (e instanceof Error ? e.message : undefined) ?? fallback
    await KsMessageBox.alert(message, title, {type: "error"})
}

/** Parse an artefact's namespace + id out of its YAML; empty strings when they can't be read. */
export function parseArtefactYaml(yaml: string): {namespace: string; id: string} {
    try {
        const parsed = YAML_UTILS.parse(yaml)
        return {namespace: parsed?.namespace ?? "", id: parsed?.id ?? ""}
    } catch {
        return {namespace: "", id: ""}
    }
}

/** A create failed because the artefact already exists, so update it instead. */
export function isAlreadyExists(e: unknown): boolean {
    return isProblemType(e, ProblemTypes.ENTITY_ALREADY_EXISTS)
}
