<template>
    <a
        href="https://kestra.io/slack?utm_source=app&utm_campaign=slack&utm_content=error"
        class="position-absolute slack-on-error el-button el-button--small is-text is-has-bg"
        target="_blank"
    >
        <Slack />
        <span>{{ $t("slack support") }}</span>
    </a>
    <span v-html="markdownRenderer" v-if="items.length === 0" />
    <ul>
        <li v-for="(item, index) in items" :key="index" class="font-monospace">
            <template v-if="item.path">
                At <code>{{ item.path }}</code>:
            </template>
            <span>{{ item.message }}</span>
        </li>
    </ul>
</template>

<script>
    import Slack from "vue-material-design-icons/Slack.vue";
    import * as Markdown from "../utils/markdown";

    export default {
        props: {
            message: {
                type: Object,
                required: true
            },
            items: {
                type: Array,
                required: true
            },
        },
        data() {
            return {
                markdownRenderer: undefined
            }
        },
        async created() {
            this.markdownRenderer = await this.renderMarkdown();
        },
        watch: {
            async source() {
                this.markdownRenderer = await this.renderMarkdown();
            }
        },
        components: {Slack},
        methods: {
            async renderMarkdown() {
                return await Markdown.render(this.message.message || this.message.content.message);
            },
        },
    };
</script>

<style lang="scss" scoped>
    ul {
        margin-top: 1rem;
        margin-bottom: 0;
        margin-left: -3rem;
    }

    li {
        font-size: 0.8rem;
        margin-top: .5rem;

    }
</style>