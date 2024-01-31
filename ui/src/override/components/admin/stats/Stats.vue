<template>
    <top-nav-bar :title="routeInfo.title" />
    <div class="mt-3" v-show="ready">
        <Usages class="mb-2" @loaded="ready = true" />
        <BasicAuthPrompt class="mt-4 mb-2" />
        <EditionComparator class="mt-4" />
    </div>
</template>
<script>
    import RouteContext from "../../../../mixins/routeContext";
    import TopNavBar from "../../../../components/layout/TopNavBar.vue";
    import Usages from "../../../../components/admin/stats/Usages.vue";
    import BasicAuthPrompt from "../../../../components/admin/stats/BasicAuthPrompt.vue";
    import EditionComparator from "../../../../components/admin/stats/EditionComparator.vue";

    export default {
        mixins: [RouteContext],
        components: {TopNavBar, Usages, BasicAuthPrompt, EditionComparator},
        data() {
            return {
                ready: false
            };
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("stats")
                }
            }
        }
    };
</script>