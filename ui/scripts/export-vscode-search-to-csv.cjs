/* eslint-disable */

// How to use:
//   1. In VS Code, perform a search.
//   2. Click "Open in editor" to open the search results in a `.code-search` file
//   3. Save the file
//   4. In terminal, run `node export-vscode-search-to-csv.js path/to/results.code-search path/to/exported.csv`

const fs = require("fs");
const path = require("path");
const readline = require("readline");

// Constants
const FILENAME_REGEX = /^([^\s+].*?):$/;
const LINE_REGEX = /^\s+(\d+):\s*(.*?)\s*$/;
const escapeString = (str) => `"${str}"`;
const logError = (msg, logType = console.error, code = 1) => {
  logType(msg);
  process.exit(code);
};

// Parsing
const [cmd, script, ...args] = process.argv;

if (!args.length) {
  logError(
    `Usage: ${cmd} ${script} /path/to/input/file.code-search /path/to/output/file`,
    console.log
  );
}

const [inputFile, outputFile] = args;
const extension = path.extname(inputFile);

if (extension !== ".code-search") {
  logError(
    `ERROR: ${extension} not supported. Supported extensions:\n\t.code-search`
  );
}

if (!outputFile) {
  logError(
    `ERROR: you must provide an output file.\n\t${cmd} ${script} ${inputFile} /path/to/output/file`
  );
  process.exit(1);
}

if (fs.existsSync(outputFile)) {
  logError(
    `ERROR: ${outputFile} already exists! Please remove it and try again.`
  );
  process.exit(1);
}

// Set up streams
const writer = fs.createWriteStream(outputFile, {flags: "wx+"});
const writeRow = ({path, lineNumber, result}) =>
  writer.write(
    `${escapeString(path)},${escapeString(lineNumber)},${escapeString(
      result
    )}\n`
  );

// write header row
writeRow({path: "Path", lineNumber: "Line number", result: "Result"});

// Set up read stream
let currentFile;
let count = 0;
const readInterface = readline.createInterface({
  input: fs.createReadStream(inputFile),
});

readInterface.on("line", (line) => {
  if (typeof line === "string") {
    if (FILENAME_REGEX.test(line)) {
      currentFile = line.match(FILENAME_REGEX)[1];
    } else if (LINE_REGEX.test(line)) {
      const [, lineNumber, result] = line.match(LINE_REGEX);
      if (lineNumber && result) {
        writeRow({
          path: currentFile,
          lineNumber,
          result,
        });
        count += 1;
      }
    }
  }
});

readInterface.on("close", () => {
  console.log(`Done! Wrote ${count} rows to ${outputFile}`);
});