export default {
    beforeRouteLeave(to, from, next) {
        this.showToast(to, from, next);
    },
    beforeRouteUpdate(to, from, next) {
        this.showToast(to, from, next);
    },
    data() {
        return {
            isConfirm: false,
        };
    },
    methods: {
        created() {
            window.addEventListener("beforeunload", this.confirmUnload);
        },
        confirmUnload (e) {
            const confirmationMessage = this.$t("unsaved changed ?")

            if (this.hasUnsavedChanged()) {
                (e || window.event).returnValue = confirmationMessage; //Gecko + IE
                return confirmationMessage; //Gecko + Webkit, Safari, Chrome etc.
            }
        },
        showToast(to, from, next) {
            if (!this.isConfirm && this.hasUnsavedChanged()) {
                this.$toast().unsavedConfirm(
                    () => this.confirm(next),
                    () => this.cancel(next)
                );
            } else {
                next();
            }
        },
        confirm(next) {
            this.isConfirm = true;
            next()
            window.setTimeout(() => {
                this.isConfirm = false;
            }, 50)
        },
        cancel(next) {
            this.$nprogress.done();
            next(false)
        },
        beforeUnmount() {
            window.removeEventListener("beforeunload", this.confirmUnload)
        },
        hasUnsavedChanged() {
            return false;
        },
    }
}
