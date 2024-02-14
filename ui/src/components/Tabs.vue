<template>
    <div class="tabs-nav">
        <el-tabs class="nav-tabs-flow-root router-link" :class="{top: top}" v-model="activeName">
            <el-tab-pane
                v-for="tab in tabs"
                :key="tab.name"
                :label="tab.title"
                :name="tab.name || 'default'"
                :disabled="tab.disabled"
            >
                <template #label>
                    <component :is="embedActiveTab ? 'a' : 'router-link'" @click="embeddedTabChange(tab)" :to="embedActiveTab ? undefined : to(tab)">
                        {{ tab.title }}
                        <el-badge :type="tab.count > 0 ? 'danger' : 'primary'" :value="tab.count" v-if="tab.count !== undefined" />
                    </component>
                </template>
            </el-tab-pane>
        </el-tabs>

        <component
            v-bind="{...activeTab.props, ...attrsWithoutClass}"
            v-on="activeTab['v-on'] ?? {}"
            ref="tabContent"
            :is="activeTab.component"
            embed
        />
    </div>
</template>

<script>
    export default {
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
            activeTab() {
                return this.tabs
                    .filter(tab => (this.embedActiveTab ?? this.$route.params.tab) === tab.name)[0] || this.tabs[0];
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
                color: var(--el-text-color-disabled);
            }
        }
    }
</style>

