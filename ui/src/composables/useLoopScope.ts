import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"
import {LOOP_SCOPE_QUERY_KEY, parseLoopScope, serializeLoopScope, type LoopScopeEntry} from "../utils/loopScope"

export function useLoopScope() {
    const route = useRoute()
    const router = useRouter()

    const entries = computed(() => parseLoopScope(route.query[LOOP_SCOPE_QUERY_KEY]))
    const key = computed(() => serializeLoopScope(entries.value) ?? "")

    function setEntries(next: LoopScopeEntry[]) {
        const serialized = serializeLoopScope(next)
        if ((serialized ?? "") === key.value) return
        const query = {...route.query}
        if (serialized) query[LOOP_SCOPE_QUERY_KEY] = serialized
        else delete query[LOOP_SCOPE_QUERY_KEY]
        router.replace({query})
    }

    return {entries, key, setEntries}
}
