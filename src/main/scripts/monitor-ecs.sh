#!/bin/bash

# Check docker daemon service

if ! service docker status; then
    echo "docker daemon down, starting docker daemon"
    set -x
    service docker stop && \
    service docker start

    #Purge containers and images
    docker container prune -f
    docker image prune -a -f
    set +x
fi

# Check ecs agent

if [[ $(docker ps -q -f 'name=ecs-agent' | wc -c) -eq 0 ]]; then
    echo "ecs agent down, starting agent"
    set -x
    docker ps -q | xargs docker inspect --format='{{ .State.Pid }}' | xargs -IZ fstrim /proc/Z/root/
    start ecs
    set +x
fi
