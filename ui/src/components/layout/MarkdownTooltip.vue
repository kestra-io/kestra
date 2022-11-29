<template>
    <span v-if="description">
        <a @click="isOpen = true">
            <help-circle
                title=""
                :id="'tooltip-desc-' + id"
            />
        </a>

        <el-drawer
            v-if="isOpen"
            v-model="isOpen"
            :title="title"
            destroy-on-close
            :append-to-body="true"
        >
            <markdown class="markdown-tooltip" :source="description" />
        </el-drawer>
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
        data() {
            return {
                isOpen: false,
            };
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
            }
        },
    };
</script>

<style lang="scss" scoped>
a {
    cursor: pointer;
}

.markdown-tooltip {
    *:last-child {
        margin-bottom: 0;
    }
}
</style>