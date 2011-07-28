/**
 * This groovy scripts can be used to clean up your local maven repository without the need to throw it away completely
 *
 * @author Jettro Coenradie
 */

def home = System.getProperty("user.home");
def path = home + '/.m2/repository/nl/rijksoverheid';
maxAgeSnapshotsInDays = 30;
now = new Date();


// Read provided argument
if (args) {
    path = args[0];
} else {
    println "Using the default path $path";
}

// Scan the current directory or the directory as provided by the first parameter of the script
def mainDir = new File(path);
if (!mainDir.directory) {
    printf("The provided directory \"%s\" is not a directory", path);
} else {
    printf("About to clean the directory \"%s\"\n", path);
    readDir(mainDir);
}

def readDir(File file) {
    def lastModified = new Date(file.lastModified());
    def ageInDays = now - lastModified;
//    printf("Dir: %s\n", file.canonicalPath);
//    printf("The directory is last modified at %s\n", lastModified);
//    printf("Number of days between last change and now is %d\n", ageInDays);
    def directories = file.listFiles(new DirectoryFilter());
    if (directories.length > 0) {
        directories.each {
            readDir(it);
        }
    } else {
//        println("No subdirectories found");
        if (ageInDays > maxAgeSnapshotsInDays && file.canonicalPath.endsWith("-SNAPSHOT")) {
            printf("About to remove directory %s\n", file.canonicalPath);
            file.deleteDir();
        }
    }
}

def class DirectoryFilter implements FileFilter {
    boolean accept(File file) {
        return file.directory;
    }
}