#!/usr/bin/env bash

START_DATE=`date +%Y%m%d%H%M%S`
FINDNAME=$0
while [ -h $FINDNAME ] ; do FINDNAME=`ls -ld $FINDNAME | awk '{print $NF}'` ; done
RUNDIR=`echo $FINDNAME | sed -e 's@/[^/]*$@@'`
unset FINDNAME

# cd to top level agent home
if test -d $RUNDIR; then
  cd $RUNDIR/..
else
  cd ..
fi

agentHome=`pwd`

JAVA="${agentHome}/jre/bin/java"

if [ ! -f "$JAVA" ]
then
		source /etc/profile
		if [ "x${JAVA_HOME}" == "x" ]; then
			echo "JAVA_HOME is not valid"
			exit 1
		fi
		JAVA="${JAVA_HOME}/bin/java"
fi

if [ ! -f "$JAVA" ]
then
        echo Invalid Java Home detected at ${JAVA}
        exit 1
fi

liblist=`ls ${agentHome}/lib/`
for lib in $liblist
do
 agent_classpath="${agent_classpath}:${agentHome}/lib/${lib}"
done
agent_class=com.falcon.suitagent.Agent

CMD=$1
if [ $2 ]; then
	for i in $@; do
		if [ "$i" != "$CMD" ]; then
			JAVA="${JAVA} ${i}"
		fi
	done
fi

client_cmd="${JAVA} \
	-server -Xms64m -Xmx256m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m -XX:SurvivorRatio=4 -XX:LargePageSizeInBytes=128m \
	-XX:+UseFastAccessorMethods -XX:MaxTenuringThreshold=5 \
	-XX:+TieredCompilation -XX:AutoBoxCacheMax=20000 \
	-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${agentHome}/logs/oom-${START_DATE}.hprof \
	-Dagent.conf.path=${agentHome}/conf/agent.properties \
	-Dauthorization.conf.path=${agentHome}/conf/authorization.properties \
	-Dagent.quartz.conf.path=${agentHome}/conf/quartz.properties \
	-Dagent.log4j.conf.path=${agentHome}/conf/log4j.properties \
	-Dagent.jmx.metrics.common.path=${agentHome}/conf/jmx/common.properties \
	-Dagent.plugin.conf.dir=${agentHome}/conf/plugin \
	-Dagent.falcon.dir=${agentHome}/falcon \
	-Dagent.falcon.conf.dir=${agentHome}/conf/falcon \
	-Dagent.home.dir=${agentHome} \
	-Dfile.encoding=UTF-8 \
	-cp ${agent_classpath} ${agent_class} ${CMD}
"

case ${CMD} in
start)
	$client_cmd
;;
stop)
	$client_cmd
;;
update)
	$client_cmd
;;
status)
	$client_cmd
;;
*)
    echo "Syntax: program < start | stop | status | update >"
esac