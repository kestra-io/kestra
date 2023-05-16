<template>
    <div>
        <el-tabs class="nav-tabs-flow-root router-link" v-model="activeName">
            <el-tab-pane
                v-for="tab in tabs"
                :key="tab.name"
                :label="tab.title"
                :name="tab.name || 'default'"
                :disabled="tab.disabled"
            >
                <template #label>
                    <router-link :to="to(tab)">
                        {{ tab.title }}
                        <el-badge :type="tab.count > 0 ? 'danger' : 'primary'" :value="tab.count" v-if="tab.count !== undefined" />
                    </router-link>
                </template>
            </el-tab-pane>
        </el-tabs>
        <component
            v-bind="{...activeTab.props, ...$attrs}"
            ref="tabContent"
            :is="activeTab.component"
            :prevent-route-info="true"
        />
    </div>
</template>

<script>
    export default {
        components: {

        },
        props: {
            tabs: {
                type: Array,
                required: true
            },
            routeName: {
                type: String,
                default: ""
            },

        },
        emits: ["changed"],
        data() {
            return {
                activeName: undefined,
            }
        },
        watch: {
            $route() {
                this.setActiveName();
            },
            activeName() {
                this.$nextTick(() => {
                    this.setActiveName();
                });
            }
        },
        created() {
            this.setActiveName();
        },
        methods: {
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
                    return {name: this.routeName, params: {...this.$route.params, ...{tab: tab.name}}, query: {...(tab.query || {})}};
                }
            },
        },
        computed: {
            activeTab() {
                return this.tabs
                    .filter(tab => this.$route.params.tab === tab.name)[0] || this.tabs[0];
            },
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

