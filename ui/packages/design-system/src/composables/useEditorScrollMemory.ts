import {type Ref} from "vue"
import {useThrottleFn} from "@vueuse/core"
import type * as monaco from "monaco-editor/esm/vs/editor/editor.api"

const SAVE_THROTTLE_MS = 100

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

    function restoreAndTrack(editor: monaco.editor.IStandaloneCodeEditor) {
        if (!scrollKey.value) return

        const savedState = load<monaco.editor.ICodeEditorViewState>("viewState")
        if (savedState) {
            editor.restoreViewState(savedState)
            editor.revealLineInCenterIfOutsideViewport?.(editor.getPosition()?.lineNumber ?? 1)
        }
        const top = load<number>("scrollTop", 0)
        if (typeof top === "number") editor.setScrollTop(top)

        const throttledSave = useThrottleFn(() => {
            save(editor.saveViewState(), "viewState")
            save(editor.getScrollTop(), "scrollTop")
        }, SAVE_THROTTLE_MS)
        editor.onDidScrollChange?.(throttledSave)
    }

    function saveViewState(editor: monaco.editor.IStandaloneCodeEditor) {
        if (!scrollKey.value) return
        save(editor.saveViewState(), "viewState")
    }

    return {load, save, restoreAndTrack, saveViewState}
}
