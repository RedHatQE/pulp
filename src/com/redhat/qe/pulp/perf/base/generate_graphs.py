#! /usr/bin/python
import subprocess
from optparse import OptionParser

def main():
	parser = OptionParser()
	parser.add_option("-i", "--input_filename", dest="in_filename", 
                        default="stat", help="Input data filename.")
	parser.add_option("-o", "--output_filename", dest="out_filename", 
                        default="stat.png", help="Output filename.")
	(options, args) = parser.parse_args()

	rfh = open("generate_graphs.gp.template", "r")
	wfh = open("generate_graphs.gp", "w")

	wfh.write(rfh.read().replace("OUTPUT_FILE_NAME", options.out_filename).replace("DATA_FILE_NAME", options.in_filename))
	
	wfh.close()

	# Exec
	p = subprocess.Popen(["gnuplot", "generate_graphs.gp"], stdout=subprocess.PIPE)
	p.wait()

if __name__ == "__main__":
	main()
