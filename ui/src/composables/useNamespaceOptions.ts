import {onMounted, ref} from "vue"
import useNamespaces from "./useNamespaces"

const NAMESPACE_FETCH_SIZE = 500

export function useNamespaceOptions() {
    const namespaces = ref<string[]>([])
    const loading = ref(false)
    const error = ref(false)

    onMounted(async () => {
        loading.value = true
        try {
            const fetched = await useNamespaces(NAMESPACE_FETCH_SIZE).all()
            namespaces.value = fetched.map(n => n.id)
        } catch {
            error.value = true
        } finally {
            loading.value = false
        }
    })

    return {namespaces, loading, error}
}
