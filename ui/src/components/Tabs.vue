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
                    <EnterpriseBadge :enable="tab.locked">
                        {{ tab.title }}
                        <el-badge :type="tab.count > 0 ? 'danger' : 'primary'" :value="tab.count" v-if="tab.count !== undefined" />
                    </EnterpriseBadge>
                </component>
            </template>
        </el-tab-pane>
    </el-tabs>
    <section v-if="isEditorActiveTab || activeTab.component" data-component="FILENAME_PLACEHOLDER#container" ref="container" v-bind="$attrs" :class="{...containerClass, 'maximized': activeTab.maximized}">
        <el-splitter v-if="isEditorActiveTab" class="editor-splitter" @resize="onSplitterResize">
            <el-splitter-panel v-if="editorStore.explorerVisible" :size="editorStore.explorerWidth" min="15%" max="30%">
                <EditorSidebar ref="sidebar" :currentNS="namespace" class="sidebar" />
            </el-splitter-panel>
            <el-splitter-panel :size="editorStore.explorerVisible ? 100 - editorStore.explorerWidth : 100">
                <component
                    v-bind="{...activeTab.props, ...attrsWithoutClass}"
                    v-on="activeTab['v-on'] ?? {}"
                    ref="tabContent"
                    :is="activeTab.component"
                    embed
                />
            </el-splitter-panel>
        </el-splitter>
        <BlueprintDetail
            v-else-if="selectedBlueprintId"
            :blueprintId="selectedBlueprintId"
            blueprintType="community"
            @back="selectedBlueprintId = undefined"
            combinedView="true"
            :kind="activeTab.props.blueprintKind"
            :embed="activeTab.props && activeTab.props.embed !== undefined ? activeTab.props.embed : true"
        />
        <component
            v-else
            v-bind="{...activeTab.props, ...attrsWithoutClass}"
            v-on="activeTab['v-on'] ?? {}"
            ref="tabContent"
            :is="activeTab.component"
            :namespace="namespaceToForward"
            @go-to-detail="blueprintId => selectedBlueprintId = blueprintId"
            :embed="activeTab.props && activeTab.props.embed !== undefined ? activeTab.props.embed : true"
        />
    </section>
</template>

<script>
    import EditorSidebar from "./inputs/EditorSidebar.vue";
    import EnterpriseBadge from "./EnterpriseBadge.vue";
    import BlueprintDetail from "./flows/blueprints/BlueprintDetail.vue";
    import {useEditorStore} from "../stores/editor";
    import {mapStores} from "pinia";

    export default {
        components: {EditorSidebar, EnterpriseBadge,BlueprintDetail},
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
                selectedBlueprintId : undefined
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
            onSplitterResize(sizes) {
                if (sizes?.length >= 1) {
                    this.editorStore.changeExplorerWidth(sizes[0]);
                }
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
                        params: {...this.$route.params, tab: tab.name},
                        query: {...tab.query}
                    };
                }
            },
            getTabClasses(tab) {
                const isEnterpriseTab = tab.locked;

                return {
                    "container": !isEnterpriseTab,
                    "mt-4": !isEnterpriseTab,
                    "px-0": isEnterpriseTab,
                };
            },
        },
        computed: {
            ...mapStores(useEditorStore),
            containerClass() {
                return this.getTabClasses(this.activeTab);
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

                    this.editorStore.closeExplorer();
                    return false;
                }

                return false;
            },
            // Those are passed to the rendered component
            // We need to exclude class as it's already applied to this component root div
            attrsWithoutClass() {
                return Object.fromEntries(
                    Object.entries(this.$attrs)
                        .filter(([key]) => key !== "class")
                );
            },
            namespaceToForward(){
                return this.activeTab.props?.namespace ?? this.namespace;
                // in the special case of Namespace creation on Namespaces page, the tabs are loaded before the namespace creation
                // in this case this.props.namespace will be used
            }
        }
    };
</script>

<style lang="scss" scoped>
section.container.mt-4:has(> section.empty) {
    margin: 0 !important;
    padding: 0 !important;
}

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

.maximized {
    margin: 0 !important;
    padding: 0;
    display: flex;
    flex-grow: 1;
}

.editor-splitter {
    height: 100%;

    :deep(.el-splitter-panel) {
        display: flex;
        flex-direction: column;
    }
}

.sidebar {
    height: 100%;
    width: 100%;
}

:deep(.el-tabs__nav-next),
:deep(.el-tabs__nav-prev) {
    &.is-disabled {
        display: none;
    }
}
</style>