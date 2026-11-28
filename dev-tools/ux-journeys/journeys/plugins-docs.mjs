export const journey = {
    id: "plugins-docs",
    title: "Consulter la documentation d'un plugin",
    summary: "L'utilisateur parcourt le catalogue de plugins, ouvre le groupe Core puis la documentation de la tâche Approval, nouvelle sur develop. La page combine description, propriétés filtrables et exemples YAML copiables.",
    category: "Documentation",
    area: "Plugins",
    tags: ["plugins", "documentation", "propriétés", "exemples", "approval"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Catalogue de plugins",
            caption: "La page Plugins s'ouvre sur un encart « Plugin versioning is an Enterprise feature » (Talk to us, Read the docs), une recherche, un filtre Core, un choix Cards / Table et une seule carte « Core – Included with Kestra – 60 tasks ». Les boutons Upload et Add plugin sont verrouillés.",
            screen: "Plugins › Liste",
            url: "/ui/main/plugins",
            elements: ["Card", "Search", "Alert", "Button", "Dropdown"],
            observation: "L'encart commercial occupe plus de place que l'unique plugin disponible dans cette image sans plugins."
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Groupe Core",
            caption: "Le groupe core liste ses 16 sous-groupes en cartes (debug, KV, output, chart, log, runner, trigger, flow…) avec leur nombre de tâches, et une table des matières à gauche avec une recherche « Search 73 tasks ».",
            screen: "Plugin › Détail",
            url: "/ui/main/plugins/groups/core",
            elements: ["Card", "Search", "Breadcrumb", "Sidebar"],
            observation: "Le compteur n'est pas cohérent : la carte du catalogue annonce 60 tasks, la recherche du groupe « Search 73 tasks »."
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Documentation de la tâche Approval",
            caption: "La page Approval (Task, Certified) explique le fonctionnement (onWait, onApprove, onDeny, API de review), affiche le type YAML et des sections repliables Examples et Properties avec un filtre et un choix All / Required.",
            screen: "Plugin › Tâche",
            url: "/ui/main/plugins/io.kestra.plugin.core.flow.Approval",
            elements: ["Card", "Search", "Tag", "Breadcrumb", "Sidebar"],
            observation: "La description renvoie à une page Approvals et à une « EE app » qui n'existent pas dans l'édition OSS."
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Propriété filtrée et dépliée",
            caption: "Le filtre « deny » réduit la liste à denyBehavior et onDeny ; denyBehavior déplié montre la valeur par défaut CANCEL, les valeurs possibles (CONTINUE, WARN, FAIL, SUCCEED, CANCEL, KILL) et sa description. Les sections Outputs et Definitions suivent.",
            screen: "Plugin › Tâche",
            url: "/ui/main/plugins/io.kestra.plugin.core.flow.Approval",
            elements: ["Search", "Tag", "Card"],
            observation: "Le filtre de propriétés et les valeurs possibles en tags rendent la référence rapide à parcourir."
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Exemple YAML",
            caption: "La section Examples dépliée affiche un flow complet utilisant Approval avec des branches onApprove et onDeny, et un bouton de copie en haut à droite du bloc.",
            screen: "Plugin › Tâche",
            url: "/ui/main/plugins/io.kestra.plugin.core.flow.Approval",
            elements: ["Code editor", "Button"],
            observation: ""
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Clic sur la carte « Core »"},
        {from: "s2", to: "s3", label: "Clic sur « Approval » dans Flow (14)"},
        {from: "s3", to: "s4", label: "Filtre « deny » et dépliage de denyBehavior"},
        {from: "s4", to: "s5", label: "Dépliage de « Examples »"}
    ]
}

export async function capture({page, shot}) {
    await page.goto("/ui/main/plugins")
    await shot("s1", {wait: 1200})

    await page.getByRole("heading", {name: "Core", exact: true}).click()
    await shot("s2", {wait: 1500})

    await page.getByText("Flow (14)").first().click()
    await page.locator("a[href$=\"core.flow.Approval\"]").first().click()
    await shot("s3", {wait: 1500})

    const propertyFilter = page.getByPlaceholder(/Filter properties/)
    await propertyFilter.fill("deny")
    await page.waitForTimeout(600)
    await page.getByText("denyBehavior", {exact: true}).last().click()
    await page.waitForTimeout(800)
    await page.getByText("Properties", {exact: true}).first().evaluate((element) => element.scrollIntoView({block: "start"}))
    await shot("s4")

    await propertyFilter.fill("")
    const examples = page.getByText("Examples", {exact: true}).first()
    await examples.click()
    await page.waitForTimeout(800)
    await examples.evaluate((element) => element.scrollIntoView({block: "start"}))
    await page.mouse.wheel(0, -60)
    await shot("s5")
}
