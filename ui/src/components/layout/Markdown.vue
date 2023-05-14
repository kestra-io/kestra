<template>
    <div>
        <span class="markdown" v-html="markdownRenderer" />
    </div>
</template>

<script>
    import Prism from "prismjs";
    import "prismjs/themes/prism-okaidia.css";
    import "prismjs/components/prism-yaml.min";
    import Markdown from "../../utils/markdown";

    export default {
        props: {
            watches: {
                type: Array,
                default: () => ["source", "show", "toc"],
            },
            source: {
                type: String,
                default: "",
            },
            permalink: {
                type: Boolean,
                default: false,
            },
        },
        emits: ["rendered"],
        computed: {
            markdownRenderer() {
                const outHtml = Markdown.render(this.source, {
                    permalink: this.permalink,
                });

                this.$emit("rendered", outHtml);

                // eslint-disable-next-line vue/no-async-in-computed-properties
                this.$nextTick(() => {
                    Prism.highlightAll();
                });

                return outHtml;
            },
        },
    };
</script>

<style lang="scss">
    .markdown {
        font-size: var(--font-size-sm);

        a.header-anchor {
            color: var(--bs-gray-600);
            font-size: var(--font-size-md);
            font-weight: normal;
        }

        .warning {
            background-color: var(--el-color-warning-light-9);
            border: 1px solid var(--el-color-warning-light-5);
            padding: 8px 16px;
            color: var(--el-color-warning);
            border-radius: var(--el-border-radius-base);

            p:last-child {
                margin-bottom: 0;
            }
        }
    }

    .markdown-tooltip {
        *:last-child {
            margin-bottom: 0;
        }
    }
</style>
