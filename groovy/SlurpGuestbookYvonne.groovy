import org.ccil.cowan.tagsoup.Parser
import groovy.util.slurpersupport.GPathResult

/**
 * This is a script that obtains the entries from the website of yvonne. I want to insert it into the database of the
 * new database, therefore I am going to create insert statements.
 *
 * @author Jettro Coenradie
 */
class GuestbookSlurper {
    private Parser tagsoupParser = new Parser()
    private XmlSlurper slurper = new XmlSlurper(tagsoupParser)
    private GPathResult page

    GuestbookSlurper() {
        page = slurper.parse("http://books.dreambook.com/nicobulder/afrikasite.html")
    }

    def obtainGuestbookItems() {
        def items = []

        def counter = 0
        page.body.center[1].center.table.each {table ->
            def item = new Expando()
            table.tr.each {row ->
                item.email = '\'\''
                item.homepage = '\'\''
                switch (row.td[0].text()) {
                    case "Name:" :
                        item.name = makeSqlSafe row.td[1].text()
                        break
                    case "E-mail address:" :
                        item.email = makeSqlSafe row.td[1].text()
                        break
                    case "Homepage URL:" :
                        item.homepage = makeSqlSafe row.td[1].text()
                        break
                    case "Comments:" :
                        item.comment = makeSqlSafe row.td[1].text()
                        break
                }
            }
            item.postdate = table.parent().font[counter].text()
            counter++
            items.add item
        }
        return items
    }

    def makeSqlSafe(String input) {
        return "'" + input.replace('\'','&#39;') + "'"
    }

}

def guestbook = new GuestbookSlurper()
def items = guestbook.obtainGuestbookItems()
items.reverse().each {

    def parsedDate = Date.parse("MMMM dd yyyy - hh:mm:ss a",
            it.postdate.split(',')[1]
            .trim()
            .replace('th','')
            .replace('rd','')
            .replace('nd','')
            .replace('1st','1')
    )
    def convertedDate = parsedDate.time / 1000

    println "INSERT INTO wp_dmsguestbook (name,email,url,date,ip,message,guestbook,spam,flag) VALUES (" +
            "$it.name,$it.email,$it.homepage,$convertedDate,'94.210.21.47',$it.comment,0,0,0" +
            ");"
}