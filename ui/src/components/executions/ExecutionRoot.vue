<template>
    <div>
        <h1><router-link :to="`/executions/${$route.params.namespace}/${$route.params.flowId}`">Execution</router-link> &gt; {{$route.params.id}}</h1>
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
    created () {
        this.$store.dispatch('execution/loadExecution', this.$route.params)
    },
    methods: {
        setTab(tab) {
            this.$router.push({
                name: "execution",
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