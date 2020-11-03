<template>
    <div>
        <span v-html="markdownRenderer"></span>
    </div>
</template>

<script>
    import Prism from "prismjs";
    import "prismjs/themes/prism-okaidia.css";
    import 'prismjs/components/prism-yaml.min';
    import Markdown from "../../utils/markdown";

    export default {
        props: {
            watches: {
                type: Array,
                default: () => ['source', 'show', 'toc'],
            },
            source: {
                type: String,
                default: ``,
            },
        },

        computed: {
            markdownRenderer() {
                const outHtml = Markdown.render(this.source);

                this.$emit('rendered', outHtml)

                this.$nextTick(() => {
                    Prism.highlightAll();
                });

                return outHtml;
            },
        },

    };
</script>
