package artifactory

import groovy.text.SimpleTemplateEngine
import groovyx.net.http.RESTClient
import net.sf.json.JSON

/**
 * This groovy class is meant to be used to clean up your Atifactory server or get more information about it's
 * contents. The api of artifactory is documented very well at the following location
 * {@see http://wiki.jfrog.org/confluence/display/RTF/Artifactory%27s+REST+API}
 *
 * At the moment there is one major use of this class, cleaning your repository.
 *
 * Reading data about the repositories is done against /api/repository, if you want to remove items you need to use
 * '/api/storage'
 *
 * Artifactory returns a strange Content Type in the response. We want to use a generic JSON library. Therefore we need
 * to map the incoming type to the standard application/json. An example of the mapping is below, all the other
 * mappings can be found in the obtainServerConnection method.
 * 'application/vnd.org.jfrog.artifactory.storage.FolderInfo+json' => server.parser.'application/json'
 *
 * The class makes use of a config object. The config object is a map with a minimum of the following fields:
 * def config = [
 *       server: 'http://localhost:8080',
 *       repository: 'libs-release-local',
 *       versionsToRemove: ['/3.2.0-build-'],
 *       dryRun: true]
 *
 * The versionsToRemove is an array of strings that are the strart of builds that should be removed. To give an idea of
 * the build numbers we use: 3.2.0-build-1 or 2011.10-build-1. The -build- is important for the solution. This is how
 * we identify an artifact instead of a group folder.
 *
 * The final option to notice is the dryRun option. This way you can get an overview of what will be deleted. If set
 * to false, it will delete the selected artifacts.
 *
 * Usage example
 * -------------
 * def config = [
 *        server: 'http://localhost:8080',
 *        repository: 'libs-release-local',
 *        versionsToRemove: ['/3.2.0-build-'],
 *        dryRun: false]
 *
 * def artifactory = new Artifactory(config)
 *
 * def numberRemoved = artifactory.cleanArtifactsRecursive('nl/gridshore/toberemoved')
 *
 * if (config.dryRun) {*    println "$numberRemoved folders would have been removed."
 *} else {*    println "$numberRemoved folders were removed."
 *}* @author Jettro Coenradie
 */
private class Artifactory {
    def engine = new SimpleTemplateEngine()
    def config

    def Artifactory(config) {
        this.config = config
    }

    /**
     * Print information about all the available repositories in the configured Artifactory
     */
    def printRepositories() {
        def server = obtainServerConnection()
        def resp = server.get(path: '/artifactory/api/repositories')
        if (resp.status != 200) {
            println "ERROR: problem with the call: " + resp.status
            System.exit(-1)
        }
        JSON json = resp.data
        json.each {
            println "key :" + it.key
            println "type : " + it.type
            println "descritpion : " + it.description
            println "url : " + it.url
            println ""
        }
    }

    /**
     * Return information about the provided path for the configured  artifactory and server.
     *
     * @param path String representing the path to obtain information for
     *
     * @return JSON object containing information about the specified folder
     */
    def JSON folderInfo(path) {
        def binding = [repository: config.repository, path: path]
        def template = engine.createTemplate('''/artifactory/api/storage/$repository/$path''').make(binding)
        def query = template.toString()

        def server = obtainServerConnection()

        def resp = server.get(path: query)
        if (resp.status != 200) {
            println "ERROR: problem obtaining folder info: " + resp.status
            println query
            System.exit(-1)
        }
        return resp.data
    }

    /**
     * Recursively removes all folders containing builds that start with the configured paths.
     *
     * @param path String containing the folder to check and use the childs to recursively check as well.
     * @return Number with the amount of folders that were removed.
     */
    def cleanArtifactsRecursive(path) {
        def deleteCounter = 0
        JSON json = folderInfo(path)
        json.children.each {child ->
            if (child.folder) {
                if (isArtifactFolder(child)) {
                    config.versionsToRemove.each {toRemove ->
                        if (child.uri.startsWith(toRemove)) {
                            removeItem(path, child)
                            deleteCounter++
                        }
                    }
                } else {
                    if (!child.uri.contains("ro-scripts")) {
                        deleteCounter += cleanArtifactsRecursive(path + child.uri)
                    }
                }
            }
        }
        return deleteCounter
    }

    private RESTClient obtainServerConnection() {
        def server = new RESTClient(config.server)
        server.parser.'application/vnd.org.jfrog.artifactory.storage.FolderInfo+json' = server.parser.'application/json'
        server.parser.'application/vnd.org.jfrog.artifactory.repositories.RepositoryDetailsList+json' = server.parser.'application/json'

        return server
    }

    private def isArtifactFolder(child) {
        child.uri.contains("-build-")
    }

    private def removeItem(path, child) {
        println "folder: " + path + child.uri + " DELETE"
        def binding = [repository: config.repository, path: path + child.uri]
        def template = engine.createTemplate('''/artifactory/$repository/$path''').make(binding)
        def query = template.toString()
        if (!config.dryRun) {
            def server = new RESTClient(config.server)
            server.delete(path: query)
        }
    }
}
