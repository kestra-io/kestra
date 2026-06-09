import moment from "moment/moment"
import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
import {storageKeys} from "./constants"

export type InputType = "STRING"
    | "NUMBER"
    | "BOOLEAN"
    | "BOOL"
    | "DATE"
    | "DATETIME"
    | "TIME"
    | "ARRAY"
    | "MULTISELECT"
    | "JSON"
    | "YAML"
    | "SECRET"
    | "FILE"
    | "DURATION"
    | "INT"
    | "FLOAT"
    | "ENUM"
    | "SELECT"
    | "URI"
    | "EMAIL"
    | "FORM";

export interface FlowInput {
    id: string;
    type?: InputType | string;
    displayName?: string;
    description?: string;
    inputs?: FlowInput[];
    [key: string]: any;
}

/**
 * Mirrors the backend `Input.expandToLeaves`: replaces each FORM group with copies of its
 * children whose id is rewritten to the dotted path (`region` -> `environment.region`).
 * FORMs never nest (rejected by backend validation), so expansion is single-level.
 * Returns the flat leaf list keyed by dotted id, which the submission/validation paths consume.
 */
export function flattenInputs(inputs: FlowInput[] | undefined): FlowInput[] {
    if (!inputs) return []
    return inputs.flatMap((input) =>
        input.type === "FORM"
            ? (input.inputs ?? []).map((child) => ({...child, id: `${input.id}.${child.id}`}))
            : [input],
    )
}

/**
 * Per-flow localStorage key for the FORM wizard's in-progress values. Scoped by tenant +
 * flow identity + revision so different flows (and revisions) never collide.
 */
export function executeFormValuesStorageKey(flow: {tenantId?: string; namespace: string; id: string; revision?: number} | undefined): string | undefined {
    if (!flow) return undefined
    return [storageKeys.EXECUTE_FORM_VALUES_PREFIX, flow.tenantId ?? "default", flow.namespace, flow.id, flow.revision ?? ""].join(":")
}

export interface WizardStep {
    kind: "plain" | "form" | "recap";
    title?: string;
    description?: string;
    leafIds?: string[];
}

/**
 * Segments inputs (document order) into wizard steps: each FORM is one step (titled by its
 * displayName), and each contiguous run of ungrouped top-level inputs is its own step — so
 * `STRING, FORM(STRING), DATE` yields `[STRING] [FORM child] [DATE]` then a final recap step.
 * Empty FORMs are skipped. Leaf ids are dotted for FORM children (mirrors `flattenInputs`).
 */
export function buildWizardSteps(inputs: FlowInput[] | undefined): WizardStep[] {
    const result: WizardStep[] = []
    let run: string[] = []
    const flushRun = () => {
        if (run.length) {
            result.push({kind: "plain", leafIds: run})
            run = []
        }
    }
    for (const input of inputs ?? []) {
        if (input.type === "FORM") {
            flushRun()
            const leafIds = flattenInputs([input]).map((l) => l.id)
            if (leafIds.length) {
                result.push({kind: "form", title: input.displayName || input.id, description: input.description, leafIds})
            }
        } else {
            run.push(input.id)
        }
    }
    flushRun()
    result.push({kind: "recap"})
    return result
}

export function normalize(type: InputType | undefined, value: any) {
    let res = value

    if (type === "BOOLEAN" && value === undefined) {
        res = "undefined"
    } else if (type === "BOOL" && value === undefined) {
        res = false
    } else if (value === null || value === undefined) {
        res = undefined
    } else if (type === "DATE" || type === "DATETIME") {
        res = moment(res).toISOString()
    } else if (type === "TIME") {
        res = moment().startOf("day").add(res, "seconds").toString()
    } else if (type === "ARRAY" || type === "MULTISELECT" || type === "JSON") {
        if (typeof res !== "string") {
            res = JSON.stringify(res).toString()
        }
    } else if (type === "YAML") {
        if (typeof res !== "string") {
            res = YAML_UTILS.stringify(res).toString()
        }
    } else if (type === "STRING" && Array.isArray(res)) {
        res = res.toString()
    }
    return res
}

export function normalizeForComponents(type: InputType | undefined, value: any) {
    let res = value

    if (value === null) {
        res = undefined
    } else if (type === "DATE" || type === "DATETIME") {
        res = moment(res).toISOString()
    } else if (type === "TIME") {
        res = moment().startOf("day").add(res, "seconds").toString()
    } else if (type === "ARRAY") {
        res = JSON.stringify(res).toString()
    } else if (type === "BOOLEAN" && value === undefined) {
        res = "undefined"
    } else if (type === "BOOL" && value === undefined) {
        res = false
    } else if (type === "STRING" && Array.isArray(res)) {
        res = res.toString()
    }
    return res
}
