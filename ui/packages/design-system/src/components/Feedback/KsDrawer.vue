<template>
    <ElDrawer
        v-model="model"
        destroyOnClose
        lockScroll
        size=""
        :appendToBody="true"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        :class="{'full-screen': fullScreen}"
        @before-close="emit('before-close', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.header || props.title" #header>
            <span>
                {{ props.title }}
                <slot name="header" />
            </span>
            <KsButton link @click="toggleFullScreen">
                <Fullscreen class="full-screen" />
            </KsButton>
        </template>
        <template v-if="$slots.footer" #footer>
            <slot name="footer" />
        </template>
    </ElDrawer>
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import {ElDrawer} from "element-plus"
    import Fullscreen from "vue-material-design-icons/Fullscreen.vue"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<boolean>()

    const props = withDefaults(defineProps<{
        title?: string
        isFullScreen?: boolean
        withHeader?: boolean
    }>(), {
        title: undefined,
        isFullScreen: false,
        withHeader: true,
    })

    const emit = defineEmits<{
        "before-close": [done: () => void]
    }>()

    defineSlots<{
        default?(): unknown
        header?(): unknown
        footer?(): unknown
    }>()

    const fullScreen = ref(props.isFullScreen)

    const toggleFullScreen = () => {
        fullScreen.value = !fullScreen.value
    }

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/drawer';
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    .kel-drawer {

        .kel-drawer__header {
            .full-screen {
                > .material-design-icon__svg {
                    width: 1.5rem;
                    height: 1.5rem;
                    bottom: -0.250rem;
                }
            }
        }

        &.ltr,
        &.rtl {
            width: 70%;
            @include res(xs) {
                width: 95%;
            }

            @include res(md) {
                width: 70%;
            }

            @include res(lg) {
                width: 35%;
                min-width: 800px;
            }

            &.sm {
                min-width: auto;

                @include res(xs) {
                    width: 95%;
                }

                @include res(sm) {
                    width: 50%;
                }

                @include res(lg) {
                    width: 30%;
                }
            }
        }

        &.ttb,
        &.btt {
            height: 70%;
            @include res(xs) {
                height: 95%;
            }

            @include res(lg) {
                height: 50%;
            }

            @include res(lg) {
                height: 35%;
                min-height: 600px;
            }

            &.sm {
                height: 30%;
                min-width: auto;

            }
        }

        &.full-screen {
            width: 99% !important;
        }

        .kel-drawer__header {
            padding: 1rem;
            margin-bottom: 0;
            background-color: var(--ks-background-panel);
            border-bottom: 1px solid var(--ks-border-primary);
            color: var(--ks-content-primary);
            font-weight: bold;
            font-size: var(--ks-font-size-md);
        }
    }

</style>
