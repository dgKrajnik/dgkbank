package com.dgkrajnik.bank.api

import com.dgkrajnik.bank.DanielIssueRequest
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.CashIssueAndPaymentFlow
import java.time.LocalDateTime
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger

// API is accessible from /api/bank. All paths specified below are relative to it.
@Path("bank")
class BankWebApi(private val rpc: CordaRPCOps) {
    companion object {
        val logger: Logger = loggerFor<BankWebApi>()
    }

    @GET
    @Path("date")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCurrentDate(): Any {
        return mapOf("date" to LocalDateTime.now().toLocalDate())
    }

    /**
     *  Request asset issuance
     */
    @POST
    @Path("issue-asset-request")
    @Consumes(MediaType.APPLICATION_JSON)
    fun issueAssetRequest(thought: String): Response {
        return try {
            rpc.startFlow(::DanielIssueRequest, thought).returnValue.getOrThrow()
            logger.info("Issue and payment request completed successfully: $thought")
            Response.status(Response.Status.CREATED).build()
        } catch (e: Exception) {
            logger.error("Issue and payment request failed", e)
            Response.status(Response.Status.FORBIDDEN).build()
        }
    }
}

