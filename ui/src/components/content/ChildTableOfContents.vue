<script lang="ts">
    import {h, defineComponent, VNode} from "vue";
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
            renderLink: {
                type: Function,
                default: (link:{
                    path:string,
                    title:string
                }) => h(RouterLink, {to: {path: "/" + link.path}}, () => link.title)
            },
        },
        async setup(props, ctx) {
            const store = useStore();
            const route = useRoute();

            let currentPage;
            if (props.pageUrl) {
                currentPage = props.pageUrl;
            } else {
                currentPage = route.params.path;
            }

            currentPage = typeof currentPage === "string" && currentPage?.endsWith("/") ? currentPage.slice(0, -1) : currentPage;

            let childrenWithMetadata = await store.dispatch("doc/children", currentPage) as Record<string, any>;
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

            interface Child {
                children?: Child[];
                path?: string;
            }

            const renderLinks = (data: Child[], level: number): VNode => {
                return h(
                    "ul",
                    level ? {"data-level": level} : null,
                    (data || []).map((link: Child) => {
                        if (link.children &&
                            (props.max === undefined || props.max <= level) &&
                            (link.children.length > 1 || link.children.length === 1 && link.children[0].path !== link.path)
                        ) {
                            return h("li", null, [props.renderLink(link), renderLinks(link.children, level + 1)]);
                        }

                        return h("li", null, props.renderLink(link));
                    })
                );
            };

            const defaultNode = (data:Child[]) => renderLinks(data, 0);

            return () => ctx.slots?.default ? ctx.slots.default({dir, ...ctx.attrs}) : defaultNode(dir);
        },
    });
</script>
