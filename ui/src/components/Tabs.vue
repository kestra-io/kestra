<template>
    <div class="card ktr-tabs">
        <div class="tabs">
            <div class="card-header">
                <div>
                    <b-nav tabs class="card-header-tabs">
                        <b-nav-item
                            v-for="tab in tabs"
                            :key="tab.name"
                            :active="$route.params.tab === tab.name"
                            @click="$emit('changed', tab)"
                            :disabled="tab.disabled"
                            :to="to(tab)"
                        >
                            {{ tab.title }}
                        </b-nav-item>
                    </b-nav>
                </div>
            </div>
            <div class="tab-content">
                <div
                    ref="tabContent"
                    :is="activeTab.component"
                    v-bind="activeTab.props"
                    v-on="$listeners"
                    :class="bodyClass"
                    :prevent-route-info="true"
                />
            </div>
        </div>
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
        methods: {
            to(tab) {
                if (this.activeTab === tab) {
                    return this.$route;
                } else {
                    return {name: this.routeName, params: {...this.$route.params, ...{tab: tab.name}}};
                }
            }
        },
        computed: {
            activeTab() {
                return this.tabs
                    .filter(tab => this.$route.params.tab === tab.name)[0] || this.tabs[0];
            },
            bodyClass() {
                const havePadding = Object.keys(this.activeTab.bodyClass || {})
                    .filter(value => value.startsWith("p"))
                    .length > 0;

                return {...{"tab-pane": true, "active": true, "p-3": !havePadding}, ...this.activeTab.bodyClass};
            }
        }
    };
</script>

<style lang="scss">
@import "../styles/_variable.scss";

.ktr-tabs {
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
                transition: 300ms;
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
            background: linear-gradient(to right, rgba(247,247,247,0) 0%, rgba(247,247,247,1) 85%);
        }

        &:after {
            left: 0;
            background: linear-gradient(to left, rgba(247,247,247,0) 0%, rgba(247,247,247,1) 85%);
        }

        &:hover > div::-webkit-scrollbar-thumb {
            background: $gray-500;
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
