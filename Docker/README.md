# SuitAgent Docker 镜像构建

 1. 根据实际情况修改`agent.cfg.json`配置
 2. 进入项目主目录
 3. sudo docker build -t alpine:suitagent .
 4. sudo docker run -v /proc:/proc_host:ro -v /dev:/dev_host:ro -d --net="host" --name suitagent alpine:suitagent


