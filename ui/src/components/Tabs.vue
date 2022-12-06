<template>
    <div>
        <el-tabs v-model="activeName" class="mb-3">
            <el-tab-pane
                v-for="tab in tabs"
                :key="tab.name"
                :label="tab.title"
                :name="tab.name"
            >
                <template #label>
                    <router-link :to="to(tab)">
                        {{ tab.title }}
                        <el-badge :value="tab.count" v-if="tab.count !== undefined" />
                    </router-link>
                </template>
            </el-tab-pane>
        </el-tabs>
        <component
            v-bind="activeTab.props"
            ref="tabContent"
            :is="activeTab.component"
            :class="{'p-3': activeTab.background === undefined || activeTab.background !== false}"
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
        created() {
            this.activeName = this.activeTab.name;
        },
        methods: {
            click(tab) {
                this.$router.push(this.to(this.tabs.filter(value => value.name === tab)[0]));
            },
            to(tab) {
                if (this.activeTab === tab) {
                    return this.$route;
                } else {
                    return {name: this.routeName, params: {...this.$route.params, ...{tab: tab.name}}};
                }
            },
        },
        computed: {
            activeTab() {
                return this.tabs
                    .filter(tab => this.$route.params.tab === tab.name)[0] || this.tabs[0];
            },
            bodyClass() {
                let background = this.activeTab.background !== false;

                return {...{"card": background}};
            }
        }
    };
</script>

