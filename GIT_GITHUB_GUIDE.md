# Git & GitHub Management Guide
> **Project:** Loan Platform — Spring Boot Microservices  
> **Author:** Yusuf Olosan  
> **Last Updated:** October 2026

---

## Table of Contents
1. [Repository Structure](#1-repository-structure)
2. [Why Two Repos?](#2-why-two-repos)
3. [How the Submodule Works](#3-how-the-submodule-works)
4. [First-Time GitHub Setup](#4-first-time-github-setup)
5. [Day-to-Day Git Workflow](#5-day-to-day-git-workflow)
6. [Working with the Config Repo](#6-working-with-the-config-repo)
7. [Branching Strategy](#7-branching-strategy)
8. [Startup Order & Why It Matters](#8-startup-order--why-it-matters)
9. [Security Considerations](#9-security-considerations)
10. [Cloning for New Team Members](#10-cloning-for-new-team-members)
11. [Common Commands Cheat Sheet](#11-common-commands-cheat-sheet)

---

## 1. Repository Structure

This project is managed as **two separate Git repositories**:

```
GitHub
├── loan-platform                 ← Main monorepo (application code)
│   ├── api-gateway/
│   ├── config-server/
│   ├── discovery-service/
│   ├── config-repo/              ← Git submodule (points to loan-platform-config)
│   ├── .gitmodules               ← Submodule link declaration
│   └── .gitignore
│
└── loan-platform-config          ← Separate config repo (Spring Cloud Config source)
    ├── application.yml           ← Global config for ALL services
    ├── api-gateway.yml           ← Config specific to api-gateway
    └── discovery-service.yml     ← Placeholder (discovery-service is self-contained)
```

The `config-repo/` directory inside `loan-platform` is a **Git submodule** — a full, independent Git repository nested inside the main one.

---

## 2. Why Two Repos?

### The core problem: Spring Cloud Config Server needs its own Git repo

Spring Cloud Config Server treats a Git repository as a database of configuration files. At startup it runs `git clone` on the URI you give it, and on every refresh it runs `git pull`. This means:

1. The config store **must** be a proper Git repository.
2. In production, the config server fetches from a **remote URL** (GitHub), not a local file path.
3. Config files need to be versioned, tagged, and audited **independently** from application source code.

### Benefits of the two-repo approach

| Concern | Benefit |
|---|---|
| **Security** | `loan-platform-config` can be private or access-controlled independently of the main code repo |
| **Audit trail** | Every config change is a separate, traceable commit — you can see exactly when a value changed and why |
| **Hot reload** | Config changes pushed to GitHub can be applied to running services via `/actuator/bus-refresh` — no code deployment needed |
| **Team separation** | Ops/DevOps teams manage config without touching application code, and vice versa |
| **Environment isolation** | Create `api-gateway-prod.yml`, `api-gateway-staging.yml` etc. in the config repo for per-environment overrides |

### Why not just one repo?

If you put everything in one repo and point the config server at it:
- In production it means cloning the entire application codebase just to read config files
- You cannot give fine-grained access to config without exposing all source code
- Config history gets buried in feature/fix commits

---

## 3. How the Submodule Works

A Git submodule is a reference from one repo (the parent) to a specific commit in another repo (the child).

```
loan-platform (parent repo)
└── config-repo/  ←  mode 160000 — Git stores only the commit SHA, not the files
```

The `.gitmodules` file records this relationship:
```ini
[submodule "config-repo"]
    path = config-repo
    url = https://github.com/<your-username>/loan-platform-config.git
```

**What this means in practice:**
- When you `git clone loan-platform`, you get a pointer to a commit in `loan-platform-config`, but not the files themselves — you need `--recurse-submodules` or `git submodule update --init`
- When you commit inside `config-repo/`, the parent repo needs a separate commit to update its pointer to the new SHA
- Both repos have completely independent branch, tag, and commit histories

---

## 4. First-Time GitHub Setup

Follow these steps **in order** — the config repo must exist on GitHub before the main repo references it.

### Step 1 — Push the config repo

```bash
# 1. Create a new GitHub repo named: loan-platform-config
#    Go to github.com → New repository

# 2. Add the remote and push
cd loan-platform/config-repo
git remote add origin https://github.com/<your-username>/loan-platform-config.git
git push -u origin main
```

### Step 2 — Update the submodule URL in the main repo

The submodule currently points to a local path (`./config-repo`). Update it to the GitHub URL:

```bash
cd loan-platform

# Edit .gitmodules:
#   Change:   url = ./config-repo
#   To:       url = https://github.com/<your-username>/loan-platform-config.git

git submodule sync
git add .gitmodules
git commit -m "chore: update config-repo submodule to GitHub remote URL"
```

### Step 3 — Update config-server to use the remote URI

In `config-server/src/main/resources/application.yaml`, change the `uri` from `file://` to the GitHub URL:

```yaml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/<your-username>/loan-platform-config.git
          clone-on-start: true
          default-label: main
```

```bash
git add config-server/src/main/resources/application.yaml
git commit -m "chore: point config-server at GitHub config repo"
```

### Step 4 — Push the main repo

```bash
# 1. Create a new GitHub repo named: loan-platform

# 2. Add the remote and push
git remote add origin https://github.com/<your-username>/loan-platform.git
git push -u origin main
```

---

## 5. Day-to-Day Git Workflow

### For application code changes (`api-gateway/`, `config-server/`, `discovery-service/`)

```bash
cd loan-platform

# 1. Create a feature branch
git checkout -b feat/your-feature-name

# 2. Make your changes, stage, and commit
git add api-gateway/src/...
git commit -m "feat(api-gateway): add circuit breaker to user-service route"

# 3. Push and open a Pull Request on GitHub
git push origin feat/your-feature-name
```

### For config changes (inside `config-repo/`)

Config changes are committed inside the submodule first, then the parent updates its pointer:

```bash
cd loan-platform/config-repo

# 1. Edit the config file, e.g. api-gateway.yml

# 2. Commit and push inside the submodule
git add api-gateway.yml
git commit -m "config(api-gateway): increase max-ip-request to 500"
git push origin main

# 3. Update the parent repo to track the new submodule commit
cd ..
git add config-repo
git commit -m "chore: update config-repo submodule pointer"
git push origin main
```

> **Hot-reload tip**: After pushing a config change, apply it to running services **without a restart**:
> ```bash
> curl -X POST http://config-user:config-secret@localhost:8888/actuator/bus-refresh
> ```

---

## 6. Working with the Config Repo

### File naming convention

Spring Cloud Config Server matches files to services using this pattern:

```
{application-name}.yml           ← loaded for that specific service only
{application-name}-{profile}.yml ← loaded when that profile is active
application.yml                  ← loaded for ALL services (global defaults)
```

| File | Loaded by |
|---|---|
| `application.yml` | Every service in the platform |
| `api-gateway.yml` | Only the `api-gateway` service |
| `api-gateway-prod.yml` | Only `api-gateway` when `spring.profiles.active=prod` |

### Adding config for a new microservice

When you add a new service (e.g. `auth-service`):

```bash
cd loan-platform/config-repo
touch auth-service.yml
# Add its config, then:
git add auth-service.yml
git commit -m "config(auth-service): initial configuration"
git push origin main

cd ..
git add config-repo
git commit -m "chore: add auth-service config"
git push origin main
```

In the new service's local `application.yaml`, add only:
```yaml
spring:
  application:
    name: auth-service   # MUST match the filename: auth-service.yml
  config:
    import:
      - optional:configserver:http://config-user:config-secret@localhost:8888
```

---

## 7. Branching Strategy

### Main repo (`loan-platform`)

```
main              ← production-ready code only, protected branch
│
├── develop       ← integration branch, always deployable to staging
│   ├── feat/jwt-refresh-token
│   ├── feat/loan-service-integration
│   └── fix/redis-connection-pool
│
└── release/1.0.0 ← release candidate, merged to main and tagged
```

| Branch | Purpose | Merged by |
|---|---|---|
| `main` | Production deployments | PR from `release/*` only |
| `develop` | Active integration | PR from `feat/*` or `fix/*` |
| `feat/<name>` | New features | Developer → PR to `develop` |
| `fix/<name>` | Bug fixes | Developer → PR to `develop` |
| `release/<version>` | Release preparation | Merged to `main` + tagged |

### Config repo (`loan-platform-config`)

```
main     ← production config
develop  ← staging / dev config
```

Keep config branches aligned with code — a `release/1.0.0` tag in the main repo should reference a stable, tagged commit in the config repo.

### Commit message format

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short description>

Types:  feat | fix | chore | refactor | docs | test | config
Scope:  api-gateway | config-server | discovery-service | global

Examples:
  feat(api-gateway): add per-route timeout configuration
  fix(auth-filter): handle null resolved IP address gracefully
  config(api-gateway): rotate JWT secret
  chore: update config-repo submodule pointer
```

---

## 8. Startup Order & Why It Matters

Services **must** start in this order:

```
1. discovery-service  (port 8761)  — Eureka Server
        ↓
2. config-server      (port 8888)  — registers with Eureka, then serves config
        ↓
3. api-gateway + all other services — fetch config, register with Eureka
```

### Why discovery-service has no config-server dependency

`discovery-service` is **fully self-contained** — it does not import from the config server. The reason is a **chicken-and-egg problem**:

- The config-server needs Eureka to be running so it can register itself
- If `discovery-service` also needed the config-server to start, neither could ever start first

**Solution:** `discovery-service/src/main/resources/application.yaml` contains all its own config locally with no `spring.config.import`. **This is intentional and must not be changed.**

---

## 9. Security Considerations

### What's currently in the config repo

`config-repo/api-gateway.yml` currently contains plain-text values:

```yaml
spring:
  datasource:
    password: random        # plain text
  data:
    redis:
      password: random      # plain text
jwt:
  secret: your-secret-key  # plain text
```

### Before pushing to a public GitHub repo — choose one option:

**Option A — Make both GitHub repos private** *(simplest for a personal project)*

**Option B — Use Spring Cloud Config encryption** *(recommended for teams)*
```bash
# Encrypt a value via the config server endpoint
curl http://config-user:config-secret@localhost:8888/encrypt -d 'my-real-password'
# Returns: AQA3x9...ciphertext...

# Use it in config-repo with the {cipher} prefix
spring:
  datasource:
    password: '{cipher}AQA3x9...ciphertext...'
```

**Option C — Inject via environment variables at runtime**
```bash
# Set on the host or container — never hardcoded in any file
export SPRING_DATASOURCE_PASSWORD=my-real-password
export JWT_SECRET=my-real-secret
```

### Config server credentials

`config-user` / `config-secret` are set in `config-server/application.yaml`. In production, inject via the environment variable `SPRING_SECURITY_USER_PASSWORD`. **Never commit a real production password to any repo.**

---

## 10. Cloning for New Team Members

### Clone everything at once

```bash
# --recurse-submodules automatically initialises and populates config-repo
git clone --recurse-submodules https://github.com/<your-username>/loan-platform.git
cd loan-platform
```

### Already cloned without the flag?

```bash
git submodule update --init --recursive
```

### After a pull that moved the submodule pointer

```bash
git pull origin main
git submodule update --recursive
```

---

## 11. Common Commands Cheat Sheet

### Main repo

```bash
# Status including submodule state
git status
git submodule status

# Pull latest including submodule pointer updates
git pull --recurse-submodules

# Recent commits
git log --oneline -10

# New feature branch
git checkout -b feat/my-feature

# Stage, commit, push
git add api-gateway/src/main/resources/application.yaml
git commit -m "config(api-gateway): update Redis pool settings"
git push -u origin feat/my-feature

# Delete a merged branch locally
git branch -d feat/my-feature
```

### Config repo (inside `config-repo/`)

```bash
# See recent config changes
git log --oneline -5

# Edit, commit, and push
git add api-gateway.yml
git commit -m "config(api-gateway): increase rate limit window to 120s"
git push origin main

# Update parent's submodule pointer
cd ..
git add config-repo
git commit -m "chore: update config-repo submodule pointer"
git push origin main
```

### Live config refresh (no service restart needed)

```bash
# Refresh ALL services at once via Spring Cloud Bus
curl -X POST http://config-user:config-secret@localhost:8888/actuator/bus-refresh

# Refresh a single service only
curl -X POST http://config-user:config-secret@localhost:8888/actuator/bus-refresh/api-gateway
```
