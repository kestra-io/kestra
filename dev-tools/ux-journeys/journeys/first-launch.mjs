export const journey = {
    id: "first-launch",
    title: "Premier lancement et création de l'admin",
    summary: "Sur une instance neuve, l'utilisateur crée le compte administrateur, répond à un court questionnaire puis entre dans l'interface. Un stepper vertical en trois étapes guide le parcours jusqu'à l'AI Copilot, où une visite guidée est proposée.",
    category: "Onboarding",
    area: "Setup",
    tags: ["setup", "admin", "mot de passe", "questionnaire", "visite guidée"],
    steps: [
        {
            key: "s1",
            file: "01.jpg",
            title: "Formulaire admin vide",
            caption: "Première visite : la page /ui/setup affiche le stepper (Create admin user, Tell us more, Start Kestra UI) et un formulaire avec email, mot de passe et confirmation. Les règles du mot de passe sont listées en gris et le bouton « Create admin user » est désactivé.",
            screen: "Setup › Admin",
            url: "/ui/setup",
            elements: ["Form", "Onboarding", "Button"],
            observation: "Les règles de mot de passe sont affichées avant la saisie, ce qui évite l'essai-erreur."
        },
        {
            key: "s2",
            file: "02.jpg",
            title: "Formulaire admin rempli",
            caption: "L'email admin@kestra.local et le mot de passe sont saisis : les quatre règles et « Passwords match » passent au vert, et le bouton « Create admin user » devient actif.",
            screen: "Setup › Admin",
            url: "/ui/setup",
            elements: ["Form", "Onboarding", "Button"],
            observation: "Validation en temps réel règle par règle, avec un feedback vert clair."
        },
        {
            key: "s3",
            file: "03.jpg",
            title: "Questionnaire « Tell us more »",
            caption: "Étape 2 « Help us improve Kestra OSS » : un menu pour l'objectif principal (ici « Learning / exploring »), des cases pour les usages prévus et une option d'inscription aux nouveautés par email. Un bouton « Skip » permet de passer.",
            screen: "Setup › Profil",
            url: "/ui/setup",
            elements: ["Form", "Dropdown", "Onboarding", "Button"],
            observation: "L'étape est facultative (Skip en haut à droite) et l'inscription email n'est pas cochée par défaut."
        },
        {
            key: "s4",
            file: "04.jpg",
            title: "Confirmation « You're all set »",
            caption: "Les deux premières étapes sont cochées en vert ; une carte « Congratulations! You're all set! » propose un unique bouton « Start Kestra UI ».",
            screen: "Setup › Terminé",
            url: "/ui/setup",
            elements: ["Onboarding", "Button", "Card"],
            observation: "Étape de confirmation sans contenu supplémentaire : elle ajoute un clic sans information (pas de rappel des identifiants ni des prochaines étapes)."
        },
        {
            key: "s5",
            file: "05.jpg",
            title: "Arrivée sur l'AI Copilot",
            caption: "L'utilisateur atterrit sur la page AI Copilot (« Turn your idea into a workflow ») avec un champ de prompt, des suggestions et un sélecteur de modèle « Kestra (free tier) ». Un panneau « Welcome to Kestra » propose de lancer la visite guidée ou de la passer.",
            screen: "AI Copilot",
            url: "/ui/main/ai",
            elements: ["Sidebar", "Onboarding", "Card", "Button", "Search"],
            observation: "La page d'accueil par défaut est l'AI Copilot et non le dashboard ; la sidebar affiche déjà des entrées verrouillées (Apps, Tests) réservées à l'Enterprise."
        }
    ],
    edges: [
        {from: "s1", to: "s2", label: "Saisie email et mot de passe"},
        {from: "s2", to: "s3", label: "Clic sur « Create admin user »"},
        {from: "s3", to: "s4", label: "Clic sur « Continue »"},
        {from: "s3", to: "s4", label: "Clic sur « Skip »"},
        {from: "s4", to: "s5", label: "Clic sur « Start Kestra UI »"}
    ]
}

export async function capture({page, shot, credentials}) {
    await page.goto("/ui/")
    await shot("s1")

    await page.locator("input[type=email]").fill(credentials.username)
    const passwords = page.locator("input[type=password]")
    await passwords.nth(0).fill(credentials.password)
    await passwords.nth(1).fill(credentials.password)
    await passwords.nth(1).blur()
    await shot("s2")

    await page.getByRole("button", {name: "Create admin user"}).click()
    await page.locator(".survey-select").click()
    await page.getByRole("option", {name: "Learning / exploring"}).click()
    await page.getByText("Data workflows", {exact: true}).click()
    await page.getByText("Scheduling & recurring jobs").click()
    await shot("s3")

    await page.getByRole("button", {name: "Continue"}).click()
    await shot("s4", {wait: 2500})

    await page.getByRole("button", {name: "Start Kestra UI"}).click()
    await page.waitForURL(/\/ui\/main\/ai/)
    await shot("s5", {wait: 3000})
}
