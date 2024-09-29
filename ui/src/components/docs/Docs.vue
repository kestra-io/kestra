<template>
    <top-nav-bar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb" />
    <div class="d-flex full-height">
        <Toc />
        <MDCRenderer class="flex-grow-1 content" v-if="ast?.body" :body="ast.body" :data="ast.data" :key="ast" :components="proseComponents" />
    </div>
</template>

<script>
    import useMarkdownParser from "@kestra-io/ui-libs/src/composables/useMarkdownParser";
    import MDCRenderer from "@kestra-io/ui-libs/src/components/content/MDCRenderer.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import {mapGetters} from "vuex";
    import Toc from "./Toc.vue";
    import {getCurrentInstance} from "vue";
    import path from "path-browserify";

    const parse = useMarkdownParser();

    export default {
        computed: {
            ...mapGetters("doc", ["pageMetadata"]),
            path() {
                let routePath = this.$route.params.path;
                if (routePath === "") {
                    return undefined;
                }
                return path.replaceAll(/(?:^|\/)\.\//g,"");
            },
            pathParts() {
                return this.path?.split("/") ?? [];
            },
            routeInfo() {
                return {
                    title: this.pageMetadata?.title ?? this.$t("docs"),
                    breadcrumb: [
                        {
                            label: this.$t("docs"),
                            link: {
                                name: "docs/view"
                            }
                        },
                        ...(this.pathParts.map((part, index) => {
                            return {
                                label: part,
                                link: {
                                    name: "docs/view",
                                    params: {
                                        path: this.pathParts.slice(0, index + 1).join("/")
                                    }
                                }
                            }
                        }))
                    ]
                };
            }
        },
        components: {Toc, TopNavBar, MDCRenderer},
        data() {
            return {
                ast: undefined,
                proseComponents: Object.fromEntries(
                    Object.keys(getCurrentInstance().appContext.components).filter(componentName => componentName.startsWith("Prose"))
                        .map(name => name.substring(5).replaceAll(/(.)([A-Z])/g, "$1-$2").toLowerCase())
                        .map(name => [name, "prose-" + name])
                )
            };
        },
        watch: {
            "$route.params.path": {
                async handler() {
                    const response = await this.$store.dispatch("doc/fetchResource", `docs${this.path === undefined ? "" : `/${this.path}`}`);
                    await this.$store.commit("doc/setPageMetadata", response.metadata);
                    let content = response.content;
                    if (!("canShare" in navigator)) {
                        content = content.replaceAll(/\s*web-share\s*/g, "");
                    }
                    this.ast = await parse(content);
                },
                immediate: true
            }
        }
    };
</script>

<style lang="scss" scoped>
    @import "@kestra-io/ui-libs/src/scss/variables";

    .content {
        margin: 64px 200px;

        #{--bs-link-color}: #8405FF;
        #{--bs-link-color-rgb}: to-rgb(#8405FF);

        html.dark & {
            #{--bs-link-color}: #BBBBFF;
            #{--bs-link-color-rgb}: to-rgb(#BBBBFF);
        }

        :deep(> h2) {
            font-weight: 600;
            border-top: 1px solid var(--bs-border-color);
            margin-bottom: 2rem;
            margin-top: 4.12rem;
            padding-top: 3.125rem;

            > a {
                border-left: 5px solid #9ca1de;
                font-size: 1.87rem;
                padding-left: .6rem;
            }
        }

        :deep(> h3) {
            padding-top: 1.25rem;
        }

        :deep(.btn:hover span) {
            color: var(--bs-body-color);
        }

        :deep(a[target=_blank]:after) {
            background-color: currentcolor;
            content: "";
            display: inline-block;
            height: 15px;
            margin-left: 1px;
            -webkit-mask: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' aria-hidden='true' focusable='false' x='0px' y='0px' viewBox='0 0 100 100' width='15' height='15' class='icon outbound'><path fill='currentColor' d='M18.8,85.1h56l0,0c2.2,0,4-1.8,4-4v-32h-8v28h-48v-48h28v-8h-32l0,0c-2.2,0-4,1.8-4,4v56C14.8,83.3,16.6,85.1,18.8,85.1z'></path> <polygon fill='currentColor' points='45.7,48.7 51.3,54.3 77.2,28.5 77.2,37.2 85.2,37.2 85.2,14.9 62.8,14.9 62.8,22.9 71.5,22.9'></polygon></svg>");
            mask: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' aria-hidden='true' focusable='false' x='0px' y='0px' viewBox='0 0 100 100' width='15' height='15' class='icon outbound'><path fill='currentColor' d='M18.8,85.1h56l0,0c2.2,0,4-1.8,4-4v-32h-8v28h-48v-48h28v-8h-32l0,0c-2.2,0-4,1.8-4,4v56C14.8,83.3,16.6,85.1,18.8,85.1z'></path> <polygon fill='currentColor' points='45.7,48.7 51.3,54.3 77.2,28.5 77.2,37.2 85.2,37.2 85.2,14.9 62.8,14.9 62.8,22.9 71.5,22.9'></polygon></svg>");
            vertical-align: baseline;
            width: 15px;
        }

        :deep(.code-block) {
            background-color: var(--bs-card-bg);
            border: 1px solid var(--bs-border-color);

            .language {
                color: var(--bs-tertiary-color);
            }
        }

        :deep(code) {
            white-space: pre;

            &:not(.code-block code) {
                font-weight: 700;
                background: var(--bs-body-bg);
                color: var(--bs-body-color);
                border: 1px solid var(--border-killing)
            }
        }

        :deep(code .line) {
            display: block;
            min-height: 1rem;
        }

        :deep(p > a) {
            text-decoration: underline;
        }

        :deep(blockquote) {
            border-left: 4px solid #8997bd;
            font-size: 1rem;
            padding-left: 1rem;

            > p {
                color: var(--bs-body-color);
            }
        }

        :deep(.card-group) {
            justify-content: space-around;
        }

        :deep(.card-group > a), :deep(> h2 > a), :deep(> h3 > a) {
            color: var(--bs-body-color);
        }

        :deep(li > a) {
            text-decoration: none !important;
        }

        :deep(.video-container) {
            position: relative;
            margin: calc(var(--spacer) * 2) 0;
            padding-top: 35.25%;
            background-color: var(--bs-gray-100);
            height: 28.351rem;
            border-radius: calc($spacer / 2);
            border: 1px solid var(--bs-border-secondary-color);

            @media only screen and (max-width: 1919px) {
                padding-top: 56.25%;
                height: auto;
                background-color: transparent;
            }

            iframe {
                margin: auto;
                max-width: 43.7rem;
                max-height: 24.351rem;
                position: absolute;
                top: 0;
                left: 0;
                bottom: 0;
                right: 0;
                width: 100%;
                height: 100%;
            }
        }

        :deep(.card) {
            --bs-card-spacer-y: 1rem;
            --bs-card-spacer-x: 1rem;
            border: 1px solid var(--bs-border-color);
            color: var(--bs-body-color);
            display: flex;
            flex-direction: column;
            min-width: 0;
            position: relative;
            word-wrap: break-word;
            background-clip: border-box;
            background-color: var(--bs-card-bg);
            border-radius: var(--bs-border-radius-lg);

            .card-body {
                color: var(--bs-card-color);
                flex: 1 1 auto;
                padding: var(--bs-card-spacer-y) var(--bs-card-spacer-x);
                gap: 1rem;
            }
        }

        :deep(hr) {
            &:has(+ .card-group), &:has(+ .alert) {
                opacity: 0;
            }

            &:has(+ h2)  {
                display: none;
            }
        }

        :deep(p) {
            line-height: 1.75rem;
        }

        :deep(.material-design-icon) {
            bottom: -0.125em;
        }

        :deep(.show-button) > .material-design-icon.icon-2x {
            &, & > .material-design-icon__svg {
                height: 1em;
                width: 1em;
            }
        }

        :deep(.doc-alert) {
            border: 1px solid var(--border-information);
            border-radius: 4px;
            color: var(--content-information);
            background: var(--background-information);
        }
    }
</style>