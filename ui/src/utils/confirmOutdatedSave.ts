import {h} from "vue"
import {KsMessageBox} from "@kestra-io/design-system"

export function confirmOutdatedSave(t: (key: string) => string, message: string): Promise<boolean> {
    return KsMessageBox({
        title: t("outdated revision save confirmation.update.title"),
        message: () => h("p", null, message),
        showCancelButton: true,
        confirmButtonText: t("ok"),
        cancelButtonText: t("cancel"),
        center: false,
        showClose: false,
    }).then(() => true).catch(() => false)
}
