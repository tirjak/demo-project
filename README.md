# demo-project

Hello World Spring Boot application with a CI pipeline built on GitHub Actions
and Jenkins.

---

## Pipeline overview

```
feature/* push / main merge
         │
    ┌────▼─────┐
    │  Build   │  mvn verify — compile + unit tests + JaCoCo coverage
    └────┬─────┘
         │
    ┌────▼──────────────────┬──────────────────────────────────────┐
    │  SonarQube Scan       │  Docker Build → Trivy Scan → ECR     │  ← parallel
    │  (code quality)       │  (image built, scanned, then pushed) │
    └───────────────────────┴──────────────────┬───────────────────┘
                                               │  (main branch only)
                                    ┌──────────▼───────────┐
                                    │  Update Manifest     │  ← sed + git commit
                                    │  k8s/deployment.yaml │    [skip ci]
                                    └──────────────────────┘
```

| Step | Tool | Detail |
|---|---|---|
| Build & unit tests | Maven 3 / JaCoCo | `mvn verify` — compiles, runs tests, generates coverage XML |
| Code scan | SonarQube | `mvn sonar:sonar` — quality gates, coverage, bugs, code smells |
| Docker build | Docker Buildx | Builds from the fat JAR in `target/` |
| Image scan | Trivy | CRITICAL vulnerabilities fail the pipeline; image is NOT pushed if any found |
| Push | Amazon ECR | Tagged with the short commit SHA |
| Update manifest | sed + git | Writes the new ECR URI into `k8s/deployment.yaml` on `main` |

---

## Project structure

```
demo-project/
├── src/
│   ├── main/java/com/demo/
│   │   ├── DemoApplication.java         # Spring Boot entry point
│   │   └── HelloWorldController.java    # GET / and GET /health
│   └── main/resources/
│       └── application.properties
├── k8s/
│   ├── deployment.yaml                  # image line updated by CI (# ci-managed)
│   └── service.yaml                     # LoadBalancer, port 80 → 8080
├── pom.xml                              # Spring Boot + JaCoCo
├── Dockerfile                           # eclipse-temurin:17-jre-alpine
├── Jenkinsfile                          # Jenkins declarative pipeline
└── .github/workflows/
    ├── feature-branch.yml               # Caller: feature branch
    ├── main-merge.yml                   # Caller: main branch
    ├── reusable-build.yml               # mvn verify + artifact upload
    ├── reusable-sonar.yml               # SonarQube scan
    ├── reusable-docker-ecr.yml          # Docker build + Trivy + ECR push
    └── reusable-update-manifest.yml     # Update k8s/deployment.yaml
```

---

## One-time setup

### 1. GitHub repository configuration

#### Secrets  (Settings → Secrets and variables → Actions → **Secrets** tab)

| Secret name             | Value |
|-------------------------|-------|
| `AWS_ACCESS_KEY_ID`     | IAM user access key ID |
| `AWS_SECRET_ACCESS_KEY` | IAM user secret access key |
| `SONAR_TOKEN`           | SonarQube user token (My Account → Security → Generate Token) |

#### Variables  (Settings → Secrets and variables → Actions → **Variables** tab)

| Variable name    | Example value |
|------------------|---------------|
| `AWS_REGION`     | `us-east-1` |
| `ECR_REGISTRY`   | `123456789012.dkr.ecr.us-east-1.amazonaws.com` |
| `ECR_REPOSITORY` | `demo-project` |
| `SONAR_HOST_URL` | `http://your-sonarqube-server:9000` |

#### IAM permissions required

The IAM user needs:
- `ecr:GetAuthorizationToken`
- `ecr:BatchCheckLayerAvailability`
- `ecr:InitiateLayerUpload` / `UploadLayerPart` / `CompleteLayerUpload`
- `ecr:PutImage`

Attach the AWS managed policy **`AmazonEC2ContainerRegistryPowerUser`** for simplicity.

#### Create the ECR repository (one time)

```bash
aws ecr create-repository \
  --repository-name demo-project \
  --region us-east-1
```

---

### 2. SonarQube server

You need a running SonarQube instance accessible from your CI runners.

**Option A — Local/self-hosted (Docker):**
```bash
docker run -d --name sonarqube \
  -p 9000:9000 \
  sonarqube:community
# Access at http://localhost:9000 (admin / admin on first login)
```

**Option B — SonarCloud (free for public repos):**
Use `https://sonarcloud.io` as `SONAR_HOST_URL` and create a token at
sonarcloud.io → My Account → Security.

---

### 3. Jenkins configuration

**Credentials** (Manage Jenkins → Credentials → Global):

| ID | Type | Value |
|---|---|---|
| `aws-credentials` | AWS Credentials | IAM access key + secret |
| `sonar-token` | Secret Text | SonarQube user token |
| `github-credentials` | Username/Password | GitHub user + PAT (repo scope) |

**Global environment variables** (Manage Jenkins → Configure System → Global properties → Environment variables):

| Name | Example value |
|---|---|
| `SONAR_HOST_URL` | `http://your-sonarqube:9000` |
| `ECR_REGISTRY` | `123456789012.dkr.ecr.us-east-1.amazonaws.com` |
| `ECR_REPOSITORY` | `demo-project` |
| `AWS_REGION` | `us-east-1` |

**Tools** (Manage Jenkins → Tools):
- JDK: name `JDK-17` — Eclipse Temurin 17
- Maven: name `Maven-3` — Maven 3.9.x

**Required plugins:**
Pipeline, Credentials Binding, JUnit, AWS Credentials, Git

---

## Running the pipeline

### Feature branch
```bash
git checkout -b feature/my-feature
git push origin feature/my-feature
```
Runs: Build → SonarQube ‖ Docker+Trivy+ECR

### Main branch (full pipeline)
```bash
git checkout main
git merge feature/my-feature
git push origin main
```
Runs: Build → SonarQube ‖ Docker+Trivy+ECR → Update manifest

---

## Running locally

```bash
# Maven
mvn spring-boot:run

# Docker (after building)
mvn clean package -DskipTests
docker build -t demo-project .
docker run -p 8080:8080 demo-project
# http://localhost:8080
```
