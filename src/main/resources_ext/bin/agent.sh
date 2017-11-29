#!/usr/bin/env bash

rm -rf /tmp/suitagentSh.log

START_DATE=`date +%Y%m%d%H%M%S`

echo "=== Agent Shell Start At ${START_DATE}" >> /tmp/suitagentSh.log

FINDNAME=$0
while [ -h $FINDNAME ] ; do FINDNAME=`ls -ld $FINDNAME | awk '{print $NF}'` ; done
RUNDIR=`echo $FINDNAME | sed -e 's@/[^/]*$@@'`
unset FINDNAME

echo "=== RUNDIR Is ${RUNDIR}" >> /tmp/suitagentSh.log

# cd to top level agent home
if test -d $RUNDIR; then
  cd $RUNDIR/..
else
  cd ..
fi

agentHome=`pwd`

echo "=== AgentHome Is ${agentHome}" >> /tmp/suitagentSh.log

JAVA="${agentHome}/jre/bin/java"

echo "=== JAVA Path Is ${JAVA}" >> /tmp/suitagentSh.log

if [ ! -f "$JAVA" ]
then
		source /etc/profile
		if [ "x${JAVA_HOME}" == "x" ]; then
			echo "JAVA_HOME is not valid"
			echo "Shell Exit : JAVA_HOME is not valid" >> /tmp/suitagentSh.log
			exit 1
		fi
		JAVA="${JAVA_HOME}/bin/java"
fi

if [ ! -f "$JAVA" ]
then
        echo Invalid Java Home detected at ${JAVA}
        echo Shell Exit : Invalid Java Home detected at ${JAVA} >> /tmp/suitagentSh.log
        exit 1
fi

echo "=== Final Java Path Is ${JAVA}" >> /tmp/suitagentSh.log

liblist=`ls ${agentHome}/lib/`
for lib in $liblist
do
 agent_classpath="${agent_classpath}:${agentHome}/lib/${lib}"
done

echo "=== ClassPath Is ${agent_classpath}" >> /tmp/suitagentSh.log

agent_class=com.falcon.suitagent.Agent

CMD=$1

echo "=== CMD Is ${CMD}" >> /tmp/suitagentSh.log

if [ $2 ]; then
	for i in $@; do
		if [ "$i" != "$CMD" ]; then
			JAVA="${JAVA} ${i}"
		fi
	done
fi

echo "=== Final Java Is ${JAVA}" >> /tmp/suitagentSh.log

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
	-cp ${agent_classpath} ${agent_class} ${CMD}
"

echo "=== Client Cmd Is ${client_cmd}" >> /tmp/suitagentSh.log

case ${CMD} in
start)
	echo "=== Do Start" >> /tmp/suitagentSh.log
	nohup $client_cmd > /dev/null 2>&1 &
;;
stop)
	echo "=== Do Stop" >> /tmp/suitagentSh.log
	$client_cmd
;;
update)
	echo "=== Do Update" >> /tmp/suitagentSh.log
	$client_cmd
;;
status)
	echo "=== Do Status" >> /tmp/suitagentSh.log
	$client_cmd
;;
*)
    echo "Syntax: program < start | stop | status | update >"
    echo "Shell Exit : Syntax: program < start | stop | status | update >" >> /tmp/suitagentSh.log
esac