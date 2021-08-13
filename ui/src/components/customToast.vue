<template>
    <b-toast @hide="onHide" id="app-toast" :variant="message.variant" solid :no-auto-hide="noAutoHide">
        <template #toast-title>
            <div class="d-flex flex-grow-1 align-items-baseline">
                <strong class="mr-auto" v-html="title" />
            </div>
        </template>
        <span>{{ text }}</span>
        <b-table
            class="mt-2 mb-0"
            small
            bordered
            :responsive="true"
            v-if="items && items.length > 0"
            striped
            hover
            :items="items"
        />
    </b-toast>
</template>
<script>
    export default {
        props: {
            message: {
                type: Object,
                required: true
            },
            noAutoHide: {
                type: Boolean,
                default: false
            }
        },
        mounted() {
            this.$bvToast.show("app-toast");
            if (this.message.timeout) {
                this.onHide()
            }
        },
        computed: {
            text () {
                return this.message.message || this.message.content.message
            },
            title () {
                return this.message.title || this.$t("error")
            },
            items() {
                const messages = this.message.content && this.message.content._embedded && this.message.content._embedded.errors ? this.message.content._embedded.errors : []
                return Array.isArray(messages) ? messages : [messages]
            }
        },
        methods: {
            onHide() {
                setTimeout(() => {
                    this.$store.commit("core/setMessage", undefined);
                }, this.message.timeout || 1000);
            }
        }
    };
</script>
<style lang="scss">
    @import "../styles/variable";
    table {
        background-color: $white;
    }
</style>
