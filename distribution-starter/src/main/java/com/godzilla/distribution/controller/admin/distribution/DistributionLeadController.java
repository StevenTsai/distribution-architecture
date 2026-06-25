package com.godzilla.distribution.controller.admin.distribution;

import com.godzilla.distribution.common.Result;
import com.godzilla.distribution.common.annotation.OpsApi;
import com.godzilla.distribution.common.annotation.SessionAuth;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.AssignDistributionLeadRequestDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributionLeadFollowUpRequestDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributionLeadRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributionLeadRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributionLeadStageRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadDetailDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadFollowUpDTO;
import com.godzilla.distribution.service.distribution.DistributionLeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@OpsApi
@RequestMapping("/api/manage/distribution/leads")
@Tag(name = "分销线索管理接口")
@RequiredArgsConstructor
public class DistributionLeadController {

    private final DistributionLeadService distributionLeadService;

    @GetMapping
    @SessionAuth
    @Operation(summary = "分页查询线索列表")
    public Result<PageResponseDTO<DistributionLeadDTO>> listLeads(
            @Parameter(description = "线索编号") @RequestParam(required = false) String leadNo,
            @Parameter(description = "患者姓名或手机号") @RequestParam(required = false) String patientKeyword,
            @Parameter(description = "来源渠道ID") @RequestParam(required = false) Long sourceDistributorId,
            @Parameter(description = "意向产品线") @RequestParam(required = false) String intentProductLine,
            @Parameter(description = "负责人ID") @RequestParam(required = false) Long ownerUserId,
            @Parameter(description = "线索阶段") @RequestParam(required = false) String stage,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(distributionLeadService.listLeads(
                leadNo, patientKeyword, sourceDistributorId, intentProductLine, ownerUserId, stage, page, pageSize));
    }

    @GetMapping("/{id}")
    @SessionAuth
    @Operation(summary = "查询线索详情")
    public Result<DistributionLeadDetailDTO> getLead(
            @Parameter(description = "线索ID", required = true) @PathVariable Long id) {
        return Result.success(distributionLeadService.getLead(id));
    }

    @PostMapping
    @SessionAuth
    @Operation(summary = "创建线索")
    public Result<Long> createLead(@Valid @RequestBody CreateDistributionLeadRequestDTO request) {
        return Result.success(distributionLeadService.createLead(request));
    }

    @PutMapping("/{id}")
    @SessionAuth
    @Operation(summary = "更新线索")
    public Result<String> updateLead(
            @Parameter(description = "线索ID", required = true) @PathVariable Long id,
            @Valid @RequestBody UpdateDistributionLeadRequestDTO request) {
        distributionLeadService.updateLead(id, request);
        return Result.success("更新成功");
    }

    @PostMapping("/{id}/assign")
    @SessionAuth
    @Operation(summary = "分配线索负责人")
    public Result<String> assignLead(
            @Parameter(description = "线索ID", required = true) @PathVariable Long id,
            @Valid @RequestBody AssignDistributionLeadRequestDTO request) {
        distributionLeadService.assignLead(id, request);
        return Result.success("分配成功");
    }

    @PostMapping("/{id}/stage")
    @SessionAuth
    @Operation(summary = "推进线索阶段")
    public Result<String> updateLeadStage(
            @Parameter(description = "线索ID", required = true) @PathVariable Long id,
            @Valid @RequestBody UpdateDistributionLeadStageRequestDTO request) {
        distributionLeadService.updateLeadStage(id, request);
        return Result.success("阶段更新成功");
    }

    @GetMapping("/{id}/follow-ups")
    @SessionAuth
    @Operation(summary = "查询线索跟进记录")
    public Result<List<DistributionLeadFollowUpDTO>> listFollowUps(
            @Parameter(description = "线索ID", required = true) @PathVariable Long id) {
        return Result.success(distributionLeadService.listFollowUps(id));
    }

    @PostMapping("/{id}/follow-ups")
    @SessionAuth
    @Operation(summary = "新增线索跟进记录")
    public Result<Long> createFollowUp(
            @Parameter(description = "线索ID", required = true) @PathVariable Long id,
            @Valid @RequestBody CreateDistributionLeadFollowUpRequestDTO request) {
        return Result.success(distributionLeadService.createFollowUp(id, request));
    }
}
