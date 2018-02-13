#!/bin/bash
if [ ! -f ./src/icc.jar ];
then
   echo "icc.jar not found. Will try to compile it."
   cd ./src
   javac ./icc/Icc.java
   if [ $? -eq 0 ];
   then
      jar cmvf ./manifest.mf ./icc.jar ./icc/*.class
   fi
   if [ $? -eq 0 ];
   then
      echo "Compilation successful."
   else
      echo "Compilation failed. Please ensure that Oracle JDK-8 is installed."
      rm ./icc/*.jar
      rm ./icc/*.class
   fi
   cd ..
fi

if [ -f ./src/icc.jar ];
then
   java -jar ./src/icc.jar ./input/itcont.txt ./input/percentile.txt ./output/repeat_donors.txt
else
   echo "Error: icc.jar not found."
fi

