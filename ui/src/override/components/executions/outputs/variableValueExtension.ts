import type {Component} from "vue"
import type {FlowForExecution} from "@kestra-io/kestra-sdk"

export type VariableValueRenderer = (path: string, flow: FlowForExecution | undefined) => Component | undefined

/** The component an edition shows a selected execution variable with, or undefined to keep the default viewer. */
export function useVariableValueRenderer(): VariableValueRenderer {
    return () => undefined
}
