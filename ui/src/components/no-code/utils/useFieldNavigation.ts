import {ref, computed} from "vue"

export interface Crumb {
    path: string;
    label: string;
}

export interface NavFrame extends Crumb {
    schema: any;
}

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
