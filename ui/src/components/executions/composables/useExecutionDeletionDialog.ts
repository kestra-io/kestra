import {h, reactive} from "vue"
import {useI18n} from "vue-i18n"
import {KsAlert, KsCheckbox, KsFormItem, KsMessageBox, KsSwitch} from "@kestra-io/design-system"

export interface ExecutionDeletionOptions {
    deleteLogs: boolean;
    deleteMetrics: boolean;
    deleteStorage: boolean;
    includeNonTerminated: boolean;
}

type DeletedData = "deleteLogs" | "deleteMetrics" | "deleteStorage"

export function useExecutionDeletionDialog() {
    const {t} = useI18n({useScope: "global"})

    /** Resolves to what the user chose to delete, or undefined when the dialog is dismissed. */
    async function confirmExecutionDeletion(messageHtml: string, {offerNonTerminated = false} = {}): Promise<ExecutionDeletionOptions | undefined> {
        const options = reactive<ExecutionDeletionOptions>({deleteLogs: true, deleteMetrics: true, deleteStorage: true, includeNonTerminated: false})
        const checkbox = (key: DeletedData, label: string) => h(KsCheckbox, {
            modelValue: options[key],
            label: t(label),
            "onUpdate:modelValue": (val: unknown) => (options[key] = Boolean(val)),
        })

        const message = () => h("div", null, [
            h("p", {class: "pb-3", innerHTML: messageHtml}),
            offerNonTerminated ? h(KsFormItem, {label: t("execution-include-non-terminated")}, {
                default: () => h(KsSwitch, {
                    modelValue: options.includeNonTerminated,
                    "onUpdate:modelValue": (val: unknown) => (options.includeNonTerminated = Boolean(val)),
                }),
            }) : null,
            offerNonTerminated && options.includeNonTerminated ? h(KsAlert, {
                title: t("execution-warn-title"),
                description: t("execution-warn-deleting-still-running"),
                type: "warning",
                closable: false,
            }) : null,
            checkbox("deleteLogs", "execution_deletion.logs"),
            checkbox("deleteMetrics", "execution_deletion.metrics"),
            checkbox("deleteStorage", "execution_deletion.storage"),
        ])

        const confirmed = await KsMessageBox.confirm(message, t("confirmation"), {customStyle: {minWidth: "600px"}})
            .then(() => true)
            .catch(() => false)

        return confirmed ? {...options} : undefined
    }

    return {confirmExecutionDeletion}
}
