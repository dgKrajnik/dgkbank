package com.dgkrajnik.bank

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.getDefaultNotary
import net.corda.webserver.services.WebServerPluginRegistry
import java.time.Duration
import java.time.Instant
import java.util.function.Function
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

val CORP_NAME = CordaX500Name(organisation = "BCS Learning", locality = "Sydney", country = "AU")
private val NOTARY_NAME = CordaX500Name(organisation = "Turicum Notary Service", locality = "Zurich", country = "CH")
private val BOD_NAME = CordaX500Name(organisation = "Bank of Daniel", locality = "Bloemfontein", country = "ZA")

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

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class DanielIssueRequest(val thought: String) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        //val notary = serviceHub.identityService.wellKnownPartyFromX500Name(NOTARY_NAME) ?: throw FlowException("Could not find Turicum Notary node.")
        //val notary = serviceHub.networkMapCache.getNotary(NOTARY_NAME) ?: throw FlowException("Could not find Turicum Notary node.")
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val bankOfD = serviceHub.identityService.wellKnownPartyFromX500Name(BOD_NAME) ?: throw FlowException("Could not find the Bank of Daniel node.")
        val selfID = serviceHub.identityService.wellKnownPartyFromX500Name(CORP_NAME) ?: throw FlowException("Could not find the BCS Corp node.")

        val issueTxBuilder = DanielContract.generateIssue(thought, bankOfD, selfID, notary)

        val bankSession = initiateFlow(bankOfD)

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
class DanielIssueResponse(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(counterpartySession, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be a Daniel transaction." using (output is DanielState)
                val daniel = output as DanielState
                "The Daniel must be issued by the Bank of Daniel" using (daniel.issuer.owningKey == serviceHub.identityService.wellKnownPartyFromX500Name(BOD_NAME)!!.owningKey)
                "I must be the Bank of Daniel" using (daniel.issuer.owningKey == ourIdentity.owningKey)
            }
        }

        subFlow(signTransactionFlow)
    }
}

// ***********
// * Plugins *
// ***********
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
