import {ElNotification, ElMessageBox, ElTable, ElTableColumn} from "element-plus"
import {h} from "vue"

export default {
    install(app) {
        app.config.globalProperties.$toast = function() {
            const self = this;

            return {
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
                confirm: function(message, callback, callbackIfCancel = () => {}) {
                    ElMessageBox.confirm(
                        this._wrap(message || self.$t("toast confirm")),
                        self.$t("confirmation"),
                        {
                            type: "warning",
                        }
                    )
                        .then(() => {
                            callback();
                        })
                },
                saved: function(name, title, options) {
                    ElNotification.closeAll();
                    ElNotification({
                        ...{
                            title: title || self.$t("saved"),
                            message: this._wrap(self.$t("saved done", {name: name})),
                            type: "success",
                        },
                        ...(options || {})
                    })
                },
                deleted: function(name, title, options) {
                    ElNotification({
                        ...{
                            title: title || self.$t("deleted"),
                            message: this._wrap(self.$t("deleted confirm", {name: name})),
                            type: "success",
                        },
                        ...(options || {})
                    })
                },
                success: function(message, title, options) {
                    ElNotification({
                        ...{
                            title: title || self.$t("success"),
                            message: this._wrap(message),
                            type: "success",
                        },
                        ...(options || {})
                    })
                },
                warning: function(message, title, options) {
                    ElNotification({
                        ...{
                            title: title || self.$t("warning"),
                            message: this._wrap(message),
                            type: "warning",
                        },
                        ...(options || {})
                    })
                },
                error: function(message, title, options) {
                    ElNotification({
                        ...{
                            title: title || self.$t("error"),
                            message: this._wrap(message),
                            type: "error",
                            duration: 0,
                            customClass: "large"
                        },
                        ...(options || {})
                    })
                },
            }
        }
    }
}
