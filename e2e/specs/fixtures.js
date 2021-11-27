const fs = require("fs");

module.exports.loadFixture = (fixture) => {
    return fs.readFileSync(`fixtures/${fixture}`, "utf8");
};
