<template>
    <sidebar-menu
        ref="sideBarRef"
        data-component="FILENAME_PLACEHOLDER"
        id="side-menu"
        :menu="localMenu"
        @update:collapsed="onToggleCollapse"
        width="268px"
        :collapsed="collapsed"
        link-component-name="LeftMenuLink"
    >
        <template #header>
            <div class="logo">
                <router-link :to="{name: 'home'}">
                    <span class="img" />
                </router-link>
            </div>
            <Environment />
        </template>

        <template #footer>
            <slot name="footer" />
        </template>

        <template #toggle-icon>
            <el-button>
                <chevron-double-right v-if="collapsed" />
                <chevron-double-left v-else />
            </el-button>
            <span class="version">
                <el-tooltip
                    effect="light"
                    :persistent="false"
                    transition=""
                    :hide-after="0"
                >
                    <template #content>
                        <code>{{ configs.commitId }}</code> <DateAgo v-if="configs.commitDate" :inverted="true" :date="configs.commitDate" />
                    </template>
                    {{ configs.version }}
                </el-tooltip>
            </span>
        </template>
    </sidebar-menu>
</template>

<script setup>
    import {computed} from "vue";
    import {useStore} from "vuex";

    import {SidebarMenu} from "vue-sidebar-menu";

    import ChevronDoubleLeft from "vue-material-design-icons/ChevronDoubleLeft.vue";
    import ChevronDoubleRight from "vue-material-design-icons/ChevronDoubleRight.vue";

    import DateAgo from "../../components/layout/DateAgo.vue"
    import Environment from "../../components/layout/Environment.vue";
    import {useLeftMenu} from "./useLeftMenu";


    const props = defineProps({
        generateMenu: {
            type: Function,
            default: () => () =>  []
        }
    })
    const $emit = defineEmits(["menu-collapse"])

    const store = useStore()


    const configs = computed(() => store.state.misc.configs);

    const {
        collapsed,
        onToggleCollapse,
        localMenu,
        sideBarRef
    } = useLeftMenu($emit, props.generateMenu);
</script>

<style lang="scss">
    #side-menu {
        z-index: 1039;
        border-right: 1px solid var(--bs-border-color);

        .logo {
            overflow: hidden;
            padding: 35px 0;
            height: 113px;
            position: relative;

            a {
                transition: 0.2s all;
                position: absolute;
                left: 37px;
                display: block;
                height: 55px;
                width: 100%;
                overflow: hidden;

                span.img {
                    height: 100%;
                    background: url(../../../src/assets/logo.svg) 0 0 no-repeat;
                    background-size: 179px 55px;
                    display: block;
                    transition: 0.2s all;

                    html.dark & {
                        background: url(../../../src/assets/logo-white.svg) 0 0 no-repeat;
                        background-size: 179px 55px;
                    }
                }
            }
        }


        span.version {
            transition: 0.2s all;
            white-space: nowrap;
            font-size: var(--font-size-xs);
            text-align: center;
            display: block;
            color: var(--bs-gray-600);
            width: auto;

            html.dark & {
                color: var(--bs-gray-800);
            }
        }

        .vsm--icon {
            transition: left 0.2s ease;
            font-size: 1.5em;
            background-color: transparent !important;
            padding-bottom: 15px;
            height: 30px !important;
            width: 30px !important;

            svg {
                position: relative;
                margin-top: 13px;
            }
        }

        .vsm--item {
            padding: 0 30px;
            transition: padding 0.2s ease;
        }

        .vsm--child {
            .vsm--item {
                padding: 0;
            }
        }

        .vsm--link {
            padding: 0.3rem 0.5rem;
            margin-bottom: 0.3rem;
            border-radius: var(--bs-border-radius-lg);
            transition: padding 0.2s ease;

            html.dark & {
                color: var(--bs-white);
            }

            &_disabled {
                pointer-events: auto;
            }

            .el-tooltip__trigger {
                display: flex;
            }
        }

        .vsm--toggle-btn {
            padding-top: 16px;
            padding-bottom: 16px;
            font-size: 20px;
            background: transparent;
            color: var(--bs-secondary);
            border-top: 1px solid var(--bs-border-color);

            .el-button {
                padding: 8px;
                margin-right: 15px;
                transition: margin-right 0.2s ease;
                html.dark & {
                    background: var(--bs-gray-500);
                }
            }
        }


        a.vsm--link_active[href="#"] {
            cursor: initial !important;
        }

        .vsm--dropdown {
            background-color: var(--bs-gray-100);

            .vsm--title {
                top: 3px;
            }
        }


        a.vsm--link_active[href="#"] {
            cursor: initial !important;
        }

        html.dark & {
            background-color: var(--bs-gray-100);

            .vsm--dropdown {
                background-color: var(--bs-gray-100);
            }
        }


        .vsm--mobile-bg {
            border-radius: 0 var(--bs-border-radius) var(--bs-border-radius) 0;
        }

        &.vsm_collapsed {
            .logo {
                a {
                    left: 8px;

                    span.img {
                        background-size: 207px 55px !important;
                    }
                }
            }

            .vsm--link {
                padding-left: 13px;
            }

            .vsm--item {
                padding: 0 5px;
            }

            .el-button {
                margin-right: 0;
            }

            span.version {
                opacity: 0;
                width: 0;
            }
        }

        .el-tooltip__trigger .lock-icon.material-design-icon > .material-design-icon__svg {
            bottom: 0 !important;
            margin-left: 5px;
        }
    }

</style>
