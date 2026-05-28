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
            background-color: var(--ks-btn-primary-bg-default);
        }

        .kel-tabs__item {
            padding: 1rem 1.5rem !important;
            transition: color 0.3s ease;
            color: var(--ks-text-secondary);

            &:hover {
                color: var(--ks-text-link);
            }

            &.is-disabled {
                color: var(--ks-text-inactive) !important;
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
                    background-color: var(--ks-btn-primary-bg-default);
                    color: var(--ks-btn-primary-text);
                }
            }
        }

        &.kel-tabs--box {
            position: sticky;
            z-index: 1000;

            .kel-tabs__active-bar {
                display: none;
            }

            .kel-tabs__nav-wrap {
                background: var(--ks-bg-input);
                border-bottom: var(--ks-border-width-thin) solid var(--ks-border-subtle);

                &::after {
                    display: none;
                }
            }

            .kel-tabs__nav {
                gap: var(--ks-spacing-1);
                padding: var(--ks-spacing-2);
            }

            .kel-tabs__header {
                margin-bottom: 0;
            }

            .kel-tabs__nav-prev {
                &:after {
                    content: '';
                    position: absolute;
                    top: 0;
                    right: -10px;
                    height: 100%;
                    width: 10px;
                    background: linear-gradient(90deg, var(--ks-bg-input) 0%, rgba(0, 0, 0, 0) 100%);
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
                    background: linear-gradient(-90deg, var(--ks-bg-input) 0%, rgba(0, 0, 0, 0) 100%);
                    z-index: calc(var(--kel-index-normal) + 2);
                }
            }

            .kel-tabs__item {
                min-width: 45px;
                max-width: fit-content;
                height: 1.75rem;
                padding: var(--ks-spacing-1) var(--ks-spacing-2) !important;
                font-size: var(--ks-font-size-xs);
                color: var(--ks-text-secondary);
                border: var(--ks-border-width-thin) solid transparent;
                border-radius: var(--ks-radius-sm);
                transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;

                &:hover,
                &.is-active {
                    background: var(--ks-btn-secondary-bg-active);
                    color: var(--ks-text-primary);
                    border-color: var(--ks-btn-secondary-border-active);
                }

                &.is-disabled {
                    background: transparent;
                    border-color: transparent;
                }
            }
        }
    }

</style>
