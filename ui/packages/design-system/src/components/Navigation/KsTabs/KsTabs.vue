<template>
    <ElTabs
        v-model="model"
        :type="type"
        :class="{'kel-tabs--box': props.type === 'box'}"
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElTabs>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {ElTabs} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<string>()

    const props = defineProps<{
        type?: "" | "card" | "border-card" | "box"
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const type = computed(() =>
        (props.type === "box" ? "" : props.type),
    )

    const filteredProps = useFilteredProps(props, ["type"])
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/tabs';

    .kel-tabs {
        a {
            color: currentColor;
        }

        .kel-tabs__active-bar {
            height: 4px;
            background-color: var(--ks-button-background-primary);
        }

        .kel-tabs__item {
            padding: 1rem 1.5rem !important;
            transition: all 0.3s ease;
            color: var(--ks-content-secondary);

            &:hover {
                color: var(--ks-content-link-hover);
            }

            &.is-disabled {
                color: var(--ks-content-inactive) !important;
            }
        }

        &.kel-tabs--card {
            .kel-tabs__item {
                &:first-child {
                    border-radius: 4px 0 0 0;
                }

                &:last-child {
                    border-radius: 0 4px 0 0;
                }

                &.is-active {
                    background-color: var(--ks-button-background-primary);
                    color: var(--ks-button-content-primary);
                }
            }
        }

        &.kel-tabs--box {
            background: var(--ks-background-card);
            border-bottom: 1px solid var(--ks-border-primary);
            padding: .5rem;
            position: sticky;
            top: var(--top-navbar-height);
            z-index: 1000;

            .kel-tabs__active-bar {
                display: none;
            }

            .kel-tabs__nav-wrap::after {
                display: none;
            }

            .kel-tabs__header {
                margin-bottom: 0;
            }

            .kel-tabs__nav-scroll {
                padding: 0 15px;
            }

            .kel-tabs__nav-prev {
                &:after {
                    content: '';
                    position: absolute;
                    top: 0;
                    right: -10px;
                    height: 100%;
                    width: 10px;
                    background: linear-gradient(90deg, var(--ks-background-card) 0%, rgba(0, 0, 0, 0) 100%);
                    z-index: calc(var(--kel-index-normal) + 2);
                }
            }

            .kel-tabs__nav-next {
                &:before {
                    content: '';
                    position: absolute;
                    top: 0;
                    left: -15px;
                    height: 100%;
                    width: 15px;
                    background: linear-gradient(-90deg, var(--ks-background-card) 0%, rgba(0, 0, 0, 0) 100%);
                    z-index: calc(var(--kel-index-normal) + 2);
                }
            }

            .kel-tabs__item {
                padding: .5rem 1rem !important;

                &.is-active {
                    background: var(--ks-button-background-secondary-hover);
                    color: var(--ks-content-link);
                    border-radius: var(--kel-border-radius-base);
                }
            }
        }
    }

</style>
