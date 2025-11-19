pipeline {
    agent any

    environment {
        EC2_HOST = 'ubuntu@43.202.236.104'
        APP_DIR  = '/home/ubuntu/S13P31A103'
        BRANCH   = 'master'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Info') {
            steps {
                echo "Deploying branch: ${BRANCH}"
                sh 'git rev-parse --short HEAD || echo "no git info"'
            }
        }

        stage('Deploy to EC2') {
            steps {
                sshagent (credentials: ['ssh-ec2-ubuntu-main']) {
                    sh """
                    ssh -o StrictHostKeyChecking=no ${EC2_HOST} '
                        set -e
                        cd ${APP_DIR}
                        ./deploy.sh
                    '
                    """
                }
            }
        }
    }
}
