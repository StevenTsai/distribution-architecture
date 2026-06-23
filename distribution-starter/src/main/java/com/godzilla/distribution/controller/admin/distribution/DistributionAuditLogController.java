package com.godzilla.distribution.controller.admin.distribution;

import com.godzilla.distribution.common.Result;
import com.godzilla.distribution.common.annotation.OpsApi;
import com.godzilla.distribution.common.annotation.SessionAuth;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionAuditLogDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionAuditLogDetailDTO;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.service.distribution.DistributionAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@OpsApi
@RequestMapping("/api/manage/distribution/audit-logs")
@Tag(name = "分销审计日志接口")
public class DistributionAuditLogController {

    @Autowired
    private DistributionAuditLogService distributionAuditLogService;

    @GetMapping
    @SessionAuth
    @Operation(summary = "分页查询分销审计日志")
    public Result<PageResponseDTO<DistributionAuditLogDTO>> listLogs(
            @Parameter(description = "业务类型") @RequestParam(required = false) String bizType,
            @Parameter(description = "业务ID") @RequestParam(required = false) Long bizId,
            @Parameter(description = "动作") @RequestParam(required = false) String action,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            return Result.success(distributionAuditLogService.listLogs(bizType, bizId, action, page, pageSize));
        } catch (BizException e) {
            return new Result<PageResponseDTO<DistributionAuditLogDTO>>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("查询分销审计日志失败", e);
            return Result.fail();
        }
    }

    @GetMapping("/{id}")
    @SessionAuth
    @Operation(summary = "查询分销审计日志详情")
    public Result<DistributionAuditLogDetailDTO> getLog(
            @Parameter(description = "审计日志ID", required = true) @PathVariable Long id) {
        try {
            return Result.success(distributionAuditLogService.getLog(id));
        } catch (BizException e) {
            return new Result<DistributionAuditLogDetailDTO>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("查询分销审计日志详情失败, id={}", id, e);
            return Result.fail();
        }
    }
}
