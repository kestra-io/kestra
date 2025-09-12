// Simple CLI entry point.
// Built to dist/kestra-devtools-cli.cjs with a shebang so it can be executed directly.

import { getWorkingDir } from "./utilities/working-dir";
import {exportTestReportSummary} from "./tests-reporting/export-test-report-summary";
import {getPRContext} from "./github-context";

function parseArgs(argv: string[]) {
    // argv[0] = node, argv[1] = script, rest are args
    const args = argv.slice(2);
    const flags: Record<string, string | boolean> = {};
    const positionals: string[] = [];

    for (let i = 0; i < args.length; i++) {
        const a = args[i];
        if (a.startsWith("--")) {
            const [k, v] = a.slice(2).split("=");
            flags[k] = v ?? true;
        } else if (a.startsWith("-") && a.length > 1) {
            const letters = a.slice(1).split("");
            letters.forEach((l) => (flags[l] = true));
        } else {
            positionals.push(a);
        }
    }

    return { flags, positionals };
}

export async function main(argv = process.argv) {
    const { flags, positionals } = parseArgs(argv);

    if (flags.h || flags.help) {
        console.log(`kestra-devtools-cli

Usage:
  kestra-devtools-cli [options] [name]

Options:
  -h, --help     Show help
  -v, --version  Show version

Examples:
kestra-devtools-cli generateTestReportSummary /Users/roman/Documents/git-repos/kestra --only-errors

`);
        return 0;
    }

    if (positionals[0] === "generateTestReportSummary") {
        const dirArg = positionals[1];
        if (!dirArg) {
            console.error(
                "Error: missing working directory argument.\nUsage: kestra-devtools-cli generateTestReportSummary <absolute-path>",
            );
            return 1;
        }
        const ci = Boolean(flags["ci"]);
        const workingDir = getWorkingDir(dirArg);
        const summary = await exportTestReportSummary(workingDir, {
            onlyErrors: Boolean(flags["only-errors"]),
            githubContext: ci ? getPRContext() : undefined
        });
        // Print to stdout so it can be piped in CI or viewed in terminal
        console.log(summary);
        return 0;
    }

    if (flags.v || flags.version) {
        // package.json is not bundled by default; prefer env-injected version if needed.
        console.log("kestra-devtools-cli v0.1.0");
        return 0;
    }

    const name = positionals[0] ?? "world";
    console.log(`Hello, ${name}!`);
    return 0;
}

// If executed directly, run main()
if (import.meta.url === `file://${process.argv[1]}`) {
    main()
        .then((code) => process.exit(code))
        .catch((err) => {
            console.error(err);
            process.exit(1);
        });
}
