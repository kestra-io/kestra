import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import HomePageButtons from "./HomePageButtons.vue"

type Button = {label: string, href: string}

const buttons: Button[] = [
    {label: "Get started", href: "/docs/getting-started"},
    {label: "Tutorial", href: "/docs/tutorial#intro"},
    {label: "Plugins", href: "/docs/plugins"},
]

function mountButtons(props: {buttons?: Button[]} = {buttons}) {
    return mount(HomePageButtons, {
        props,
        global: {
            stubs: {
                ContextDocsLink: {props: ["href"], template: "<a :href=\"href\"><slot /></a>"},
                KsButton: {props: ["type", "size"], template: "<button :data-type=\"type\" :data-size=\"size\"><slot /></button>"},
            },
        },
    })
}

describe("HomePageButtons", () => {
    test("renders one link per button, in order", () => {
        const links = mountButtons().findAll("a")

        expect(links.map((link) => link.text())).toEqual(["Get started", "Tutorial", "Plugins"])
    })

    test("strips the first six characters from the href", () => {
        const wrapper = mountButtons({buttons: [{label: "Get started", href: "/docs/getting-started"}]})

        expect(wrapper.get("a").attributes("href")).toBe("getting-started")
    })

    test("removes the fragment from the href", () => {
        const wrapper = mountButtons({buttons: [{label: "Tutorial", href: "/docs/tutorial#intro"}]})

        expect(wrapper.get("a").attributes("href")).toBe("tutorial")
    })

    test("removes everything from the first fragment when the path holds several", () => {
        const wrapper = mountButtons({buttons: [{label: "Nested", href: "/docs/guide#part#section"}]})

        expect(wrapper.get("a").attributes("href")).toBe("guide")
    })

    test("styles only the first button as primary", () => {
        const types = mountButtons().findAll("button").map((button) => button.attributes("data-type"))

        expect(types).toEqual(["primary", "", ""])
    })

    test("renders every button at large size", () => {
        const sizes = mountButtons().findAll("button").map((button) => button.attributes("data-size"))

        expect(sizes).toEqual(["large", "large", "large"])
    })

    test("renders the label as the button text", () => {
        const wrapper = mountButtons({buttons: [{label: "Read the docs", href: "/docs/overview"}]})

        expect(wrapper.get("button").text()).toBe("Read the docs")
    })

    test.each([
        ["empty", {buttons: []}],
        ["omitted", {}],
    ])("renders no links when buttons is %s", (_, props) => {
        expect(mountButtons(props).findAll("a")).toHaveLength(0)
    })

    test("gives each mount its own default array", () => {
        const first = mountButtons({})
        const second = mountButtons({})

        expect(first.props("buttons")).toEqual([])
        expect(first.props("buttons")).not.toBe(second.props("buttons"))
    })
})
