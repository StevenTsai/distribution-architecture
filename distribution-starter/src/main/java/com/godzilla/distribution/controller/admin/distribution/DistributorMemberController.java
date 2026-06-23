package com.godzilla.distribution.controller.admin.distribution;

import com.godzilla.distribution.common.Result;
import com.godzilla.distribution.common.annotation.OpsApi;
import com.godzilla.distribution.common.annotation.SessionAuth;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributorMemberRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorMemberRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorMemberStatusRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorMemberDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorMemberDetailDTO;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.service.distribution.DistributorMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@OpsApi
@RequestMapping("/api/manage/distribution/members")
@Tag(name = "分销渠道成员管理接口")
public class DistributorMemberController {

    @Autowired
    private DistributorMemberService distributorMemberService;

    @GetMapping
    @SessionAuth
    @Operation(summary = "分页查询渠道成员列表")
    public Result<PageResponseDTO<DistributionDistributorMemberDTO>> listMembers(
            @Parameter(description = "所属渠道ID") @RequestParam(required = false) Long distributorId,
            @Parameter(description = "成员状态") @RequestParam(required = false) String status,
            @Parameter(description = "成员角色") @RequestParam(required = false) String roleCode,
            @Parameter(description = "成员手机号") @RequestParam(required = false) String phone,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            return Result.success(distributorMemberService.listMembers(distributorId, status, roleCode, phone, page, pageSize));
        } catch (BizException e) {
            return new Result<PageResponseDTO<DistributionDistributorMemberDTO>>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("查询渠道成员列表失败", e);
            return Result.fail();
        }
    }

    @GetMapping("/{id}")
    @SessionAuth
    @Operation(summary = "查询渠道成员详情")
    public Result<DistributionDistributorMemberDetailDTO> getMember(
            @Parameter(description = "成员ID", required = true) @PathVariable Long id) {
        try {
            return Result.success(distributorMemberService.getMember(id));
        } catch (BizException e) {
            return new Result<DistributionDistributorMemberDetailDTO>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("查询渠道成员详情失败, id={}", id, e);
            return Result.fail();
        }
    }

    @PostMapping
    @SessionAuth
    @Operation(summary = "创建渠道成员")
    public Result<Long> createMember(@Valid @RequestBody CreateDistributorMemberRequestDTO request) {
        try {
            return Result.success(distributorMemberService.createMember(request));
        } catch (BizException e) {
            return new Result<Long>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("创建渠道成员失败", e);
            return Result.fail();
        }
    }

    @PutMapping("/{id}")
    @SessionAuth
    @Operation(summary = "更新渠道成员")
    public Result<String> updateMember(
            @Parameter(description = "成员ID", required = true) @PathVariable Long id,
            @Valid @RequestBody UpdateDistributorMemberRequestDTO request) {
        try {
            distributorMemberService.updateMember(id, request);
            return Result.success("更新成功");
        } catch (BizException e) {
            return new Result<String>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("更新渠道成员失败, id={}", id, e);
            return Result.fail();
        }
    }

    @PostMapping("/{id}/status")
    @SessionAuth
    @Operation(summary = "更新渠道成员状态")
    public Result<String> updateMemberStatus(
            @Parameter(description = "成员ID", required = true) @PathVariable Long id,
            @Valid @RequestBody UpdateDistributorMemberStatusRequestDTO request) {
        try {
            distributorMemberService.updateMemberStatus(id, request);
            return Result.success("状态更新成功");
        } catch (BizException e) {
            return new Result<String>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("更新渠道成员状态失败, id={}", id, e);
            return Result.fail();
        }
    }
}
