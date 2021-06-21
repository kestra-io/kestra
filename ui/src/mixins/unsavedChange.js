export default {
    beforeRouteLeave(to, from, next) {
        if (this.hasUnsavedChanged()) {
            this.$toast().unsavedConfirm(
                () => next(),
                () => next(false),
            );
        } else {
            next();
        }
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
        beforeDestroy() {
            window.removeEventListener("beforeunload", this.confirmUnload)
        },
        hasUnsavedChanged() {
            return false;
        },
    }
}
