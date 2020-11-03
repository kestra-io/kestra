<template>
    <span v-if="description">
        <help-circle title="" :id="'tooltip-desc-' + id" />
        <b-popover triggers="hover" :target="'tooltip-desc-' + id" placement="bottom">
            <markdown :source="description"></markdown>
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
                default: ``,
            }
        },
        computed: {
        }
    };
</script>
