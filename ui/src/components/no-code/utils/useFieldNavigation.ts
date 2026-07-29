import {ref, computed} from "vue"

export interface NavFrame {
    /** Path of the field inside the task model, e.g. "retry" or "inputs[0]". */
    path: string;
    /** Display label shown in the breadcrumb and the field header. */
    label: string;
    /** The field (or array item) schema. */
    schema: any;
}

/**
 * Per-pane push-in-place navigation stack for the no-code task form. Drilling
 * into a deep field (object, array of objects, complex anyOf) pushes a frame;
 * the form then renders that frame full-width with a breadcrumb back.
 */
export function useFieldNavigation() {
    const stack = ref<NavFrame[]>([])

    const current = computed<NavFrame | undefined>(() => stack.value[stack.value.length - 1])

    function push(frame: NavFrame) {
        stack.value = [...stack.value, frame]
    }

    function pop() {
        stack.value = stack.value.slice(0, -1)
    }

    function popTo(index: number) {
        stack.value = stack.value.slice(0, index + 1)
    }

    function reset() {
        if (stack.value.length) stack.value = []
    }

    return {stack, current, push, pop, popTo, reset}
}

export type FieldNavigation = ReturnType<typeof useFieldNavigation>;
