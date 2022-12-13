<template>
    <div>
        <el-form class="ks-horizontal">
            <el-form-item :label="$t('Language')">
                <el-select :model-value="lang" @update:model-value="onLang">
                    <el-option
                        v-for="item in langOptions"
                        :key="item.value"
                        :label="item.text"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>

            <el-form-item :label="$t('theme')">
                <el-select :model-value="theme" @update:model-value="onTheme">
                    <el-option
                        v-for="item in themesOptions"
                        :key="item.value"
                        :label="item.text"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>

            <el-form-item :label="$t('Editor theme')">
                <el-select :model-value="editorTheme" @update:model-value="onEditorTheme">
                    <el-option
                        v-for="item in editorThemesOptions"
                        :key="item.value"
                        :label="item.text"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>

            <el-form-item label="&nbsp;">
                <el-checkbox :label="$t('Fold auto')" :model-value="autofoldTextEditor" @update:model-value="onAutofoldTextEditor" />
            </el-form-item>

            <el-form-item :label="$t('Default namespace')">
                <namespace-select data-type="flow" :value="defaultNamespace" @update:model-value="onNamespaceSelect" />
            </el-form-item>
        </el-form>
    </div>
</template>

<script>
    import RouteContext from "../../mixins/routeContext";
    import NamespaceSelect from "../../components/namespace/NamespaceSelect.vue";

    export default {
        mixins: [RouteContext],
        components: {
            NamespaceSelect,
        },
        data() {
            return {
                defaultNamespace: undefined,
                lang: undefined,
                theme: undefined,
                editorTheme: undefined,
                autofoldTextEditor: undefined,
            };
        },
        created() {
            const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;

            this.defaultNamespace = localStorage.getItem("defaultNamespace") || "";
            this.lang = localStorage.getItem("lang") || "en";
            this.theme = localStorage.getItem("theme") || "light";
            this.editorTheme = localStorage.getItem("editorTheme") || (darkTheme ? "vs-dark" : "vs");
            this.autofoldTextEditor = localStorage.getItem("autofoldTextEditor") === "true";
        },
        methods: {
            onNamespaceSelect(value) {
                this.defaultNamespace = value;

                if (value) {
                    localStorage.setItem("defaultNamespace", value)
                } else {
                    localStorage.removeItem("defaultNamespace")
                }
                this.$toast().saved();
            },
            onLang(value) {
                localStorage.setItem("lang", value);
                this.$moment.locale(value);
                this.$root.$i18n.locale = value;
                this.lang = value;
                this.$toast().saved();
            },
            onTheme(value) {
                this.$root.switchTheme(value)
                this.theme = value;
                this.$toast().saved();
            },
            onEditorTheme(value) {
                localStorage.setItem("editorTheme", value);
                this.editorTheme = value;
                this.$toast().saved();
            },
            onAutofoldTextEditor(value) {
                localStorage.setItem("autofoldTextEditor", value);
                this.autofoldTextEditor = value;
                this.$toast().saved();
            },
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("settings")
                };
            },
            langOptions() {
                return [
                    {value: "en", text: "English"},
                    {value: "fr", text: "Français"}
                ];
            },
            themesOptions() {
                return [
                    {value: "light", text: "Light"},
                    {value: "dark", text: "Dark"}
                ]
            },
            editorThemesOptions() {
                return  [
                    {value: "vs", text: "Light"},
                    {value: "vs-dark", text: "Dark"}
                ]
            }
        }
    };
</script>
