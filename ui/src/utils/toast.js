export default {
    install(Vue) {
        Vue.prototype.$toast = function() {
            const self = this;

            return {
                message: function(type, message, name) {
                    if (name) {
                        return [self.$createElement('span', {domProps: {innerHTML: self.$t(type + " name", {name: name})}})];
                    } else if (message) {
                        return message;
                    } else {
                        return self.$t(type)
                    }
                },
                confirm : function(name, callback) {
                    self.$bvModal
                        .msgBoxConfirm(
                            [self.$createElement('span', {domProps: {innerHTML: self.$t("delete confirm", {msg: name})}})],
                            {title: [self.$t("confirmation")]}
                        )
                        .then(confirm => {
                            if (confirm) {
                                callback()
                                    .then(() => {
                                        this.success({type: "deleted", name: name})
                                    });
                            }
                        })
                },
                success: function({message, title, name, type}) {
                    self.$bvToast.toast(this.message(type || "saved", message, name), {
                        title: title || self.$t("success"),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "success"
                    })
                },
                warning: function(message, title) {
                    self.$bvToast.toast(message, {
                        title: title || self.$t("warning"),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "warning"
                    })
                },
                error: function(message, title) {
                    self.$bvToast.toast(message, {
                        title: title || self.$t("warning"),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "danger"
                    })
                }
            }
        }
    }
}
