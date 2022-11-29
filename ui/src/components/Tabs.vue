<template>
    <div class="card ktr-tabs">
        <el-tabs v-model="activeName">
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

<style lang="scss">
@import "../styles/_variable.scss";

.ktr-tabs {
    transition: all 0.3s ease;

    &.card {
        background-color: transparent;
        border: 0;
    }

    .tabs > .card-header {
        padding: 0;
        position: relative;

        > div {
            padding: $card-spacer-y $card-spacer-x;
            overflow-x: scroll;
            overflow-y: hidden;
            direction: rtl;
            transform: rotate(180deg);

            &::-webkit-scrollbar {
                height: 5px;
            }

            &::-webkit-scrollbar-track, &::-webkit-scrollbar-thumb {
                background: transparent;
                transition: 320ms;
            }

            &::-webkit-scrollbar-thumb:hover {
                background: $gray-500;
            }

            .card-header-tabs {
                margin-top: -11px;
                direction: ltr;
                transform: rotate(-180deg);
            }

            @-moz-document url-prefix() {
                & {
                    overflow-x: auto;
                    .card-header-tabs {
                        padding-top: 5px;
                    }
                }
            }
        }

        &:before, &:after {
            content: "";
            position: absolute;
            top: 0;
            right: 0;
            width: 10px;
            height: 100%;
            z-index: 2;
            background: linear-gradient(to right, rgba(248, 248, 252, 0) 0%, rgba(248, 248, 252, 1) 85%);

            .theme-dark & {
                background: linear-gradient(to right, rgba(27, 30, 42, 0) 0%, rgb(27, 30, 42) 95%);
            }
        }

        &:after {
            left: 0;
            background: linear-gradient(to left, rgba(248, 248, 252, 0) 0%, rgba(248, 248, 252, 1) 85%);

            .theme-dark & {
                background: linear-gradient(to left, rgba(27, 30, 42, 0) 0%, rgb(27, 30, 42) 95%);
            }
        }

        &:hover > div::-webkit-scrollbar-thumb {
            background: var(--secondary);
        }

        .nav {
            flex-wrap: nowrap;

            .nav-item {
                white-space: nowrap;
            }
        }
    }
}
</style>
