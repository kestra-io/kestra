import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"

/** A flow or block read back from YAML. The keys the specs walk are typed as present, so a missing one fails the assertion that reads it. */
export interface ParsedBlock {
    [key: string]: unknown;
    id: string;
    type: string;
    namespace: string;
    message: string;
    prefix: string;
    tasks: ParsedBlock[];
    then: ParsedBlock[];
    else: ParsedBlock[];
    errors: ParsedBlock[];
    triggers: ParsedBlock[];
    conditions: ParsedBlock[];
    cases: Record<string, ParsedBlock[]>;
    defaults: ParsedBlock[];
    dependsOn: string[];
    task: ParsedBlock;
}

export function parseBlock(yaml: string): ParsedBlock {
    const parsed = flowYamlUtils.parse<ParsedBlock>(yaml)
    if (!parsed) throw new Error("The YAML parsed to an empty document.")
    return parsed
}
