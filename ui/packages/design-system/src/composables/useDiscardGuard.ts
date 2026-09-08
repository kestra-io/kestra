import {getCurrentInstance, ref} from "vue"
import {KsMessageBox} from "../components/Feedback/KsMessageBox"

export function useDiscardGuard(isDirty: () => boolean | undefined, options?: {message?: string}) {
    const translate = getCurrentInstance()?.appContext.config.globalProperties.$t as ((key: string) => string) | undefined
    const t = (key: string) => translate?.(key) ?? key
    const isConfirming = ref(false)

    function guardedClose(proceed: () => void) {
        if (!isDirty()) {
            proceed()
            return
        }
        if (isConfirming.value) {
            return
        }
        isConfirming.value = true
        KsMessageBox
            .confirm(options?.message ?? t("ks_discard_guard.message"), t("ks_discard_guard.title"), {type: "warning", showCancelButton: true})
            .then(() => proceed())
            .catch(() => {})
            .finally(() => {
                isConfirming.value = false
            })
    }

    return {guardedClose, isConfirming}
}
