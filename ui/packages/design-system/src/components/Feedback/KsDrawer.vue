<template>
    <ElDrawer
        v-model="model"
        destroyOnClose
        lockScroll
        :size="resizable ? drawerSize : ''"
        :appendToBody="true"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        :class="{'full-screen': fullScreen && !resizable, 'is-resizable': resizable}"
        @before-close="emit('before-close', $event)"
    >
        <template v-if="$slots.default || resizable" #default>
            <div
                v-if="resizable"
                class="kel-drawer__resize-handle"
                @mousedown.prevent="startResize"
            />
            <slot />
        </template>
        <template v-if="$slots.header || props.title" #header>
            <span>
                {{ props.title }}
                <slot name="header" />
            </span>
            <KsButton link @click="toggleFullScreen">
                <ArrowCollapse v-if="fullScreen" class="full-screen" />
                <ArrowExpand v-else class="full-screen" />
            </KsButton>
        </template>
        <template v-if="$slots.footer" #footer>
            <slot name="footer" />
        </template>
    </ElDrawer>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import {ElDrawer} from "element-plus"
    import ArrowExpand from "vue-material-design-icons/ArrowExpand.vue"
    import ArrowCollapse from "vue-material-design-icons/ArrowCollapse.vue"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<boolean>()

    const props = withDefaults(defineProps<{
        title?: string
        isFullScreen?: boolean
        withHeader?: boolean
        resizable?: boolean
    }>(), {
        title: undefined,
        isFullScreen: false,
        withHeader: true,
        resizable: false,
    })

    const emit = defineEmits<{
        "before-close": [done: () => void]
    }>()

    defineSlots<{
        default?(): unknown
        header?(): unknown
        footer?(): unknown
    }>()

    const MIN_DRAWER_WIDTH = 360
    const FULLSCREEN_THRESHOLD = 0.95
    const maxDrawerWidth = () => window.innerWidth - 16

    const fullScreenToggle = ref(props.isFullScreen)
    const drawerWidth = ref<number | null>(null)

    const resizableFull = computed(() =>
        props.resizable && drawerWidth.value != null && drawerWidth.value >= window.innerWidth * FULLSCREEN_THRESHOLD,
    )
    const fullScreen = computed(() => (props.resizable ? resizableFull.value : fullScreenToggle.value))

    const drawerSize = computed(() => (drawerWidth.value != null ? `${drawerWidth.value}px` : "65%"))

    const toggleFullScreen = () => {
        if (props.resizable) {
            drawerWidth.value = resizableFull.value ? null : maxDrawerWidth()
        } else {
            fullScreenToggle.value = !fullScreenToggle.value
        }
    }

    const startResize = (event: MouseEvent) => {
        const panel = (event.target as HTMLElement).closest(".kel-drawer") as HTMLElement | null
        if (!panel) return

        const onMove = (move: MouseEvent) => {
            const raw = panel.getBoundingClientRect().right - move.clientX
            drawerWidth.value = Math.min(Math.max(raw, MIN_DRAWER_WIDTH), maxDrawerWidth())
        }
        const onUp = () => {
            document.removeEventListener("mousemove", onMove)
            document.removeEventListener("mouseup", onUp)
            document.body.style.userSelect = ""
        }

        document.body.style.userSelect = "none"
        document.addEventListener("mousemove", onMove)
        document.addEventListener("mouseup", onUp)
    }

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/drawer';
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    .kel-drawer {
        --kel-drawer-bg-color: var(--ks-bg-sidebar);

        &.ltr {
            border-right: 1px solid var(--ks-border-default);
        }

        &.rtl {
            border-left: 1px solid var(--ks-border-default);
        }

        &.ttb {
            border-bottom: 1px solid var(--ks-border-default);
        }

        &.btt {
            border-top: 1px solid var(--ks-border-default);
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

        &.is-resizable {
            .kel-drawer__body {
                position: relative;
            }

            .kel-drawer__resize-handle {
                position: absolute;
                top: 0;
                bottom: 0;
                left: 0;
                width: 6px;
                cursor: ew-resize;
                z-index: 10;
                transition: background-color 0.1s;

                &:hover,
                &:active {
                    background-color: var(--ks-btn-primary-bg-default);
                }
            }
        }

        .kel-drawer__header {
            padding: 1rem;
            margin-bottom: 0;
            background-color: var(--ks-bg-base);
            border-bottom: 1px solid var(--ks-border-default);
            color: var(--ks-text-primary);
            font-weight: bold;

            h3 {
                font-size: var(--kel-font-size-large);
                margin-bottom: 0;
            }

            .full-screen {
                margin-right: 1rem;
                > .material-design-icon__svg {
                    width: 1.375rem;
                    height: 1.375rem;
                    bottom: -0.250rem;
                }
            }
        }
    }

</style>
