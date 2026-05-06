import {KsMarkdown, KsMessageBox, KsNotification, KsTable, KsTableColumn} from "@kestra-io/design-system"
import {App, h} from "vue"
import {useI18n} from "vue-i18n"


export const makeToast = (t: (t:string, options?: Record<string, string>) => string) => ({
    _wrap: function(message:string) {
        if(Array.isArray(message) && message.length > 0){
            return h(
                KsTable,
                {
                    stripe: true,
                    tableLayout: "auto",
                    fixed: true,
                    data: message,
                    class: ["mt-2"],
                    size: "small",
                },
                [
                    h(KsTableColumn, {label: "Message", formatter: (row: any) => { return h("span",{innerHTML:row.message})}})
                ]
            )
        } else {
            return h(KsMarkdown, {content: message});
        }
    },
    _MarkdownWrap: function(message:string) {
        return h(KsMarkdown, {content: message})
    },
    confirm: function(message:string, callback: () => Promise<any>, type = "warning" as const, showCancelButton = true) {
        return KsMessageBox
            .confirm(typeof message === "string" ? this._MarkdownWrap(message || t("toast confirm")) : h(message), t("confirmation"), {type, showCancelButton})
            .then(() => callback())
            .catch(() => {
                // User cancelled
            });
    },
    saved: function(name:string, title?:string, options?: Record<string, any>) {
        KsNotification.closeAll();
        const message = options?.multiple
            ? t("multiple saved done", {name})
            : t("saved done", {name: name});
        KsNotification({

                title: title || t("saved"),
                message: this._wrap(message),
                position: "bottom-right",
                type: "success",
            ...options
        });
    },
    deleted: function(name:string, title?:string, options?: Record<string, any>) {
        KsNotification({

                title: title || t("deleted"),
                message: this._wrap(t("deleted confirm", {name: name})),
                position: "bottom-right",
                type: "success",
            ...options
        })
    },
    success: function(message:string, title?:string, options?: Record<string, any>) {
        KsNotification({

                title: title || t("success"),
                message: this._wrap(message),
                position: "bottom-right",
                type: "success",
            ...options
        })
    },
    warning: function(message:string, title?:string, options?: Record<string, any>) {
        KsNotification({

                title: title || t("warning"),
                message: this._wrap(message),
                position: "bottom-right",
                type: "warning",
            ...options
        })
    },
    error: function(message:string, title?:string, options?: Record<string, any>) {
        KsNotification({

                title: title ?? t("error"),
                message: this._wrap(message),
                position: "bottom-right",
                type: "error",
                duration: 0,
                customClass: "kel-notification__large",
            ...options
        })
    }
})

export default {
    install(app: App) {
        app.config.globalProperties.$toast = () => {
            return makeToast(app.config.globalProperties.$t);
        }
    }
}

export function useToast(){
    const {t} = useI18n({useScope: "global"});
    return makeToast(t)
}
