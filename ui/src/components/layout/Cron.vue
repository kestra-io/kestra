<template>
    <span>
        {{ humanReadableCron }}
    </span>
</template>

<script>
    import Utils from "../../utils/utils";
    import cronstrue from "cronstrue";
    import "cronstrue/locales/fr";

    export default {
        props: {
            cronExpression: {
                type: String,
                default: undefined
            }
        },
        computed: {
            humanReadableCron() {
                return cronstrue.toString(this.cronExpression, {locale: Utils.getLang()});
            }
        }
    }
</script>