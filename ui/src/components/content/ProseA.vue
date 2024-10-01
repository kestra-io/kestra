<template>
    <component :is="linkType" v-bind="linkProps">
        <slot />
    </component>
</template>
<script>
    import {mapGetters} from "vuex";
    import path from "path-browserify";

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
                return this.href.startsWith("/") || /https?:\/\/.*/.test(this.href);
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
                        href: this.href.startsWith("/") ? "https://kestra.io" + this.href : this.href,
                        target: "_blank"
                    }
                }


                let relativeLink = this.href.replaceAll(/(\/|^)\d+?\.(?!\d)/g, "$1").replace(/(?:\/index)?\.md(#|$)/, "");
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
