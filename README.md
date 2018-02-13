## icc - Insight Data Science Coding Challenge
This repository contains Fabricio Kury's submission to a coding challenge by Insight Data Science - Data Engineering.
  
  
#### Requirements
This is a Java program therefore the Java SE Runtime Environment (JRE) is required. It has been tested in Ubuntu 16.04.3 LTS with **JRE-8 version 1.8.0_161**, but it might be backwards compatible with earlier Java versions.  
  
  
#### How to execute
As per the coding challenge, just call _bash run.sh_.  
**If it doesn't run, please just try deleting the file src/icc.jar and trying again.** Deleting the JAR file will make run.sh recompile the source code from scratch, which requires the Java Development Kit (JDK) 8. *JDK 8 is not required to execute the program*, only to recompile it.  
  
  
#### Expected behavior
The bash script run.sh will execute the compiled JAR program (icc.jar).  
The program will read files ./input/itcont.txt and ./input/percentile.txt and produce ./output/repeat_donors.txt progressively as specified the coding challenge.  
Program will report progress to stdout when it has ingested 10,000 lines from itcont.txt, and thereafter progress report will happen in intervals that increase at 40% rate (10,000, 14,000, 19,600...) (the idea is to adapt progress reporting to the size of the input file which is unknown). When it has finished processing itcont.txt, it will report the total number of lines processed (which should equal the number of lines in itcont.txt) and output "Processing complete." to stdout.  
The messages from exceptions occurring during executions are printed to stderr with the respective line of itcont.txt.  
  
  
#### Conceptual overview of the algorithm and its time complexity
The challenge asks for an input file to be read on a rolling basis (as if it were streamed), identify repeat donors, and print statistics (percentile, count and sum) based on donation recipient+zip code+year.  
  
**Reading input data:**: The script processes itcont.txt one line at a time (although the file may be read in chunks). Each line is read character-by-character seeking the delimiter "|" and the fields are identified by their numeric position as defined in the FEC data dictionary, therefore allowing for considerable efficiency even in cases of severely misformed input (e.g. single lines with millions of characters). Once it has found all fields it needs, it stops parsing the line and ingests the record. Misformed individual fields are identified in ways that are specific to each field. Please see the source code at Icc.java for the details.  
  
**Repeat donors:** they are identified by name + ZIP code. The string "donor name=ZIP code" is hashed (HashMap in Java) and that hash provides access to the integer containing the earliest known year of a donation from that donor. If a donor makes a donation, and there is another donation already recorded in a previous year, that donor is a repeat donor.  
  
**Recipient group:** the source code calls a "recipient group" the combination of recipient + zip code + year. This is the group of contributions to be considered for computing the percentile, count and sum. The string "recipient ID=zip code=year" is used as key in a hash map (Java HashMap). The corresponding value of the key is a PercentileQueue object, please read below.  
  
**Percentile, count and sum:** A paramount concern for this coding challenge is computational efficiency, considering that for each record from a repeat donor it is needed to output a percentile, a count and a sum of the contributions. The class PercentileQueue performs this task with two heap data structures called "lower" and "upper". The individual contributions are balanced between the two heaps such that the root of lower is always the desired percentile. The count of contributions is simply the size of both heaps together, and the sum is kept precomputed by PercentileQueue every time it ingests a new contribution. **This allows for logarithmic time when inserting new contributions, and constant time when retrieving the percentile, count and sum.**  
  
  
*Thank you for the opportunity!*  
**Fabricio Kury**  
February 13, 2018
