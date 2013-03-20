#!/usr/bin/python
import subprocess
import os
from time import strftime
from optparse import OptionParser

"""
Stat Gathered:
- Free/Used mem
- IO usage
- CPU utilization
- Network traffic
"""
if __name__=="__main__":
	parser = OptionParser()
	parser.add_option("-n", "--num_sync", dest="num_sync", 
                        default="0",
						help="Number of concurrent sync.")
	parser.add_option("-w", "--write_output", dest="write_output", 
                        default=False, action="store_true",
						help="Write output file.")
	(options, args) = parser.parse_args()

	stat_header = ["timestamp", "mem_used", "mem_free", "disk_name", "tps", "read/s", "write/s", "user", "sys", "idle"]
	stat_info = []
	# Free/Used Mem
	p = subprocess.Popen(["free", "-m"], stdout=subprocess.PIPE)	
	p.wait()
	out = p.stdout
	for line in out.readlines():
		if (line.startswith("Mem")):
			s = [x.strip() for x in line.split(" ") if len(x) != 0]
			stat_info.extend([s[2], s[3]])
	# IO Usage
	p = subprocess.Popen(["iostat", "-m", "-N"], stdout=subprocess.PIPE)	
	p.wait()
	out = p.stdout
	for line in out.readlines():
		if (line.startswith("vg") and ("root" in line)):
			s = [x.strip() for x in line.split(" ") if len(x) != 0]
			stat_info.extend([s[0], s[1], s[2], s[3]])
	# CPU util
	p = subprocess.Popen(["iostat", "-c"], stdout=subprocess.PIPE)	
	p.wait()
	out = p.stdout
	line = out.readlines()
	c_stat = [s for s in [s for s in [s.strip("\n") for s in line] if s!=""][-1].split(" ") if s!=""]
	stat_info.extend([c_stat[0], c_stat[2], c_stat[-1]])
	# TODO
	# Network traffic

	if options.write_output:
		wfh = ""
		if (os.path.exists("stat_%s" % options.num_sync)):
			wfh = open("stat_%s" % options.num_sync, "a")
		else:
			wfh = open("stat_%s" % options.num_sync, "w")
			header = ' '.join(stat_header)
			wfh.write("%s\n" % header)
		wfh.write("%s " % strftime("%H:%M:%S"))
		for data in stat_info:
			wfh.write(data)
			wfh.write(" ")
		wfh.write("\n")
		wfh.close()
	else:
		s = " ".join(stat_info)
		print "%s %s" % (strftime("%H:%M:%S"), s)
