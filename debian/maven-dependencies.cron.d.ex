#
# Regular cron jobs for the maven-dependencies package
#
0 4	* * *	root	[ -x /usr/bin/maven-dependencies_maintenance ] && /usr/bin/maven-dependencies_maintenance
