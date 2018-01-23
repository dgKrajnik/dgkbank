package com.dgkrajnik.bank.server

import com.dgkrajnik.bank.DanielIssueRequest
import com.dgkrajnik.bank.DanielMoveRequest
import com.dgkrajnik.bank.DanielState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.CordaX500Name.Companion.parse
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.base64ToByteArray
import net.corda.core.utilities.base64toBase58
import net.corda.core.utilities.toBase64
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.servlet.http.HttpServletRequest


private const val CONTROLLER_NAME = "config.controller.name"

/**
 *  A controller for interacting with the node via RPC.
 */
@RestController
@RequestMapping("/dgkbank") // The paths for GET and POST requests are relative to this base path.
private class RestController(
        private val rpc: NodeRPCConnection,
        @Value("\${config.rpc.port}") val rpcPort: Int,
        @Value("\${$CONTROLLER_NAME}") private val controllerName: String) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val myName = rpc.proxy.nodeInfo().legalIdentities.first().name

    @GetMapping("/date", produces=[MediaType.APPLICATION_JSON])
    fun getCurrentDate(): Any {
        return mapOf("date" to LocalDateTime.now().toLocalDate())
    }

    @GetMapping("/port", produces=[MediaType.TEXT_PLAIN])
    fun getPort(): String {
        return rpcPort.toString()
    }

    /**
     *  Request asset issuance
     */
    @PostMapping("/issue-asset-request", consumes=[MediaType.APPLICATION_JSON])
    fun issueAssetRequest(@RequestBody params: IssueParams): Response {
        //val issuerX500 = CordaX500Name.parse(params.issuer) // We should definitely write a jackson thing to process json into a name, but for now this'll work.
        val issuerX500 = params.issuer
        return try {
            val proxy = rpc.proxy
            val issuerID = proxy.wellKnownPartyFromX500Name(issuerX500) ?: throw IllegalArgumentException("Could not find the issuer node '$issuerX500'.")
            proxy.startFlow(::DanielIssueRequest, params.thought, issuerID).returnValue.getOrThrow()
            logger.info("Issue request completed successfully: $params.thought")
            Response.status(Response.Status.CREATED).build()
        } catch (e: Exception) {
            logger.error("Issue request failed", e)
            Response.status(Response.Status.FORBIDDEN).build()
        }
    }

    /**
     *  Request asset move
     */
    @PostMapping("/issue-move-request", produces=[], consumes=[MediaType.APPLICATION_JSON])
    fun issueMoveRequest(@RequestBody params: MoveParams): Response {
        //val newOwnerX500 = CordaX500Name.parse(params.newOwner)
        val newOwnerX500 = params.newOwner
        val daniel = StateRef(SecureHash.SHA256(params.danielHash.base64ToByteArray()), params.danielIndex)
        return try {
            val proxy = rpc.proxy
            val sar = proxy.vaultQueryBy<DanielState>(QueryCriteria.VaultQueryCriteria(stateRefs = listOf(daniel))).states.getOrNull(0) ?: throw IllegalArgumentException("Could not find state")
            val issuerID = proxy.wellKnownPartyFromX500Name(newOwnerX500) ?: throw IllegalArgumentException("Could not find the new owner node '$newOwnerX500'.")
            proxy.startFlow(::DanielMoveRequest, sar, issuerID).returnValue.getOrThrow()
            logger.info("Movement request completed successfully")
            Response.status(Response.Status.CREATED).build()
        } catch (e: Exception) {
            logger.error("Movement request failed", e)
            Response.status(Response.Status.FORBIDDEN).build()
        }
    }

    /** Returns the node's name. */
    @GetMapping("/myname", produces = ["text/plain"])
    private fun myName() = myName.toString()

    /** Returns a list of the node's network peers. */
    @GetMapping("/peersnames", produces = ["application/json"])
    private fun peersNames(): Map<String, List<String>> {
        val nodes = rpc.proxy.networkMapSnapshot()
        val nodeNames = nodes.map { it.legalIdentities.first().name }
        val filteredNodeNames = nodeNames.filter { it.organisation !in listOf(controllerName, myName) }
        val filteredNodeNamesToStr = filteredNodeNames.map { it.toString() }
        return mapOf("peers" to filteredNodeNamesToStr)
    }

    /** Returns a list of existing DanielState's. */
    @GetMapping("/getdaniels", produces = ["application/json"])
    private fun getDaniels(): List<Map<String, Any>> {
        val danielStateAndRefs = rpc.proxy.vaultQueryBy<DanielState>().states
        val danielStates = danielStateAndRefs.map { it }
        return danielStates.map { mapOf(
                "issuer" to it.state.data.issuer.toString(),
                "owner" to it.state.data.owner.toString(),
                "thought" to it.state.data.thought,
                "hash" to it.ref.txhash.bytes.toBase64(),
                "index" to it.ref.index) }
    }
}

private data class IssueParams(val thought: String, val issuer: CordaX500Name)
private data class MoveParams(val danielHash: String, val danielIndex: Int, val newOwner: CordaX500Name)
