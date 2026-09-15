package com.componentvault.android.data

import com.componentvault.android.model.ComponentOfficialMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BatchJlcModelsTest {
    @Test fun deduplicatesExactRawButKeepsDifferentBagsForSameSku(){
        val first="pc:C70565,qty:10,pdi:A";val second="pc:C70565,qty:20,pdi:B"
        val (draft,duplicates)=BatchJlcDraftCodec.add(BatchJlcDraft(),listOf(first,"  $first  ",second))
        assertEquals(2,draft.rows.size);assertEquals(1,duplicates)
    }
    @Test fun draftJsonRoundTripPreservesStableIdsRawAndSelection(){
        val draft=BatchJlcDraft(rows=listOf(BatchJlcRow(raw="pc:C1,qty:2",selected=false,description="商品图片：https://assets.lcsc.com/a.png")))
        assertEquals(draft,BatchJlcDraftCodec.decode(BatchJlcDraftCodec.encode(draft)))
    }
    @Test fun batchDescriptionContainsOnlyPublicMetadataNotPackagingPayload(){
        val description=ComponentOfficialMetadata(
            source="lcsc_domestic_web",model="M1",brand="Maker",
            imageUrl="https://assets.lcsc.com/images/a.png",
            parameters=mapOf("阻值" to "10k"),
        ).toBatchDescription()
        assertTrue("型号：M1" in description)
        assertTrue("商品图片：https://assets.lcsc.com/images/a.png" in description)
        assertTrue("参数：阻值：10k" in description)
        assertTrue("pc:" !in description)
        assertTrue("订单" !in description)
    }
    @Test fun invalidQuantityIsNotReadyAndAggregateOverflowFails(){
        val invalid=BatchJlcRow(raw="x",status=BatchJlcStatus.Pending,sku="C1",quantityText="0",location="A")
        assertTrue(BatchJlcCommitPlanner.plan(listOf(invalid),emptySet()).isEmpty())
        val rows=listOf(
            BatchJlcRow(raw="a",status=BatchJlcStatus.Ready,sku="C1",quantityText=Int.MAX_VALUE.toString(),location="A"),
            BatchJlcRow(raw="b",status=BatchJlcStatus.Ready,sku="C1",quantityText="1",location="A"),
        )
        assertFailsWith<ArithmeticException>{BatchJlcCommitPlanner.plan(rows,emptySet())}
    }
    @Test fun receiptsExcludeRowsFromReplayPlan(){val row=BatchJlcRow(id="row-1",raw="x",status=BatchJlcStatus.Ready,sku="C1",quantityText="2",location="A");assertTrue(BatchJlcCommitPlanner.plan(listOf(row),setOf("row-1")).isEmpty())}
}
