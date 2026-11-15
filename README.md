# Fitwell — Plateforme de Stages de Recherche au Maroc

Fitwell met en relation des étudiants universitaires et des laboratoires de recherche au Maroc pour des stages de recherche scientifique.

- **Principe produit** : L'assistant IA propose, l'humain décide. Aucune candidature ou message n'est envoyé sans confirmation explicite.
- **Honnêteté des données** : Tous les indicateurs, jauges et compteurs sont calculés dynamiquement depuis la base de données.
- **Éthique & Vie privée** : Conformité avec la **Loi marocaine 09-08** (la photo de profil n'est visible par un labo qu'après candidature, et le score de compatibilité ne prend jamais en compte le nom, le genre, la photo ou l'origine).

---

## 1. Adresses d'accès (Ports configurés)

Afin d'éviter tout conflit avec d'autres projets locaux (ex: Datalift sur 5432, 3000, 8000), les ports hôtes sont configurés comme suit :

| Service | Port Hôte (Machine) | Port Interne (Docker) | URL d'accès |
| :--- | :--- | :--- | :--- |
| **Frontend Web** | `3001` | `80` (Nginx) | [http://localhost:3001](http://localhost:3001) |
| **Backend API** | `8080` | `8080` (JDK HttpServer) | [http://localhost:8080/api/health](http://localhost:8080/api/health) |
| **PostgreSQL** | `5433` | `5432` | `localhost:5433` (DB: `fitwell`) |

Ces ports sont modifiables à tout moment dans le fichier `.env` via :
```env
POSTGRES_HOST_PORT=5433
FRONTEND_PORT=3001
BACKEND_PORT=8080
```

---

## 2. Démarrage rapide avec Docker

Dans votre terminal à la racine du projet :

```powershell
docker compose up --build
```

### Robustesse & Healthchecks
- Le conteneur **PostgreSQL** démarre et valide son état (`pg_isready`).
- Le conteneur **Backend Java** attend que la base soit prête, applique les migrations (`V001__initial_schema.sql`), insère les données de démo et démarre le serveur HTTP.
- Le conteneur **Frontend Nginx** dépend de l'état `service_healthy` du backend : il ne démarre que lorsque le backend répond avec succès sur `/api/health`, ce qui empêche Nginx de redémarrer en boucle en cas de latence.

---

## 3. Comptes de Démonstration (Seed de Développement)

Des comptes démo réalistes sont initialisés automatiquement au premier lancement :

| Rôle | Email | Mot de passe | Description |
| :--- | :--- | :--- | :--- |
| 🎓 **Étudiant** | `yassine.bennis@univ-casa.ma` | `StudentSecure2026!` | Yassine Bennis, Master 1 Data Science, Université Hassan II |
| 🔬 **Laboratoire** | `s.elidrissi@univ-fes.ma` | `LabSecure2026!` | Dr. Salma El Idrissi, Genomics Unit, Fès |
| 🛡️ **Admin** | `admin@fitwell.ma` | `AdminSecure2026!` | Mohcine Sahtani, Administrateur plateforme |

> **Astuce** : Sur l'écran de connexion ([http://localhost:3001/login](http://localhost:3001/login)), vous trouverez des boutons de sélection rapide pour tester chaque compte en un clic.

---

## 4. Architecture Technique

### Backend : Java 21 SE Pur
- **Serveur HTTP** : `com.sun.net.httpserver.HttpServer` avec Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`).
- **Accès base de données** : Pure JDBC (`java.sql.PreparedStatement` exclusivement, aucun SQL concaténé), patron de conception DAO.
- **Sécurité des mots de passe** : `PBKDF2WithHmacSHA256` avec **600 000 itérations**, comparaison à temps constant.
- **Sessions** : Jetons `SecureRandom` 256 bits hachés en SHA-256 en base, transmis par cookie `HttpOnly; SameSite=Strict`.
- **Règles d'autorisation** : Double contrôle middleware + couche service (renvoi d'un code **404** au lieu de 403 pour les ressources d'autrui afin d'éviter l'énumération).
- **Dépendances interdites** (bloquées au build par `maven-enforcer-plugin`) :
  - `org.springframework`, `jakarta.*`, `javax.servlet`, `org.hibernate`.

### Justification des bibliothèques externes autorisées
- `org.postgresql:postgresql` : Driver JDBC officiel pour la communication avec PostgreSQL.
- `com.fasterxml.jackson.core:jackson-databind` : Sérialisation et désérialisation JSON performante et typée.
- `org.apache.pdfbox:pdfbox` : Extraction sécurisée du texte des CV PDF (avec vérification des magic bytes `%PDF-` et limite de 10 pages).
- `org.jsoup:jsoup` : Analyse HTML pour le sourcing externe (avec protection SSRF stricte contre les IP privées et réseaux Docker).
- `org.slf4j:slf4j-api` & `ch.qos.logback:logback-classic` : Journalisation structurée.
- `org.junit.jupiter` & `org.mockito` : Tests unitaires et mocks.
- `org.testcontainers:postgresql` : Tests d'intégration sur conteneurs éphémères.

### Frontend : React + TypeScript + Vite + Tailwind CSS
- Interface reproduisant fidèlement les 20 écrans de la maquette (palette bordeaux `#5B1421`, sable `#D3B996`, cartes `#E2CEB0`, boutons `#791E2D`).
- Routage sécurisé par rôle (`STUDENT`, `LAB`, `ADMIN`).
- Pastilles de notification calculées en direct depuis `/api/badges`.

---

## 5. Moteur de Score de Compatibilité Déterministe

Le score n'utilise **aucun LLM** pour le calcul du pourcentage, garantissant reproductibilité, explicabilité et équité :
$$\text{Score} = w_{\text{skills}} \cdot \text{Couverture}_{\text{compétences}} + w_{\text{interest}} \cdot \text{Adéquation}_{\text{domaine}} + w_{\text{dates}} \cdot \text{Disponibilité}$$

- **Couverture des compétences demandées** :
  $$\text{Couverture} = \frac{\sum_{s \in S_{\text{possédées}} \cap S_{\text{requises}}} w(s) + \sum_{s \in S_{\text{possédées}} \cap S_{\text{optionnelles}}} w(s)}{\sum_{s \in S_{\text{requises}}} w(s) + \sum_{s \in S_{\text{optionnelles}}} w(s)}$$
- **Paliers de correspondance** :
  - $\ge 90\%$ : *Excellent match*
  - $75\% - 89\%$ : *Strong match*
  - $60\% - 74\%$ : *Good match*
  - $< 60\%$ : *Low match*
- **Variables strictement exclues** : Nom, prénom, genre, photo, université d'origine et nationalité.

---

## 6. Lancement en Local (Sans Docker)

Si vous préférez exécuter le backend et le frontend hors Docker :

1. Démarrez uniquement PostgreSQL :
   ```powershell
   docker compose up -d postgres
   ```
2. Lancez le frontend :
   ```powershell
   cd frontend
   npm run dev
   ```
   Accessible sur `http://localhost:3001`.

---

## 7. Parcours d'Inscription & Sécurité Auth

### Page d'inscription unique `/register`
- **Design fidèle aux tokens** : Palette `#5B1421` (bordeaux), `#D3B996` (sable), `#E2CEB0` (cartes), typographies et arrondis conformes à `docs/design/tokens.md`.
- **Champs du formulaire** :
  - `Name` : Nom complet de l'utilisateur.
  - `I am…` : Menu déroulant obligatoire (`Student`, `HR`, `CEO / Lab director`) avec invite initiale non sélectionnable `"Select your profile"`. Aucune saisie libre.
  - `Lab name` et `City` : Champs conditionnels affichés **uniquement** si `HR` ou `CEO / Lab director` est sélectionné.
  - `Email` : Champ intitulé "Email" acceptant n'importe quelle adresse email valide (`name@example.com`, `gmail.com`, `outlook.com`, universitaire, etc.). Aucune restriction de domaine ne bloque l'inscription.
  - `Password` : Mot de passe sécurisé (12 caractères minimum, pas dans les mots de passe courants, bouton œil afficher/masquer, indicateur visuel de force informatif).
  - `Politique de confidentialité (Loi 09-08)` : Case d'acceptation obligatoire.
  - Bouton `Register` et lien `"Already have an account? Login"` redirigeant vers `/login`.

### Règles de sécurité renforcées
1. **Contrôle d'accès strict** : Le client transmet `accountType` validé côté serveur contre l'ensemble fermé `{STUDENT, HR, CEO}`. Toute autre valeur (`ADMIN`, `LAB`, `SUPERUSER`) est rejetée avec un code HTTP 400. Tout champ `role` fourni par le client est strictement ignoré. Le rôle ADMIN ne peut jamais être obtenu par inscription.
2. **Acceptation universelle des emails** : Tout format d'email valide (RFC 5321, max 254 caractères, trim, minuscules, unicité insensible à la casse) est accepté à l'inscription. Aucun contrôle de domaine ne bloque un étudiant, un RH ou un CEO.
3. **Statut d'approbation des laboratoires (PENDING)** :
   - `STUDENT` crée immédiatement un compte étudiant.
   - `HR` et `CEO` créent un compte `LAB` avec statut non vérifié (`verified = false`), une adhésion `lab_members` et une `claim_requests` en statut `PENDING`.
   - Si une adresse email personnelle ou gratuite (ex. `gmail.com`, `yahoo.com`) est utilisée par un RH ou CEO, la demande dans la section admin "Labs and claims" affiche le badge `Personal email` avec la suggestion de solliciter l'URL d'une page de staff / annuaire universitaire avant validation.
   - Tant que l'administrateur n'a pas approuvé la demande via `/api/admin/labs-claims/{id}/approve`, le labo ne peut ni publier d'offre (403 Forbidden) ni consulter de candidats, et est redirigé vers l'écran `/lab/pending` (*"Your lab request is pending approval"*).
4. **Université de l'étudiant & Badge "Verified student"** :
   - L'université n'est plus déduite de l'adresse email : l'étudiant sélectionne son établissement dans une liste déroulante des universités marocaines ou choisit "Other" pour saisir son université librement (disponible à l'onboarding et dans les Paramètres / Settings).
   - La table `allowed_university_domains` sert exclusivement à afficher un badge `Verified student` si le domaine de l'email correspond à une université répertoriée. Elle ne bloque jamais l'inscription ni la connexion, et n'est pas utilisée pour le calcul du score de compatibilité.
5. **Anti-énumération des comptes** : Réponse générique identique (`"If your email is eligible, a verification link has been sent to your address."`) que l'adresse existe déjà ou non en base.
6. **Vérification d'email obligatoire & Outils de Développement (`APP_ENV=dev`)** :
   - Jeton cryptographique 256 bits à usage unique (durée 24 h). Toute tentative de connexion est rejetée (403 Forbidden) tant que l'email n'a pas été validé.
   - **En développement (`APP_ENV=dev`)**, plusieurs mécanismes facilitent le test sans serveur SMTP réel :
     1. **Lien cliquable direct sur `/register`** : Sur l'écran *"Check your inbox"*, un encadré `🛠️ DEV ONLY : verification link` s'affiche avec le lien cliquable vers `/verify-email?token=...`.
     2. **Bouton "Resend verification email" sur `/login`** : Si un utilisateur tente de se connecter avec un compte non vérifié, le bouton apparaît sous l'erreur. Un clic renvoie un jeton (limité à 3 essais par 15 min, réponse anti-énumération) et affiche immédiatement le lien cliquable en mode dev.
     3. **Simulation dans les journaux (logs)** : Le lien complet est journalisé dans la sortie standard du backend : `[EMAIL VERIFICATION SIMULATION] Verification link for ...`.
     4. **Auto-validation immédiate** : En activant `DEV_AUTO_VERIFY_EMAIL=true` dans `.env`, les nouveaux comptes créés sont automatiquement marqués vérifiés dès l'inscription, sautant complètement l'étape de validation.
   - **En production (`APP_ENV=production`)**, aucun de ces encadrés ni liens de simulation n'est renvoyé par l'API ni affiché dans l'interface.
7. **Écran de connexion `/login`** :
   - Identifiant "Email" (`name@example.com`), mot de passe avec œil afficher/masquer, bouton `Login`, lien vers `Register`.
   - Message d'erreur indifférencié en cas de mauvais email ou mauvais mot de passe (`"Invalid email or password"`).
   - Routage automatique post-connexion :
     - Étudiant : redirigé vers `/student/onboarding` à la première connexion, puis `/student/home`.
     - Labo approuvé : redirigé vers `/lab/dashboard`.
     - Labo en attente : redirigé vers `/lab/pending`.
     - Admin : redirigé vers `/admin/overview`.
   - La section `QUICK DEMO ACCOUNTS (SEED)` ne s'affiche qu'avec `APP_ENV=dev`.

---

## 8. Suite de Tests Automatisés

### Tests Backend (`RegisterAndAuthSecurityTest.java` & `AuthorizationSecurityTest.java`)
- Inscription acceptant les emails `gmail.com` pour Student, HR et CEO (HTTP 200).
- Rejet des formats d'email invalides (sans `@` ou dépassant 254 caractères, HTTP 400).
- Étudiant avec `gmail.com` recevant un jeton de vérification et bloqué à la connexion tant que l'email n'est pas validé (HTTP 403).
- Inscription HR avec `gmail.com` créant un compte labo en attente (`verified = false`) avec drapeau `Personal email` (`domain_matches = false`) visible par l'administrateur.
- Inscription réussie pour n'importe quel domaine d'email valide sans blocage institutionnel.
- Inscription `HR` créant un compte labo en attente avec blocage de publication d'offre (HTTP 403).
- Rejet strict de `accountType=ADMIN` (HTTP 400).
- Ignorance du champ payload `role=ADMIN` garantissant l'impossibilité d'élever ses privilèges.
- Rejet d'`accountType` manquant ou invalide (HTTP 400).
- Protection anti-énumération retournant la même réponse pour un email déjà utilisé.
- Rejet des mots de passe de moins de 12 caractères (HTTP 400).
- Autorisation immédiate de publication d'offre après approbation par l'administrateur (HTTP 201).

### Tests Frontend Vitest (`RegisterPage.test.tsx`, `LoginPage.test.tsx`, `StudentOnboardingPage.test.tsx`, `StudentExplorePage.test.tsx`, `StudentOfferDetailPage.test.tsx`)
- Rendu fidèle de tous les champs de `/register` avec champ Email et placeholder `name@example.com`.
- Vérification que `Lab name` et `City` ne sont pas affichés lorsque `Student` est sélectionné.
- Vérification que `Lab name` et `City` s'affichent dynamiquement lorsque `HR` ou `CEO / Lab director` est sélectionné.
- Vérification de la disparition dynamique des champs labo si l'utilisateur re-sélectionne `Student`.
- Test de l'onboarding "Your CV" : zone de dépôt, pastilles éditables, suppression de compétences, mise à jour dynamique du compteur de champs lus et accessibilité clavier.

---

## 9. Espace Étudiant : Parcours d'Onboarding "Your CV" (Étape 2 sur 4)

L'écran d'onboarding étudiant reproduit fidèlement la maquette de référence `docs/design/pages/page-02.png` avec toutes les données dynamiques extraites du vrai CV.

### 1. Parcours utilisateur & États de navigation
- **Première connexion** : Un utilisateur avec le rôle `STUDENT` dont l'onboarding n'est pas terminé (`onboarding_completed = false`) est automatiquement redirigé vers `/onboarding/cv`. Une fois terminé, les connexions ultérieures mènent directement à `/student/home`.
- **Barre de progression en 4 segments** :
  1. `Account` (terminé, barre active bordeaux `#5B1421`)
  2. `Your CV` (étape active, barre bordeaux `#5B1421`)
  3. `Interests` (étape suivante `/onboarding/interests`)
  4. `Availability` (étape suivante `/onboarding/availability`)
- **"Skip for now"** : Permet à l'étudiant d'ignorer temporairement l'étape via `POST /api/student/onboarding/skip`. L'étudiant est redirigé vers `/student/home`, qui affiche un bandeau d'alerte bienveillant :
  > *"Upload your CV: Complete your CV upload to unlock full matching accuracy."* avec un bouton direct vers `/onboarding/cv`.

### 2. Zone de dépôt accessible & Analyse du CV
- **Dépôt universel** : Glisser-déposer (drag-and-drop), explorateur de fichiers au clic et accessibilité clavier complète (`tabIndex={0}`, déclenchement par touches `Entrée` ou `Espace`, `role="button"`, `aria-label`).
- **Indicateur d'analyse** : Pendant le traitement, affiche un message dynamique avec retour vocal accessible (`aria-live="polite"`) : *"Analyzing your CV..."*.
- **Affichage des vraies métadonnées** : Dès l'analyse terminée, affiche le vrai nom du fichier et sa vraie taille (ex: `CV-Yassine.pdf` et `Uploaded - 214 KB`).
- **Actions** :
  - Bouton *"Replace file"* : permet de sélectionner un nouveau PDF (l'ancien brouillon et fichier physique sont proprement nettoyés).
  - Bouton *"Looks right, continue"* : valide les champs corrigés par l'étudiant et passe à l'étape suivante.

### 3. Panneau "What Fitwell found" & Pastilles interactives
- **Quatre sections structurées** :
  - **Education** : niveau et formation détectés (ex: `Master 1, Data science`).
  - **Skills** : compétences techniques identifiées sous forme de pastilles roses élégantes (`#F5E6E8` avec texte bordeaux `#5B1421`).
  - **Languages** : langues parlées (ex: `Arabic`, `French`, `English`).
  - **Projects** : projets académiques ou personnels extraits (ex: `Image segmentation`, `Research abstracts scraper`).
- **Formule exacte du compteur de champs lus** :
  $$\text{fieldsReadCount} = (\text{education} \ne \emptyset \ ?\ 1 : 0) + \text{skills.size}() + \text{languages.size}() + \text{projects.size}()$$
  Le compteur en haut à droite (*"N fields read"*) est réévalué en direct à chaque ajout ou suppression de pastille.
- **Édition directe par l'étudiant** :
  - Retrait d'une pastille en un clic sur la croix (`×`).
  - Modification inline du texte d'une pastille (clic, saisie et validation par `Entrée` ou icône de validation).
  - Bouton d'ajout `+ Add` pour chaque section permettant de compléter manuellement le profil.
- **Bandeau "Check this" conditionnel** :
  - Calculé strictement par règles déterministes : affiché **uniquement** si un projet ne dispose pas de résultat chiffré, si aucune formation ou langue n'a été détectée, ou si moins de 3 compétences sont trouvées.
  - Dès que l'étudiant lève ces doutes ou si le CV est complet, le bandeau est **strictement masqué**.
- **Gestion des PDF scannés / images** :
  - Si le PDF ne contient aucun texte sélectionnable, l'API renvoie `noTextFound: true` sans inventer ni halluciner de contenu. Un message informatif invite l'étudiant à saisir manuellement ses données ou à téléverser un PDF textuel.

### 4. Sécurité Backend & API Java 21 SE
- **Endpoints dédiés** :
  - `POST /api/student/onboarding/cv` : Réception binaire du PDF, lecture des métadonnées dans les en-têtes `X-File-Name` et `Content-Type`.
  - `GET /api/student/onboarding/state` : Récupération de l'état d'onboarding (`completed`, `skipped`, brouillon existant).
  - `PUT /api/student/onboarding/cv/confirm` : Validation et écriture des champs corrigés dans `student_profiles`, `student_skills` et `student_projects`.
  - `POST /api/student/onboarding/skip` : Marquage de l'onboarding comme ignoré.
  - `GET /api/student/onboarding/draft/{id}` : Consultation d'un brouillon.
- **Vérification cryptographique des magic bytes** :
  - Les 5 premiers octets du fichier doivent être rigoureusement `%PDF-` (`0x25 0x50 0x44 0x46 0x2D`). Un fichier HTML ou texte renommé en `.pdf` est immédiatement rejeté avec une erreur 400.
- **Isolation stricte des brouillons (DRAFT)** :
  - Les données extraites sont stockées sous statut `DRAFT` dans la table `cvs`. **Rien** n'est écrit dans le profil public, les compétences ou les projets tant que l'étudiant n'a pas confirmé explicitement.
  - Le fichier physique est stocké avec un nom UUID aléatoire dans un volume Docker dédié hors de la racine web publique.
- **Anti-énumération & Protection d'accès** :
  - `GET /api/student/onboarding/draft/{id}` renvoie un code **404 Not Found** (et jamais 403) si le brouillon appartient à un autre étudiant, empêchant toute déduction d'identifiants existants.
- **Limitation de débit (Rate Limiting)** :
  - Maximum **10 téléversements de CV par heure et par étudiant** (`countRecentCvUploads`). Toute tentative supplémentaire déclenche un blocage 409/429.
- **Protection de la vie privée & Journalisation d'audit** :
  - Les informations personnelles identifiables (email, téléphone) sont nettoyées avant tout traitement.
  - Les journaux d'audit (`AuditLog`) consignent l'action `STUDENT_CV_DRAFT_UPLOADED` sans jamais sauvegarder ni journaliser le contenu textuel ou brut du CV.



