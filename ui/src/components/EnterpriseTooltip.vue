<template>
    <el-tooltip :persistent="false" :focus-on-show="true" popper-class="ee-tooltip" :disabled="!disabled" :placement="placement">
        <template #content v-if="link">
            <p>{{ $t("ee-tooltip.features-blocked") }}</p>

            <a
                class="el-button el-button--primary d-block"
                type="primary"
                :href="link"
                target="_blank"
            >
                Talk to us
            </a>
        </template>
        <template #default>
            <span ref="slot-container">
                <slot />
                <lock v-if="disabled" />
            </span>
        </template>
    </el-tooltip>
</template>

<script>
    import Lock from "vue-material-design-icons/Lock.vue";

    export default {
        components: {Lock},
        props: {
            top: {
                type: Boolean,
                default: true
            },
            placement: {
                type: String,
                default: "auto"
            },
            disabled: {
                type: Boolean,
                default: false
            },
            content: {
                type: String,
                default: undefined
            },
            term: {
                type: String,
                default: undefined
            },
        },
        computed: {
            link() {
                let link = "https://kestra.io/demo?utm_source=app&utm_content=ee-tooltip&utm_term=" + this.term;

                if (this.content) {
                    link = link + "&utm_content=" + this.content;
                }

                return link;
            }
        }
    };
</script>

<style lang="scss" scoped>
    :global(.el-popper.ee-tooltip) {
        max-width: 320px;
        padding: calc(var(--spacer) * 2);
    }

    p {
        font-size: var(--font-size-lg);
        text-align: center;
        margin-bottom: calc(var(--spacer) * 2);
        font-weight: bold;
    }

    :deep(.material-design-icon) > .material-design-icon__svg {
        bottom: -0.125em;
    }
</style>

