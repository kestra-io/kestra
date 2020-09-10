<template>
    <div v-if="execution && outputs">
        <b-navbar toggleable="lg" type="light" variant="light">
            <b-navbar-toggle target="nav-collapse"></b-navbar-toggle>
            <b-collapse id="nav-collapse" is-nav>
                <b-nav-form>
                    <v-select
                        v-model="filter"
                        :reduce="option => option.value"
                        @input="onSearch"
                        :options="selectOptions"
                        :placeholder="$t('display output for specific task') + '...'"
                    ></v-select>
                </b-nav-form>
            </b-collapse>
        </b-navbar>

        <b-table
            responsive="xl"
            striped
            hover
            bordered
            :fields="fields"
            :items="outputs"
            class="mb-0"
        >
            <template v-slot:cell(key)="row">
                <code>{{ row.item.key }}</code>
            </template>

            <template v-slot:cell(value)="row">
                <var>{{ row.item.value }}</var>
            </template>

            <template v-slot:cell(output)="row">
                <var-value :execution="execution" :value="row.item.output" />
            </template>
        </b-table>
    </div>
</template>
<script>
import { mapState } from "vuex";
import md5 from "md5";
import VarValue from "./VarValue";
import Utils from "../../utils/utils";

export default {
    components: {
        VarValue,
    },
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
        onSearch() {
            if (this.filter && this.$route.query.search !== this.filter) {
                const newRoute = { query: { ...this.$route.query } };
                newRoute.query.search = this.filter;
                this.$router.push(newRoute);
            }
        },
        taskRunOutputToken(taskRun) {
            return md5(taskRun.taskId + (taskRun.value ? ` - ${taskRun.value}`: ''));
        }
    },
    computed: {
        ...mapState("execution", ["execution"]),
        selectOptions() {
            const options = {};
            for (const taskRun of this.execution.taskRunList || []) {
                options[this.taskRunOutputToken(taskRun)] = {
                    label: taskRun.taskId + (taskRun.value ? ` - ${taskRun.value}`: ''),
                    value: this.taskRunOutputToken(taskRun)
                }
            }

            return Object.values(options);
        },
        fields() {
            return [
                {
                    key: "task",
                    label: this.$t("task")
                },
                {
                    key: "value",
                    label: this.$t("value")
                },
                {
                    key: "key",
                    label: this.$t("name")
                },
                {
                    key: "output",
                    label: this.$t("output")
                }
            ];
        },
        outputs() {
            const outputs = [];
            for (const taskRun of this.execution.taskRunList || []) {
                const token = this.taskRunOutputToken(taskRun)
                if (!this.filter || token === this.filter) {
                    Utils.executionVars(taskRun.outputs).forEach(output => {
                        const item = {
                            key: output.key,
                            output: output.value,
                            task: taskRun.taskId,
                            value: taskRun.value
                        };

                        outputs.push(item);
                    })
                }
            }
            return outputs;
        }
    }
};
</script>
