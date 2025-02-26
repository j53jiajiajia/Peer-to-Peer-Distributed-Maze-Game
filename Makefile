# Compile all Java files under src directory
compile:
	javac -d bin -cp src src/src/*.java
	javac StressTest.java
	javac PlayersFightTreasureTest.java
# Run the StressTest
run-stress-test:
	java StressTest 127.0.0.1 6789 "java -cp bin src.Game"

# Run the Tracker
run-tracker:
	java -cp bin src.Tracker 6789 15 10

run-tracker-and-stress-test:
	make run-tracker & \
	sleep 1 && \
	java StressTest 127.0.0.1 6789 "java -cp bin src.Game"

# Run the Game
run-game:
	java -cp bin src.Game "127.0.0.1" 6789 $(playerId)

# This is for testing the extreme case that the maze is too small for all the players to move
# E.g., tt will increase the chance of more than one player fight for the same treasure
run-tiny-maze-and-stress-test:
	java -cp bin src.Tracker 6789 2 1 & \
	sleep 1 && \
	java PlayersFightTreasureTest 127.0.0.1 6789 "java -cp bin src.Game"

# Kill Tracker in case it is still running
kill-tracker:
	pkill -f "java.*Tracker"
# Clean compiled files
clean:
	rm -rf bin

# Create bin directory
init:
	mkdir -p bin

# Gradle sync
gradle-sync:
	./gradlew --refresh-dependencies

# Add gradle-sync to the 'all' target
all: init compile gradle-sync compile-stress-test

# Run unit test
unittest:
	./gradlew test

unittest-collect-treasure:
	./gradlew test --tests TestTrackerGame.testPlayerFightTreasure
	./gradlew test --tests TestTrackerGame.testTwoPlayerCollectTreasure

clean-logs:
	rm -rf CS5223_StressTest12123/*

.PHONY: compile run-tracker run-game clean init all
