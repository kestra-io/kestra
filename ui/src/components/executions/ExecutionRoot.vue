<template>
    <div>
        <h1>Execution &gt; {{$route.params.executionId}}</h1>
        <b-card no-body>
            <b-tabs card>
                <b-tab
                    v-for="tab in tabs"
                    :key="tab.tab"
                    @click="setTab(tab.tab)"
                    :active="$route.query.tab === tab.tab"
                    :title="tab.title"
                >
                    <b-card-text>
                        <div :is="tab.tab" />
                    </b-card-text>
                </b-tab>
            </b-tabs>
        </b-card>
    </div>
</template>
<script>
import Gantt from "./Gantt";
import Overview from "./Overview";
import Logs from "./Logs";
export default {
    components: {
        Overview,
        Gantt,
        Logs
    },
    methods: {
        setTab(tab) {
            this.$router.push({
                path: "execution",
                params: this.$route.params,
                query: { tab }
            });
        }
    },
    computed: {
        tabs() {
            const title = title => this.$t(title).capitalize();
            return [
                {
                    tab: "overview",
                    title: title("overview")
                },
                {
                    tab: "gantt",
                    title: title("gantt")
                },
                {
                    tab: "logs",
                    title: title("logs")
                }
            ];
        }
    }
};
</script>