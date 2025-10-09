<script lang="ts">
    import {h, defineComponent} from "vue";
    import {useDocStore} from "../../stores/doc";
    import {RouterLink, useRoute} from "vue-router";

    interface DataItem {children: any[], path: string}


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
            const docStore = useDocStore();
            const route = useRoute();

            let currentPage;
            if (props.pageUrl) {
                currentPage = props.pageUrl;
            } else {
                currentPage = route.params.path.toString();
            }

            currentPage = currentPage?.endsWith("/") ? currentPage.slice(0, -1) : currentPage;

            let childrenWithMetadata = await docStore.children(currentPage) as Record<string, any>;
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

            const renderLinks = (data: DataItem[], level: number) => {
                return h(
                    "ul",
                    level ? {"data-level": level} : null,
                    (data || []).map((link):any => {
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

            const defaultNode = (data: DataItem[]) => renderLinks(data, 0);

            return () => ctx.slots?.default ? ctx.slots.default({dir, ...ctx.attrs}) : defaultNode(dir);
        },
    });
</script>
