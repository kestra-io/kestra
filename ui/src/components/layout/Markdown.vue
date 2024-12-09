<template>
    <div data-component="FILENAME_PLACEHOLDER">
        <span class="markdown" v-html="markdownRenderer" />
    </div>
</template>

<script>
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
            fontSizeVar: {
                type: String,
                default: "font-size-sm"
            }
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
        methods: {
            async renderMarkdown() {
                return  await Markdown.render(this.source, {
                    permalink: this.permalink,
                });
            },
        },
        computed: {
            fontSizeCss() {
                return `var(--${this.fontSizeVar})`;
            },
            permalinkCss() {
                return this.permalink ? "-20px" : "0";
            }
        },
    };
</script>

<style lang="scss">
    .markdown {
        font-size: v-bind(fontSizeCss);

        table {
            border-collapse: collapse;
            width: 100%;
            color: var(--bs-body-color);
        }

        table,
        th {
            border-bottom: 2px solid var(--bs-border-color);
        }

        th,
        td {
            padding: 0.5em;
        }

        th {
            text-align: left;
        }

        a.header-anchor {
            color: var(--bs-gray-600);
            font-size: var(--font-size-base);
            font-weight: normal;
        }

        .warning {
            background-color: var(--el-color-warning-light-9);
            border: 1px solid var(--el-color-warning-light-5);
            padding: 8px 16px;
            color: var(--el-color-warning);
            border-radius: var(--el-border-radius-base);
            margin-bottom: var(--spacer);

            p:last-child {
                margin-bottom: 0;
            }
        }

        .info {
            background-color: var(--el-color-info-light-9);
            border: 1px solid var(--el-color-info-light-5);
            padding: 8px 16px;
            color: var(--el-color-info);
            border-radius: var(--el-border-radius-base);
            margin-bottom: var(--spacer);

            p:last-child {
                margin-bottom: 0;
            }
        }

        pre {
            border-radius: var(--bs-border-radius-lg);
            border: 1px solid var(--bs-border-color);
        }

        blockquote {
            margin-top: 0;
        }

        mark {
            background: var(--bs-success);
            color: var(--bs-white);
            font-size: var(--font-size-sm);
            padding: 2px 8px 2px 8px;
            border-radius: var(--bs-border-radius-sm);

            * {
                color: var(--bs-white) !important;
            }
        }

        h2 {
            margin-top: calc(var(--spacer) * 2);
        }

        h3 {
            margin-top: calc(var(--spacer) * 1.5);
        }

        h4 {
            margin-top: calc(var(--spacer) * 1.25);
        }

        h2, h3, h4, h5 {
            margin-left: v-bind(permalinkCss);

            .header-anchor {
                opacity: 0;
                transition: all ease 0.2s;
            }

            &:hover {
                .header-anchor {
                    opacity: 1;
                }
            }
            padding: 5px ;
            border-left: 4px solid var(--bs-border-color);
        }

        strong > code,
        li > code,
        td > code,
        p > code{
            border-radius: var(--bs-border-radius-sm);
            border: 1px solid var(--bs-border-color);
            color: var(--bs-body-color);
        }

        h3, h4, h5 {
            code {
                background: var(--bs-white);
                font-size: 0.65em;
                padding: 2px 8px;
                font-weight: 400;
                border-radius: var(--bs-border-radius-sm);
                border: 1px solid var(--bs-border-color);
                color: var(--bs-body-color);

                html.dark & {
                    background: var(--bs-gray-100);
                }
            }
        }
    }
    .markdown-tooltip {
        *:last-child {
            margin-bottom: 0;
        }
        line-height: 15px;
        padding: 5px;
    }
</style>
