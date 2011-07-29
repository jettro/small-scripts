/**
 * This groovy scripts can be used to clean up your local maven repository without the need to throw it away completely.
 *
 * You can provide the script with a number of arguments, it is probably easier though to change the Configuration object
 * to your needs before you run the script.
 *
 * I will first explain the defaults, than tell the order of arguments that you can provide to influence what the
 * script does. By default we clean the complete maven repository that is in your home folder. By default we show
 * the details of what we do, but we do a dry run, so we actually do not do a lot. By default we throw away snapshots
 * that are older than 60 days.
 *
 * Defaults plus order of arguments as they can be provided to the script
 * dryRun : true
 * printDetails : true
 * path : home.from.system/.m2/repository
 * maxAgeSnapshotsInDays : 60
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

def class Configuration {
    def homeFolder = System.getProperty("user.home")
    def path = homeFolder + "/.m2/repository"
    def maxAgeSnapshotsInDays = 60
    def dryRun = true
    def printDetails = true
}

/*
 * Main Execution of the script
 */
storeProvidedArgsInConfiguration()
printConfiguration()

def mainDir = new File(configuration.path)
if (!mainDir.directory) {
    printf("The provided directory \"%s\" is not a directory", configuration.path)
    System.exit(-1)
}

cleanDir(mainDir);
printResultOfCleanAction()

/*
 * Helper functions
 */

private def printResultOfCleanAction() {
    printf("* Total size cleaned is %dk\n", Math.round(cleanedSize / 1024))
    println "*******************************************************************************"
    if (configuration.printDetails) {
        details.each {
            println it
        }
    }
}


private def cleanDir(File file) {
    def lastModified = new Date(file.lastModified());
    def ageInDays = now - lastModified;
    def directories = file.listFiles(new DirectoryFilter());

    if (directories.length > 0) {
        directories.each {
            cleanDir(it);
        }
    } else {
        if (ageInDays > configuration.maxAgeSnapshotsInDays && file.canonicalPath.endsWith("-SNAPSHOT")) {
            def files = file.listFiles()
            def size = 0
            files.each {
                size += it.size()
            }
            cleanedSize += size
            details.add("About to remove directory $file.canonicalPath with total size $size and $ageInDays days old");
            if (!configuration.dryRun) {
                file.deleteDir();
            }
        }
    }
}

// Read provided arguments and store the provided arguments in the configuration object
private def storeProvidedArgsInConfiguration() {
    if (args) {
        configuration.dryRun = args[0].toBoolean()
        if (args.length > 1) {
            configuration.printDetails = args[1].toBoolean()
        }
        if (args.length > 2) {
            configuration.path = args[2]
        }
        if (args.length > 3) {
            configuration.maxAgeSnapshotsInDays = args[3]
        }
    }
}

private def printConfiguration() {
    println "*******************************************************************************"
    println "* About the clean a maven repository"
    printf("* Start cleaning at path: %s\n", configuration.path)
    printf("* Remove all snapshots that are older than %d days\n", configuration.maxAgeSnapshotsInDays)
    if (configuration.dryRun) {
        println("* We are going to do a dry run")
    }

}

def class DirectoryFilter implements FileFilter {
    boolean accept(File file) {
        return file.directory;
    }
}