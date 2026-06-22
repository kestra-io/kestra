import moment from "moment/moment";
import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
import {storageKeys} from "./constants";

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
 * Returns the longest form id that is a dotted prefix of `id` (`environment` for
 * `environment.region`), or undefined if none matches. Form-id-aware (not a naive first-dot
 * split) because top-level ids may themselves contain dots, and a form id may too.
 */
function longestFormPrefix(id: string, formIds: string[]): string | undefined {
    return formIds
        .filter((f) => id.startsWith(f + "."))
        .sort((a, b) => b.length - a.length)[0]
}

/**
 * Strips the owning FORM's id prefix from a dotted leaf id so a child without an explicit
 * displayName shows its bare name (`form.a` -> `a`) — the section header already names the form.
 * Form-id-aware (not a naive first-dot split) because top-level ids may themselves contain dots;
 * longest matching prefix wins. Leaves non-form ids untouched.
 */
export function formChildName(id: string, formIds: string[]): string {
    const prefix = longestFormPrefix(id, formIds)
    return prefix ? id.slice(prefix.length + 1) : id
}

/**
 * Inverse of `flattenInputs`: rebuilds the document-ordered FORM tree from a flat list of dotted
 * leaves + `formGroups` metadata. Each leaf owned by a form (longest matching prefix wins) becomes
 * a BARE-id child of that FORM node; the FORM node is placed at the position of its first child
 * leaf, and `displayName`/`description` come from `formGroups`. Ungrouped leaves pass through
 * unchanged. Children carry bare ids so the round-trip holds:
 * `flattenInputs(unflattenToForms(leaves, groups))` deep-equals `leaves` (children contiguous, as
 * the backend emits them). Used by EE Apps, which receives flat leaves with no FORM nodes.
 */
export function unflattenToForms(
    leaves: FlowInput[] | undefined,
    formGroups: Record<string, {displayName?: string; description?: string}> | undefined,
): FlowInput[] {
    if (!leaves) return []
    const formIds = Object.keys(formGroups ?? {})
    if (!formIds.length) return leaves

    const result: FlowInput[] = []
    const nodes = new Map<string, FlowInput>()
    for (const leaf of leaves) {
        const owner = longestFormPrefix(leaf.id, formIds)
        if (!owner) {
            result.push(leaf)
            continue
        }
        let node = nodes.get(owner)
        if (!node) {
            node = {
                id: owner,
                type: "FORM",
                displayName: formGroups?.[owner]?.displayName,
                description: formGroups?.[owner]?.description,
                inputs: [],
            }
            nodes.set(owner, node)
            result.push(node)
        }
        node.inputs!.push({...leaf, id: leaf.id.slice(owner.length + 1)})
    }
    return result
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
    let res = value;

    if (type === "BOOLEAN" && value === undefined) {
        res = "undefined";
    } else if (type === "BOOL" && value === undefined) {
        res = false
    } else if (value === null || value === undefined) {
        res = undefined;
    } else if (type === "DATE" || type === "DATETIME") {
        res = moment(res).toISOString()
    } else if (type === "TIME") {
        res = moment().startOf("day").add(res, "seconds").toString()
    } else if (type === "ARRAY" || type === "MULTISELECT" || type === "JSON") {
        if (typeof res !== "string") {
            res = JSON.stringify(res).toString();
        }
    } else if (type === "YAML") {
        if (typeof res !== "string") {
            res = YAML_UTILS.stringify(res).toString();
        }
    } else if (type === "STRING" && Array.isArray(res)) {
        res = res.toString();
    }
    return res;
}

export function normalizeForComponents(type: InputType | undefined, value: any) {
    let res = value;

    if (value === null) {
        res = undefined;
    } else if (type === "DATE" || type === "DATETIME") {
        res = moment(res).toISOString()
    } else if (type === "TIME") {
        res = moment().startOf("day").add(res, "seconds").toString()
    } else if (type === "ARRAY") {
        res = JSON.stringify(res).toString();
    } else if (type === "BOOLEAN" && value === undefined) {
        res = "undefined";
    } else if (type === "BOOL" && value === undefined) {
        res = false
    } else if (type === "STRING" && Array.isArray(res)) {
        res = res.toString();
    }
    return res;
}
