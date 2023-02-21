<template>
    <nav class="top-line">
        <slot />
    </nav>
</template>
<script>
    export default {}
</script>
<style lang="scss">
.top-line {
    position: sticky;
    top: 0px;
    height: 50px;
    border-radius: 0;
    //border-top: 1px solid var(--bs-border-color);
    z-index: 90;
    padding: 0.5rem;
    transition: margin-left ease 0.2s;
    margin-bottom: 0.5rem;

    html.dark & {
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
        //margin-left: var(--menu-collapsed-width);
    }

    .menu-not-collapsed & {
        //margin-left: var(--menu-width);
    }
}
</style>
