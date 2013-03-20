#! /usr/bin/python

""" Utility method intended to be used by automation to verify sync success """

import gzip
import sys
import os
import fnmatch
import xml.etree.ElementTree as xml
from xml.dom.minidom import parseString
from optparse import OptionParser

""" Parse inputted primary.xml.gz and 
	return a generator of rpm name and its checksum. """
def get_package(fname):
    shalist = []
    fnamelist = []
    try:
        rfh = gzip.open(fname, "rb")
        tree = xml.parse(rfh)

        for i in tree.getiterator():
            if "checksum" in i.tag:
                shalist.append((i.items()[1][1], i.text))  
            if "location" in i.tag:
                fnamelist.append(i.items()[0][1])
        rfh.close()
    except:
        print "Unexpected error:", sys.exc_info()[0]
        raise
    return zip(shalist, fnamelist)

def check_physical_rpm(checksum_type, checksum, name):
    base_dir = "/var/lib/pulp/packages"
    match = ""
    for root, dirname, filename in os.walk(base_dir):
        for filename in fnmatch.filter(filename, name):
            match = os.path.join(root, filename)
            lchecksum = ""
            if checksum_type == "sha":
                ph = os.popen("sha1sum %s" % match)
                lchecksum = ph.read().split(" ")[0].strip()
                ph.close()
            elif checksum_type == "sha256":
                ph = os.popen("sha256sum %s" % match)
                lchecksum = ph.read().split(" ")[0].strip()
                ph.close()
            if (lchecksum == checksum):
                return True 	
            else:
                print "Error: sha256sum does not match (%s, %s)" % (lchecksum, checksum)
                return False
    print "Error: Cannot locate %s " % name
    return False

""" Main Method """
def main():
    parser = OptionParser()
    parser.add_option("-f", "--file", dest="xml_file", 
                        default="/tmp/primary.xml.gz",
						help="Full file path to primary.xml.gz")
    parser.add_option("-r", "--repoid", dest="repo_id", 
                        default="foo", 
						help="Repository ID")
    parser.add_option("-y", "--yum_check", dest="yum_check", 
                        default=False, action="store_true",
						help="Performs yum check.")
    parser.add_option("-s", "--file_check", dest="fs_check", 
                        default=False, action="store_true",
						help="Performs fs check.")

    (options, args) = parser.parse_args()

    yum_result = os.popen("repoquery -a --qf \'%{name}-%{version}-%{release}.%{arch}.rpm\' --show-dupes --repoid=" + options.repo_id).read().split("\n")
    p_count = 0
    fs_count = 0
    y_count = 0
    for item in get_package(options.xml_file):
        if (options.fs_check):
            if not check_physical_rpm(item[0][0], item[0][1], item[1]):
                sys.exit(1)
            fs_count+=1
        if (options.yum_check):
            if not item[1] in yum_result:
                print "Error: Cannot find %s in YUM" % item[1]
                sys.exit(1)
            y_count+=1
        p_count+=1

    print "Total items parsed %d." % p_count
    print "Items verified and passed FS test %d." % fs_count
    print "Items verified and passed YUM test %d." % y_count

if __name__ == "__main__":
    main()
