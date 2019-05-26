#!/bin/sh
java -cp emx370.jar -XX:-DontCompileHugeMethods -Xmx1024m -Xms512m dev.hawala.vm370.Emx370 $*
