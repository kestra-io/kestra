import {h, reactive} from "vue"
import {useI18n} from "vue-i18n"
import {KsAlert, KsCheckbox, KsFormItem, KsMessageBox, KsSwitch} from "@kestra-io/design-system"

export interface ExecutionDeletionOptions {
    deleteLogs: boolean;
    deleteMetrics: boolean;
    deleteStorage: boolean;
    includeNonTerminated: boolean;
}

export interface ConfirmExecutionDeletionConfig {
    offerNonTerminated?: boolean;
    nonTerminatedCount?: number;
    total?: number;
}

type DeletedData = "deleteLogs" | "deleteMetrics" | "deleteStorage"

export function useExecutionDeletionDialog() {
    const {t} = useI18n({useScope: "global"})

    /** Resolves to what the user chose to delete, or undefined when the dialog is dismissed. */
    async function confirmExecutionDeletion(
        messageHtml: string,
        {offerNonTerminated = false, nonTerminatedCount = 0, total = 0}: ConfirmExecutionDeletionConfig = {},
    ): Promise<ExecutionDeletionOptions | undefined> {
        const options = reactive<ExecutionDeletionOptions>({
            deleteLogs: true,
            deleteMetrics: true,
            deleteStorage: true,
            includeNonTerminated: false,
        })
        const checkbox = (key: DeletedData, label: string) => h(KsCheckbox, {
            modelValue: options[key],
            label: t(label),
            "onUpdate:modelValue": (val: unknown) => (options[key] = Boolean(val)),
        })

        const requiresConsent = nonTerminatedCount > 0

        const message = () => h("div", null, [
            h("p", {style: {paddingBottom: "var(--ks-spacing-4)"}, innerHTML: messageHtml}),
            offerNonTerminated ? h(KsFormItem, {label: t("execution-include-non-terminated")}, {
                default: () => h(KsSwitch, {
                    modelValue: options.includeNonTerminated,
                    "onUpdate:modelValue": (val: unknown) => (options.includeNonTerminated = Boolean(val)),
                }),
            }) : null,
            requiresConsent ? h(KsAlert, {
                title: t("execution-warn-title"),
                description: `${t("execution-warn-non-terminated-count", {count: nonTerminatedCount, total: total || nonTerminatedCount})} ${t("execution-warn-deleting-still-running")}`,
                type: "warning",
                closable: false,
            }) : (offerNonTerminated && options.includeNonTerminated ? h(KsAlert, {
                title: t("execution-warn-title"),
                description: t("execution-warn-deleting-still-running"),
                type: "warning",
                closable: false,
            }) : null),
            checkbox("deleteLogs", "execution_deletion.logs"),
            checkbox("deleteMetrics", "execution_deletion.metrics"),
            checkbox("deleteStorage", "execution_deletion.storage"),
        ])

        const confirmed = await KsMessageBox.confirm(message, t("confirmation"), {
            customStyle: {minWidth: "600px"},
            confirmButtonDisabled: requiresConsent,
            beforeClose: (action: string, instance: {confirmButtonDisabled?: boolean}, done: () => void) => {
                if (action === "confirm" && requiresConsent && !options.includeNonTerminated) {
                    instance.confirmButtonDisabled = true
                    return
                }
                done()
            },
        } as unknown as Parameters<typeof KsMessageBox.confirm>[2])
            .then(() => true)
            .catch(() => false)

        return confirmed ? {...options} : undefined
    }

    return {confirmExecutionDeletion}
}
