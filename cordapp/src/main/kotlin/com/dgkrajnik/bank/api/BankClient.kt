package com.dgkrajnik.bank.api

import com.dgkrajnik.bank.DanielIssueRequest
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.testing.http.HttpApi

/**
 * Interface for communicating with the Bank of Daniel node.
 */
object BankOfDanielClientApi {
    /**
     * HTTP API
     */
    // TODO: security controls required
    fun requestWebIssue(webAddress: NetworkHostAndPort, thought: String) {
        val api = HttpApi.fromHostAndPort(webAddress, "api/bank")
        api.postJson("issue-asset-request", thought)
    }

    /**
     * RPC API
     *
     * @return a pair of the issuing and payment transactions.
     */
    // TODO: privileged security controls required
    fun requestRPCIssue(rpcAddress: NetworkHostAndPort, thought: String): SignedTransaction {
        val client = CordaRPCClient(rpcAddress)
        client.start("user1", "test").use { connection ->
            val rpc = connection.proxy
            rpc.waitUntilNetworkReady().getOrThrow()

            return rpc.startFlow(::DanielIssueRequest, thought)
                    .returnValue.getOrThrow()
        }
    }
}