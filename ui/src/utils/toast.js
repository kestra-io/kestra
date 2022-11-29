import {ElNotification, ElMessageBox, ElMessage} from "element-plus"
import {h} from "vue"

export default {
    install(app) {
        app.config.globalProperties.$toast = function() {
            const self = this;

            return {
                _wrap: function(message) {
                    return h("span", {innerHTML: message});
                },
                confirm: function(message, callback, cancel) {
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
                        .catch(() => {
                            cancel();
                        })
                },
                saved: function(name, title) {
                    ElNotification({
                        title: title || self.$t("saved"),
                        message: this._wrap(self.$t("saved done", {name: name})),
                        type: "success",
                    })
                },
                deleted: function(name, title) {
                    ElNotification({
                        title: title || self.$t("deleted"),
                        message: this._wrap(self.$t("deleted confirm", {name: name})),
                        type: "success",
                    })
                },
                success: function(message, title) {
                    ElNotification({
                        title: title || self.$t("success"),
                        message: this._wrap(message),
                        type: "success",
                    })
                },
                warning: function(message, title) {
                    ElNotification({
                        title: title || self.$t("warning"),
                        message: this._wrap(message),
                        type: "warning",
                    })
                },
                error: function(message, title) {
                    ElNotification({
                        title: title || self.$t("error"),
                        message: this._wrap(message),
                        type: "danger",
                    })
                },
                unsavedConfirm(ok, ko) {
                    self.$toast()
                        .confirm(
                            self.$t("unsaved changed ?"),
                            () => {ok()},
                            () => {ko()}
                        );
                },
            }
        }
    }
}
