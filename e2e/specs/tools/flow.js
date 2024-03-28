const { setEditorText } = require("../editor");
const { loadFixture } = require("../fixtures");
const yaml = require("js-yaml");
const { click, waitFor, goto, text } = require("taiko");
const assert = require("assert").strict;

module.exports.flowTools = {
    add: async (fixtureName, transform) => {
        await goto(process.env.TEST_BASE_URL + "/ui/flows");
        await click("Create");
        let fixture = loadFixture(fixtureName);
        if (transform) {
            fixture = yaml.dump(transform(yaml.load(fixture)));
        }
        await setEditorText(fixture);
        await waitFor(1000);
        assert.ok(await text("Save").isVisible());
        await click("Save");
        await waitFor(2000);
    },
};
