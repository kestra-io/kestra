<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="container" v-show="ready">
        <Usages class="mb-2" @loaded="ready = true" />
        <EditionComparator class="mt-4" />
    </section>
</template>
<script>
    import RouteContext from "../../../../mixins/routeContext";
    import TopNavBar from "../../../../components/layout/TopNavBar.vue";
    import Usages from "../../../../components/admin/stats/Usages.vue";
    import EditionComparator from "../../../../components/admin/stats/EditionComparator.vue";

    export default {
        mixins: [RouteContext],
        components: {TopNavBar, Usages, EditionComparator},
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