# DGKBank

`gradlew deployNodes` to get the nodes up.  
`build/nodes/runnodes` to run them.  
`gradlew runBankClient -Pthought=<thought>` while the nodes are up to make an RPC request for some Daniels with a given thought.

In the CLI of the corporation or the bank, you may use  
`run vaultQuery contractStateType: com.dgkrajnik.bank.DanielState`  
to check the vault for your new states

`gradlew test` To run the offline flow tests  
`gradlew integrationTest` to run the (broken) integration tests.

