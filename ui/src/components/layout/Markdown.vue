<template>
    <div>
        <span ref="doc" v-html="markdownRenderer"></span>
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
        mounted() {
            setTimeout(() => {
                window.scrollTo({
                    top: document.querySelector(location.hash).getBoundingClientRect().top + window.pageYOffset,
                    behavior: 'smooth'
                })
            }, 1000)
        },
        methods: {
            computeAnchors() {
                let lastTitle = ''
                for (const child of this.$refs.doc.children) {
                    if (child.tagName === 'H2') {
                        lastTitle = child.textContent.replaceAll('.', '-')
                        child.setAttribute('id', lastTitle)
                        child.innerHTML = `<a href="#${lastTitle}">${child.innerHTML}</a>`
                    }
                    if (child.tagName === 'H3') {
                        const childId = `${lastTitle}-${child.textContent}`.replaceAll('.', '-')
                        child.setAttribute('id', childId)
                        child.innerHTML = `<a href="#${childId}">${child.innerHTML}</a>`
                    }
                }
            }
        },
        computed: {
            markdownRenderer() {
                const outHtml = Markdown.render(this.source);

                this.$emit('rendered', outHtml)

                this.$nextTick(() => {
                    Prism.highlightAll();
                    this.computeAnchors()
                });

                return outHtml;
            },
        },

    };
</script>
<style scoped>
span /deep/ h3 a {
    color:#e83e8c;
}
span /deep/ h2 a {
    color:#222;
}
</style>
