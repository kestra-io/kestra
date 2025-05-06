<template>
    <span data-component="FILENAME_PLACEHOLDER">
        {{ humanReadableCron }}
    </span>
</template>

<script>
    import "cronstrue/locales/fr";

    export default {
        props: {
            date:{
                type:String,
                default: undefined
            }
        },
        computed: {
            humanReadableCron() {
                const newDate = new Date(this.date)
                const timeString = newDate.toLocaleTimeString("en-US", {
                    hour: "numeric",
                    minute: "2-digit",
                    hour12: true
                });         
                return timeString;
            }
        }
    }
</script>