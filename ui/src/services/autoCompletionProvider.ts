import {YamlElement} from "@kestra-io/ui-libs";
import {apiUrlWithoutTenants} from "../override/utils/route";
import {useAxios} from "../utils/axios";

export const QUOTE = "'";

let cachedFilters: string[] | null = null;
let cachedFunctions: string[] | null = null;

export function resetExpressionCache() {
    cachedFilters = null;
    cachedFunctions = null;
}

export function fillExpressionCache(filters: string[], functions: string[]) {
    cachedFilters = filters;
    cachedFunctions = functions;
}

async function fetchExpressionFilters(): Promise<string[]> {
    if (cachedFilters === null) {
        try {
            const axios = useAxios();
            cachedFilters = (await axios.get<string[]>(`${apiUrlWithoutTenants()}/pebble/filters`)).data;
        } catch {
            return [];
        }
    }
    return cachedFilters;
}

async function fetchExpressionFunctions(): Promise<string[]> {
    if (cachedFunctions === null) {
        try {
            const axios = useAxios();
            cachedFunctions = (await axios.get<string[]>(`${apiUrlWithoutTenants()}/pebble/functions`)).data;
        } catch {
            return [];
        }
    }
    return cachedFunctions;
}

export interface RootCompletionContext {
    source: string;
    offset: number;
}

export class PebbleAutoCompletion {
    rootFieldAutoCompletion(_context?: RootCompletionContext): Promise<string[]> {
        return Promise.resolve([]);
    }

    nestedFieldAutoCompletion(_source: string, _parsed: any | undefined, _parentField: string, _cursorIndex?: number): Promise<string[]> {
        return Promise.resolve([])
    }

    functionAutoCompletion(_parsed: any | undefined, _functionName: string, _args: Record<string, string>): Promise<string[]> {
        return Promise.resolve([]);
    }

    filterAutoCompletion(): Promise<string[]> {
        return fetchExpressionFilters();
    }

    functionNames(): Promise<string[]> {
        return fetchExpressionFunctions();
    }
}

export class YamlAutoCompletion extends PebbleAutoCompletion {
    valueAutoCompletion(_source: string, _parsed: any | undefined, _yamlElement: YamlElement | undefined): Promise<string[]> {
        return Promise.resolve([]);
    }
}
