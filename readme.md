#DGK Bank

###Introduction:
- DGK Bank is a small Corda Network that distribute a asset called DANIEL. 
- DANIEL is a non fungible asset that contain thoughts.


###Getting Started:
These instructions will get you a copy of the project up and running on your local machine for development and testing purposes.  


###Prerequisites: 

The following items should be installed in your system: 

1. JDK 1.8  
 
 Start this message (https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html)
 

2   KOTLIN 1.2
 
(https://kotlinlang.org/docs/tutorials/command-line.html)

###Installing: 
A step by step series of examples that tell you how to get a development deployment running. 
#####STEPS- 
Ensure that you have the build.gradle file with the deployNodes task in the project root. 

**1.To build the network's nodes** 

OPEN:     Open a terminal in the project root directory.
 

RUN COMMAND:      Run  
`gradlew deployNodes`




**2 To run the development network:** 

NAVIGATE:    Navigate to `./build/nodes.` 

RUN FILE:      `runnodes` 

RUN:   To make an issuance request to the bank, run:     
    `gradlew runTemplateClient` 


###Running the Tests: 
  **1 Unit Test:**  
  
        `./gradlew test` 

 **2 Integration Test:**
 `./gradlew runIntegrationTest`  
 

###Built With: 
**1 Corda-**  Framework 

**2 Intelij-**  IDE 

**3 Gradle-**  Dependency Management 

**4 Kotlin-**   Language 

###Author 
**Corda Team-** For Template Code 

**Daniel  Krajnik-** Rest of the work 
 
 
 
###Acknowledgment 
-Hat tip to anyone who's code was used 

-Inspiration
 
-YOU 














