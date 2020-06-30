<template>
    <div>
        <span v-html="markdownRenderer"></span>
    </div>
</template>

<script>
    import markdownIt from 'markdown-it'
    import mark from 'markdown-it-mark'
    import meta from 'markdown-it-meta'

    import Prism from "prismjs";
    import "prismjs/themes/prism-okaidia.css";
    import 'prismjs/components/prism-yaml.min';

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
                let md = new markdownIt()
                    .use(mark)
                    .use(meta)

                md.set({
                    html: true,
                    xhtmlOut: true,
                    breaks: true,
                    linkify: true,
                    typographer: true,
                    langPrefix: 'language-',
                    quotes: '“”‘’',
                })

                md.renderer.rules.table_open = () => `<table class="${this.tableClass}">\n`

                let defaultLinkRenderer = md.renderer.rules.link_open ||
                    function (tokens, idx, options, env, self) {
                        return self.renderToken(tokens, idx, options)
                    }

                md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
                    Object.keys(this.anchorAttributes).map((attribute) => {
                        let aIndex = tokens[idx].attrIndex(attribute)
                        let value = this.anchorAttributes[attribute]
                        if (aIndex < 0) {
                            tokens[idx].attrPush([attribute, value]) // add new attribute
                        } else {
                            tokens[idx].attrs[aIndex][1] = value
                        }
                    })
                    return defaultLinkRenderer(tokens, idx, options, env, self)
                }

                let outHtml = md.render(
                    this.source
                );

                this.$emit('rendered', outHtml)

                this.$nextTick(() => {
                    Prism.highlightAll();
                });

                return outHtml;
            },
        },

    };
</script>
