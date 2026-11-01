# SkillMirror

![License](https://img.shields.io/badge/license-MIT-blue)

> **Le miroir de vos compétences.** Application mobile Android qui évalue le niveau réel d'un candidat, lui montre ce qui lui manque par rapport au marché, lui propose un parcours d'apprentissage, puis le met en relation avec des offres d'emploi et des recruteurs.

Projet du module **Développement d'applications mobiles** (2026).

> Remplacez `OWNER` dans les badges par le nom de votre organisation GitHub.

---

## Sommaire

1. [Présentation](#présentation)
2. [Fonctionnalités](#fonctionnalités)
3. [Architecture](#architecture)
4. [Stack technique](#stack-technique)
5. [Structure du dépôt](#structure-du-dépôt)
6. [Prérequis](#prérequis)
7. [Démarrage rapide](#démarrage-rapide)
8. [Tests](#tests)
9. [CI/CD](#cicd)
10. [Workflow Git](#workflow-git)
11. [Documentation](#documentation)
12. [Équipe](#équipe)
13. [Feuille de route](#feuille-de-route)
14. [Licence](#licence)

## Présentation

SkillMirror répond à trois questions : *Qu'est-ce que je maîtrise ? Que me demande le marché ? Quelles offres me correspondent ?*

Le parcours : **formulaire et CV → score initial → compétences à développer → ressources et roadmap → évaluation → score actualisé → offres compatibles → candidature**. Côté entreprise, les recruteurs publient des offres et reçoivent des candidats qualifiés, classés par score de correspondance.

**Principe directeur : aucun modèle de machine learning n'est entraîné.** Le projet repose sur des règles et formules pondérées pour les scores, sur du RAG (LangChain) pour générer questions et recommandations à partir de documents réels, sur des APIs et du scraping pour les offres, et sur un LLM utilisé en inférence uniquement (Ollama en local par défaut).

## Fonctionnalités

**Candidat**
- Connexion Google (Firebase Auth) ou e-mail, formulaire d'onboarding en plusieurs écrans
- Import du CV (PDF/DOCX) et extraction automatique des compétences
- Score initial (cercle de maîtrise) et compétences à développer, avec bouton **Évaluer**
- Ressources d'étude et roadmap par niveau (débutant, intermédiaire, avancé)
- Banque de questions générée à partir de ce que demande le marché (QCM, code, vrai/faux, question ouverte, simulation d'entretien)
- Offres compatibles avec pourcentage de correspondance et compétences manquantes
- Candidatures, historique, courbe de progression
- Notifications (nouvelles offres, rappels, invitations) et messagerie avec les recruteurs

**Candidat premium**
- CV proposé automatiquement aux recruteurs dont une offre dépasse le seuil de correspondance
- Statistiques avancées, évaluations et simulations d'entretien illimitées

**Recruteur**
- Profil entreprise, tableau de bord, publication d'offres (brouillon ou publiée)
- Candidats proposés classés par score, filtres (80 % et plus, disponibles), invitation et messagerie
- Profils anonymisés jusqu'à l'invitation ou la candidature

**Administrateur**
- Référentiel de compétences, validation des questions générées, modération des offres, statistiques

## Architecture

```
┌──────────────────┐  HTTPS + JWT  ┌─────────────────────────────┐
│ Application      │ ◄───────────► │ API Spring Boot (cœur métier)│
│ Flutter (Android)│               │ Auth, offres, candidatures,  │
└──────────────────┘               │ scores, abonnements          │
                                   └──────┬───────────────┬───────┘
                                          │ REST interne  │ JDBC
                              ┌───────────▼─────────┐  ┌──▼────────────────┐
                              │ Service IA FastAPI  │─►│ PostgreSQL        │
                              │ LangChain + RAG     │  │ + pgvector        │
                              └──┬────────┬──────┬──┘  └───────────────────┘
                                 │        │      │
                          Ollama (LLM)  Redis +  Connecteurs externes
                                        Celery   (offres, ressources)
```

- **Spring Boot** est la seule porte d'entrée du mobile : utilisateurs, offres, candidatures, droits d'accès.
- **FastAPI** n'est appelé que par Spring Boot (réseau interne, clé de service).
- **Flutter** ne contient aucune logique de score : présentation et état local uniquement.

## Stack technique

| Couche | Technologie |
|---|---|
| Mobile | Flutter (Dart) |
| Backend | Java 17+, Spring Boot 3, Spring Security, JPA |
| Service IA | Python 3.11, FastAPI, LangChain |
| LLM (inférence) | Ollama (Llama 3.1, Qwen 2.5 ou Mistral), option API cloud |
| Embeddings | bge-m3 ou multilingual-e5 (sentence-transformers) |
| Base de données | PostgreSQL 16 + pgvector |
| Tâches asynchrones | Redis + Celery, `@Scheduled` côté Spring |
| Parsing de CV | PyMuPDF, pdfplumber, python-docx, Tesseract |
| Authentification | Google Sign-In + Firebase Auth, JWT |
| Notifications | Firebase Cloud Messaging |
| DevOps | Docker Compose, GitHub Actions |

## Structure du dépôt

```
skillmirror/
├── backend/          API Spring Boot (Maven)
├── ai-service/       Service IA FastAPI + LangChain
├── mobile/           Application Flutter
├── docs/             Dossier de conception, maquettes, diagrammes UML, rapport
├── .github/          Workflows CI/CD, modèles d'issues et de PR
├── docker-compose.yml
├── .env.example
└── README.md
```

## Prérequis

| Outil | Version |
|---|---|
| JDK | 17 ou plus |
| Maven | 3.9+ |
| Python | 3.11+ |
| Flutter | stable (3.x) |
| Docker | pour PostgreSQL + pgvector et Redis |
| Ollama | pour le LLM local (optionnel si API cloud) |
| Android Studio | émulateur ou téléphone en mode développeur |

## Démarrage rapide

```bash
# 1. Cloner le dépôt
git clone https://github.com/OWNER/skillmirror.git
cd skillmirror

# 2. Configuration
cp .env.example .env        # puis adapter les valeurs

# 3. Services d'infrastructure (PostgreSQL + pgvector, Redis)
docker compose up -d

# 4. LLM local (optionnel)
ollama pull llama3.1:8b

# 5. Service IA
cd ai-service
python -m venv venv && source venv/bin/activate   # Windows : venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000          # documentation : http://localhost:8000/docs

# 6. Backend (autre terminal)
cd backend
mvn spring-boot:run                                # Swagger : http://localhost:8080/swagger-ui.html

# 7. Application mobile (autre terminal)
cd mobile
flutter pub get
flutter run
```

Sur l'émulateur Android, le backend de votre machine est joignable à l'adresse `http://10.0.2.2:8080`.

## Tests

```bash
cd backend    && mvn verify          # JUnit 5 + Mockito
cd ai-service && pytest              # pytest
cd mobile     && flutter test        # flutter_test
```

## CI/CD

Les workflows GitHub Actions sont dans `.github/workflows/`.

| Workflow | Déclencheur | Rôle |
|---|---|---|
| `backend-ci.yml` | push et PR touchant `backend/` | Compile et lance les tests Java |
| `ai-ci.yml` | push et PR touchant `ai-service/` | Lint (ruff) et tests pytest |
| `mobile-ci.yml` | push et PR touchant `mobile/` | `flutter analyze`, tests, APK debug |
| `release.yml` | tag `v*` (ex. `v1.0.0`) | JAR et APK attachés à une release GitHub |

## Workflow Git

- `main` : branche stable et protégée, uniquement des Pull Requests validées par la CI et relues.
- Branches de travail : `feature/<sujet>`, `fix/<sujet>`, `docs/<sujet>`.
- Commits au format [Conventional Commits](https://www.conventionalcommits.org/fr/) : `feat:`, `fix:`, `docs:`, `test:`, `chore:`.

Détails dans [CONTRIBUTING.md](CONTRIBUTING.md).

## Documentation

Dans [`docs/`](docs/) : dossier de conception technique, maquettes, diagrammes de cas d'utilisation et de classes, rapport et support de soutenance.

## Équipe

| Nom | Rôle |
|---|---|
| [Nom 1] | Backend Spring Boot |
| [Nom 2] | Application Flutter |
| [Nom 3] | Service IA (RAG, matching) |
| [Nom 4] | Base de données, tests, documentation |

Encadrant : [Nom de l'enseignant]

## Feuille de route

- [x] Conception, maquettes et diagrammes UML
- [ ] Authentification, formulaire d'onboarding, import du CV
- [ ] Score initial, compétences à développer, roadmap
- [ ] Banque de questions (RAG) et évaluation
- [ ] Matching offres / candidats et candidatures
- [ ] Espace recruteur (publication, candidats proposés)
- [ ] Notifications et messagerie
- [ ] Mode premium
- [ ] Démonstration finale

Perspectives : version iOS, déploiement cloud, simulation d'entretien vocale, application web recruteur.

## Licence

Distribué sous licence MIT. Voir [LICENSE](LICENSE).
