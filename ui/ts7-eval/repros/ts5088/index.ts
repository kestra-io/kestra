import {ref} from "vue"

export type Schema = {
    properties?: Record<string, Schema>;
    items?: Schema;
}

export function useStack() {
    const stack = ref<{schema: Schema}[]>([])
    return {stack}
}
