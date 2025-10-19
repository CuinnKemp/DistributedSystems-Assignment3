# DistributedSystems-Assignment3
For an outline of the design and choices made see [go here!](Design.md)
## Compilation
In order to build the project use: `mvn package` 
this will create a directory `/target` that contains `paxos.jar`

## Execution
In order to start a node open terminal in the project root and run:
```
 java -jar target/paxos.jar <memberId> [--profile <profile>] [--configPath <path2config>]"
```
Profile and Config paths are option by default the `STANDARD` profile is used.<br>
The profile options are: 
1. `RELIABLE`: Respond to messages almost instantly.
2. `LATENT`: Experience significant, variable network delays.
3. `FAILING`: May crash or become permanently unresponsive at any point. (also drops messages)
4. `STANDARD`: Experience moderate, variable network delays. <br>

The default config path is `cluster.conf` in the project directory. 
If you want to create your own config file the format is `{memberId} {uri} {port}` e.g. `M1 localhost 9000`

## Running Test Scripts
There are 5 simulated scenario scripts that test the different functionalities and capabilities of the PAXOS algorithm.
In order to run all scenarios open a terminal in the project root and execute the `run_tests.sh` bash script:
```commandline
bash ./run_tests.sh
```
[Here](TestDescription.md) is an outline of what each of the tests are and their expected output:

