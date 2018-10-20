# AWS Tools - Scripts

## ECS

# Monitor ECS cluster instances

[monitor-ecs.sh](monitor-ecs.sh): A script that checks if the docker daemon and ecs agent are running. If not, it attempts to clean up and start them.

To be used in a cron job on an ECS container instance as follows:

```
*/10 * * * * SHELL=/bin/sh PATH=/bin:/sbin:/usr/bin:/usr/sbin /home/ec2-user/monitor-ecs.sh 2>&1 | /usr/bin/logger -t monitor-ecs
```

The above command logs to `/var/log/messages` with tag `monitor-ecs`.