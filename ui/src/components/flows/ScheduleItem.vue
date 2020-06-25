<template>
    <b-list-group-item>
        <b-row>
            <b-col md="6">
                {{schedule.cron}}
                <b-form-group>
                    <b-input type="text" v-model="schedule.cron" />
                </b-form-group>
                <p class="text-danger" v-if="!isValid">{{$t('invalid schedule')}}</p>
                <p class="text-primary" v-else>{{cronHumanReadable}}</p>
                <b-btn variant="warning" @click="remove">
                    <delete />Remove
                </b-btn>
            </b-col>
            <b-col md="6" class="text-center">
                <div v-if="occurences.length">
                    <p class="font-weight-bold">3 Next occurences</p>
                    <p v-for="(occurence, x) in occurences" :key="x">{{occurence | date('LLL:ss')}}</p>
                </div>
            </b-col>
        </b-row>
    </b-list-group-item>
</template>
<script>
const cronstrue = require("cronstrue/i18n");
const cronParser = require("cron-parser");
import Delete from "vue-material-design-icons/Delete";

export default {
    components: {
        Delete
    },
    props: {
        schedule: {
            type: Object,
            required: true
        },
        index: {
            type: Number,
            required: true
        }
    },
    computed: {
        occurences() {
            const occurences = [];
            if (!this.isValid) {
                return occurences;
            }
            const interval = cronParser.parseExpression(this.schedule.cron);
            for (let i = 0; i < 3; i++) {
                occurences.push(interval.next().toDate());
            }
            return occurences;
        },
        cronHumanReadable() {
            const locale = localStorage.getItem("lang") || "en";
            try {
                return cronstrue.toString(this.schedule.cron, { locale });
            } catch {
                return "invalid cron expression";
            }
        },
        isValid() {
            return require("cron-validator").isValidCron(this.schedule.cron);
        }
    },
    methods: {
        remove() {
            this.$bvModal
                .msgBoxConfirm(this.$t("Are you sure?"))
                .then(value => {
                    if (value) {
                        this.$emit("remove", this.index);
                    }
                });
        },
        onChange() {
            this.schedule.cron = "";
        }
    }
};
</script>