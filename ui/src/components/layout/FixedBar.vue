<template>
    <nav class="fixed-bar" :style="{top: `${topOffset}px`}">
        <slot />
    </nav>
</template>
<script>
    export default {
        mounted() {
            this.$el.parentElement.classList.add("with-fixed-bar");
            this.computeFixedBarPositionWithCss();

            window.addEventListener("resize", this.computeFixedBarPositionWithCss);
            window.addEventListener("scroll", this.computeFixedBarPosition);
        },
        data() {
            return {
                topBarInitialOffset: 0,
                topOffset: 0
            }
        },
        methods: {
            computeFixedBarPositionWithCss() {
                this.topBarInitialOffset = 80 + parseFloat(
                    window.getComputedStyle(this.$el.parentElement)
                        .getPropertyValue("--offset-to-fit-bar")
                        .replace("px", "")
                );
                this.computeFixedBarPosition(true);
            },
            computeFixedBarPosition() {
                this.topOffset = Math.max(0, this.topBarInitialOffset - window.scrollY)
                if (window.scrollY > this.topBarInitialOffset) {
                    this.$el.classList.add("with-bar");
                } else {
                    this.$el.classList.remove("with-bar");
                }
            }
        },
        unmounted() {
            window.removeEventListener("scroll", this.computeFixedBarPosition);
            window.removeEventListener("resize", this.computeFixedBarPositionWithCss);
            this.$el.parentElement.classList.remove("with-fixed-bar");
        }
    }
</script>
<style lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .with-fixed-bar {
        --offset-to-fit-bar: 0px;

        @include media-breakpoint-down(lg) {
            --offset-to-fit-bar: 40px;
        }

        padding-top: var(--offset-to-fit-bar);
    }

    .fixed-bar {
        position: fixed;
        right: 0;
        left: 0;
        border-radius: 0;
        z-index: 90;
        padding: var(--spacer) var(--offset-from-menu) var(--spacer) 0;
        text-align: right;
        transition: margin-left ease 0.2s;

        &.with-bar {
            border-bottom: 1px solid var(--bs-border-color);
            background-color: var(--bs-white);

            html.dark & {
                background-color: var(--bs-gray-100-darken-5);
            }
        }

        button {
            margin-left: var(--spacer);

            span:first-child {
                margin-right: calc(var(--spacer) / 3);
            }
        }

        ul {
            display: flex;
            list-style: none;
            margin: 0;
            flex-wrap: nowrap;
            padding: 0;
            justify-content: flex-end;

            li.spacer {
                flex-grow: 2;
            }

            li.left {
                margin-left: 0;
            }

            li {
                p {
                    padding: 8px 15px;
                    font-size: var(--font-size-sm);
                    line-height: var(--font-size-sm);
                    margin-bottom: 0;
                }
            }
        }

        .menu-collapsed & {
            margin-left: var(--menu-collapsed-width);
        }
        .menu-not-collapsed & {
            margin-left: var(--menu-width);
        }
    }
</style>