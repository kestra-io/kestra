import {h, App, Component} from "vue"
import {useI18n} from "vue-i18n"
import {ElNotification, ElMessageBox, ElTable, ElTableColumn, NotificationOptions} from "element-plus"

import Markdown from "../components/layout/Markdown.vue"

// eslint-disable-next-line no-unused-vars
const makeToast = (t: (t:string, options?: Record<string, string>) => string) => ({
    _wrap: function(message:string) {
        if(Array.isArray(message) && message.length > 0){
            return h(
                ElTable,
                {
                    stripe: true,
                    tableLayout: "auto",
                    fixed: true,
                    data: message,
                    class: ["mt-2"],
                    size: "small",
                },
                [
                    h(ElTableColumn, {label: "Message", formatter: (row) => { return h("span",{innerHTML:row.message})}})
                ]
            )
        } else {
            return h(Markdown, {source: message});
        }
    },
    _MarkdownWrap: function(message: string) {
        return h(Markdown, {source: message})
    },
    confirm: function(message: Component | string, callback: () => void, type = "warning" as const) {
        ElMessageBox
            .confirm(typeof message === "string" ? this._MarkdownWrap(message || t("toast confirm")) : h(message), t("confirmation"), {type})
            .then(() => callback())
    },
    saved: function(name: string, title: string, options?: NotificationOptions & {multiple?: boolean}) {
        ElNotification.closeAll();
        const message = options?.multiple
            ? t("multiple saved done", {name})
            : t("saved done", {name: name});
        ElNotification({
            ...{
                title: title || t("saved"),
                message: this._wrap(message),
                position: "bottom-right",
                type: "success",
            },
            ...(options || {})
        });
    },
    deleted: function(name: string, title: string, options?: NotificationOptions) {
        ElNotification({
            ...{
                title: title || t("deleted"),
                message: this._wrap(t("deleted confirm", {name: name})),
                position: "bottom-right",
                type: "success",
            },
            ...(options ?? {})
        })
    },
    success: function(message: string, title: string, options?: NotificationOptions) {
        ElNotification({
            ...{
                title: title || t("success"),
                message: this._wrap(message),
                position: "bottom-right",
                type: "success",
            },
            ...(options || {})
        })
    },
    warning: function(message: string, title: string, options?: NotificationOptions) {
        ElNotification({
            ...{
                title: title || t("warning"),
                message: this._wrap(message),
                position: "bottom-right",
                type: "warning",
            },
            ...(options || {})
        })
    },
    error: function(message: string, title: string, options?: NotificationOptions) {
        ElNotification({
            ...{
                title: title || t("error"),
                message: this._wrap(message),
                position: "bottom-right",
                type: "error",
                duration: 0,
                customClass: "large"
            },
            ...(options || {})
        })
    }
})

export default {
    install(app: App) {
        app.config.globalProperties.$toast = function() {
            const self = this;

            return makeToast(self.$t);
        }
    }
}

export function useToast(){
    const {t} = useI18n()
    return makeToast(t)
}
