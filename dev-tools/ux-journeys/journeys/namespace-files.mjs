import {pasteInEditor, settle} from "../lib.mjs"

const QUERY = `-- Daily order totals per country
SELECT country,
       COUNT(*) AS orders,
       SUM(amount) AS revenue
FROM sales.orders
WHERE order_date = CURRENT_DATE - 1
GROUP BY country
ORDER BY revenue DESC;
`

export const journey = {
    id: "namespace-files",
    title: "Gérer fichiers et KV store d'un namespace",
    summary: "L'utilisateur ouvre le namespace company.data depuis l'arborescence, crée un fichier SQL dans les Namespace Files puis ajoute une clé dans le KV Store. La page namespace est organisée avec un menu de sections vertical, dont plusieurs sont verrouillées en OSS.",
    category: "Données",
    area: "Namespaces",
    tags: ["namespace", "fichiers", "kv store", "sql", "drawer"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Arborescence des namespaces",
            caption: "La page Namespaces présente une arborescence : system (épinglé, « Keep your platform in check with system flows ») puis company avec ses enfants data, marketing et team. L'utilisateur clique sur data.",
            screen: "Namespaces › Liste",
            url: "/ui/main/namespaces",
            elements: ["File tree", "Filter bar", "Search", "Sidebar"],
            observation: "La hiérarchie par points (company.data) est rendue comme un vrai arbre, plus lisible qu'une liste plate."
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Vue d'ensemble du namespace",
            caption: "L'Overview de company.data affiche Success Ratio 82,35 %, Failed Ratio, un histogramme, un donut de 17 exécutions et un tableau des dernières exécutions avec onglets par état. Un menu vertical liste les sections du namespace.",
            screen: "Namespace › Vue d'ensemble",
            url: "/ui/main/namespaces/edit/company.data/overview",
            elements: ["Chart", "Card", "Table", "Tabs", "Filter bar", "Breadcrumb"],
            observation: "Huit sections sur seize portent un cadenas (Secrets, Variables, Policies, Revisions…) : le menu met en avant des fonctions indisponibles en OSS."
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Namespace Files vide",
            caption: "La section Namespace Files affiche un état vide illustré qui explique l'usage (Python, SQL, dbt, Terraform, lecture avec read('path/to/file')) et propose « New File » ou « Learn more ».",
            screen: "Namespace › Fichiers",
            url: "/ui/main/namespaces/edit/company.data/files",
            elements: ["Empty state", "Button", "Sidebar"],
            observation: "Bon état vide : il explique le concept et donne la syntaxe pour s'en servir dans un flow."
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Dialogue de création de fichier",
            caption: "« Create file » demande le nom avec extension (orders_summary.sql saisi) et un dossier parent optionnel, avec Cancel et Create.",
            screen: "Dialog › Créer un fichier",
            url: "/ui/main/namespaces/edit/company.data/files",
            elements: ["Modal", "Form", "Dropdown", "Button"],
            observation: ""
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Éditeur de fichier vide",
            caption: "Le fichier apparaît dans l'arbre de gauche et s'ouvre dans un onglet d'éditeur vide ; la barre d'outils propose recherche, nouveau fichier, nouveau dossier, import et export.",
            screen: "Namespace › Fichiers",
            url: "/ui/main/namespaces/edit/company.data/files",
            elements: ["File tree", "Code editor", "Tabs", "Split view"],
            observation: ""
        },
        {
            key: "s6",
            file: "06.jpg",
            title: "Requête SQL saisie",
            caption: "Une requête SQL de totaux par pays est saisie avec coloration syntaxique ; un point dans l'onglet orders_summary.sql signale des modifications non sauvegardées.",
            screen: "Namespace › Fichiers",
            url: "/ui/main/namespaces/edit/company.data/files",
            elements: ["Code editor", "File tree", "Tabs"],
            observation: ""
        },
        {
            key: "s7",
            file: "07.jpg",
            title: "Fichier enregistré",
            caption: "Après Ctrl+S, le point de l'onglet disparaît : le fichier est enregistré dans le namespace.",
            screen: "Namespace › Fichiers",
            url: "/ui/main/namespaces/edit/company.data/files",
            elements: ["Code editor", "File tree", "Tabs"],
            observation: "Aucun toast ni message ne confirme la sauvegarde : seul le point de l'onglet disparaît, contrairement au KV Store qui affiche « Successfully saved »."
        },
        {
            key: "s8",
            file: "08.jpg",
            title: "KV Store du namespace",
            caption: "La section KV Store liste la clé last_sales_report écrite par le flow daily_sales_report, avec les actions copier l'expression Pebble, éditer et supprimer. En haut : « Inherited KV pairs » et « Create ».",
            screen: "Namespace › KV Store",
            url: "/ui/main/namespaces/edit/company.data/kv",
            elements: ["Table", "Search", "Button", "Breadcrumb"],
            observation: "L'action « Copy pebble expression » par ligne fait le lien direct entre la donnée et son usage dans un flow."
        },
        {
            key: "s9",
            file: "09.jpg",
            title: "Formulaire de nouvelle clé",
            caption: "« Create » ouvre un drawer à droite : Key (warehouse_schema), Type (STRING), Value, Description et Expiration (Never), puis Save.",
            screen: "Drawer › Clé KV",
            url: "/ui/main/namespaces/edit/company.data/kv",
            elements: ["Drawer", "Form", "Dropdown", "Button"],
            observation: "Le titre du drawer devient « Update value for key 'warehouse_schema' » dès que la clé est saisie, alors qu'il s'agit d'une création."
        },
        {
            key: "s10",
            file: "10.jpg",
            title: "Clé ajoutée",
            caption: "Le drawer se ferme, la clé warehouse_schema apparaît dans le tableau avec sa description, et un toast « Successfully saved » confirme l'enregistrement.",
            screen: "Namespace › KV Store",
            url: "/ui/main/namespaces/edit/company.data/kv",
            elements: ["Table", "Toast"],
            observation: ""
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Clic sur « data »"},
        {from: "s2", to: "s3", label: "Section « Namespace Files »"},
        {from: "s3", to: "s4", label: "Clic sur « New File »"},
        {from: "s4", to: "s5", label: "Clic sur « Create »"},
        {from: "s5", to: "s6", label: "Saisie du SQL"},
        {from: "s6", to: "s7", label: "Ctrl+S"},
        {from: "s7", to: "s8", label: "Section « KV Store »"},
        {from: "s8", to: "s9", label: "Clic sur « Create »"},
        {from: "s9", to: "s10", label: "Clic sur « Save »"}
    ]
}

export async function capture({page, shot}) {
    await page.goto("/ui/main/namespaces")
    await shot("s1", {wait: 1200})

    await page.getByText("data", {exact: true}).first().click()
    await shot("s2", {wait: 1500})

    await page.locator("a[href$=\"/company.data/files\"]").first().click()
    await shot("s3", {wait: 1500})

    await page.getByRole("button", {name: "New File"}).click()
    await page.waitForTimeout(800)
    await page.keyboard.type("orders_summary.sql")
    await shot("s4", {instant: true})

    await page.locator(".kel-overlay-dialog").getByRole("button", {name: "Create"}).click()
    await page.waitForTimeout(2000)
    await shot("s5", {wait: 1000})

    await pasteInEditor(page, QUERY)
    await page.waitForTimeout(800)
    await shot("s6", {instant: true})

    await page.keyboard.press("Control+S")
    await page.waitForTimeout(1200)
    await shot("s7", {instant: true, toasts: true})

    await settle(page, 1000)
    await page.locator("a[href$=\"/company.data/kv\"]").first().click()
    await shot("s8", {wait: 1500})

    await page.getByRole("button", {name: "Create", exact: true}).click()
    await page.waitForTimeout(1200)
    const drawer = page.locator(".kel-drawer.open")
    const inputs = drawer.locator("input.kel-input__inner")
    await inputs.nth(0).fill("warehouse_schema")
    await drawer.locator("textarea").first().fill("analytics_prod")
    await inputs.nth(1).fill("Target schema for the daily sales report")
    await page.waitForTimeout(500)
    await shot("s9", {instant: true})

    await drawer.getByRole("button", {name: "Save"}).click()
    await page.waitForTimeout(1500)
    await shot("s10", {toasts: true})
}
