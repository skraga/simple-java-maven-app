#!/usr/bin/env groovy

def servers = [:]
servers['dev'] = ['127.0.0.1']
servers['uat'] = ['8.8.8.8', '8.8.4.4']
servers['pro'] = ['208.67.222.222', '208.67.220.220']

void pinger(Map args) {
    assert args.address != null
    def count = args.count ?: '5'
    try {
        sh "ping -c ${count} ${args.address}"
    } catch (Exception e) {
        echo "Error while pinging: ${e}"
    }
}

pipeline {
    agent none
    triggers {
        cron('H */4 * * 1-5')
        // upstream(upstreamProjects: 'job1,job2', threshold: hudson.model.Result.SUCCESS)
        // pollSCM('H */4 * * 1-5')
    }
    parameters {
        // There is an issue with parameters refresh (re-run job)
        choice(name: 'ENVIRONMENT', choices: ['dev', 'uat', 'pro'], description: 'Select environment')
    }
    libraries {
        lib('demo-shared-library')
    }
    options {
        timeout(time: 1, unit: 'HOURS')
        buildDiscarder(logRotator(numToKeepStr: '1'))
        disableConcurrentBuilds()
        skipDefaultCheckout true
        timestamps()
        ansiColor('xterm')
        parallelsAlwaysFailFast()
    }
    stages {
        stage('BuildAndTest') {
            options { retry(2) }
            parallel {
                stage('BuildAndTest JDK8') {
                    agent {
                        docker {
                            image 'maven:3.5.4-jdk-8-alpine'
                            args '-v maven-repo:/root/.m2'
                        }
                    }
                    steps {
                        checkout scm
                        stash includes: '*.groovy', name: 'externalGroovyFiles'
                        script {
                            // readMavenPom() requires Pipeline Utility Steps Plugin
                            echo "ArtifactID: ${readMavenPom().getArtifactId()}"
                            echo "Build number: ${currentBuild.getNumber()}"
                        }
                        sh 'mvn -B -DskipTests clean package'
                        retry(3) {
                            sh 'mvn test'
                        }
                    }
                    post {
                        success {
                            junit 'target/surefire-reports/*.xml'
                            archiveArtifacts artifacts: 'target/**/*.jar', fingerprint: true
                        }
                        regression {
                            echo 'Failure, unstable: send a message to developers here'
                        }
                    }
                }
                stage('Fake Build JDK11') {
                    agent { docker 'maven:3.6.0-jdk-11-slim' }
                    steps {
                        echo 'Hello, Maven'
                        sh 'java -version'
                    }
                }
                stage('Fake Build master') {
                    tools { maven 'apache-maven-3.6.0' }
                    agent { label 'master' }
                    steps {
                        echo 'Hello, Maven from Master'
                        sh 'mvn --version && java -version'
                    }
                }
            }
        }
        stage('Shared code') {
            agent any
            steps {
                unstash 'externalGroovyFiles'
                script {
                    // load groovy file locally
                    def externalFile = load("externalFile.groovy")
                    externalFile("Darth Vader")
                    externalFile.sayHello person: 'Jedi'

                    // using Shared Libraries
                    // read constants
                    echo "DOCKER_REGISTRY_AWS: ${Constants.DOCKER_REGISTRY_AWS}"
                    echo "CONFLUENCE_URL: ${Constants.CONFLUENCE_URL}"

                    // using Library resource
                    def data1 = libraryResource 'org/conf/data/datafile.txt'
                    echo data1
                    script1 = libraryResource 'org/deployment/scripts/test_deploy.sh'
                    writeFile file: 'test_deploy.sh', text: script1
                    sh "bash test_deploy.sh hello there"

                    // using 3rd party libs
                    thirdParty()

                    // using Jenkins model
                    jenkinsInternal.setJobDescription('This is the demo job')
                    jenkinsInternal.setBuildDescription(params.ENVIRONMENT)
                }
            }
        }
        stage('Deployment confirmation') {
            when { expression { return params.ENVIRONMENT == 'pro' } }
            agent none
            steps {
                script {
                    timeout(5) {
                        env.DEPLOYMENT_CONFIRMED = input message: 'User input required',
                            submitter: 'admin, ivanov, petrov',
                            parameters: [choice(name: 'Deploy to PRODUCTION servers',
                                                choices: 'NO\nNO\nNO\nNO\nyes\nNO\nNO',
                                                description: 'Choose "yes" if you want to deploy to PRODUCTION')]
                    }
                }
            }
        }
        stage('Deploy') {
            agent { docker 'openjdk:8-jre' }
            when {
                anyOf {
                    expression { params.ENVIRONMENT != 'pro' }
                    environment name: 'DEPLOYMENT_CONFIRMED', value: 'yes'
                }
            }
            steps {
                script {
                    def nodes = [:]
                    for (server in servers[params.ENVIRONMENT]) {
                        def currentServer = server
                        nodes["Pinging ${currentServer}"] = {
                            pinger address: currentServer
                        }
                    }
                    parallel nodes
                }
            }
            post {
                failure {
                    echo 'INFO: Deployment failure, relax and keep calm =)'
                }
            }
        }
    }
}
