import {YamlElement} from "@kestra-io/design-system"
import {apiUrlWithoutTenants} from "../override/utils/route"
import {useClient} from "@kestra-io/kestra-sdk"

export const QUOTE = "'"

export interface FunctionArgument {
    name: string;
    defaultValue: string | null;
}

export interface PebbleFunctionDef {
    name: string;
    arguments: FunctionArgument[];
}

let cachedFilters: string[] | null = null
let cachedFunctions: PebbleFunctionDef[] | null = null

export function resetExpressionCache() {
    cachedFilters = null
    cachedFunctions = null
}

export function fillExpressionCache(filters: string[], functions: PebbleFunctionDef[]) {
    cachedFilters = filters
    cachedFunctions = functions
}

async function fetchExpressionFilters(): Promise<string[]> {
    if (cachedFilters === null) {
        try {
            const axios = useClient()
            cachedFilters = (await axios.get<string[]>(`${apiUrlWithoutTenants()}/pebble/filters`)).data
        } catch {
            return []
        }
    }
    return cachedFilters
}

async function fetchExpressionFunctions(): Promise<PebbleFunctionDef[]> {
    if (cachedFunctions === null) {
        try {
            const axios = useClient()
            cachedFunctions = (await axios.get<PebbleFunctionDef[]>(`${apiUrlWithoutTenants()}/pebble/functions`)).data
        } catch {
            return []
        }
    }
    return cachedFunctions
}

/**
 * Converts a PebbleFunctionDef into a Monaco snippet string using named-argument syntax.
 * Arguments with null defaults are omitted.
 */
export function functionToSnippet(fn: PebbleFunctionDef): string {
    const argsWithDefaults = fn.arguments.filter(arg => arg.defaultValue !== null)
    if (argsWithDefaults.length === 0) {
        return `${fn.name}()`
    }
    const params = argsWithDefaults.map((arg, i) => {
        const placeholder = `\${${i + 1}:${arg.defaultValue}}`
        return `${arg.name}=${placeholder}`
    }).join(", ")
    return `${fn.name}(${params})`
}

export class PebbleAutoCompletion {
    rootFieldAutoCompletion(): Promise<string[]> {
        return Promise.resolve([])
    }

    nestedFieldAutoCompletion(_source: string, _parsed: any | undefined, _parentField: string, _cursorIndex?: number): Promise<string[]> {
        return Promise.resolve([])
    }

    functionAutoCompletion(_parsed: any | undefined, _functionName: string, _args: Record<string, string>): Promise<string[]> {
        return Promise.resolve([])
    }

    filterAutoCompletion(): Promise<string[]> {
        return fetchExpressionFilters()
    }

    functionsWithDefaults(): Promise<PebbleFunctionDef[]> {
        return fetchExpressionFunctions()
    }
}

export class YamlAutoCompletion extends PebbleAutoCompletion {
    valueAutoCompletion(_source: string, _parsed: any | undefined, _yamlElement: YamlElement | undefined): Promise<string[]> {
        return Promise.resolve([])
    }
}
