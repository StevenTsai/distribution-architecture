package com.godzilla.distribution.controller.admin.distribution;

import com.godzilla.distribution.common.Result;
import com.godzilla.distribution.common.annotation.OpsApi;
import com.godzilla.distribution.common.annotation.SessionAuth;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.ApproveDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.request.RejectDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionComplianceRecordDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionComplianceRecordDetailDTO;
import com.godzilla.distribution.service.distribution.DistributionComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@OpsApi
@RequestMapping("/api/manage/distribution/compliance-records")
@Tag(name = "分销合规台账接口")
@RequiredArgsConstructor
public class DistributionComplianceController {

    private final DistributionComplianceService distributionComplianceService;

    @GetMapping
    @SessionAuth
    @Operation(summary = "分页查询分销合规记录")
    public Result<PageResponseDTO<DistributionComplianceRecordDTO>> listRecords(
            @Parameter(description = "业务类型") @RequestParam(required = false) String bizType,
            @Parameter(description = "业务ID") @RequestParam(required = false) Long bizId,
            @Parameter(description = "记录类型") @RequestParam(required = false) String recordType,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(distributionComplianceService.listRecords(bizType, bizId, recordType, status, page, pageSize));
    }

    @GetMapping("/{id}")
    @SessionAuth
    @Operation(summary = "查询分销合规记录详情")
    public Result<DistributionComplianceRecordDetailDTO> getRecord(
            @Parameter(description = "合规记录ID", required = true) @PathVariable Long id) {
        return Result.success(distributionComplianceService.getRecord(id));
    }

    @PostMapping
    @SessionAuth
    @Operation(summary = "创建分销合规记录")
    public Result<Long> createRecord(@Valid @RequestBody CreateDistributionComplianceRecordRequestDTO request) {
        return Result.success(distributionComplianceService.createRecord(request));
    }

    @PostMapping("/{id}/approve")
    @SessionAuth
    @Operation(summary = "审核通过分销合规记录")
    public Result<String> approveRecord(
            @Parameter(description = "合规记录ID", required = true) @PathVariable Long id,
            @Valid @RequestBody(required = false) ApproveDistributionComplianceRecordRequestDTO request) {
        distributionComplianceService.approveRecord(id, request == null ? new ApproveDistributionComplianceRecordRequestDTO() : request);
        return Result.success("审核通过成功");
    }

    @PostMapping("/{id}/reject")
    @SessionAuth
    @Operation(summary = "驳回分销合规记录")
    public Result<String> rejectRecord(
            @Parameter(description = "合规记录ID", required = true) @PathVariable Long id,
            @Valid @RequestBody(required = false) RejectDistributionComplianceRecordRequestDTO request) {
        distributionComplianceService.rejectRecord(id, request == null ? new RejectDistributionComplianceRecordRequestDTO() : request);
        return Result.success("驳回成功");
    }
}
