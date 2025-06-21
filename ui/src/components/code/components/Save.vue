<script setup lang="ts">
    const props = defineProps({
        what: {
            type: String,
            required: false,
            default: "tasks"
        },
    });

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});
</script>
