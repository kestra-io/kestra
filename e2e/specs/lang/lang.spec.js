const { setEditorText } = require("../editor");
const { loadFixture } = require("../fixtures");
const { goto, text, click, dropDown, toRightOf, waitFor } = require("taiko");
const browser = require("../../browser");
const assert = require("assert").strict;

describe("Test on quote wizard", () => {
    beforeAll(async () => {
        await browser.openBrowser();
    });

    afterAll(async () => {
        await browser.closeBrowser();
    });

    describe("As user I want to change UI language", () => {
        test("I go to settings and change language", async () => {
            await goto(process.env.TEST_BASE_URL + "/ui/flows");
            await assert.ok(text("Flows").exists());
            await click("Settings");
            await assert.ok(text("Language").exists());
            await dropDown(toRightOf("Language")).select("Français");
            await assert.ok(text("enregistré avec succès").exists());
            await assert.ok(text("Langue").exists());
            await assert.ok(text("Accueil").exists());
            await click("Flows");
            await assert.ok(text("Chercher").exists());
            await assert.ok(text("Espace de nom").exists());
            await click("Paramètres");
            await assert.ok(text("Langue").exists());
            await dropDown(toRightOf("Langue")).select("English");
            await assert.ok(text("Language").exists());
            await assert.ok(text("is successfully saved").exists());
        });
    });
});
