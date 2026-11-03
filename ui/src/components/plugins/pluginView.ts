import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"

export const PLUGIN_VIEWS = ["cards", "table"] as const
export type PluginView = typeof PLUGIN_VIEWS[number]

/** A choice kept in the route query, which drops the key when the first choice, the default, is picked. */
export function useQueryChoice<T extends string>(key: string, choices: readonly T[]) {
    const route = useRoute()
    const router = useRouter()
    const fallback = choices[0]

    return computed<T>({
        get: () => choices.find(choice => choice === route.query[key]) ?? fallback,
        set: (choice) => router.push({query: {...route.query, [key]: choice === fallback ? undefined : choice}}),
    })
}

export function usePluginView() {
    return useQueryChoice("view", PLUGIN_VIEWS)
}
