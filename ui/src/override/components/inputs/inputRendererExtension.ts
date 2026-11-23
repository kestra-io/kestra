import type {Component} from "vue"
import type {InputMetaData} from "../../../stores/executions"

export type InputRenderer = (input: InputMetaData) => Component | undefined

/** The control an edition renders an input with instead of the default one, or undefined to keep the default. */
export function useInputRendererExtension(): InputRenderer {
    return () => undefined
}
