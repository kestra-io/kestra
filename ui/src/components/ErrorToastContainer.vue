<template>
    <a
        href="https://kestra.io/slack?utm_source=app&utm_content=error"
        class="position-absolute slack-on-error el-button el-button--small is-text is-has-bg"
        target="_blank"
    >
        <Slack />
        <span>{{ $t("slack support") }}</span>
    </a>
    <span v-html="markdownRenderer" v-if="this.items.length === 0" />
    <ul>
        <li v-for="(item, index) in this.items" :key="index" class="font-monospace">
            <template v-if="item.path">
                At <code>{{ item.path }}</code>:
            </template>
            <span>{{ item.message }}</span>
        </li>
    </ul>
</template>

<script>
    import Slack from "vue-material-design-icons/Slack.vue";
    import Markdown from "../utils/markdown";

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
        margin-top: calc(var(--spacer) * 1);
        margin-bottom: 0;
        margin-left: calc(var(--spacer) * -3);
    }

    li {
        font-size: 0.8rem;
        margin-top: calc(var(--spacer) * 0.5);

    }
</style>