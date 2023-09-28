<template>
    <div class="list">
        <template v-for="[status, count] of sorted">
            <div v-if="count > 0" :key="status" class="d-flex justify-content-around mb-4 column-gap-4 row-gap-2 flex-wrap">
                <div class="icon">
                    <status :label="false" :status="status" />
                </div>

                <div class="center">
                    <h6>
                        {{ status.toLowerCase().capitalize() }}
                    </h6>
                    <div class="percent">
                        {{ percent(count) }}%
                    </div>
                </div>

                <div class="big-number text-break">
                    {{ count }}
                </div>
            </div>
        </template>
    </div>
</template>
<script>
    import Status from "../Status.vue";

    export default {
        components: {
            Status
        },
        props: {
            data: {
                type: Object,
                required: true
            },
        },
        methods: {
            percent(count) {
                const sum = Object.values(this.data.executionCounts).reduce((a, b) => a + b, 0);
                return Math.round(count * 100 / sum);
            },
        },
        computed: {
            sorted() {
                return new Map(Object.entries(this.data.executionCounts).sort((a, b) => b[1] - a[1]));
            }
        }
    };
</script>

<style lang="scss" scoped>
    .list {
        border: 0;
        padding: 0;
        max-width: 100%;

        > div {
            justify-content: center;
            align-items: center;
            color: var(--bs-gray-900);

            .icon {
                vertical-align: middle;
            }

            .center {
                flex-grow: 1;

                h6 {
                    line-height: 1;
                    margin-bottom: 0;
                    font-size: var(--font-size-sm);
                    text-transform: uppercase;
                    font-weight: bold;
                }

                .percent {
                    line-height: 1.5;
                    font-size: var(--font-size-xs);
                }
            }


            .big-number {
                vertical-align: middle;
                font-weight: bold;
            }
        }
    }
</style>

