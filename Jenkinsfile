#!/usr/bin/env groovy
pipeline {
    agent any
    parameters {
        choice(name: 'MAVEN_RELEASE', choices: ['NO','YES'], description: 'If you want to perform a maven release please choose the YES option')
    }
    options {
        disableConcurrentBuilds()
    }

    stages {
        stage('Build & Test') {
            steps {
                sh 'mvn clean install'
            }
            post {
                success {
                    junit '**/target/surefire-reports/**/*.xml'
                }
            }
        }

        stage('Install & Analyze') {
            parallel {
                stage('Put to Artifactory') {
                    steps { sh 'mvn deploy -DskipTests=true' }
                }
                stage('Sonar') {
                    steps { sh 'mvn sonar:sonar -DskipTests=true' }
                }
            }
        }

        stage('Release') {
            when {
                allOf {
                    branch 'master'
                    not { changelog '.*\\[maven-release-plugin\\].*' }
                    expression { return params.MAVEN_RELEASE=='YES' }
                }
            }

            steps {
                script {
                    pom = readMavenPom file: 'pom.xml'
                    VERSION = pom.version.substring(0, pom.version.length() - "-SNAPSHOT".length())
                    currentBuild.displayName = VERSION
                }
                sh 'git checkout master || git checkout -b master'
                sh 'git reset --hard origin/master'
                sh 'mvn -B -e -Dresume=false clean release:prepare release:perform'
            }
        }

    }
}