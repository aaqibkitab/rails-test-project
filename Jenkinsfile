/**
 * OpenShift Example CICD pipeline
 *
 * For detailed information please see the 'Jenkinsfile.md' readme
 *
 * NOTES:
 * - you need to setup a checkout behavior of the pipeline to checkout as SSH
 * - semantic version works based on you release your master code from the 'master' branch as per good practices
 * - in order for semantic versioning to work you need to provide credentials with read/write access to the repo
 * - input calls should be made outside of a node block to prevent it from holding onto a heavy executor
 * - milestone should be used to force all previous builds waiting to be promoted to abort once this one is clicked
 */

oc = '/opt/homebrew/bin/oc'
//jenkinsSlaveLabel = 'Built-In' // Not sure about this
appName = 'rails-app'
appSpace = 'aaqibzahoor-dev'
repoVersionKey = 'bitbucket-ssh-key-read-write'

// dynamic properties (NOT resolvable on pipeline start)
appDeployName = { "${APP_NAME}-${APP_REGION}" }
configDir = { "env/${APP_REGION}" }

// cluster urls
openshiftDevUrl = 'https://api.sandbox.x8i5.p1.openshiftapps.com:6443'
openshiftNonProdUrl = '#'

// flag to allow us to mark a pipeline run in bitbucket as a success & prevent subsequent steps from being run
aborted = false

// helpers
//publishVersion = fileLoader.fromGit('repoVersioner.groovy', 'ssh://git@bitbucket.int.corp.sun:2222/jh/repo-versioner.git', 'v1.0.3', repoVersionKey, jenkinsSlaveLabel)

// TODO: test using dynamic slaves, ensure we do not hang onto pods while waiting for manual steps
// TODO: issue with bitbucket and forking into your user account it hashes the URL 'ssh://git@bitbucket.int.corp.sun:2222/~****/domestic-canary-cicd.git'

pipeline {
  agent {
   kubernetes {
      cloud 'kubernetes'
      namespace appSpace
      //credentialsId 'openshift-oc-credentials'
      label 'cicd-pod1'
      yamlFile 'agent-pod.yml'
    }
  }
  //agent any
  options { timestamps() }
  environment {
    // dynamic properties (resolvable on pipeline start)
    APP_NAME = generateAppName(appName)
    BUILD_LABEL = generateBuildTag()
    BUILD_ARTIFACT_URL = "${NEXUS_URL}/service/local/repositories/inhouse/content/openshift-platform/${appName}/${BUILD_LABEL}/${appName}-${BUILD_LABEL}.jar"
    // static properties
    APP_REGION = 'dev'
    BRANCH_NAME = 'master'
    APP_HEALTH_DELAY = 45
    APP_HEALTH_PATH = '/health'
    APP_SECRET = 'bitbucket-ssh-key'
    APP_SPACE = "${appSpace}"
    CONFIG_BRANCH = 'master'
    CONFIG_FILE = 'application.yml' // Not sure about it
    CONFIG_REPO = 'https://github.com/aaqibkitab/rails-test-project.git'
    CONFIG_CRED_ID = 'config-ssh-key'
    GIT_HASHED_BRANCH_FILTER = '.*(develop|release|hotfix|bugfix).*'
    GIT_SEMVER_BRANCH = 'master'
    JAVA_HOME = '/apps/java/current-version-8' // Do we need this?
    OPENSHIFT_CREDS = credentials('openshift-oc-credentials')
    NEXUS_URL = 'https://nexus.int.corp.sun' // Not able to find this?
    SECRET_FILE = 'application-secrets.yml'
    SIMPLE_FLOW_FILTER = '.*(feature).*'
  }
  stages {
    stage('BuildImage') {
      steps {
        script {
          echo "${OPENSHIFT_CREDS}"
          authenticate openshiftDevUrl

          try {
            sh "$oc get bc ${APP_NAME}"
          } catch (ex) {
            sh "$oc new-build --code ${env.GIT_URL}#${GIT_BRANCH} " +
                    "--name ${APP_NAME} " +
                    "--labels='app=${APP_NAME},related=${appDeployName()}' " +
                    "--strategy=docker --allow-missing-images"

            sh "$oc cancel-build bc/${APP_NAME}"   // cancel first build, triggered below
          }

          sh "$oc start-build ${APP_NAME} --follow --wait"
          sh "$oc tag ${APP_NAME}:latest ${APP_NAME}:latest"
        }
      }
    }

    stage('DeployDev') {
      environment {
        APP_REGION = 'dev'
      }
      steps {
        script {
            deploySteps(openshiftDevUrl)
        }
      }
    }


    stage('CleanUp') {
      when {
        expression {
          skipAbortedOrCondition { (env.BRANCH_NAME ==~ env.SIMPLE_FLOW_FILTER) }
        }
      }
      steps {
        echo "clean"
      }
    }
  }
}

//
// PIPELINE HELPERS - should be kept in the Jenkinsfile to allow teams to customise and not create black-boxes
//

def deploySteps(String cluster, def domains = []) {
  authenticate cluster

  dir('config') {
    refreshConfig()
  }

  try {
    sh "$oc get dc/${appDeployName()}"
  } catch(ex) {
    createDeployment()
  }

  configureDeployment(domains)

  sh "$oc rollout latest dc/${appDeployName()}"
  sh "$oc logs dc/${appDeployName()} --follow"
}

def configureDeployment(def domains = []) {
  sh "$oc rollout pause dc/${appDeployName()}"

  try {
    // mount configuration objects as volumes on the container
    def volumeMaskRegex = '.*volume mount.*already exists.*'
    shMask "$oc set volume dc/${appDeployName()} --add --configmap-name=${APP_NAME}-config-${APP_REGION} --mount-path=/opt/app-root/config", "${volumeMaskRegex}"
    shMask "$oc set volume dc/${appDeployName()} --add --secret-name=${APP_NAME}-secret-${APP_REGION} --mount-path=/opt/app-root/secrets", "${volumeMaskRegex}"

    // add a health check to the deployment configuration
   // sh "$oc set probe dc/${appDeployName()} --readiness --get-url=http://:8080${APP_HEALTH_PATH} --initial-delay-seconds=${APP_HEALTH_DELAY}"
   // sh "$oc set probe dc/${appDeployName()} --liveness --get-url=http://:8080${APP_HEALTH_PATH} --initial-delay-seconds=${APP_HEALTH_DELAY}"

    // set any runtime environment variables
    sh "$oc set env dc/${appDeployName()} SPRING_CONFIG_LOCATION=/opt/app-root/secrets/"
    sh "$oc set env dc/${appDeployName()} SPRING_PROFILES_ACTIVE=${APP_REGION}"

    // add domain based labels to service before creating the route so they are inherited
    if (domains.size() > 0) {
      def labels = buildDomainLabels(domains)
      sh "$oc label svc ${appDeployName()} ${labels.trim()} --overwrite=true"
    }

    // create a secure route for application, the platform integration will automatic generate a unique DNS name
    shMask "$oc create route edge ${appDeployName()} --port 3000-80 --service=${appDeployName()} --insecure-policy Redirect --wildcard-policy None", '.*route.*already exists.*'
  }
  finally {
    // prevent dc from being stuck in a paused state on error
       sh "$oc rollout resume dc/${appDeployName()}"
  }
}

def refreshConfig() {
  // checkout config first, if this fails we don't want to delete our config maps etc
  git url: env.CONFIG_REPO, credentialsId: env.CONFIG_CRED_ID, branch: env.CONFIG_BRANCH

  sh "$oc delete configmap ${APP_NAME}-config-${APP_REGION} --ignore-not-found=true"
  sh "$oc delete secret ${APP_NAME}-secret-${APP_REGION} --ignore-not-found=true"

  sh "$oc create configmap ${APP_NAME}-config-${APP_REGION} --from-file application.yml=${configDir()}/${CONFIG_FILE}"
  sh "$oc create secret generic ${APP_NAME}-secret-${APP_REGION} --from-file application.yml=${configDir()}/${SECRET_FILE}"
}
def createDeployment() {
  sh "$oc run ${appDeployName()} --image=image-registry.openshift-image-registry.svc:5000/${APP_SPACE}/${APP_NAME}:latest " +
          "--image-pull-policy=IfNotPresent " +
          "--labels app=${appDeployName()} "
  sh "$oc create deploymentconfig ${appDeployName()} --image=image-registry.openshift-image-registry.svc:5000/${APP_SPACE}/${APP_NAME}:latest"
  sh "$oc create service clusterip ${appDeployName()} --tcp 3000:80"
}

def skipAbortedOrCondition(Closure closure) {
  !aborted && closure.call()
}

def authenticate(cluster) {
  sh "$oc login --token=${OPENSHIFT_CREDS} --server=${cluster}"
  sh "$oc project ${APP_SPACE}"
}

def generateAppName(appName) {
  // need to use BRANCH_NAME not GIT_BRANCH here, BRANCH_NAME is always available
  if (env.BRANCH_NAME == 'master') {
    return appName
  }
  //"${appName}-${env.BRANCH_NAME?.replace('/', '-')?.toLowerCase()}"
  "rails-app-master"
}

def generateBuildTag() {
  String buildLabel = null
  
  buildLabel = "1"
}

//
// HELPER METHOD - Good candidates for jenkins helpers
//

def buildDomainLabels(domains = []) {
  def labels = ''
  def defaultDomain = defaultClusterDomain(appSpace)
  def containsDefaultDomain = domains.contains(defaultDomain)
  if (!containsDefaultDomain) {
    labels = " domain/${defaultDomain}=false"
  }
  domains.each { domain ->
    labels = labels + " domain/${domain}=true"
  }
  labels
}

def defaultClusterDomain(String appSpace) {
  // https://confluence.int.corp.sun/confluence/display/ODP/Inbound+Networking
  def result = shCapture("$oc whoami --show-server")
  String clusterUrl = result.output
  boolean isPublicAppSpace = appSpace.contains('-public')
  if (clusterUrl.contains('-dev')) {
    return 'int'
  } else if (clusterUrl.contains('-nonprod')) {
    return (isPublicAppSpace) ? 'exttest.lab' : 'xint'
  } else if (clusterUrl.contains('-prod')) {
    return (isPublicAppSpace) ? 'ext' : 'int'
  }
}

def confirmationStep(String agentLabel, String prompt, String task = 'Task', int timeoutHours = 2, Closure yesScript) {
  try {
    timeout(time: timeoutHours, unit:'HOURS') {
      input prompt
      milestone()
    }
    node(agentLabel) {
      yesScript.call()
    }
  } catch(ex) {
    println "ERROR = $ex"
    def user = ex.getCauses()[0].getUser() as String
    currentBuild.result = "SUCCESS"
    currentBuild.description = (user != "SYSTEM") ? "${task} aborted by ${user}" : "${task} timed out"
    aborted = true
  }
}

/**
 * Run a shell command masking errors on specified failures determined by the regular expression
 * @param command - the shell command you wish to run
 * @param regex - a regex of error messages you wish to mask
 * @return an object with a 'status' and 'error' attributes
 */
def shMask(command, regex) {
  def result = shCapture(command)
  if (result.status != 0 && !(result.output ==~ /$regex/)) {
    error result.output
  }
  result
}

/**
 * Allows you to run a shell command and programmatically determine what you want to do with the error code and message
 * @param command - the shell command you wish to run
 * @return an object with a 'status' and 'error' attributes
 */
def shCapture(command) {
  def out = sh(script: 'mktemp', returnStdout: true).trim()
  def status = sh(script: "${command} &> $out", returnStatus: true)
  def output = readFile out
  [status: status, output: output?.replaceAll("[\n\r]", ' ')]
}
