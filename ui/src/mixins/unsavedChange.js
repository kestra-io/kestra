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
        hasUnsavedChanged() {
            return false;
        }
    }
}
