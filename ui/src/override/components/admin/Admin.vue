<template>
    <div>
        <tabs route-name="admin" ref="currentTab" :tabs="tabs" />
    </div>
</template>

<script>
    import RouteContext from "../../../mixins/routeContext";
    import Tabs from "../../../components/Tabs.vue";
    import Triggers from "../../../components/admin/Triggers.vue";

    export default {
        mixins: [RouteContext],
        components: {
            Tabs
        },
        data() {
            return {
                tabs: this.getTabs()
            }
        },
        methods: {
            activeTabName() {
                if (this.$refs.currentTab) {
                    return this.$refs.currentTab.activeTab.name || "triggers";
                }

                return null;
            },
            getTabs() {
                return [
                    {
                        name: "triggers",
                        component: Triggers,
                        title: this.$t("triggers"),
                    }
                ]
            }
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("administration")
                }
            }
        }
    };
</script>