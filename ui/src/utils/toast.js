export default {
    install(Vue) {
        Vue.prototype.$toast = function() {
            const self = this;

            return {
                _wrap: function(message) {
                    return [self.$createElement("span", {domProps: {innerHTML: message}})];
                },
                confirm: function(message, callback) {
                    return self.$bvModal
                        .msgBoxConfirm(
                            this._wrap(message || self.$t("toast confirm")),
                            {title: [self.$t("confirmation")]}
                        )
                        .then(confirm => {
                            if (confirm) {
                                callback()
                            }
                        })
                },
                saved: function(name, title) {
                    self.$bvToast.toast(this._wrap(self.$t("saved done", {name: name})), {
                        title: title || self.$t("saved"),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "success"
                    })
                },
                deleted: function(name, title) {
                    self.$bvToast.toast(this._wrap(self.$t("deleted confirm", {name: name})), {
                        title: title || self.$t("deleted"),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "success"
                    })
                },
                success: function(message, title) {
                    self.$bvToast.toast(this._wrap(message), {
                        title: title || self.$t("success"),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "success"
                    })
                },
                warning: function(message, title) {
                    self.$bvToast.toast(this._wrap(message), {
                        title: title || self.$t("warning"),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "warning"
                    })
                },
                error: function(message, title) {
                    self.$bvToast.toast(this._wrap(message), {
                        title: title || self.$t("error"),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "danger"
                    })
                }
            }
        }
    }
}
