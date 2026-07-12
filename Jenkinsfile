def IMAGE_TAG  = ''
def FULL_IMAGE = ''

pipeline {
    agent {
        kubernetes {
            cloud 'minikube'
            label 'multiarch-pod-semtech' // Matches the Pod Template label set in Jenkins Cloud UI
            defaultContainer 'build'
        }
    }

    environment {
        ECR_REGISTRY   = '348165962256.dkr.ecr.us-east-1.amazonaws.com'
        ECR_REPOSITORY = 'semtech_demo_image'
        AWS_REGION     = 'us-east-1'
        SONAR_HOST_URL = 'https://budget-trident-freckles.ngrok-free.dev'
    }

    stages {
        stage('Checkout Code') {
            steps {
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
                withSonarQubeEnv('sonar_server') { 
                    sh 'mvn sonar:sonar -Dsonar.projectKey=demo-project -Dsonar.host.url=$SONAR_HOST_URL'
                }
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    // Wait for the DinD sidecar container in the template to become healthy
                    sh 'until docker info > /dev/null 2>&1; do echo "Waiting for DinD..."; sleep 2; done'
                    sh 'mvn package -DskipTests'
                    
                    def gitCommit = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    def sanitizedBranch = env.BRANCH_NAME.replaceAll('/', '-')
                    
                    IMAGE_TAG  = "${sanitizedBranch}-${gitCommit}-${env.BUILD_NUMBER}"
                    FULL_IMAGE = "${env.ECR_REGISTRY}/${env.ECR_REPOSITORY}:${IMAGE_TAG}"
                    
                    echo "Building image: ${FULL_IMAGE}"
                    
                    sh """
                        docker build --platform linux/amd64 -t ${FULL_IMAGE} .
                        docker save ${FULL_IMAGE} -o image.tar
                    """
                }
            }
        }

        stage('Trivy Image Scan') {
            steps {
                sh 'curl -sO https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl'
                sh '''
                    mkdir -p trivy-reports
                    trivy image \
                        --format template \
                        --template "@html.tpl" \
                        --severity HIGH,CRITICAL \
                        --input image.tar \
                        --output trivy-reports/security-dashboard.html
                '''
            }
            post {
                always {
                    publishHTML([
                        allowMissing: false,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'trivy-reports',
                        reportFiles: 'security-dashboard.html',
                        reportName: 'Trivy Security Dashboard'
                    ])
                }
            }
        }

        stage('Push to ECR') {
            steps {
                sh """
                    aws ecr get-login-password --region ${env.AWS_REGION} | \
                        docker login --username AWS --password-stdin ${env.ECR_REGISTRY}
                    docker buildx use multiarch