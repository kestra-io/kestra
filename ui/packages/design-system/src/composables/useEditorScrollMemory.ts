import {type Ref} from "vue"

export function useEditorScrollMemory(scrollKey: Ref<string | undefined>) {
    function storageKey(key: string) {
        return `editorScroll:${scrollKey.value}:${key}`
    }

    function load<T>(key: string, fallback?: T): T | undefined {
        if (!scrollKey.value) return fallback
        try {
            const raw = localStorage.getItem(storageKey(key))
            return raw ? (JSON.parse(raw) as T) : fallback
        } catch {
            return fallback
        }
    }

    function save<T>(data: T, key: string) {
        if (!scrollKey.value) return
        try {
            localStorage.setItem(storageKey(key), JSON.stringify(data))
        } catch {
            return
        }
    }

    return {load, save}
}
