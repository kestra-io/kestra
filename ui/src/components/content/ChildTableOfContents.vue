<script>
    import {h, defineComponent} from "vue";
    import {useStore} from "vuex";
    import {RouterLink, useRoute} from "vue-router";

    export default defineComponent({
        props: {
            pageUrl: {
                type: String,
                default: undefined
            },
            max: {
                type: Number,
                default: undefined
            },
        },
        async setup(props) {
            const store = useStore();
            const route = useRoute();

            let currentPage;
            if (props.pageUrl) {
                currentPage = props.pageUrl;
            } else {
                currentPage = route.params.path;
            }

            currentPage = currentPage.endsWith("/") ? currentPage.slice(0, -1) : currentPage;

            let childrenWithMetadata = await store.dispatch("doc/children", currentPage);
            childrenWithMetadata = Object.fromEntries(Object.entries(childrenWithMetadata).map(([url, metadata]) => [url, {...metadata, path: url}]));
            Object.entries(childrenWithMetadata)
                .forEach(([url, metadata]) => {
                    const split = url.split("/");
                    const parentUrl = split.slice(0, split.length - 1).join("/");
                    const parent = childrenWithMetadata[parentUrl];
                    if (parent !== undefined) {
                        parent.children = [...(parent.children ?? []), metadata];
                    }
                });

            const dir = Object.entries(childrenWithMetadata)[0]?.[1]?.children;
            return {dir};
        },

        render(ctx) {
            const {dir, max} = ctx;

            const renderLink = (link) => h(RouterLink, {to: {path: "/" + link.path}}, () => link.title);

            const renderLinks = (data, level) => {
                return h(
                    "ul",
                    level ? {"data-level": level} : null,
                    (data || []).map((link) => {
                        if (link.children &&
                            (max === undefined || max <= level) &&
                            (link.children.length > 1 || link.children.length === 1 && link.children[0].path !== link.path)
                        ) {
                            return h("li", null, [renderLink(link), renderLinks(link.children, level + 1)]);
                        }

                        return h("li", null, renderLink(link));
                    })
                );
            };

            const defaultNode = (data) => renderLinks(data, 0);

            return this.$slots?.default ? this.$slots.default({dir, ...this.$attrs}) : defaultNode(dir);
        }
    });
</script>
