// The following property need to be set for the HTML report @TODO figure out how to get this nicely on jenkins
//System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "")

/**
 * The root results folder for items configurable by environmental variables
 */
def TEST_RESULTS_FOLDER = "not_configured"

/**
 * The location of the unit test results
 */
def UNIT_RESULTS = "${TEST_RESULTS_FOLDER}/not_configured"

/**
 * The location of the integration test results
 */
def INTEGRATION_RESULTS = "${TEST_RESULTS_FOLDER}/not_configured"

/**
 * The name of the master branch
 */
def MASTER_BRANCH = "master"

/**
* Is this a release branch? Temporary workaround that won't break everything horribly if we merge.
*/
def RELEASE_BRANCH = false

/**
 * List of people who will get all emails for master builds
 */
def MASTER_RECIPIENTS_LIST = "cc:mark.ackert@broadcom.com"

/**
 * The result string for a successful build
 */
def BUILD_SUCCESS = 'SUCCESS'

/**
 * The result string for an unstable build
 */
def BUILD_UNSTABLE = 'UNSTABLE'

/**
 * The result string for a failed build
 */
def BUILD_FAILURE = 'FAILURE'

/**
 * The user's name for git commits
 */
def GIT_USER_NAME = 'zowe-robot'

/**
 * The user's email address for git commits
 */
def GIT_USER_EMAIL = 'zowe.robot@gmail.com'

/**
 * The base repository url for github
 */
def GIT_REPO_URL = 'github.com/zowe/explorer-api-common.git'

/**
 * The credentials id field for the authorization token for GitHub stored in Jenkins
 */
def GIT_CREDENTIALS_ID = 'zowe-robot-github'

/**
 * A command to be run that gets the current revision pulled down
 */
def GIT_REVISION_LOOKUP = 'git log -n 1 --pretty=format:%h'

/**
 * The credentials id field for the artifactory username and password
 */
def ARTIFACTORY_CREDENTIALS_ID = 'nexus2-marktest-credential'

/**
 * The email address for the artifactory
 */
def ARTIFACTORY_EMAIL = GIT_USER_EMAIL


// Setup conditional build options. Would have done this in the options of the declarative pipeline, but it is pretty
// much impossible to have conditional options based on the branch :/
def opts = []

if (BRANCH_NAME == MASTER_BRANCH) {
    // Only keep 20 builds
    opts.push(buildDiscarder(logRotator(numToKeepStr: '20')))

    // Concurrent builds need to be disabled on the master branch because
    // it needs to actively commit and do a build. There's no point in publishing
    // twice in quick succession
    opts.push(disableConcurrentBuilds())
} else {
    if (BRANCH_NAME.equals("nexus-test")){
        RELEASE_BRANCH = true
    }
    // Only keep 5 builds on other branches
    opts.push(buildDiscarder(logRotator(numToKeepStr: '5')))
}

properties(opts)

pipeline {
    agent {
        label 'ibm-jenkins-slave-nvm-jnlp'
    }

    environment {
        // Environment variable for flow control. Tells most of the steps if they should build.
        SHOULD_BUILD = "true"

        // Environment variable for flow control. Indicates if the git source was updated by the pipeline.
        GIT_SOURCE_UPDATED = "false"
    }

    stages {

        /************************************************************************
         * STAGE
         * -----
         * Bootstrap gradlew
         *
         * TIMEOUT
         * -------
         * 5 Minutes
         *
         * EXECUTION CONDITIONS
         * --------------------
         * - SHOULD_BUILD is true
         * - The build is still successful
         *
         * DESCRIPTION
         * -----------
         * Executes `bootstrap_gradle.sh` to bootstrap gradlew (downloads gradle-wrapper).
         *
         * OUTPUTS
         * -------
         * gradle-wrapper.jar is present in "./gradle/wrapper/" directory.
         ************************************************************************/
        stage ('Bootstrap Gradlew') {
            when {
                expression {
                    return SHOULD_BUILD == 'true'
                }
            }
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    sh './bootstrap_gradlew.sh'
                }
            }
        }

        /************************************************************************
         * STAGE
         * -----
         * Build the source code.
         *
         * TIMEOUT
         * -------
         * 10 Minutes
         *
         * EXECUTION CONDITIONS
         * --------------------
         * - SHOULD_BUILD is true
         * - The build is still successful
         *
         * DESCRIPTION
         * -----------
         * Executes `gradle build` to build the source code.
         *
         * OUTPUTS
         * -------
         * Jenkins: Compiled application executable code
         ************************************************************************/
        stage('Build') {
            when {
                expression {
                    return SHOULD_BUILD == 'true'
                }
            }
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    sh './gradlew build'
                }
            }
        }

        /************************************************************************
         * STAGE
         * -----
         * Run unit tests [ TODO: DISABLED ]
         *
         * TIMEOUT
         * -------
         * 10 Minutes
         *
         * EXECUTION CONDITIONS
         * --------------------
         * - SHOULD_BUILD is true
         * - The build is still successful
         *
         * DESCRIPTION
         * -----------
         * Executes the `gradle test` to run unit tests.
         *
         * OUTPUTS
         * -------
         * None [ TODO: Add test reporting ]
         ************************************************************************/
        stage('Unit Test') {
            when {
                expression {
                    return SHOULD_BUILD == 'true'
                }
            }
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    echo 'Build'
                    sh './gradlew test'
                }
            }
        }

        /************************************************************************
        * STAGE
        * -----
        * SonarQube Scanner
        *
        * EXECUTION CONDITIONS
        * --------------------
        * - SHOULD_BUILD is true
        * - The build is still successful and not unstable
        *
        * DESCRIPTION
        * -----------
        * Runs the sonar-scanner analysis tool, which submits the source, test resutls,
        *  and coverage results for analysis in our SonarQube server.
        ***********************************************************************/
        stage('sonar') {
            when {
                allOf {
                    expression {
                        return SHOULD_BUILD == 'true'
                    }
                    expression {
                        return currentBuild.resultIsBetterOrEqualTo(BUILD_SUCCESS)
                    }
                }
            }
            steps {
                withSonarQubeEnv('sonar-default-server') {
                    sh "./gradlew --info sonarqube -Psonar.host.url=${SONAR_HOST_URL} -Psonar.login=${SONAR_AUTH_TOKEN}"
                }
            }
        }

        /************************************************************************
         * STAGE
         * -----
         * Deploy
         *
         * TIMEOUT
         * -------
         * 5 Minutes
         *
         * EXECUTION CONDITIONS
         * --------------------
         * - SHOULD_BUILD is true
         * - The current branch is MASTER branch or a RELEASE_BRANCH
         * - The build is still successful and not unstable
         *
         * DESCRIPTION
         * -----------
         * Deploys the current build as a maven artifact to a Maven Repository.
         *
         * OUTPUTS
         * -------
         * maven|gradle: an artifact is deployed
         ************************************************************************/
        stage('Deploy') {
            when {
                allOf {
                    expression {
                        return SHOULD_BUILD == 'true'
                    }
                    expression {
                        return currentBuild.resultIsBetterOrEqualTo(BUILD_SUCCESS)
                    }
                    expression {
                        return BRANCH_NAME.equals(MASTER_BRANCH) || RELEASE_BRANCH;
                    }
                }
            }
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    echo 'Publish Artifacts'
                    // Get the registry that we need to publish to
                    withCredentials([usernamePassword(credentialsId: ARTIFACTORY_CREDENTIALS_ID, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        sh "./gradlew publishArtifacts -Pdeploy.username=$USERNAME -Pdeploy.password=$PASSWORD"
                    }
                }
            }
        }
    }
    post {
        /************************************************************************
         * POST BUILD ACTION
         *
         * This step only is executed when SHOULD_BUILD is true.
         *
         * Sends out emails when any of the following are true:
         *
         * - It is the first build for a new branch
         * - The build is successful but the previous build was not
         * - The build failed or is unstable
         * - The build is on the MASTER_BRANCH
         *
         * In the case that an email was sent out, it will send it to individuals
         * who were involved with the build and if broken those involved in
         * breaking the build. If this build is for the MASTER_BRANCH, then an
         * additional set of individuals will also get an email that the build
         * occurred.
         ************************************************************************/
        always {
            script {
                def buildStatus = currentBuild.currentResult

                if (SHOULD_BUILD == 'true') {
                    try {
                        def previousBuild = currentBuild.getPreviousBuild()
                        def recipients = ""

                        def subject = "${currentBuild.currentResult}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
                        def consoleOutput = """
                        <p>Branch: <b>${BRANCH_NAME}</b></p>
                        <p>Check console output at "<a href="${RUN_DISPLAY_URL}">${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>"</p>
                        """

                        def details = ""

                        if (previousBuild == null) {
                            details = "<p>Initial build for new branch.</p>"
                        } else if (currentBuild.resultIsBetterOrEqualTo(BUILD_SUCCESS) && previousBuild.resultIsWorseOrEqualTo(BUILD_UNSTABLE)) {
                            details = "<p>Build returned to normal.</p>"
                        }

                        // Issue #53 - Previously if the first build for a branch failed, logs would not be captured.
                        //             Now they do!
                        if (currentBuild.resultIsWorseOrEqualTo(BUILD_UNSTABLE)) {
                            // Archives any test artifacts for logging and debugging purposes
                            // TODO: archiveArtifacts allowEmptyArchive: true, artifacts: '__tests__/__results__/**/*.log'
                            details = "${details}<p>Build Failure.</p>"
                        }

                        if (details != "") {
                            echo "Sending out email with details"
                            emailext(
                                    subject: subject,
                                    to: recipients,
                                    body: "${details} ${consoleOutput}",
                                    recipientProviders: [[$class: 'DevelopersRecipientProvider'],
                                                         [$class: 'UpstreamComitterRecipientProvider'],
                                                         [$class: 'CulpritsRecipientProvider'],
                                                         [$class: 'RequesterRecipientProvider']]
                            )
                        }
                    } catch (e) {
                        echo "Experienced an error sending an email for a ${buildStatus} build"
                        currentBuild.result = buildStatus
                    }
                }
            }
        }
    }
}
