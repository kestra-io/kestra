const { openBrowser, closeBrowser, currentURL, waitFor } = require("taiko");

const assert = require("assert");
const { env } = require("process");

const browser = {
    async openBrowser(
        headless = getDefaultHeadless(),
        args = global.TAIKO_BROWSER_ARGS
    ) {
        // args.push("--window-size=1920,1080");
        // args = args.filter((x) => x !== "--window-size=1440,900");
        await openBrowser({ headless, args });
    },

    async closeBrowser() {
        await waitFor(1500);
        await closeBrowser();
    },
    async assertCurrentUrlPath(path) {
        const url = await currentURL();
        assert.strictEqual(url, process.env.TEST_BASE_URL + path);
    },
};

function getDefaultHeadless() {
    return (process.env.HEADLESS || true).toString().toLowerCase() === "true";
}

module.exports = browser;
