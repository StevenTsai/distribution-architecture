package com.godzilla.distribution.controller.admin.distribution;

import com.godzilla.distribution.common.Result;
import com.godzilla.distribution.common.annotation.OpsApi;
import com.godzilla.distribution.common.annotation.SessionAuth;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributorRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorStatusRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorDetailDTO;
import com.godzilla.distribution.service.distribution.DistributionDistributorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@OpsApi
@RequestMapping("/api/manage/distribution/distributors")
@Tag(name = "分销渠道管理接口")
@RequiredArgsConstructor
public class DistributorController {

    private final DistributionDistributorService distributorService;

    @GetMapping
    @SessionAuth
    @Operation(summary = "分页查询渠道列表")
    public Result<PageResponseDTO<DistributionDistributorDTO>> listDistributors(
            @Parameter(description = "渠道名称") @RequestParam(required = false) String name,
            @Parameter(description = "渠道状态") @RequestParam(required = false) String status,
            @Parameter(description = "渠道等级") @RequestParam(required = false) String levelCode,
            @Parameter(description = "产品线") @RequestParam(required = false) String productLine,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(distributorService.listDistributors(name, status, levelCode, productLine, page, pageSize));
    }

    @GetMapping("/{id}")
    @SessionAuth
    @Operation(summary = "查询渠道详情")
    public Result<DistributionDistributorDetailDTO> getDistributor(
            @Parameter(description = "渠道ID", required = true) @PathVariable Long id) {
        return Result.success(distributorService.getDistributor(id));
    }

    @PostMapping
    @SessionAuth
    @Operation(summary = "创建渠道")
    public Result<Long> createDistributor(@Valid @RequestBody CreateDistributorRequestDTO request) {
        return Result.success(distributorService.createDistributor(request));
    }

    @PutMapping("/{id}")
    @SessionAuth
    @Operation(summary = "更新渠道")
    public Result<String> updateDistributor(
            @Parameter(description = "渠道ID", required = true) @PathVariable Long id,
            @Valid @RequestBody UpdateDistributorRequestDTO request) {
        distributorService.updateDistributor(id, request);
        return Result.success("更新成功");
    }

    @PostMapping("/{id}/status")
    @SessionAuth
    @Operation(summary = "更新渠道状态")
    public Result<String> updateDistributorStatus(
            @Parameter(description = "渠道ID", required = true) @PathVariable Long id,
            @Valid @RequestBody UpdateDistributorStatusRequestDTO request) {
        distributorService.updateDistributorStatus(id, request);
        return Result.success("状态更新成功");
    }

    @DeleteMapping("/{id}")
    @SessionAuth
    @Operation(summary = "删除渠道")
    public Result<String> deleteDistributor(
            @Parameter(description = "渠道ID", required = true) @PathVariable Long id) {
        distributorService.deleteDistributor(id);
        return Result.success("删除成功");
    }
}
