<template>
    <b-toast @hide="onHide" :id="toastId" :variant="variant" solid :no-auto-hide="noAutoHide">
        <template v-slot:toast-title>
            <div class="d-flex flex-grow-1 align-items-baseline">
                <strong class="mr-auto">{{title}}</strong>
            </div>
        </template>
        <span>{{content.message || content}}</span>
        <b-table v-if="items && items.length > 0" striped hover :items="items"></b-table>
    </b-toast>
</template>
<script>
export default {
    props: {
        variant: {
            type: String,
            default: "danger"
        },
        title: {
            type: String,
            required: true
        },
        toastId: {
            type: String,
            required: true
        },
        content: {
            type: Object,
            required: true
        },
        noAutoHide: {
            type: Boolean,
            default: false
        }
    },
    mounted() {
        this.$bvToast.show(this.toastId);
    },
    computed: {
        items() {
            const messages = this.content && this.content._embedded && this.content._embedded.errors ? this.content._embedded.errors : []
            return Array.isArray(messages) ? messages : [messages]
        }
    },
    methods: {
        onHide() {
            setTimeout(() => {
                this.$store.commit("core/setErrorMessage", undefined);
            }, 1000);
        }
    }
};
</script>
