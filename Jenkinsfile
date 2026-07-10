pipeline {
    agent {
        kubernetes {
            // Must match the name of the cloud provider in your Jenkins settings
            cloud 'kubernetes' 
            namespace 'jenkins'
            defaultContainer 'maven'
            yaml '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    component: jenkins-agent
spec:
  containers:
  - name: maven
    image: maven:3.9.6-eclipse-temurin-17
    command:
    - cat
    tty: true
'''
        }
    }

    stages {
        stage('Checkout Code') {
            steps {
                // Using your specific GitHub repo and credential ID
                git credentialsId: 'GitHub_cred', 
                    url: 'https://github.com/tirjak/demo-project.git', 
                    branch: 'main' // Change to 'master' if your default branch is master
            }
        }

        stage('Maven Compile') {
            steps {
                container('maven') {
                    echo 'Starting compilation inside EKS agent pod...'
                    sh 'mvn clean compile'
                }
            }
        }
    }
    
    post {
        always {
            echo 'Pipeline finished. EKS will automatically terminate this agent pod.'
        }
    }
}