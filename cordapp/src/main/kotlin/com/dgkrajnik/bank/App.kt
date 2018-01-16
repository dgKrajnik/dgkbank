package com.dgkrajnik.bank

import co.paralleluniverse.fibers.Suspendable
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.testing.chooseIdentity
import net.corda.testing.getDefaultNotary
import net.corda.webserver.services.WebServerPluginRegistry
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.function.Function
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

val CORP_NAME = CordaX500Name(organisation = "BCS Learning", locality = "Sydney", country = "AU")
internal val NOTARY_NAME = CordaX500Name(organisation = "Turicum Notary Service", locality = "Zurich", country = "CH", commonName="corda.notary.validating")
internal val BOD_NAME = CordaX500Name(organisation = "Bank of Daniel", locality = "Bloemfontein", country = "ZA")
private var whitelistedIssuers: Set<CordaX500Name> = emptySet()

// *****************
// * API Endpoints *
// *****************
@Path("template")
class TemplateApi(val rpcOps: CordaRPCOps) {
    // Accessible at /api/template/templateGetEndpoint.
    @GET
    @Path("templateGetEndpoint")
    @Produces(MediaType.APPLICATION_JSON)
    fun templateGetEndpoint(): Response {
        return Response.ok("Template GET endpoint.").build()
    }
}

private fun getIssuerWhitelist(serviceHub: ServiceHub): Set<PublicKey> {
    if (whitelistedIssuers.isEmpty()) {
        val tempSet: MutableSet<CordaX500Name> = mutableSetOf()
        val conf = ConfigFactory.parseResources("application.conf")
        conf.getObjectList("whitelists.daniel_issuers").flatMapTo(tempSet, { x ->
            val cn = x.get("common_name")
            val parsedName: CordaX500Name = when (cn) {
                null -> CordaX500Name(
                        x.get("organization")?.unwrapped() as String? ?: "",
                        x.get("locality")?.unwrapped() as String? ?: "",
                        x.get("country")?.unwrapped() as String? ?: ""
                )
                else -> CordaX500Name(
                        cn.unwrapped() as String? ?: "",
                        x.get("organization")?.unwrapped() as String? ?: "",
                        x.get("locality")?.unwrapped() as String? ?: "",
                        x.get("country")?.unwrapped() as String? ?: ""
                )
            }
            listOf(parsedName)
        })
        whitelistedIssuers = tempSet
    }
    val outs: MutableSet<PublicKey> = mutableSetOf()
    whitelistedIssuers.flatMapTo(outs, { x ->
        val key = serviceHub.identityService.wellKnownPartyFromX500Name(x)?.owningKey
        if (key != null) {
            listOf(key)
        } else {
            emptyList()
        }
    })
    return outs
}

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class DanielIssueRequest(val thought: String, val issuer: Party) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.getNotary(NOTARY_NAME) ?: throw FlowException("Could not find the trusted Turicum Notary node.")
        val selfID = serviceHub.myInfo.legalIdentities[0]

        val issueTxBuilder = DanielContract.generateIssue(thought, issuer, selfID, notary)

        val bankSession = initiateFlow(issuer)

        issueTxBuilder.setTimeWindow(TimeWindow.fromStartAndDuration(Instant.now(serviceHub.clock), Duration.ofMillis(10000)))

        // Verifying the transaction.
        issueTxBuilder.verify(serviceHub)

        // Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(issueTxBuilder)

        // Obtaining the counterparty's signature.
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(bankSession), CollectSignaturesFlow.tracker()))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx))
    }
}

@InitiatedBy(DanielIssueRequest::class)
class DanielIssueResponse(val counterpartySession: FlowSession) : FlowLogic<Unit>() { @Suspendable override fun call() { val signTransactionFlow = object : SignTransactionFlow(counterpartySession, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val whitelistedIssuers = getIssuerWhitelist(serviceHub)
                val output = stx.tx.outputs.single().data
                "This must be a Daniel transaction." using (output is DanielState)
                val daniel = output as DanielState
                "I must be a whitelisted node" using (whitelistedIssuers.contains(ourIdentity.owningKey))
                "The Daniel must be issued by a whitelisted node" using (whitelistedIssuers.contains(daniel.issuer.owningKey))
                "The issuer of a Daniel must be the issuing node" using (daniel.issuer.owningKey == ourIdentity.owningKey)
            }
        }

        subFlow(signTransactionFlow)
    }
}

@InitiatingFlow
@StartableByRPC
class DanielMoveRequest(val daniel: StateAndRef<DanielState>, val newOwner: Party) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()
    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.getNotary(NOTARY_NAME) ?: throw FlowException("Could not find Turicum Notary node.")

        val txBuilder = TransactionBuilder(notary=notary)
        DanielContract.generateMove(txBuilder, daniel, newOwner)

        val moveSession = initiateFlow(newOwner)

        txBuilder.setTimeWindow(TimeWindow.fromStartAndDuration(Instant.now(serviceHub.clock), Duration.ofMillis(10000)))

        // Verifying the transaction.
        txBuilder.verify(serviceHub)

        // Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // Obtaining the counterparty's signature.
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(moveSession), CollectSignaturesFlow.tracker()))

        // Finalising the transaction.
        return subFlow(FinalityFlow(fullySignedTx))
    }
}

@InitiatedBy(DanielMoveRequest::class)
class DanielMoveResponse(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(counterpartySession, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val whitelistedIssuers = getIssuerWhitelist(serviceHub)
                val output = stx.tx.outputs.single().data
                "This must be a Daniel transaction." using (output is DanielState)
                val daniel = output as DanielState
                "The Daniel must be issued by a whitelisted node" using (whitelistedIssuers.contains(daniel.issuer.owningKey))
                "The issuer of a Daniel must be the issuing node" using (daniel.issuer.owningKey == ourIdentity.owningKey)
            }
        }

        subFlow(signTransactionFlow)
    }
}

// ***********
// * Plugins *
// ***********
/*
class TemplateWebPlugin : WebServerPluginRegistry {
    // A list of classes that expose web JAX-RS REST APIs.
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::TemplateApi))
    //A list of directories in the resources directory that will be served by Jetty under /web.
    // This template's web frontend is accessible at /web/template.
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the templateWeb directory in resources to /web/template
            "template" to javaClass.classLoader.getResource("templateWeb").toExternalForm()
    )
}
*/

// Serialization whitelist.
class TemplateSerializationWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf()
}
