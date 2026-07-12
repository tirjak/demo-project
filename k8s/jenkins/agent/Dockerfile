# ─────────────────────────────────────────────────────────────────────────────
# Jenkins Build Agent
#
# Tools installed to match local Jenkins setup:
#   - JDK 17         (from base image)
#   - Maven 3.9.6
#   - Docker CE CLI + buildx plugin  (talks to DinD sidecar via DOCKER_HOST)
#   - AWS CLI v2     (arch-aware: x86_64 or aarch64)
#   - Trivy          (latest via install script)
#   - Git            (latest)
#
# Build & push:
#   docker build -t <ECR_REGISTRY>/jenkins-agent:latest .
#   docker push <ECR_REGISTRY>/jenkins-agent:latest
# ─────────────────────────────────────────────────────────────────────────────
FROM jenkins/inbound-agent:jdk17

USER root

# ── System dependencies ───────────────────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    gnupg \
    git \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# ── Docker CE CLI + buildx plugin ────────────────────────────────────────────
# Install from Docker's official apt repo to get a version that includes buildx
RUN install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg \
       | gpg --dearmor -o /etc/apt/keyrings/docker.gpg \
    && chmod a+r /etc/apt/keyrings/docker.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
       https://download.docker.com/linux/debian \
       $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
       > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
       docker-ce-cli \
       docker-buildx-plugin \
    && rm -rf /var/lib/apt/lists/*

# ── Maven 3.9.6 ───────────────────────────────────────────────────────────────
ARG MAVEN_VERSION=3.9.6
RUN curl -fsSL \
    https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    | tar -xz -C /opt \
    && ln -s /opt/apache-maven-${MAVEN_VERSION} /opt/maven

# ── AWS CLI v2 (architecture-aware) ──────────────────────────────────────────
RUN ARCH=$(dpkg --print-architecture) \
    && case "$ARCH" in \
         amd64) AWS_ARCH="x86_64" ;; \
         arm64) AWS_ARCH="aarch64" ;; \
         *) echo "Unsupported arch: $ARCH" && exit 1 ;; \
       esac \
    && curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-${AWS_ARCH}.zip" \
       -o awscliv2.zip \
    && unzip -q awscliv2.zip \
    && ./aws/install \
    && rm -rf awscliv2.zip aws/

# ── Trivy ─────────────────────────────────────────────────────────────────────
RUN curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh \
    | sh -s -- -b /usr/local/bin

USER jenkins

ENV MAVEN_HOME=/opt/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"
