/**
 * This groovy scripts can be used to clean up your local maven repository without the need to throw it away completely.
 *
 * You can tweak the script by changing the Configuration object. Parameters that can be changed are discussed below.
 * The defaults in the script are for safety, we by default show everything and do nothing
 *
 * homeFolder : The user home folder taken from the system property
 * path : The home folder as mentioned above together /.m2/repository, the default location of a maven repository
 * dryRun : true, so we do not actually delete something by default
 * printDetails : true, to check what will happen if you run this script without the dry run.
 * maxAgeSnapshotsInDays : 60, older snapshots are removed
 * maxAgeInDays : 90, older versions are removed if a higher version is available
 * versionsToKeep : ["3.1.0.M1"], array of versions that we do not want to delete (Not used for SNAPSHOTS)
 * snapshotsOnly : true, by default we only check the snapshots, change if you want to check the other versions as well
 *
 * @author Jettro Coenradie
 */

/*
 * Here we specify the defaults of the script
 */
now = new Date()
configuration = new Configuration()
cleanedSize = 0
details = []
directoryFilter = new DirectoryFilter()
nonSnapshotDirectoryFilter = new NonSnapshotDirectoryFilter()

def class Configuration {
    def homeFolder = System.getProperty("user.home")
    def path = homeFolder + "/.m2/repository"
    def dryRun = true
    def printDetails = true
    def maxAgeSnapshotsInDays = 60
    def maxAgeInDays = 90
    def versionsToKeep = ["3.1.0.M1"]
    def snapshotsOnly = true
}

/*
 * Main Execution of the script
 */
printConfiguration()

def mainDir = new File(configuration.path)
if (!mainDir.directory) {
    printf("The provided directory \"%s\" is not a directory", configuration.path)
    System.exit(-1)
}

cleanMavenRepository(mainDir);
printResultOfCleanAction()

/*
 * Helper functions
 */

private def cleanMavenRepository(File file) {
    def lastModified = new Date(file.lastModified());
    def ageInDays = now - lastModified;
    def directories = file.listFiles(directoryFilter);

    if (directories.length > 0) {
        directories.each {
            cleanMavenRepository(it);
        }
    } else {
        if (ageInDays > configuration.maxAgeSnapshotsInDays && file.canonicalPath.endsWith("-SNAPSHOT")) {
            int size = removeDirAndReturnFreedKBytes(file)
            details.add("About to remove directory $file.canonicalPath with total size $size and $ageInDays days old");
        } else if (ageInDays > configuration.maxAgeInDays && !file.canonicalPath.endsWith("-SNAPSHOT") && !configuration.snapshotsOnly) {
            String highest = obtainHighestVersionOfArtifact(file)
            if (file.name != highest && !configuration.versionsToKeep.contains(file.name)) {
                int size = removeDirAndReturnFreedKBytes(file)
                details.add("About to remove directory $file.canonicalPath with total size $size and $ageInDays days old and not highest version $highest");
            }
        }
    }
}

/**
 * Removes the directory if this is not a dry run. Calculates the amount of Kb that was freed and returns it.
 * @param file File representing a directory to remove
 * @return integer representing the amount of Kb that was freed
 */
private int removeDirAndReturnFreedKBytes(File file) {
    int size = calculateDirectorySize(file)
    cleanedSize += size
    if (!configuration.dryRun) {
        file.deleteDir();
    }
    return size
}

/**
 * Returns the string representation of the artifact with the highest version
 * @param file File repsenting a directory to check for the highest version of artifact
 * @return String representing the highest version
 */
private String obtainHighestVersionOfArtifact(File file) {
    def folderWithVersions = file.parentFile
    // Keep only the highest version
    def versionsFolders = folderWithVersions.listFiles(nonSnapshotDirectoryFilter)
    def highest = '0'
    versionsFolders.each {
        if (higherThan(highest, it.name)) {
            highest = it.name
        }
    }
    return highest
}

/**
 * True if newVersion > than highestVersion. Higher means if the tokenized string is higher in one of the elements
 * starting from the left. If the token of the string is a pure number, we compare numbers. If (part of) it is a
 * we compare strings. Yes this could mean problems in very special cases. Therefore we love people that stick to
 * maven standards. There are some special cases, they are documented below.
 * - If an artifact ends with RELEASE, by default we value it higher than another artifact
 *
 * examples for strings that we value as a release are:
 * 3.0.0.RELEASE, 2.5.6, 2.3.4-beta1
 *
 * @param highestVersion String representing the current highest version
 * @param newVersion String to compare with the highest version
 * @return true if the newVersion is higher than the current highestVersion
 */
private boolean higherThan(highestVersion, newVersion) {
    def highestArr = highestVersion.tokenize('.')
    def newArr = newVersion.tokenize('.')
    if (highestVersion.endsWith("RELEASE") && !newVersion.endsWith("RELEASE")) {
        return false
    }
    return compareTwoIntegersInArray(highestArr, newArr, 0)
}

/**
 * Utility function that is used recursive to find the highest of two items in an array on the same position. If they are
 * equal, compare the next item in the array if available. If the first item of an array is teh same and one of the two
 * items has only one item in the array, the other one wins.
 * @param highestArr Array containing the elements of the current highest value
 * @param newArr Array containing the elements of the value to compare with the highest value
 * @param counter Integer representing the number in the array to compare (starting with 0)
 * @return true if the newArr is higher than the highestArray
 */
private boolean compareTwoIntegersInArray(highestArr, newArr, counter) {
    def counterPlus1 = counter + 1
    if (highestArr[counter] == newArr[counter]) {
        if (highestArr.size() > counterPlus1 && newArr.size() > counterPlus1) {
            return compareTwoIntegersInArray(highestArr, newArr, counterPlus1)
        } else if (newArr.size() > counterPlus1) {
            return true
        }
    } else {
        def highest = highestArr[counter]
        def newest = newArr[counter]
        if (highest.isInteger() && newest.isInteger()) {
            if (highest.toInteger() < newest.toInteger()) {
                return true
            }
        } else {
            if (highest < newest) {
                return true
            }
        }
    }
    return false
}

/**
 * Returns the size in Kbytes of the content of the provided directory
 *
 * @param file File that must be a directory to calculate the size for.
 * @return Integer representing the size in Kb of the provided directory
 */
private int calculateDirectorySize(File file) {
    def files = file.listFiles()
    def size = 0
    files.each {
        size += it.size()
    }
    return size
}

private def printConfiguration() {
    println "*******************************************************************************"
    println "* About the clean a maven repository"
    printf("* Start cleaning at path: %s\n", configuration.path)
    printf("* Remove all snapshots that are older than %d days\n", configuration.maxAgeSnapshotsInDays)
    printf("* Remove all versions that are older than %d days and do have a higher version available\n", configuration.maxAgeInDays)
    if (configuration.dryRun) {
        println("* We are going to do a dry run")
    }
    if (configuration.snapshotsOnly) {
        println("* We only remove snapshots, no versioned artifacts are deleted")
    }

}

private def printResultOfCleanAction() {
    printf("* Total size cleaned is %dk\n", Math.round(cleanedSize / 1024))
    println "*******************************************************************************"
    if (configuration.printDetails) {
        details.each {
            println it
        }
    }
}

def class DirectoryFilter implements FileFilter {
    boolean accept(File file) {
        return file.directory;
    }
}

def class NonSnapshotDirectoryFilter implements FileFilter {
    boolean accept(File file) {
        return file.directory && !file.name.endsWith("-SNAPSHOT")
    }
}