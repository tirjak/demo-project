pipeline {
    agent any

    tools {
        jdk   'jdk_mac'
        maven 'maven_3.9'
    }

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
                // Using your specific GitHub repo and credential ID
                git credentialsId: 'Github_cred', 
                    url: 'https://github.com/tirjak/demo-project.git', 
                    branch: 'main' // Change to 'master' if your default branch is master
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
                    sh "docker build -t ${FULL_IMAGE} ."
                }
            }
        }

        stage('Trivy Image Scan') {
            steps {
                sh """
                    /opt/homebrew/bin/trivy image \
                        --format table \
                        --severity HIGH,CRITICAL \
                        --output trivy-report.txt \
                        ${FULL_IMAGE}
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
                    docker push ${FULL_IMAGE}
                """
            }
        }

        stage('Update Manifest') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'Github_cred',
                                                  usernameVariable: 'GIT_USER',
                                                  passwordVariable: 'GIT_TOKEN')]) {
                    sh """
                        sed -i '' 's|image: .*|image: ${FULL_IMAGE} |' k8s/deployment.yaml
                        git config user.email "tirjak@demo-project.com"
                        git config user.name "tirjak"
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