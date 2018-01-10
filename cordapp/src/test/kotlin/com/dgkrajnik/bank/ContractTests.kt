package com.dgkrajnik.bank

import net.corda.testing.*
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import org.junit.Test

class ContractTests {

    @Before
    fun setup() {
        setCordappPackages("com.dgkrajnik.bank")
    }

    @After
    fun tearDown() {
        unsetCordappPackages()
    }

    @Test
    fun `untimed daniel issuance`() {
        ledger {
            // This should fail because issuances must be timestamped
            transaction("UntimedIssuance") {
                output(DANIEL_CONTRACT_ID, "untimeddanny") { getDaniel() }
                command(MEGA_CORP_PUBKEY) { DanielContract.Commands.Issue() }
                attachments(DANIEL_CONTRACT_ID)
                fails()
            }
        }
    }

    @Test
    fun `unthinking daniel issuance`() {
        ledger {
            // This should fail because invalid daniels don't have a thought.
            transaction("FailIssuance") {
                output(DANIEL_CONTRACT_ID, "faildanny") { getInvalidDaniel() }
                command(MEGA_CORP_PUBKEY) { DanielContract.Commands.Issue() }
                attachments(DANIEL_CONTRACT_ID)
                timeWindow(TEST_TX_TIME)
                fails()
            }
        }
    }

    @Test
    fun `chain daniel tweaked double assign`() {
        val issuer = MEGA_CORP.ref(123)

        ledger {
            unverifiedTransaction {
                attachments(DANIEL_CONTRACT_ID)
                val daniel = getDaniel()
                output(DANIEL_CONTRACT_ID, "daniel", daniel)
            }

            transaction("Issuance") {
                output(DANIEL_CONTRACT_ID, "testdanny") { getDaniel() }
                command(MEGA_CORP_PUBKEY) { DanielContract.Commands.Issue() }
                attachments(DANIEL_CONTRACT_ID)
                timeWindow(TEST_TX_TIME)
                verifies()
            }

            transaction("Move") {
                input("testdanny")
                output(DANIEL_CONTRACT_ID, "alice's daniel") { "testdanny".output<DanielState>().withOwner(ALICE) }
                command(MEGA_CORP_PUBKEY) { DanielContract.Commands.Move() }
                verifies()
            }

            tweak {
                // This transaction should verify locally but fail globally because we double-use the "paper".
                transaction {
                    input("testdanny")
                    // We moved a paper to another pubkey.
                    output(DANIEL_CONTRACT_ID, "bob's daniel") { "testdanny".output<DanielState>().withOwner(BOB) }
                    command(MEGA_CORP_PUBKEY) { DanielContract.Commands.Move() }
                    verifies()
                }
                fails()
            }

            //...But since that was done in a tweak, the original ledger is valid.
            verifies()
        }
    }

    private fun getDaniel(): DanielState = DanielState(
            issuer = MEGA_CORP,
            owner = MEGA_CORP,
            thought = "TESTING IS A PARALLEL UNIVERSE"
    )

    private fun getInvalidDaniel(): DanielState = DanielState(
            issuer = MEGA_CORP,
            owner = MEGA_CORP,
            thought = ""
    )
}