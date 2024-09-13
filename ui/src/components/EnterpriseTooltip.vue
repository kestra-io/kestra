<template>
    <el-tooltip
        data-component="FILENAME_PLACEHOLDER"
        :visible="visible"
        :persistent="false"
        :focus-on-show="true"
        popper-class="ee-tooltip"
        :disabled="!disabled"
        :placement="placement"
    >
        <template #content v-if="link">
            <el-button circle class="ee-tooltip-close" @click="changeVisibility(false)">
                <Close />
            </el-button>

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
            <span ref="slot-container" class="cursor-pointer" @click="changeVisibility()">
                <slot />
                <lock v-if="disabled" />
            </span>
        </template>
    </el-tooltip>
</template>

<script>
    import Close from "vue-material-design-icons/Close.vue";
    import Lock from "vue-material-design-icons/Lock.vue";

    export default {
        components: {Close, Lock},
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
        data() {
            return {
                visible: false,
            }
        },
        methods: {
            changeVisibility(visible = true) {
                if (visible) document.querySelector(".ee-tooltip")?.remove();
                this.visible = visible
            }
        },
        computed: {
            link() {

                let link = "https://kestra.io/demo?utm_source=app&utm_content=ee-tooltip";

                if (this.term) {
                    link = link + "&utm_term=" + this.term;
                }

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

    .ee-tooltip-close {
            position: absolute;
            top: 0;
            right: 0;
            border: none;
            margin: 0.5rem;
        }
</style>

