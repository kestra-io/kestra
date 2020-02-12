<template>
    <div v-if="execution && outputs">
        <hr />
        <h2>{{$t('outputs') | cap}}</h2>
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
    methods: {
        itemUrl(value) {
            return `${apiRoot}executions/${this.execution.id}/file?filePath=${value.uri}&type=${value.type}`;
        }
    },
    computed: {
        ...mapState("execution", ["execution"]),
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
                for (const key in task.outputs) {
                    const item = { key: task.taskId, value: task.outputs[key], task: task.id };
                    if (typeof task.outputs[key] === "string" && task.outputs[key].startsWith &&
                        task.outputs[key].startsWith("kestra:///")) {
                        item.download = true;
                    }
                    outputs.push(item);
                }
            }
            return outputs;
        }
    }
};
</script>