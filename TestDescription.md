# Desciption of Testing Scenarios
## Scenario 1
To run the individual test: `bash ./run_paxos_senario2`<br><br>
**Setup:** All 9 members are launched with the reliable profile (i.e., no artificial delay or failures). <br>
<br>
**Test:** Trigger a single proposal from one member (M1 with proposal value "M1")<br>
<br>
**Expected Outcome:** Consensus is reached quickly and correctly. All members should output that M1 was elected.

## Scenario 2
To run the individual test: `bash ./run_paxos_senario2` <br><br>
**Setup:** All 9 members are launched with the reliable profile.<br>
<br>
**Test:** Two proposals are triggered at the same time from M1 with value "M1" and M2 with "M2"<br>
<br>
**Expected Outcome:** The Paxos algorithm correctly resolves the conflict, and all members reach a consensus on a single winner (either "M1" or "M2").

## Scenario 3a
To run the individual test: `bash ./run_paxos_senario3a` <br><br>
**Setup:** Launch the members with a mix of profiles: M1 (reliable), M2 (latent), M3 (failure), M4-M9 (standard).<br>
<br>
**Test:** A standard member M4 initiates a proposal "M4".<br>
<br>
**Expected Outcome:** consensus of "M4" with M3 potentially crashing (and not coming back online i.e. not learning a value)

## Scenario 3b
To run the individual test: `bash ./run_paxos_senario3b` <br><br>
**Setup:** Launch the members with a mix of profiles: M1 (reliable), M2 (latent), M3 (failure), M4-M9 (standard).<br>
<br>
**Test:** A LATENT member M2 initiates a proposal "M2".<br>
<br>
**Expected Outcome:** consensus of "M2" with M3 potentially crashing (and not coming back online i.e. not learning a value)

## Scenario 3c
To run the individual test: `bash ./run_paxos_senario3b` <br><br>
**Setup:** Launch the members with a mix of profiles: M1 (reliable), M2 (latent), M3 (failure), M4-M9 (standard).<br>
<br>
**Test:** A FAILING member (M3) initiates a proposal and immediately crashes.<br>
<br>
**Expected Outcome:** the remaining operational members must successfully reach consensus on a single winner.
