#!groovy

major = 0
minor = 0
point = 0
nodeLabel = null
latestCommit = null
latestTag = null
latestTaggedCommit = null
publishedVersion = null
stashCloneCredentialId = null

/*
 * CONVENIENCE FUNCTIONS
 */
def call(Map args) {
    nodeLabel = args['nodeLabel']
    if(args['stashCloneCredentialId']) {
        stashCloneCredentialId = args['stashCloneCredentialId']
    }

    echo 'we\'re in ansiballers'

    isGitRepo()
    setLatestCommit()
    setLatestTag(latestCommit)
    setLatestTaggedCommit()

    if (getCommitFromRef(latestTag) != latestCommit) {
        if (setTargetVersion(args['version'])) {
            return publishVersion(stashCloneCredentialId)
        } else if (setDefaultVersion(latestTag)) {
            return publishVersion(stashCloneCredentialId)
        }
    } else {
        println "repoVersioner - The latest commit already has ${latestTag} published to it, skipping."
        return latestTag
    }
}

/*
 * API FUNCTIONS
 */

def getLatestTag(branchName) {
    sh "git fetch --tags && git fetch"
    latestTag = sh(script: "git describe ${branchName} --abbrev=0 --tags --always", returnStdout: true).trim()
    return latestTag
}

def setLatestTag(branchName = 'HEAD') {
    nodeSwitcher() {
        try {
            if (stashCloneCredentialId) {
                sshagent([stashCloneCredentialId]) {
                    getLatestTag(branchName)
                }
            } else {
                getLatestTag(branchName)
            }
            println "repoVersioner - Found latest tag ${latestTag} from ${branchName}."
        } catch (Exception e) {
            latestTag = ""
            println "repoVersioner - No tags exist for ${branchName}. Setting tag as empty."
        }
    }
}

def setLatestCommit() {
    latestCommit = getCommitFromRef('HEAD')
    println "repoVersioner - Setting latestCommit to ${latestCommit}."
}

def setLatestTaggedCommit() {
    if (latestTag) {
        latestTaggedCommit = getCommitFromRef(latestTag)
    } else {
        latestTaggedCommit = getCommitFromRef(setLatestTag())
    }
    println "repoVersioner - Setting latestTaggedCommit to ${latestTaggedCommit}."
}

def autoBumpVersion() {
    def previousVersion = getVersion()
    point += 1
    println "repoVersioner - Autobumping version to ${getVersion()} from ${previousVersion}."
    return getVersion()
}

// Sets the major, minor and point release numbers from the first match found.
// Should only be used for 3 part versions based upon semver.org.
def parseVersion(str) {
    println "repoVersioner - Parsing version from ${str}."
    // TODO handle/ignore suffix?
    def regex = /\d+(\.\d+)?(\.\d+)?/
    def version = str =~ regex
    if (version) {
        def tokens = version.group().split('\\.')
        println "repoVersioner - Version found in ${version.group()}."
        def result = [:]
        if (tokens.length > 2) {
            return [major: tokens[0].toInteger(), minor: tokens[1].toInteger(), point: tokens[2].toInteger()]
        } else if (tokens.length > 1) {
            return [major: tokens[0].toInteger(), minor: tokens[1].toInteger(), point: 0]
        } else if (tokens.length > 0 ) {
            return [major: tokens[0].toInteger(), minor: 0, point: 0]
        } else {
            println "repoVersioner - Could not split ${version.group()} on '.'. Got ${tokens}."
        }
    } else {
        println "repoVersioner - No version found."
        return false
    }
}

// Sets the target version from specified string, branch name or by bumping the last version.
def setTargetVersion(target = null) {
    if (target) {
        def version = parseVersion(target)
        println "repoVersioner - Attempting to set version to ${version}."
        if (version) {
            // String target contains a version that matches our regex.
            major = version['major']
            minor = version['minor']
            point = version['point']
            if (!getCommitFromRef(getVersion())) {
                // The target commit has not already been published
                return getVersion()
            } else {
                major = 0
                minor = 0
                point = 0
                println "repoVersioner - $target has already been published. Autobumping from latest."
            }
        } else {
            println "repoVersioner - $target doesn't contain a valid version. Autobumping from latest."
        }
    }
    return false
}

def setDefaultVersion(previousVersion) {
    if (previousVersion) {
        println "repoVersioner - Latest tag is ${previousVersion}."
        def version = parseVersion(previousVersion)
        if (version) {
            major = version['major']
            minor = version['minor']
            point = version['point']
            println "repoVersioner - The tag ${previousVersion} contains a valid version. Autobumping it."
            return autoBumpVersion()
        } else {
            major = 0
            minor = 0
            point = 1
            println "repoVersioner - The latest tag, ${version}, doesn't have a number that matches a semver version. Initialising at v0.0.1."
            return getVersion()
        }
    } else { // tag is empty, none exists
        println "repoVersioner - Cannot find any tag in this repository. Initialising it at v0.0.1."
        major = 0
        minor = 0
        point = 1
        return getVersion() // Create first tag!
    }
}

def getVersion() {
    return "v$major.$minor.$point"
}

// does not affect the internal version stored in the module, just tells you what *would* be next
def findNextVersion(currentVersionString=null) {
    // for more control you can increment version parts from the pipeline, eg repoVersioner.minor += 1
    def version = parseVersion(currentVersionString?: getVersion())
    version.point += 1
    def versionStr = "v${version.major}.${version.minor}.${version.point}"
    println "repoVersioner - found next version ${versionStr} from version ${currentVersionString}."
    return versionStr
}

// publish a given version, or internally determined version.
def publishVersion(stashCloneCredentialId, version=null) {
    if (!version) version = getVersion()
    println "repoVersioner - Publishing ${version}."
    def publishScript = """
                        git tag '${version}'
                        git push origin '${version}'
                        """
    if (stashCloneCredentialId) {
        nodeSwitcher() {
            sshagent([stashCloneCredentialId]) {
                sh publishScript
            }
        }
    } else {
        nodeSwitcher() {
            sh publishScript
        }
    }
    return version
}

/*
* HELPER FUNCTIONS
*/

def isGitRepo() {
    // Exit code 0 is a success. Anything else is fail. Have to negate the result.
    println "repoVersioner - Checking current directory is a git repo."
    def result = null
    nodeSwitcher() {
        result = sh(script: "test -d .git", returnStatus: true)
    }
    // Exit code not 0
    if (result) {
        throw new RuntimeException("repoVersioner - This is not a valid git repo. Ensure a git clone is performed before doing version checks.")
    } else {
        return true
    }
}

def getCommitFromRef(ref) {
    def taggedCommit = ""
    if (ref) {
        nodeSwitcher() {
            try {
                taggedCommit = sh(script: "git rev-list '${ref}' -n 1", returnStdout: true).trim()
                println "repoVersioner - Found commit $taggedCommit from ${ref}."
            } catch (Exception e) {
                println "repoVersioner - ${ref} doesn't exist in this git repository, setting commit as empty."
            }
        }
    }
    return taggedCommit // Empty string if nothing exists
}

def nodeSwitcher(Closure closure) {
    if (nodeLabel) {
        node(nodeLabel) { // Use a node since a user has defined one.
            closure.call()
        }
    } else { // Assume a user has wrapped these calls in a node block
        closure.call()
    }
}

def ensureVersionBumped(String version) {
    isGitRepo()
    if (version) {
        def result = sh(script: "git fetch -t origin refs/tags/${version}", returnStatus: true)
        if (result == 128) {
            echo "Didn't find the tag ${version}. Will accept a publish."
        } else {
            throw new RuntimeException("Version ${version} has already been published! Bump your Jenkinsfile version.")
        }
    } else {
        version = bumpedVersion()
        echo "No version specified, bumping version to ${version}"
    }
    return version
}

def ensureVersionBumped(Map args = [:]) {
    def version = args['version'] ?: ""

    // If a credential id is specified, use SSH agent. Otherwise try using default id_rsa
    if (args['stashCloneCredentialId']) {
        sshagent([args['stashCloneCredentialId']]) {
            return ensureVersionBumped(version)
        }
    } else {
        return ensureVersionBumped(version)
    }
}

return this
