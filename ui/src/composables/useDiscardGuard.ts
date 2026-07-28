import {ref} from "vue"
import {useI18n} from "vue-i18n"
import {KsMessageBox} from "@kestra-io/design-system"

export function useDiscardGuard(isDirty: () => boolean | undefined, options?: {message?: string}) {
    const {t} = useI18n({useScope: "global"})
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
            .confirm(options?.message ?? t("discard changes confirmation"), t("confirmation"), {type: "warning", showCancelButton: true})
            .then(() => proceed())
            .catch(() => {})
            .finally(() => {
                isConfirming.value = false
            })
    }

    return {guardedClose, isConfirming}
}
