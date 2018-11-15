define func = 
	rm -rf libs
	rm -rf out1
	rm -rf out2
	mkdir libs
	curl https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar -o libs/slf4j-api-1.7.25.jar	
	curl https://repo1.maven.org/maven2/org/apache/zookeeper/zookeeper/3.4.12/zookeeper-3.4.12.jar -o libs/zookeeper-3.4.12.jar
	curl https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/1.7.25/slf4j-simple-1.7.25.jar -o libs/slf4j-simple-1.7.25.jar
	mkdir out1
	javac -d out1 -cp ".:libs/*" Player/src/*.java
	mkdir out2
	javac -d out2 -cp ".:libs/*" PlayerWatcher/src/*.java
	sudo cp -pf ./player.sh /usr/bin/player
	sudo cp -pf ./watcher.sh /usr/bin/watcher
endef

make: ;$(value func)

.ONE_SHELL:
