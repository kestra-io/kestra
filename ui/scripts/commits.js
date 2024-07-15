const fs = require("fs");
const {execSync} = require("child_process");

// Get the last commit hash and date details
const hash = execSync("git rev-parse HEAD").toString().trim();
const date = execSync("git log -1 --format=%cd").toString().trim();

// Prepare the comment to add to the file
const comment = `
<!--
  Commit: ${hash}
  Date: ${date}
-->
`;

// Define the path to the index.html file
const path = "../webserver/src/main/resources/ui/index.html";

// Read the content of index.html
const content = fs.readFileSync(path, "utf8");

// Write the comment at the beginning of index.html
fs.writeFileSync(path, comment + content);

console.log(`Added commit details to ${path}`);
