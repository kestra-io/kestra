<template>
    <div>
        <span v-for="trigger in triggers" :key="uid(trigger)" :id="uid(trigger)">
            <b-avatar
                size="sm"
                variant="primary"
                :text="name(trigger)"
                button
            />
            <b-popover triggers="hover" :target="uid(trigger)" placement="left" :title="`${$t('trigger details')}: ${trigger ? trigger.id : ''}`">
                <vars :stacked="true" :data="triggerData(trigger)" />
            </b-popover>
        </span>
    </div>
</template>
<script>
    import Markdown from "../../utils/markdown";
    import Vars from "../executions/Vars";

    export default {
        props: {
            flow: {
                type: Object,
                default: () => undefined,
            },
            execution: {
                type: Object,
                default: () => undefined,
            },
        },
        components: {
            Vars
        },
        methods: {
            uid(trigger) {
                return (this.flow ? this.flow.namespace + "-" + this.flow.id : this.execution.id) + "-" + trigger.id
            },
            name(trigger) {
                let split = trigger.type.split(".");

                return split[split.length - 1].substr(0, 1).toUpperCase();
            },
            triggerData(trigger) {
                if (trigger.description) {
                    return {...trigger, description: Markdown.render(trigger.description)}
                }

                return trigger
            },
        },
        computed: {
            triggers() {
                if (this.flow && this.flow.triggers) {
                    return this.flow.triggers
                } else if (this.execution && this.execution.trigger) {
                    return [this.execution.trigger]
                } else {
                    return []
                }

            }
        }
    };
</script>

<style lang="scss" scoped>
@use "sass:math";
@import "../../styles/_variable.scss";

.b-avatar {
    margin-right: math.div($badge-pill-padding-x, 2);
}

</style>
