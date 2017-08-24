# SuitAgent Docker 镜像构建以及运行

1.  根据实际情况修改`agent.cfg.json`配置

2. 进入项目主目录

    ```shell
    mvn clean package -Plinux64-noJar-docker -Dmaven.test.skip=true
    sudo docker build -t alpine:suitagent .

    # 两种运行方法：

    # 1、指定本机已有的cAdvisor服务，比如cAdvisor监听在5555端口：
    sudo docker run -d \
    	-e "cadvisor_port=5555" \
    	-v /proc:/proc_host:ro \
    	-v /var/lib/docker:/var/lib/docker:ro \
    	-v /dev:/dev_host:ro \
    	-v /var/log/suitagent:/opt/falcon-agent/logs:rw \
    	-v /var/log/suitagent/falcon-agent:/opt/falcon-agent/falcon/agent/var/:rw \
    	-v /:/rootfs:ro \
        -v /var/run:/var/run:rw \
        -v /sys:/sys:ro \
    	--net="host" --name suitagent \
    	alpine:suitagent
    	
    # 2、不指定cAdvisor，SuitAgent将会启动内置的cAdvisor服务：
    sudo docker run -d \
    	-v /proc:/proc_host:ro \
    	-v /var/lib/docker:/var/lib/docker:ro \
    	-v /dev:/dev_host:ro \
    	-v /var/log/suitagent:/opt/falcon-agent/logs:rw \
    	-v /var/log/suitagent/falcon-agent:/opt/falcon-agent/falcon/agent/var/:rw \
    	-v /:/rootfs:ro \
        -v /var/run:/var/run:rw \
        -v /sys:/sys:ro \
    	--net="host" --name suitagent \
    	alpine:suitagent
    ```

3.  日志路径（宿主机）：`/var/log/suitagent`


