package com.dgkrajnik.bank

import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.nodeapi.internal.ServiceType
import net.corda.testing.*
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class ResolveTransactionsFlowTest {
    lateinit var mockNet: MockNetwork
    lateinit var corp: StartedNode<MockNetwork.MockNode>
    lateinit var bod: StartedNode<MockNetwork.MockNode>
    lateinit var notary: StartedNode<MockNetwork.MockNode>

    @Before
    fun setup() {
        setCordappPackages("com.dgkrajnik.bank")
        mockNet = MockNetwork()
        notary = mockNet.createNotaryNode(legalName=NOTARY_NAME)
        bod = mockNet.createPartyNode(legalName=BOD_NAME, networkMapAddress=notary.network.myAddress)
        corp = mockNet.createPartyNode(legalName=CORP_NAME, networkMapAddress=notary.network.myAddress)
        mockNet.registerIdentities()
        bod.internals.registerInitiatedFlow(DanielIssueResponse::class.java)
        bod.internals.registerInitiatedFlow(DanielMoveResponse::class.java)
        corp.internals.registerInitiatedFlow(DanielMoveResponse::class.java)
    }

    @After
    fun tearDown() {
        mockNet.stopNodes()
        unsetCordappPackages()
    }

    @Test
    fun `simple issuance flow`() {
        val p = DanielIssueRequest("TEST THOUGHT")
        val future = corp.services.startFlow(p).resultFuture
        mockNet.runNetwork()
        val results = future.getOrThrow()
        val ds = results.tx.outputStates[0] as DanielState
        assertEquals("TEST THOUGHT", ds.thought)
    }

    @Test
    fun `simple issue-and-move flow`() {
        val p = DanielIssueRequest("IF WORK IS ENERGY EXPENDED OVER TIME WHY DON'T WE GET PAID IN CALORIES")
        val future = corp.services.startFlow(p).resultFuture
        mockNet.runNetwork()
        val results = future.getOrThrow()
        val ds = results.tx.outputStates[0] as DanielState
        assertEquals("IF WORK IS ENERGY EXPENDED OVER TIME WHY DON'T WE GET PAID IN CALORIES", ds.thought)

        val move = DanielMoveRequest(bod.info.chooseIdentity(), results.tx.outRef(0))
        val moveFuture = corp.services.startFlow(move).resultFuture
        mockNet.runNetwork()
        val moveResults = moveFuture.getOrThrow()
        val mds = moveResults.tx.outputStates[0] as DanielState
        assertEquals(bod.info.chooseIdentity(), mds.owner)
    }
}
