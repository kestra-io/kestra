<template>
    <div>
        <b-form-group :label="$t('Language')" label-cols-sm="3">
            <b-form-select v-model="lang" :options="langOptions"></b-form-select>
        </b-form-group>
        <b-form-group :label="$t('Fold auto')" label-cols-sm="3">
            <b-checkbox v-model="autofoldTextEditor" value="1" unchecked-value="0" />
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
                this.$toast().saved();
            },
            get() {
                return localStorage.getItem("lang") || "en";
            }
        },
        autofoldTextEditor: {
            set(value) {
                localStorage.setItem("autofoldTextEditor", value);

                this.$toast().saved();
            },
            get() {
                return localStorage.getItem('autofoldTextEditor')
            }
        },

    }
};
</script>
