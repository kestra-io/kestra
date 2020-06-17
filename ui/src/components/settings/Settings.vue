<template>
    <div>
        <b-form-group :label="$t('Language')">
            <b-form-select v-model="lang" :options="langOptions"></b-form-select>
        </b-form-group>
    </div>
</template>

<script>
import RouteContext from "../../mixins/routeContext";
export default {
    mixins: [RouteContext],
    data() {
        return {
            langOptions: [
                { value: "en", text: "English" },
                { value: "fr", text: "Français" }
            ]
        };
    },
    computed: {
        routeInfo() {
            return {
                title: this.$t("settings")
            };
        },
        lang: {
            set(lang) {
                localStorage.setItem("lang", lang);
                this.$moment.locale(lang);
                this.$root.$i18n.locale = lang;
                this.$toast().success({message: this.$t("saved")});
            },
            get() {
                return localStorage.getItem("lang") || "en";
            }
        }
    }
};
</script>
