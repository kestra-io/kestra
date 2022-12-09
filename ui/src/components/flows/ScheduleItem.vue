<template>
    <el-collapse-item :name="schedule.id">
        <template #title>
            {{ schedule.id }} <el-tag class="ms-2" disable-transitions type="info">{{ schedule.cron }}</el-tag>
        </template>
        <el-form class="ks-horizontal" :rules="rules" :model="item" >
            <el-form-item :label="$t('id')">
                <el-input v-model="item.id" />
            </el-form-item>

            <el-form-item prop="cron">
                <template #label>
                    {{ $t('schedules.cron.expression') }}

                    <el-tooltip :target="'tooltip-' + index" placement="bottom" :persistent="false">
                        <template #content>
                            <div v-if="isValid">
                                <p class="font-weight-bold">
                                    3 Next occurrences
                                </p>

                                <span v-if="occurences.length">
                                    <span v-for="(occurence, x) in occurences" :key="x">{{ $filters.date(occurence) }}<br></span>
                                </span>
                            </div>
                            <span v-else>
                                {{ $t("schedules.cron.invalid") }}
                            </span>
                        </template>

                        <help />
                    </el-tooltip>
                </template>

                <el-input required v-model="item.cron" />
                <small class="text-muted">
                    {{ cronHumanReadable }}
                </small>
            </el-form-item>

            <el-form-item :label="$t('schedules.cron.backfilll')">
                <el-date-picker
                    v-model="backfillStart"
                    type="datetime"
                />
            </el-form-item>

            <el-form-item class="submit">
                <el-button :icon="Delete" type="danger" @click="remove">
                    Delete
                </el-button>
            </el-form-item>
        </el-form>
    </el-collapse-item>
</template>

<script setup>
    import Delete from "vue-material-design-icons/Delete";
</script>

<script>
    const cronstrue = require("cronstrue/i18n");
    const cronParser = require("cron-parser");
    const cronValidator = require("cron-validator");
    import Help from "vue-material-design-icons/HelpBox";

    export default {
        components: {
            Help,
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
        emits: ["remove", "set"],
        data() {
            return {
                item: {
                    id: undefined,
                    cron: undefined,
                    backfill: undefined
                },
                rules: {
                    cron: [
                        {required: true, trigger: "change"},
                        {validator: this.isValidCron, trigger: "change"},
                    ],
                }
            }
        },
        created() {
            this.item = this.schedule;
        },
        computed: {
            backfillStart: {
                get: function () {
                    return this.item.backfill && this.item.backfill.start !== undefined ?
                        this.$moment(this.item.backfill.start).toDate() :
                        undefined;
                },
                set: function (val) {
                    let current = this.item;

                    if (current.backfill === undefined) {
                        current.backfill = {};
                    }

                    if (val) {
                        current.backfill = {"start": this.$moment(val).format()};
                    } else {
                        delete current.backfill;
                    }

                    this.emit(current);
                }
            },
            occurences() {
                const occurences = [];
                if (!this.isValid) {
                    return occurences;
                }
                const interval = cronParser.parseExpression(this.item.cron);
                for (let i = 0; i < 3; i++) {
                    occurences.push(interval.next().toDate());
                }
                return occurences;
            },
            cronHumanReadable() {
                const locale = localStorage.getItem("lang") || "en";
                try {
                    return cronstrue.toString(this.item.cron, {locale});
                } catch {
                    return this.$t("schedules.cron.invalid");
                }
            },
            isValid() {
                return this.item.cron && cronValidator.isValidCron(this.item.cron);
            }
        },
        methods: {
            isValidCron(rule, value, callback) {
                if (cronValidator.isValidCron(value)) {
                    callback();
                } else {
                    return callback(new Error(this.$t("invalid field", {name: "cron"})));

                }
            },
            remove() {
                this.$emit("remove", this.index);
            },
            emit(item) {
                if (item.backfill && Object.keys(item.backfill).length === 0) {
                    delete item.backfill;
                }

                this.$emit("set", this.index, item);
            },
        }
    };
</script>
