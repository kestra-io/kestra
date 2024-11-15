<template>
    <component :is="linkType" v-bind="linkProps">
        <slot />
    </component>
</template>
<script>
    import {mapGetters} from "vuex";
    import path from "path-browserify";
    import {normalizeDocsPath, normalizeRemoteHref, isRemoteLink} from "./utils";

    export default {
        props: {
            href: {
                type: String,
                default: ""
            },
            target: {
                type: String,
                default: undefined,
                required: false
            }
        },
        computed: {
            ...mapGetters("doc", ["pageMetadata"]),
            isRemoteLink() {
                return isRemoteLink(this.href);
            },
            linkType() {
                if (this.isRemoteLink) {
                    return "a";
                }
                return "router-link";
            },
            linkProps() {
                if (this.isRemoteLink) {
                    return {
                        href: normalizeRemoteHref(this.href),
                        target: "_blank"
                    }
                }


                let relativeLink = normalizeDocsPath(this.href);
                if (this.pageMetadata.isIndex === false) {
                    relativeLink = "../" + relativeLink;
                }
                const to = path.normalize(this.append(this.$route.path, relativeLink));
                return {
                    to
                }
            }
        }
    }
</script>
