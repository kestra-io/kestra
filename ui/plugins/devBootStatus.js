import fs from "fs"
import net from "net"
import path from "path"
import {execFile} from "child_process"
import {promisify} from "util"
import {fileURLToPath} from "url"

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const execFileAsync = promisify(execFile)

const STATUS_PATH = "/__kestra/dev-status"
const GRADLE_CLIENT_MAINS = ["org.gradle.wrapper.GradleWrapperMain", "org.gradle.launcher.GradleMain"]

/** @param {string} target */
function acceptsConnections(target) {
    const {hostname, port, protocol} = new URL(target)
    return new Promise((resolve) => {
        const socket = net.connect({host: hostname, port: Number(port) || (protocol === "https:" ? 443 : 80)})
        const settle = (/** @type {boolean} */ connected) => {
            socket.destroy()
            resolve(connected)
        }
        socket.setTimeout(2000)
        socket.once("connect", () => settle(true))
        socket.once("error", () => settle(false))
        socket.once("timeout", () => settle(false))
    })
}

/** @param {string} target */
async function probeBackend(target) {
    if (!await acceptsConnections(target)) return "down"
    try {
        await fetch(target, {method: "HEAD", redirect: "manual", signal: AbortSignal.timeout(5000)})
        return "up"
    } catch {
        return "starting"
    }
}

/** `null` when it cannot be told, e.g. on Windows or with the build running from an IDE. */
async function isGradleRunning() {
    if (process.platform === "win32") return null
    try {
        const {stdout} = await execFileAsync("ps", ["-eo", "args="])
        return GRADLE_CLIENT_MAINS.some((main) => stdout.includes(main))
    } catch {
        return null
    }
}

/** index.html points at main.js while the file is main.ts. */
const withoutExtension = (/** @type {string} */ file) => file.replace(/\.[jt]s$/, "")

/** @param {import("vite").ViteDevServer} server */
function depsOptimizerPhase(server) {
    const optimizer = server.environments.client.depsOptimizer
    if (optimizer?.scanProcessing) return "scanning"
    if (optimizer && Object.values(optimizer.metadata.discovered).some((dep) => dep.processing)) return "bundling"
    return "idle"
}

/**
 * Dev-only: lists what the boot loader is waiting for (backend, dependency optimization, app modules),
 * so a slow `npm run dev` says why instead of only showing the animated logo.
 * @param {string} target the backend the API proxy points to
 * @returns {import("vite").Plugin}
 */
export function devBootStatus(target) {
    let entry = ""
    return {
        name: "dev-boot-status",
        // Storybook serves its iframe through transformIndexHtml too, and the injected checklist breaks Monaco stories there.
        apply: (_config, {command}) => command === "serve" && !process.env.STORYBOOK,
        configResolved(config) {
            const src = fs.readFileSync(path.resolve(config.root, "index.html"), "utf-8").match(/<script type="module" src="\/?([^"]+)"/)?.[1]
            entry = src ? withoutExtension(path.resolve(config.root, src)) : ""
        },
        configureServer(server) {
            server.middlewares.use(STATUS_PATH, async (_req, res) => {
                const backend = await probeBackend(target)
                const gradleRunning = backend === "down" ? await isGradleRunning() : null
                res.setHeader("Content-Type", "application/json")
                res.setHeader("Cache-Control", "no-store")
                res.end(JSON.stringify({backend, gradleRunning, deps: depsOptimizerPhase(server)}))
            })
        },
        // Appended after the entry's own body, which only runs once Module Federation and every import have.
        transform(code, id) {
            if (entry && withoutExtension(id) === entry) return {code: `${code}\nwindow.__kestraDevBootStatus?.()\n`, map: null}
        },
        transformIndexHtml() {
            const markup = fs.readFileSync(path.resolve(__dirname, "../dev-boot-status.html"), "utf-8")
            return [{
                tag: "div",
                attrs: {id: "dev-boot-status"},
                children: markup.replaceAll("__BACKEND_URL__", target).replaceAll("__STATUS_PATH__", STATUS_PATH),
                injectTo: "body",
            }]
        },
    }
}
