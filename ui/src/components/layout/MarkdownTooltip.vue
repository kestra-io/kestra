<template>
    <span v-if="description">
        <help-circle title="" :id="'tooltip-desc-' + id" />
        <b-popover triggers="hover" :target="'tooltip-desc-' + id" placement="bottom" >
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
            description: {
                type: String,
                default: "",
            }
        },
        computed: {
        }
    };
</script>

<style lang="scss">
.markdown-tooltip {
    *:last-child {
        margin-bottom: 0;
    }
}
</style>