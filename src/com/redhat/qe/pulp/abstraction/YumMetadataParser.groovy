import java.util.zip.GZIPInputStream

class XMLParse {

	public static boolean doesIdInGroupExist(String filePath, String searchString) {
        def file = new File(filePath).getText()
        def parser = new XmlSlurper()
		parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
		parser.setFeature("http://xml.org/sax/features/namespaces", false) 
        def xml = parser.parseText(file)
		for (item in xml.group) {
			if (item.id.text() == searchString) {
				return true
			}
		}
		return false
	}

	public static boolean doesDescriptionInGroupExist(String filePath, String searchString) {
        def file = new File(filePath).getText()
        def parser = new XmlSlurper()
		parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
		parser.setFeature("http://xml.org/sax/features/namespaces", false) 
        def xml = parser.parseText(file)
		for (item in xml.group) {
			if (item.description.text() == searchString) {
				return true
			}
		}
		return false
	}

	public static Collection getAllGroups(String filePath) {
        def file = new File(filePath).getText()
        def parser = new XmlSlurper()
		parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
		parser.setFeature("http://xml.org/sax/features/namespaces", false) 
        def xml = parser.parseText(file)
		def rtn = []
		xml.group.each() {
			rtn.add("id=" + it.id.text() + ",description=" + it.description.text())
		}
		return rtn
	}

	public static Collection getAllPackages(String filePath) {
		def fstream = new GZIPInputStream(new FileInputStream(filePath))
		def parser = new XmlSlurper()
		parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
		parser.setFeature("http://xml.org/sax/features/namespaces", false) 
		def xml = parser.parse(fstream)
		def rtn = []
		xml.package.each() {
			rtn.add(it.location.@href.text())
		}
		return rtn
	}

	public static String findPrimaryXML(String filePath) {
        def file = new File(filePath).getText()
        def parser = new XmlSlurper()
		parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
		parser.setFeature("http://xml.org/sax/features/namespaces", false) 
		def xml = parser.parseText(file)
		def rtn = ""
		xml.data.each() {
			if (it.@type.text().contains('primary')) {
				rtn = (it.location.@href)
			}
		}
		return rtn
	}

    public static void main(String[] args) {
		//println doesIdInGroupExist("/tmp/test_comps.xml", "Clones")	
		//println doesDescriptionInGroupExist("/tmp/test_comps.xml", "Test123")	
		//println getAllGroups("/tmp/test_comps.xml")
		//println getAllPackages("/tmp/primary.xml.gz")
		println findPrimaryXML("/tmp/repomd.xml")
    }
}
