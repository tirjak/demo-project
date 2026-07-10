pipeline {
    agent any

    tools {
        jdk   'idk_jenkins'
        maven 'maven_3.9'
    }

    environment {
        ECR_REGISTRY   = "${ECR_REGISTRY}"    // e.g. 123456789012.dkr.ecr.us-east-1.amazonaws.com
        ECR_REPOSITORY = "${ECR_REPOSITORY}"  // e.g. demo-project
        AWS_REGION     = "${AWS_REGION}"      // e.g. us-east-1
        SONAR_HOST_URL = "${SONAR_HOST_URL}"  // e.g. http://localhost:9000
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
    }
    
    post {
        always {
            echo 'Pipeline finished. EKS will automatically terminate this agent pod.'
        }
    }
}