pipeline {
    agent any

    tools {
        jdk   'jdk_mac'
        maven 'maven_3.9'
    }

    // ── Webhook trigger ───────────────────────────────────────────────────────
    // Requires the "Generic Webhook Trigger" Jenkins plugin.
    // GitHub webhook URL: http://<JENKINS_URL>/generic-webhook-trigger/invoke?token=demo-project-webhook
    // Events to send: "Just the push event"
    triggers {
        GenericTrigger(
            genericVariables: [
                [key: 'ref',      value: '$.ref'],
                [key: 'repo_url', value: '$.repository.clone_url']
            ],
            token:       'demo-project-webhook',
            causeString: 'GitHub push to $ref',
            // Only trigger for main/master and feature/* or feat/* branches
            regexpFilterText:       '$ref',
            regexpFilterExpression: '^refs/heads/(main|master|feature/.+|feat/.+)$',
            printContributedVariables: true,
            printPostContent: false
        )
    }

    environment {
        ECR_REGISTRY   = '348165962256.dkr.ecr.us-east-1.amazonaws.com'
        ECR_REPOSITORY = 'semtech_demo_image'
        AWS_REGION     = 'us-east-1'
        SONAR_HOST_URL = 'http://localhost:9000'
        IMAGE_TAG      = ''
        FULL_IMAGE     = ''
        PATH           = "/opt/homebrew/bin:/Users/tirjakmohapatra/.docker/bin:${env.PATH}"
        // Populated in the Initialize stage from the webhook 'ref' payload
        TARGET_BRANCH  = ''
        IS_MAIN        = 'false'
    }

    stages {
        // ── 0. Resolve branch from webhook payload ────────────────────────────
        stage('Initialize') {
            steps {
                script {
                    // env.ref comes from the Generic Webhook Trigger variable extraction
                    // e.g. "refs/heads/feature/auth" → "feature/auth"
                    def rawRef = env.ref ?: 'refs/heads/main'
                    env.TARGET_BRANCH = rawRef.replace('refs/heads/', '')
                    env.IS_MAIN = (env.TARGET_BRANCH == 'main' || env.TARGET_BRANCH == 'master') ? 'true' : 'false'
                    echo "Triggered branch : ${env.TARGET_BRANCH}"
                    echo "Is main branch   : ${env.IS_MAIN}"
                }
            }
        }

        stage('Checkout Code') {
            steps {
                // Checkout the branch that was actually pushed
                git credentialsId: 'githubid', 
                    url: 'https://github.com/tirjak/demo-project.git', 
                    branch: env.TARGET_BRANCH
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

        stage('OWASP Dependency Check') {
            steps {
                withCredentials([string(credentialsId: 'NVD_API_KEY_OWASP', variable: 'NVD_API_KEY')]) {
                    dependencyCheck additionalArguments: "--scan ./ --format XML --format HTML --out ./dependency-check-report --disableYarnAudit --disableNodeAudit --nvdApiKey ${NVD_API_KEY}",
                                    odcInstallation: 'OWASP-DC'
                }
            }
            post {
                always {
                    dependencyCheckPublisher pattern: 'dependency-check-report/dependency-check-report.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    sh 'mvn package -DskipTests'
                    def gitCommit = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    IMAGE_TAG  = "${gitCommit}-${BUILD_NUMBER}"
                    FULL_IMAGE = "${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"
                    // Build amd64 locally and save as Docker-format tar for Trivy
                    sh """
                        docker build --platform linux/amd64 -t ${FULL_IMAGE} .
                        docker save ${FULL_IMAGE} -o image.tar
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
                    aws ecr get-login-password --region ${AWS_REGION} | \
                        docker login --username AWS --password-stdin ${ECR_REGISTRY}
                    docker buildx use multiarch-builder 2>/dev/null || \
                        docker buildx create --use --name multiarch-builder
                    docker buildx build \
                        --platform linux/amd64,linux/arm64 \
                        --push \
                        -t ${FULL_IMAGE} .
                """
            }
        }

        stage('Update Manifest') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'GitHub_cred',
                                                  usernameVariable: 'GIT_USER',
                                                  passwordVariable: 'GIT_TOKEN')]) {
                    sh """
                        sed -i '' "s|image: ${ECR_REGISTRY}/${ECR_REPOSITORY}:.*|image: ${FULL_IMAGE}|" k8s/deployment.yaml
                        git config user.email "jenkins@demo-project.com"
                        git config user.name "Jenkins CI"
                        git add k8s/deployment.yaml
                        git commit -m "ci: update image tag to ${IMAGE_TAG}"
                        git push https://${GIT_USER}:${GIT_TOKEN}@github.com/tirjak/demo-project.git HEAD:main
                    """
                }
            }
        }

    }

    post {
        always {
            echo 'Pipeline finished.'
        }
    }
}