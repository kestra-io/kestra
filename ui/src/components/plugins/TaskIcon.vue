<template>
    <el-tooltip placement="left" :persistent="false" transition="" :hide-after="0">
        <template #content>
            <span>
                {{ tooltipStart }}
            </span>
            <code>
                {{ tooltipEnd }}
            </code>
        </template>
        <div v-bind="$attrs" class="wrapper" v-if="icon" :class="classes">
            <div class="icon" :style="styles" :alt="cls" />
            <div v-if="!onlyIcon" class="hover">
                {{ name }}
            </div>
        </div>
    </el-tooltip>
</template>

<script>
    import {mapState} from "vuex";
    import {Buffer} from "buffer";
    import {cssVariable} from "../../utils/global";

    export default {
        props: {
            cls: {
                type: String,
                required: true
            },
            onlyIcon: {
                type: Boolean,
                default: false
            },
            theme: {
                type: String,
                default: undefined,
                validator(value) {
                    return ["dark", "light"].includes(value)
                }
            }
        },
        computed: {
            ...mapState("plugin", ["icons"]),
            name() {
                return this.icon ? this.icon.name : this.cls;
            },
            classes() {
                return {
                    "flowable": this.icon ? this.icon.flowable : false,
                    "only-icon": this.onlyIcon
                }
            },
            styles() {
                return {
                    backgroundImage: `url(data:image/svg+xml;base64,${this.imageBase64})`
                }
            },
            tooltipStart() {
                return this.name ? this.cls.replace("." + this.name, ".") : this.cls;
            },
            tooltipEnd() {
                return this.name ;
            },
            imageBase64() {
                let icon = this.icon && this.icon.icon ? Buffer.from(this.icon.icon, 'base64').toString('utf8') : undefined;

                if (!icon) {
                    icon = "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                        "xmlns:xlink=\"http://www.w3.org/1999/xlink\" aria-hidden=\"true\" " +
                        "focusable=\"false\" width=\"0.75em\" height=\"1em\" style=\"-ms-transform: " +
                        "rotate(360deg); -webkit-transform: rotate(360deg); transform: rotate(360deg);\" " +
                        "preserveAspectRatio=\"xMidYMid meet\" viewBox=\"0 0 384 512\">" +
                        "<path d=\"M288 32H0v448h384V128l-96-96zm64 416H32V64h224l96 96v288z\" fill=\"currentColor\"/>" +
                        "</svg>";
                }

                const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;
                let color = darkTheme ? cssVariable("--bs-gray-900") : cssVariable("--bs-black");

                if (this.theme) {
                    color = this.theme === "dark" ? cssVariable("--bs-gray-900") : cssVariable("--bs-black");
                }

                icon = icon.replaceAll("currentColor", color);

                return Buffer.from(icon, "utf8").toString("base64");
            },
            icon() {
                return (this.icons || {})[this.cls]
            },
        }
    };
</script>

<style lang="scss" scoped>
    div.wrapper {
        height: 100%;
        width: 100%;
        font-size: var(--font-size-xs);

        > .icon {
            margin: calc(var(--spacer) / 5);
            height: calc(100% - 25px);
            display: block;
            background-size: contain;
            background-repeat: no-repeat;
            background-position: center center;
        }

        > .hover {
            position: absolute;
            background: var(--el-color-info-light-9);
            border-top: 1px solid var(--bs-border-color);
            color: var(--bs-body-color);
            font-size: 70%;
            text-overflow: ellipsis;
            overflow: hidden;
            max-width: 100%;
            white-space: nowrap;
            width: 100%;
            bottom: 0;
            text-align: center;
            padding: calc(var(--spacer) / 5);
        }

        &.flowable {
            > .hover {
                background: var(--el-color-warning-light-9);
            }
        }

        &.only-icon {
            > .icon {
                height: 100%;
                position: relative;

                :deep(svg) {
                    position: absolute;
                    padding: 2px;
                    top: 0
                }
            }
        }
    }

</style>
