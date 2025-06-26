package com.fbg.api.fix.integration

import com.fbg.api.fix.domain.BetFanaticsOrderState
import com.fbg.api.fix.domain.ExecutionReport

data class FbgExecutionReport(

    // the execution report which generated this problem
    val executionReport: ExecutionReport,

    // enriched with mapping back to fbg info
    val fbgOrderId: String,
    val betStatus: BetFanaticsOrderState,
    val rejectionReason: String? = null,

    // Note:
    //  metadata can be important; if we need to add fields, without re-deploying the model everywhere
    //  Otherwise, need heavy lifting with Conflent Schema registry etc.
    val metadata: Map<String, Any> = emptyMap()
) {
//    companion object {
//        fun fromFix(fixReport: ExecutionReport): FbgExecutionReport {
//            return FbgExecutionReport(
//                fixOrderId = fixReport.getOrderID().getValue(),
//                fixExecutionId = fixReport.getExecID().getValue(),
//                execType = fixReport.execType,
//                ordStatus = fixReport.ordStatus,
//                betStatus = FbgOrderStateMapper.fromFix(fixReport.execType, fixReport.ordStatus),
//
//            )
//        }
//    }
}