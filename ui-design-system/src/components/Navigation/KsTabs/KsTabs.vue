<script setup lang="ts">
    import {ElTabs, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        modelValue?: string
        type?: "" | "card" | "border-card"
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:modelValue": [name: string]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-tabs
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event as string)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-tabs>
</template>

<style lang="scss">
    .kel-tabs {
        .kel-tabs__active-bar {
            height: 4px;
            background-color: var(--ks-button-background-primary);
        }

        .kel-tabs__item {
            padding: 0;
            transition: all 0.3s ease;

            > * {
                padding: 1rem 1.5rem;

            }

            a {
                color: var(--ks-content-secondary);
                transition: 0.3s ease;
            }

            &.is-active > * {
                background-color: var(--ks-button-background-primary);
                color: var(--ks-button-content-primary);
            }

            &.is-disabled a {
                color: var(--ks-content-inactive) !important;
            }
        }

        .kel-tabs__nav-wrap::after {
            height: 1px;
            background-color: var(--ks-border-primary);
        }

        html.dark & {
            .kel-tabs__active-bar {
                background-color: var(--ks-button-background-secondary-hover);
            }

            .kel-tabs__item {
                &.is-active > * {
                    color: var(--ks-content-secondary);
                }
            }
        }


        &.top {
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
                > * {
                    padding: .5rem 1rem;
                }

                a:hover{
                    color: var(--ks-content-link);
                }

                &.is-active > a {
                    background: var(--ks-button-background-secondary-hover);
                    color: var(--ks-content-link);
                    border-radius: var(--kel-border-radius-base);
                }
            }

        }

        &.kel-tabs--card {
            margin-top: 32px;

            .kel-tabs__nav-wrap{
                margin-bottom: 1px;
            }

            & > .kel-tabs__header .kel-tabs__nav{
                background-color: var(--ks-background-card);
                border-bottom: 1px solid var(--ks-border-inactive);
                gap: 2px;

                .kel-tabs__item{
                    padding: 0 !important;
                    border: none;
                    &:first-child a{
                        margin-left: 1px;
                        border-top-left-radius: 3px;
                    }
                    &:last-child a{
                        border-top-right-radius: 3px;
                    }
                    a{
                        padding-top: .5rem;
                        padding-bottom: .5rem;
                        font-weight: normal!important;
                        color: var(--ks-content-primary);
                        &:hover{
                            // create an outline without cutting the rounded corners
                            box-shadow: 0 0 0 1px var(--ks-border-active);
                        }
                    }
                    &.is-active a{
                        background-color: var(--ks-background-body);
                        color: var(--ks-content-link);
                        position: relative;
                        z-index: 1;
                        // create an outline without cutting the rounded corners
                        box-shadow: 0 0 0 1px var(--ks-border-active);
                    }
                }
            }
        }
    }
</style>