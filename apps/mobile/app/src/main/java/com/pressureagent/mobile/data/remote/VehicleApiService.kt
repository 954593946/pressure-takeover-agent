package com.pressureagent.mobile.data.remote

import com.pressureagent.mobile.domain.model.TripSummary
import com.pressureagent.mobile.domain.model.VehicleInfo
import com.pressureagent.mobile.domain.model.VehicleStateResponse
import com.pressureagent.mobile.domain.model.VehicleStatus
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Vehicle API — 车辆状态与行程历史。
 *
 * 独立于 Agent API（/v1/state）的单车数据接口，方便车云团队独立对接。
 * Base URL 可能与 Agent API 不同，通过独立 Retrofit instance 或统一 gateway 访问。
 *
 * ## API 规格
 *
 * ### GET /v1/vehicle/status
 * 获取当前车辆实时状态。返回 [VehicleStateResponse]：
 * - vehicleInfo：车辆基础信息（型号、车牌、连接状态）
 * - vehicleStatus：实时状态（电池、续航、门锁、空调、位置、安防）
 *
 * ### GET /v1/vehicle/trips?limit={n}
 * 获取最近 n 条行程历史。每条 [TripSummary] 包含：
 * - date、origin、destination、duration、distance、hadTakeover
 *
 * ### 认证
 * 所有接口需携带 Bearer token（与 Agent API 共用）。
 *
 * ### 后端对接
 * 后端团队需实现以上两个端点。Demo 阶段使用 mock interceptor 返回固定数据。
 */
interface VehicleApiService {

    @GET("v1/vehicle/status")
    suspend fun getVehicleState(): VehicleStateResponse

    @GET("v1/vehicle/trips")
    suspend fun getRecentTrips(
        @retrofit2.http.Query("limit") limit: Int = 5,
    ): List<TripSummary>
}
