import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsUpload from "../../../src/components/Form/KsUpload.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsUpload", () => {
    test("renders upload element", () => {
        const wrapper = mount(KsUpload, {
            props: {action: "#"},
            slots: {default: "<button>Upload</button>"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-upload").exists()).toBe(true)
    })

    test("default slot renders trigger content", () => {
        const wrapper = mount(KsUpload, {
            props: {action: "#"},
            slots: {default: "<span class='trigger-label'>Click to upload</span>"},
            global: globalConfig,
        })
        expect(wrapper.find(".trigger-label").exists()).toBe(true)
    })

    test("tip slot renders hint text", () => {
        const wrapper = mount(KsUpload, {
            props: {action: "#"},
            slots: {
                default: "<button>Upload</button>",
                tip: "<div class='hint'>Only PDF files</div>",
            },
            global: globalConfig,
        })
        expect(wrapper.find(".hint").exists()).toBe(true)
    })

    test("drag prop applies dragger class", () => {
        const wrapper = mount(KsUpload, {
            props: {action: "#", drag: true},
            slots: {default: "<span>Drop here</span>"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-upload-dragger").exists()).toBe(true)
    })

    test("showFileList false hides file list", () => {
        const wrapper = mount(KsUpload, {
            props: {action: "#", showFileList: false},
            slots: {default: "<button>Upload</button>"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-upload-list").exists()).toBe(false)
    })
})
