#!/bin/bash
java -Dfile.encoding=UTF8 -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=384M -jar `dirname $0`/project/sbt-launch.jar "$@"