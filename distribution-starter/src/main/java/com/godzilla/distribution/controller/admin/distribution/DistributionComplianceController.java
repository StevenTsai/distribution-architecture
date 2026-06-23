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
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.service.distribution.DistributionComplianceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@RestController
@OpsApi
@RequestMapping("/api/manage/distribution/compliance-records")
@Tag(name = "分销合规台账接口")
public class DistributionComplianceController {

    @Autowired
    private DistributionComplianceService distributionComplianceService;

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
        try {
            return Result.success(distributionComplianceService.listRecords(bizType, bizId, recordType, status, page, pageSize));
        } catch (BizException e) {
            return new Result<PageResponseDTO<DistributionComplianceRecordDTO>>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("查询分销合规记录失败", e);
            return Result.fail();
        }
    }

    @GetMapping("/{id}")
    @SessionAuth
    @Operation(summary = "查询分销合规记录详情")
    public Result<DistributionComplianceRecordDetailDTO> getRecord(
            @Parameter(description = "合规记录ID", required = true) @PathVariable Long id) {
        try {
            return Result.success(distributionComplianceService.getRecord(id));
        } catch (BizException e) {
            return new Result<DistributionComplianceRecordDetailDTO>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("查询分销合规记录详情失败, id={}", id, e);
            return Result.fail();
        }
    }

    @PostMapping
    @SessionAuth
    @Operation(summary = "创建分销合规记录")
    public Result<Long> createRecord(@Valid @RequestBody CreateDistributionComplianceRecordRequestDTO request) {
        try {
            return Result.success(distributionComplianceService.createRecord(request));
        } catch (BizException e) {
            return new Result<Long>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("创建分销合规记录失败", e);
            return Result.fail();
        }
    }

    @PostMapping("/{id}/approve")
    @SessionAuth
    @Operation(summary = "审核通过分销合规记录")
    public Result<String> approveRecord(
            @Parameter(description = "合规记录ID", required = true) @PathVariable Long id,
            @Valid @RequestBody(required = false) ApproveDistributionComplianceRecordRequestDTO request) {
        try {
            distributionComplianceService.approveRecord(id, request == null ? new ApproveDistributionComplianceRecordRequestDTO() : request);
            return Result.success("审核通过成功");
        } catch (BizException e) {
            return new Result<String>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("审核通过分销合规记录失败, id={}", id, e);
            return Result.fail();
        }
    }

    @PostMapping("/{id}/reject")
    @SessionAuth
    @Operation(summary = "驳回分销合规记录")
    public Result<String> rejectRecord(
            @Parameter(description = "合规记录ID", required = true) @PathVariable Long id,
            @Valid @RequestBody(required = false) RejectDistributionComplianceRecordRequestDTO request) {
        try {
            distributionComplianceService.rejectRecord(id, request == null ? new RejectDistributionComplianceRecordRequestDTO() : request);
            return Result.success("驳回成功");
        } catch (BizException e) {
            return new Result<String>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("驳回分销合规记录失败, id={}", id, e);
            return Result.fail();
        }
    }
}
