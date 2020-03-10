<template>
    <div v-if="execution && outputs">
        <hr />
        <h2>{{$t('outputs') | cap}}</h2>
        <b-row>
            <b-col md="6">
                <b-form-group
                    :label="$t('display output for specific task').capitalize()"
                    label-for="input-for-output"
                >
                    <v-select
                        v-model="filter"
                        :reduce="option => option.label"
                        @input="onSearch"
                        :options="selectOptions"
                        :placeholder="$t('search') + '...'"
                    ></v-select>
                </b-form-group>
            </b-col>
        </b-row>
        <b-table
            responsive="xl"
            striped
            hover
            bordered
            :fields="fields"
            :items="outputs"
            class="mb-0"
        >
            <template v-slot:cell(value)="row">
                <b-link
                    class="btn btn-primary"
                    v-if="row.item.download"
                    target="_blank"
                    :href="itemUrl({uri: row.item.value})"
                >{{$t('download') | cap}}</b-link>
                <span v-else>{{row.item.value}}</span>
            </template>
        </b-table>
    </div>
</template>
<script>
import { mapState } from "vuex";
import { apiRoot } from "../../http";

export default {
    data() {
        return {
            filter: ""
        };
    },
    created() {
        if (this.$route.query.search) {
            this.filter = this.$route.query.search || ""
        }
    },
    watch: {
        $route() {
            if (this.$route.query.search !== this.filter) {
                this.filter = this.$route.query.search || "";
            }
        }
    },
    methods: {
        itemUrl(value) {
            return `${apiRoot}executions/${this.execution.id}/file?filePath=${value.uri}&type=${value.type}`;
        },
        onSearch() {
            if (this.filter && this.$route.query.search !== this.filter) {
                const newRoute = { query: { ...this.$route.query } };
                newRoute.query.search = this.filter;
                this.$router.push(newRoute);
            }
        }
    },
    computed: {
        ...mapState("execution", ["execution"]),
        selectOptions() {
            const options = [];
            for (const task of this.execution.taskRunList || []) {
                for (const key in task.outputs) {
                    options.push({
                        label: task.taskId + (task.value ? ` - ${task.value}`: ''),
                        value: task.outputs[key]
                    });
                }
            }
            return options;
        },
        fields() {
            return [
                {
                    key: "key",
                    label: this.$t("name")
                },
                {
                    key: "task",
                    label: this.$t("task")
                },
                {
                    key: "value",
                    label: this.$t("value")
                }
            ];
        },
        outputs() {
            const outputs = [];
            for (const task of this.execution.taskRunList || []) {
                const token = task.taskId + (task.value ? ` - ${task.value}`: '')
                if (!this.filter || token === this.filter) {
                    for (const key in task.outputs) {
                        const item = {
                            key: token,
                            value: task.outputs[key],
                            task: task.id
                        };
                        if (
                            typeof task.outputs[key] === "string" &&
                            task.outputs[key].startsWith("kestra:///")
                        ) {
                            item.download = true;
                        }
                        outputs.push(item);
                    }
                }
            }
            return outputs;
        }
    }
};
</script>