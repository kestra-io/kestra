import {YamlElement} from "@kestra-io/ui-libs";

export const QUOTE = "'";

export class YamlAutoCompletion {
    rootFieldAutoCompletion(): Promise<string[]> {
        return Promise.resolve([]);
    }

    nestedFieldAutoCompletion(_source: string, _parsed: any | undefined, _parentField: string): Promise<string[]> {
        return Promise.resolve([])
    }

    valueAutoCompletion(_source: string, _parsed: any | undefined, _yamlElement: YamlElement | undefined): Promise<string[]> {
        return Promise.resolve([]);
    }

    functionAutoCompletion(_parsed: any | undefined, _functionName: string, _args: Record<string, string>): Promise<string[]> {
        return Promise.resolve([]);
    }
}