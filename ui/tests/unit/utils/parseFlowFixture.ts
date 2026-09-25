import {parse} from "@kestra-io/topology/flow-yaml-utils"

export interface ParsedTaskFixture {
    [key: string]: unknown
    id?: string
    type?: string
    message?: string
    messageText?: string
    htmlTextContent?: string
    executionId?: string
    channel?: string
    cron?: string
    timezone?: string
    payload?: string
    to?: string
    host?: string
    port?: number
    key?: string
    dependsOn?: string[]
    task?: ParsedTaskFixture
    tasks?: ParsedTaskFixture[]
    errors?: ParsedTaskFixture[]
    then?: ParsedTaskFixture[]
    else?: ParsedTaskFixture[]
    defaults?: ParsedTaskFixture[]
    cases?: Record<string, ParsedTaskFixture[]>
    conditions?: {prefix?: string; type?: string; namespace?: string}[]
    states?: string[]
}

export interface ParsedFlowFixture {
    [key: string]: unknown
    id?: string
    namespace?: string
    type?: string
    tasks?: ParsedTaskFixture[]
    errors?: ParsedTaskFixture[]
    triggers?: ParsedTaskFixture[]
}

export function parseFlowFixture(source: string): ParsedFlowFixture {
    const flow = parse<ParsedFlowFixture>(source)
    if (!flow) throw new Error("Cannot parse an empty flow fixture.")
    return flow
}

export function requireFixtureValue<T>(value: T | undefined): T {
    if (value === undefined) throw new Error("Expected fixture value is missing.")
    return value
}
