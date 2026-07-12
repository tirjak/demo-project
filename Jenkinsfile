pipeline {
    // Run every build inside the CI Docker image.
    // JDK 17, Maven 3.9, Docker CLI, AWS CLI, and Trivy are all baked in.
    // Build the image once before using: docker build -t demo-project-ci:latest -f Dockerfile.ci .
    agent {
        docker {
            image 'demo-project-ci:latest'
            // Share the host Docker daemon so docker build/push work inside the container
            args  '-v /var/run/docker.sock:/var/run/docker.sock'
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
        SONAR_HOST_URL = 'http://localhost:9000'
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
                    // BRANCH_NAME = 'main' for direct pushes, 'PR-N' for pull requests
                    def isMain     = (env.BRANCH_NAME == 'main' || env.BRANCH_NAME == 'master')
                    def branchPrefix = isMain ? 'main' : 'feature'
                    IMAGE_TAG  = "${branchPrefix}-${gitCommit}-${BUILD_NUMBER}"
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
                        sed -i "s|image: ${ECR_REGISTRY}/${ECR_REPOSITORY}:.*|image: ${FULL_IMAGE}|" k8s/deployment.yaml
                        git config user.email "jenkins@demo-project.com"
                        git config user.name "Jenkins CI"
                        git add k8s/deployment.yaml
                        git commit -m "ci: update image tag to ${IMAGE_TAG}"
                        git push https://${GIT_USER}:${GIT_TOKEN}@github.com/tirjak/demo-project.git HEAD:${BRANCH_NAME}
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