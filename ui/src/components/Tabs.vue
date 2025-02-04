<template>
    <el-tabs data-component="FILENAME_PLACEHOLDER" class="router-link" :class="{top: top}" v-model="activeName" :type="type">
        <el-tab-pane
            v-for="tab in tabs.filter(t => !t.hidden)"
            :key="tab.name"
            :label="tab.title"
            :name="tab.name || 'default'"
            :disabled="tab.disabled"
            :data-component="`FILENAME_PLACEHOLDER#${tab}`"
        >
            <template #label>
                <component :is="embedActiveTab || tab.disabled ? 'a' : 'router-link'" @click="embeddedTabChange(tab)" :to="embedActiveTab ? undefined : to(tab)" :data-test-id="tab.name">
                    <el-tooltip v-if="tab.disabled && tab.props && tab.props.showTooltip" :content="$t('add-trigger-in-editor')" placement="top">
                        <span><strong>{{ tab.title }}</strong></span>
                    </el-tooltip>
                    <enterprise-badge :enable="tab.locked">
                        {{ tab.title }}
                        <el-badge :type="tab.count > 0 ? 'danger' : 'primary'" :value="tab.count" v-if="tab.count !== undefined" />
                    </enterprise-badge>
                </component>
            </template>
        </el-tab-pane>
    </el-tabs>

    <section v-if="isEditorActiveTab || activeTab.component" data-component="FILENAME_PLACEHOLDER#container" ref="container" v-bind="$attrs" :class="{...containerClass, 'd-flex flex-row': isEditorActiveTab, 'namespace-editor': isNamespaceEditor, 'maximized': activeTab.maximized}">
        <EditorSidebar v-if="isEditorActiveTab" ref="sidebar" :style="`flex: 0 0 calc(${explorerWidth}% - 11px);`" :current-n-s="namespace" />
        <div v-if="isEditorActiveTab && explorerVisible" @mousedown.prevent.stop="dragSidebar" class="slider" />
        <div v-if="isEditorActiveTab" :style="`flex: 1 1 ${100 - (isEditorActiveTab && explorerVisible ? explorerWidth : 0)}%;`">
            <component
                v-bind="{...activeTab.props, ...attrsWithoutClass}"
                v-on="activeTab['v-on'] ?? {}"
                ref="tabContent"
                :is="activeTab.component"
                embed
            />
        </div>
        <component
            v-else
            v-bind="{...activeTab.props, ...attrsWithoutClass}"
            v-on="activeTab['v-on'] ?? {}"
            ref="tabContent"
            :is="activeTab.component"
            :embed="activeTab.props && activeTab.props.embed !== undefined ? activeTab.props.embed : true"
        />
    </section>
</template>

<script>
    import {mapState, mapMutations} from "vuex";

    import EditorSidebar from "./inputs/EditorSidebar.vue";
    import EnterpriseBadge from "./EnterpriseBadge.vue";

    export default {
        components: {EditorSidebar, EnterpriseBadge},
        props: {
            tabs: {
                type: Array,
                required: true
            },
            routeName: {
                type: String,
                default: ""
            },
            top: {
                type: Boolean,
                default: true
            },
            /**
             * The active embedded tab. If this component is not embedded, keep it undefined.
             */
            embedActiveTab: {
                type: String,
                required: false,
                default: undefined
            },
            namespace: {
                type: String,
                default: null
            },
            type: {
                type: String,
                default: undefined
            }
        },
        emits: [
            /**
             * Especially useful when embedded since you need to handle the embedActiveTab prop change on the parent component.
             * @property {Object} newTab the new active tab
             */
            "changed"
        ],
        data() {
            return {
                activeName: undefined,
            }
        },
        watch: {
            $route() {
                this.setActiveName();
            },
            activeTab() {
                this.$nextTick(() => {
                    this.setActiveName();
                });
            }
        },
        mounted() {
            this.setActiveName();
        },
        methods: {
            ...mapMutations("editor", ["changeExplorerWidth", "closeExplorer"]),
            dragSidebar(e){
                const SELF = this;

                let dragX = e.clientX;

                let blockWidth = this.$refs.sidebar.$el.offsetWidth;
                let parentWidth = this.$refs.container.offsetWidth;

                let blockWidthPercent = (blockWidth / parentWidth) * 100;

                document.onmousemove = function onMouseMove(e) {
                    let percent = blockWidthPercent + ((e.clientX - dragX) / parentWidth) * 100;
                    SELF.changeExplorerWidth(percent)
                };

                document.onmouseup = () => {
                    document.onmousemove = document.onmouseup = null;
                };
            },
            embeddedTabChange(tab) {
                this.$emit("changed", tab);
            },
            setActiveName() {
                this.activeName = this.activeTab.name || "default";
            },
            click(tab) {
                this.$router.push(this.to(this.tabs.filter(value => value.name === tab)[0]));
            },
            to(tab) {
                if (this.activeTab === tab) {
                    this.setActiveName()
                    return this.$route;
                } else {
                    return {
                        name: this.routeName || this.$route.name,
                        params: {...this.$route.params, ...{tab: tab.name}},
                        query: {...(tab.query || {})}
                    };
                }
            },
        },
        computed: {
            ...mapState({
                explorerVisible: (state) => state.editor.explorerVisible,
                explorerWidth: (state) => state.editor.explorerWidth,
            }),
            containerClass() {
                if (this.activeTab.containerClass) {
                    return {[this.activeTab.containerClass] : true};
                }

                return {"container" : true, "mt-4": true};
            },
            activeTab() {
                return this.tabs
                    .filter(tab => (this.embedActiveTab ?? this.$route.params.tab) === tab.name)[0] || this.tabs[0];
            },
            isEditorActiveTab() {
                const TAB = this.activeTab.name;
                const ROUTE = this.$route.name;

                if (["flows/update", "flows/create"].includes(ROUTE)) {
                    return TAB === "edit";
                } else if (
                    ["namespaces/update", "namespaces/create"].includes(ROUTE)
                ) {
                    if (TAB === "files") return true;

                    this.closeExplorer();
                    return false;
                }

                return false;
            },
            isNamespaceEditor(){
                return this.activeTab?.props?.isNamespace === true;
            },
            // Those are passed to the rendered component
            // We need to exclude class as it's already applied to this component root div
            attrsWithoutClass() {
                return Object.fromEntries(
                    Object.entries(this.$attrs)
                        .filter(([key]) => key !== "class")
                );
            }
        }
    };
</script>

<style lang="scss" scoped>
    :deep(.el-tabs) {
        .el-tabs__item.is-disabled {
            &:after {
                top: 0;
                content: "";
                position: absolute;
                display: block;
                width: 100%;
                height: 100%;
                z-index: 1000;
            }

            a {
                color: var(--ks-content-inactive);
            }
        }
    }

    .slider {
        flex: 0 0 3px;
        border-radius: 0.15rem;
        margin: 0 4px;
        background-color: var(--ks-border-primary);
        border: none;
        cursor: col-resize;
        user-select: none; /* disable selection */

        &:hover {
            background-color: var(--ks-border-active);
        }
    }

    .namespace-editor {
        margin: 0 !important;
        padding: 0;
        flex-grow: 1;
    }

    .maximized {
        margin: 0 !important;
        padding: 0;
        display: flex;
        flex-grow: 1;
        flex-direction: column;
    }
</style>

<style lang="scss">
    .el-tabs__nav-next, .el-tabs__nav-prev{
        &.is-disabled{
            display: none;
        }
    }
</style>