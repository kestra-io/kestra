// Runs a shell command, samples the summed RSS of its whole process tree every 50 ms (Linux, via ps),
// and appends {name, wallMs, peakTreeRssKb, exitCode} as one JSON line to the given file.
import {spawn, execFileSync} from "node:child_process";
import {appendFileSync} from "node:fs";

const [outFile, name, command] = process.argv.slice(2);
const start = performance.now();
const child = spawn("bash", ["-c", command], {stdio: "inherit"});
let peak = 0;

function treeRssKb(root) {
    const rows = execFileSync("ps", ["-eo", "pid=,ppid=,rss="], {encoding: "utf8"}).trim().split("\n")
        .map((l) => l.trim().split(/\s+/).map(Number));
    const kids = new Map();
    for (const [pid, ppid] of rows) kids.set(ppid, [...(kids.get(ppid) ?? []), pid]);
    const rss = new Map(rows.map(([pid, , kb]) => [pid, kb]));
    let sum = 0;
    for (const stack = [root]; stack.length;) {
        const pid = stack.pop();
        sum += rss.get(pid) ?? 0;
        stack.push(...(kids.get(pid) ?? []));
    }
    return sum;
}

const timer = setInterval(() => { peak = Math.max(peak, treeRssKb(child.pid)); }, 50);
child.on("exit", (code) => {
    clearInterval(timer);
    const wallMs = Math.round(performance.now() - start);
    appendFileSync(outFile, JSON.stringify({name, wallMs, peakTreeRssKb: peak, exitCode: code}) + "\n");
    process.exit(0);
});
