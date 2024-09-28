<template>
    <component :is="linkType" v-bind="linkProps">
        <slot />
    </component>
</template>
<script>
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

                return {
                    to: this.append(this.$route.path, this.href.replaceAll(/\d+\.([^/]*)/g, "$1").replace(/\.md$/, ""))
                }
            }
        }
    }
</script>
