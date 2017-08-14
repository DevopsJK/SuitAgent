FROM anapsix/alpine-java
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
	&& cp agent.cfg.json falcon-agent/conf/falcon/ \
	&& cp agent.sh falcon-agent/bin/ \
	&& rm -rf agent.cfg.json \
	&& rm -rf agent.sh

ENV LANG zh_CN.UTF-8
ENV LANGUAGE zh_CN:zh
ENV LC_ALL zh_CN.UTF-8

CMD /opt/falcon-agent/bin/agent.sh start
HEALTHCHECK --interval=5s --timeout=3s \
  CMD curl -fs http://localhost:4519/mock/list || exit 1