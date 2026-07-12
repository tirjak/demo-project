pipeline {
    // Each build runs inside a fresh EKS pod with two containers:
    //   build — CI image (JDK 17, Maven, Docker CLI, AWS CLI, Trivy)
    //   dind  — Docker-in-Docker daemon (EKS nodes use containerd; no host socket available)
    // Docker CLI in 'build' talks to the DinD daemon via tcp://localhost:2375
    agent {
        kubernetes {
            cloud 'minikube'
            yaml '''
apiVersion: v1
kind: Pod
spec:
  initContainers:
  - name: qemu-install
    image: tonistiigi/binfmt:latest
    args: ["--install", "all"]
    securityContext:
      privileged: true
  containers:
  - name: build
    image: jenkins-agent:latest
    imagePullPolicy: Never
    command: [cat]
    tty: true
    env:
    - name: DOCKER_HOST
      value: tcp://localhost:2375
    - name: DOCKER_TLS_VERIFY
      value: ""
    volumeMounts:
    - name: aws-credentials
      mountPath: /root/.aws
      readOnly: true
  - name: dind
    image: docker:dind
    securityContext:
      privileged: true
    env:
    - name: DOCKER_TLS_CERTDIR
      value: ""
    volumeMounts:
    - name: docker-storage
      mountPath: /var/lib/docker
  volumes:
  - name: docker-storage
    emptyDir: {}
  - name: aws-credentials
    secret:
      secretName: aws-credentials
'''
            defaultContainer 'build'
        }
    }

    // Webhook triggers are configured at the Multibranch Pipeline job level
    // (GitHub Branch Source plugin → "push" + "pull_request" events).
    // Jenkins automatically sets env.BRANCH_NAME for branch builds
    // and env.CHANGE_ID / env.CHANGE_BRANCH for PR builds.

    environment {
        ECR_REGISTRY   = '348165962256.dkr.ecr.us-east-1.amazonaws.com'
        ECR_REPOSITORY = 'semtech_demo_image'
        AWS_REGION     = 'us-east-1'
        SONAR_HOST_URL = 'https://budget-trident-freckles.ngrok-free.dev'
        IMAGE_TAG      = ''
        FULL_IMAGE     = ''
    }

    stages {
        stage('Checkout Code') {
            steps {
                // Multibranch Pipeline: checkout scm checks out the branch Jenkins discovered
                checkout scm
            }
        }

        stage('Maven Compile') {
            steps {
                echo 'Starting compilation...'
                sh 'mvn clean compile'
            }
        }

        stage('Unit Tests') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        stage('Code Coverage') {
            steps {
                sh 'mvn jacoco:report'
            }
            post {
                always {
                    recordCoverage(
                        tools: [[parser: 'JACOCO', pattern: 'target/site/jacoco/jacoco.xml']]
                    )
                }
            }
        }

        stage('sonarQube Analysis') {
            steps {
                echo 'Starting SonarQube analysis...'
                withSonarQubeEnv('sonar_server') { // Ensure 'SonarQube' matches your SonarQube server configuration in Jenkins
                    sh 'mvn sonar:sonar -Dsonar.projectKey=demo-project -Dsonar.host.url=$SONAR_HOST_URL'
                }
            }
        }

        // stage('OWASP Dependency Check') {
        //     steps {
        //         withCredentials([string(credentialsId: 'NVD_API_KEY_OWASP', variable: 'NVD_API_KEY')]) {
        //             dependencyCheck additionalArguments: "--scan ./ --format XML --format HTML --out ./dependency-check-report --disableYarnAudit --disableNodeAudit --nvdApiKey ${NVD_API_KEY}",
        //                             odcInstallation: 'OWASP-DC'
        //         }
        //     }
        //     post {
        //         always {
        //             dependencyCheckPublisher pattern: 'dependency-check-report/dependency-check-report.xml'
        //         }
        //     }
        // }

        stage('Docker Build') {
            steps {
                script {
                    // Wait for the DinD sidecar daemon to be ready
                    sh 'until docker info > /dev/null 2>&1; do echo "Waiting for DinD..."; sleep 2; done'
                    sh 'mvn package -DskipTests'
                    def gitCommit = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    // Sanitize branch name for Docker tag: replace '/' with '-'
                    // e.g. feature/auth → feature-auth, PR-1 → PR-1, main → main
                    def sanitizedBranch = env.BRANCH_NAME.replaceAll('/', '-')
                    // Use local variables for interpolation — env vars set dynamically
                    // in the same script block are not reliably readable back via env.X
                    def imageTag  = "${sanitizedBranch}-${gitCommit}-${env.BUILD_NUMBER}"
                    def fullImage = "${env.ECR_REGISTRY}/${env.ECR_REPOSITORY}:${imageTag}"
                    // Persist for later stages
                    env.IMAGE_TAG  = imageTag
                    env.FULL_IMAGE = fullImage
                    echo "Building image: ${fullImage}"
                    // Build for amd64 inside the EKS pod and save as tar for Trivy
                    sh """
                        docker build --platform linux/amd64 -t ${fullImage} .
                        docker save ${fullImage} -o image.tar
                    """
                }
            }
        }

        stage('Trivy Image Scan') {
            steps {
                sh """
                    trivy image \
                        --format table \
                        --severity HIGH,CRITICAL \
                        --input image.tar \
                        --output trivy-report.txt
                """
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-report.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Push to ECR') {
            steps {
                sh """
                    aws ecr get-login-password --region ${env.AWS_REGION} | \
                        docker login --username AWS --password-stdin ${env.ECR_REGISTRY}
                    docker buildx use multiarch-builder 2>/dev/null || \
                        docker buildx create --use --name multiarch-builder
                    docker buildx build \
                        --platform linux/amd64,linux/arm64 \
                        --push \
                        -t ${env.FULL_IMAGE} .
                """
            }
        }

        stage('Update Manifest') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'GitHub_cred',
                                                  usernameVariable: 'GIT_USER',
                                                  passwordVariable: 'GIT_TOKEN')]) {
                    sh """
                        sed -i "s|image: ${env.ECR_REGISTRY}/${env.ECR_REPOSITORY}:.*|image: ${env.FULL_IMAGE}|" k8s/deployment.yaml
                        git config user.email "jenkins@demo-project.com"
                        git config user.name "Jenkins CI"
                        git add k8s/deployment.yaml
                        git commit -m "ci: update image tag to ${env.IMAGE_TAG}"
                        git push https://\${GIT_USER}:\${GIT_TOKEN}@github.com/tirjak/demo-project.git HEAD:${env.BRANCH_NAME}
                    """
                }
            }
        }

    }

    post {
        always {
            echo 'Pipeline finished.'
        }
        failure {
            echo "Pipeline FAILED — branch: ${env.BRANCH_NAME}, build: #${env.BUILD_NUMBER}"
        }
    }
}