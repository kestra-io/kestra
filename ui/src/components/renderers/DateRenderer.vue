<template>
    <div>
        <span :id="`date-renderer-popover-${uuid}`">
            {{ date | date(format) }}
        </span>
        <b-popover :target="`date-renderer-popover-${uuid}`" triggers="hover" :placement="placement">
            <date-ago :date="date" />
        </b-popover>
    </div>
</template>
<script>
    import DateAgo from "../layout/DateAgo";
    import Utils from "../../utils/utils"
    export default {
        components: {DateAgo},
        props: {
            date: {
                type: String,
                required:true
            },
            format: {
                type: String,
                default: "LLLL"
            },
            placement: {
                type: String,
                default: "top"
            }
        },
        computed: {
            uuid () {
                return Utils.uid()
            }
        }
    }
</script>