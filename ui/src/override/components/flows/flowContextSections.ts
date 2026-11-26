import {computed, type ComputedRef, type Ref} from "vue"
import type {DataSection} from "../../../components/flows/contextSections/types"

/** Extension point for context sections computed from the flow being edited (EE adds the applied policies). */
export function useFlowContextSections(_namespace: Ref<string | undefined>): ComputedRef<DataSection[]> {
    return computed(() => [])
}
