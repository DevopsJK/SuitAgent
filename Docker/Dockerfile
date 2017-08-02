FROM ubuntu
MAINTAINER long.qian@msxf.com
WORKDIR /opt
ADD target/falcon-agent-linux64.tar.gz .
COPY Docker/agent.cfg.json .
COPY Docker/agent.sh .

ENV TZ=Asia/Shanghai

RUN apt-get update \
	&& apt-get upgrade -y \
	&& ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
	&& apt-get -y install language-pack-zh-hans language-pack-zh-hans-base \
	&& locale \
	&& DEBIAN_FRONTEND=noninteractive dpkg-reconfigure locales \
	&& locale-gen zh_CN.UTF-8 \
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