<template>
    <div>
        <span v-html="markdownRenderer" />
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
@import "../../styles/_variable.scss";
.markdown {
    a.header-anchor {
        color: var(--gray-600);
        font-size: $font-size-base;
        font-weight: normal;
    }
}
</style>
