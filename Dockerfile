FROM 10.250.250.16/devops/java:8u131-msxf
MAINTAINER long.qian@msxf.com
WORKDIR /opt
ADD target/falcon-agent-linux64-noJar-docker.tar.gz .
COPY Docker/agent.cfg.json .
COPY Docker/agent.sh .

ENV TZ=Asia/Shanghai

RUN apk update \
	&& apk upgrade \
	&& apk add --update procps bash curl iputils\
	&& ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
	&& rm -rf falcon-agent/conf/falcon/agent.cfg.json \
	&& rm -rf falcon-agent/bin/agent.sh \
	&& mv agent.cfg.json falcon-agent/conf/falcon/ \
	&& mv agent.sh falcon-agent/bin/

ENV LANG zh_CN.UTF-8
ENV LANGUAGE zh_CN:zh
ENV LC_ALL zh_CN.UTF-8

CMD /opt/falcon-agent/bin/agent.sh start
HEALTHCHECK --interval=5s --timeout=3s \
  CMD curl -fs http://localhost:4519/mock/list || exit 1