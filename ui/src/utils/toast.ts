import {ElNotification, ElMessageBox, ElTable, ElTableColumn} from "element-plus"
import {h} from "vue"
import {useI18n} from "vue-i18n"

// eslint-disable-next-line no-unused-vars
const makeToast = (t: (t:string, options?: Record<string, string>) => string) => ({
    _wrap: function(message) {
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
            return h("span", {innerHTML: message});
        }
    },
    confirm: function(message, callback, renderVNode = false, type = "warning" as const) {
        ElMessageBox
            .confirm(renderVNode ? message : this._wrap(message || t("toast confirm")), t("confirmation"), {type})
            .then(() => callback())
    },
    saved: function(name, title, options) {
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
    deleted: function(name, title, options) {
        ElNotification({
            ...{
                title: title || t("deleted"),
                message: this._wrap(t("deleted confirm", {name: name})),
                position: "bottom-right",
                type: "success",
            },
            ...(options || {})
        })
    },
    success: function(message, title, options) {
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
    warning: function(message, title, options) {
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
    error: function(message, title, options) {
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
    install(app) {
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
