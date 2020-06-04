<template>
    <div v-if="flow">
        <b-row>
            <b-col md="8">
                <b-list-group>
                    <schedule-item
                        @remove="remove"
                        :schedule="schedule"
                        :index="x"
                        v-for="(schedule, x) in triggers"
                        :key="x"
                    />
                </b-list-group>
            </b-col>
            <b-col md="4">
                <b-row>
                    <b-col class="text-center">
                        <p>
                            <small>Cron helper</small>
                        </p>
                        <b-table responsive :items="cronHelpData"></b-table>
                        <b-table responsive :items="cronHelpTokens"></b-table>
                    </b-col>
                </b-row>
                <b-row>
                    <b-col>
                        <b-form-group>
                            <b-btn variant="primary" @click="addSchedule">
                                <plus />
                                {{$t('add schedule') | cap}}
                            </b-btn>
                        </b-form-group>
                    </b-col>
                </b-row>
            </b-col>
        </b-row>
    </div>
</template>
<script>
import { mapState } from "vuex";
import Plus from "vue-material-design-icons/Plus";
import ScheduleItem from "./ScheduleItem";

export default {
    components: {
        Plus,
        ScheduleItem
    },
    watch: {
        flow() {
            console.log("on flow change");
        }
    },
    computed: {
        ...mapState("flow", ["flow", "triggers"]),
        validForm() {
            return true;
        },

        cronHelpData() {
            const helpRecord = {};
            helpRecord[this.$t("minute")] = "*";
            helpRecord[this.$t("hour")] = "*";
            helpRecord[this.$t("day (month)")] = "*";
            helpRecord[this.$t("month")] = "*";
            helpRecord[this.$t("day (week)")] = "*";
            return [helpRecord];
        },
        cronHelpTokens() {
            const helpRecord = {};
            helpRecord[this.$t("any value")] = "*";
            helpRecord[this.$t("value list separator")] = ",";
            helpRecord[this.$t("range of values")] = "-";
            helpRecord[this.$t("step values")] = "/";
            return [helpRecord];
        }
    },
    methods: {
        remove(index) {
            this.$store.commit("flow/removeTrigger", index);
            this.$store.dispatch('flow/updateFlowTrigger')
        },
        addSchedule() {
            this.$store.commit("flow/addTrigger", {
                cron: "0 4 * * 1,4",
                type: "org.kestra.core.models.triggers.types.Schedule"
            });
            this.$store.dispatch('flow/updateFlowTrigger')
        }
    }
};
</script>
<style lang="scss" scoped>
</style>