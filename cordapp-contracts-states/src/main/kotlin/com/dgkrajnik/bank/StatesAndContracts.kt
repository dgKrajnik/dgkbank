package com.dgkrajnik.bank

import net.corda.core.contracts.*
import net.corda.core.crypto.NullKeys
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import java.time.Instant

// *****************
// * Contract Code *
// *****************
// This is used to identify our contract when building a transaction
val DANIEL_CONTRACT_ID = "com.dgkrajnik.bank.DanielContract"

class DanielContract : Contract {
    interface Commands : CommandData {
        class Issue : Commands
        class Move : Commands
        //class Exit: Commands TODO: Implement exits
    }

    override fun verify(tx: LedgerTransaction) {
        // Group by everything except owner: any modification to the Daniel at all is considered changing it fundamentally.
        // Grouping lets us handle multiple states (of the same type) in one transaction.
        val groups = tx.groupStates(DanielState::withoutOwner)

        val command = tx.commands.requireSingleCommand<DanielContract.Commands>()

        val timeWindow: TimeWindow? = tx.timeWindow

        for ((inputs, outputs, _) in groups) {
            when (command.value) {
                is DanielContract.Commands.Move -> {
                    val input = inputs.single()
                    requireThat {
                        "The transaction is signed by the owner of the Daniel." using (input.owner.owningKey in command.signers)
                        "The state is propagated." using (outputs.size == 1)
                        // Don't need to check anything else, as if outputs.size == 1 then the output is equal to
                        // the input ignoring the owner field due to the grouping.
                    }
                }

                is DanielContract.Commands.Issue -> {
                    val output = outputs.single()
                    val time = timeWindow?.untilTime ?: throw IllegalArgumentException("Issuances must be timestamped")
                    requireThat {
                        // Don't allow people to issue commercial paper under other entities identities.
                        "Output states are issued by a command signer." using (output.issuer.owningKey in command.signers)
                        "Output contains a thought." using (!output.thought.equals(""))
                        "Can't reissue an existing state." using inputs.isEmpty()
                    }
                }

                else -> throw IllegalArgumentException("Unrecognised command")
            }
        }
    }

    companion object {
        val logger: Logger = loggerFor<DanielContract>()

        /*
         * Genreates an issuance of a Daniel.
         */
        fun generateIssue(thought: String, issuer: AbstractParty, owner: AbstractParty,
                          notary: Party): TransactionBuilder {
            val state = DanielState(thought, issuer, owner)
            val stateAndContract = StateAndContract(state, DANIEL_CONTRACT_ID)
            return TransactionBuilder(notary = notary).withItems(stateAndContract, Command(Commands.Issue(), issuer.owningKey))
        }

        /*
         * Generates a Move command from an existing Daniel to a new owner.
         */
        fun generateMove(tx: TransactionBuilder, daniel: StateAndRef<DanielState>, newOwner: AbstractParty) {
            tx.addInputState(daniel)
            val outputState = daniel.state.data.withOwner(newOwner)
            tx.addOutputState(outputState, DANIEL_CONTRACT_ID)
            tx.addCommand(Command(Commands.Move(), daniel.state.data.owner.owningKey))
        }
    }
}

// *********
// * State *
// *********
data class DanielState(
        val thought: String,
        val issuer: AbstractParty,
        val owner: AbstractParty) : ContractState {
    override val participants: List<AbstractParty> get() = listOf(issuer, owner)

    fun withoutOwner() = copy(owner = AnonymousParty(NullKeys.NullPublicKey))

    fun withOwner(newOwner: AbstractParty): DanielState {
        return copy(owner=newOwner)
    }
}