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

        stage('sonarQube Analysis') {
            steps {
                echo 'Starting SonarQube analysis...'
                withSonarQubeEnv('sonar_server') { // Ensure 'SonarQube' matches your SonarQube server configuration in Jenkins
                    sh 'mvn sonar:sonar -Dsonar.projectKey=demo-project -Dsonar.host.url=$SONAR_HOST_URL'
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