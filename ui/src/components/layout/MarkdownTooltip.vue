<template>
    <span v-if="description">

        <b-link class="text-inherited" v-b-modal="`tooltip-desc-modal-${id}`">
            <help-circle
                title=""
                :id="'tooltip-desc-' + id"
            />
        </b-link>

        <b-modal
            :id="'tooltip-desc-modal-' + id"
            :title="title"
            header-bg-variant="dark"
            header-text-variant="light"
            hide-backdrop
            hide-footer
            modal-class="right"
            size="xl"
            v-if="modal"
        >
            <markdown class="markdown-tooltip" :source="description" />
        </b-modal>

        <b-popover triggers="hover" :target="'tooltip-desc-' + id" placement="left" v-if="!modal" :title="title">
            <markdown class="markdown-tooltip" :source="description" />
        </b-popover>
    </span>
</template>
<script>
    import HelpCircle from "vue-material-design-icons/HelpCircle";
    import Markdown from "./Markdown";

    export default {
        components: {
            HelpCircle,
            Markdown
        },
        props: {
            id: {
                type: String,
                required: true
            },
            title: {
                type: String,
                default: "",
            },
            description: {
                type: String,
                default: "",
            },
            modal: {
                type: Boolean,
                default: false,
            }
        },
        computed: {
            isModal() {
                return this.modal || this.description.indexOf("\n") > 0;
            }
        }
    };
</script>

<style lang="scss">
.markdown-tooltip {
    *:last-child {
        margin-bottom: 0;
    }
}
.text-inherited {
    color: unset;
}
</style>