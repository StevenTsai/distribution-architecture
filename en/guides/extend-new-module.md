# Extending a New Module: Adding a Complete Business Module from Scratch

[中文](../../guides/extend-new-module.md) | English

> Using the "Distributor Training Management" module as an example, this guide demonstrates how to add a complete business module with CRUD, permissions, and auditing on top of the existing architecture.

---

## Goals

Add a "Distributor Training Management" module that supports:
- Create/edit/delete training records
- Query training lists by distributor and member
- Training status management (pending → completed → expired)
- Data permission control
- Audit log recording

## Overview: Files to Create/Modify

```
Files to create (7):
├── entity/distribution/DistributionTrainingEntity.java
├── dto/distribution/request/CreateDistributionTrainingRequestDTO.java
├── dto/distribution/response/DistributionTrainingDTO.java
├── mapper/distribution/DistributionTrainingMapper.java
├── resources/mapper/distribution/DistributionTrainingMapper.xml
├── service/distribution/DistributionTrainingService.java
└── service/distribution/impl/DistributionTrainingServiceImpl.java

Files to modify (2):
├── controller/admin/distribution/DistributionTrainingController.java  ← new
└── 03_training_tables.sql                        ← new
```

## Step 1: Design the Database Table

Create `03_training_tables.sql`:

```sql
CREATE TABLE IF NOT EXISTS `distribution_training` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key ID',
  `distributor_id` BIGINT NOT NULL COMMENT 'Owning distributor ID',
  `member_id` BIGINT NOT NULL COMMENT 'Trainee member ID',
  `training_type` VARCHAR(32) NOT NULL COMMENT 'Training type: online/offline/exam',
  `training_name` VARCHAR(128) NOT NULL COMMENT 'Training name',
  `training_date` DATE DEFAULT NULL COMMENT 'Training date',
  `score` INT DEFAULT NULL COMMENT 'Assessment score',
  `status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT 'Status: pending/completed/expired',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Remark',
  `created_by` BIGINT DEFAULT NULL COMMENT 'Creator user_id',
  `updated_by` BIGINT DEFAULT NULL COMMENT 'Updater user_id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
  `modify_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0-no, 1-yes',
  PRIMARY KEY (`id`),
  KEY idx_training_distributor_id (`distributor_id`),
  KEY idx_training_member_id (`member_id`),
  KEY idx_training_status (`status`),
  KEY idx_training_create_time (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Distributor training records table';
```

Run the table creation:

```bash
mysql -u root -p distribution_server < 03_training_tables.sql
```

## Step 2: Create the Entity

```java
// entity/distribution/DistributionTrainingEntity.java
package com.example.distribution.entity.distribution;

import lombok.Data;
import java.util.Date;

@Data
public class DistributionTrainingEntity {
    private Long id;
    private Long distributorId;
    private Long memberId;
    private String trainingType;
    private String trainingName;
    private Date trainingDate;
    private Integer score;
    private String status;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date modifyTime;
    private Integer deleted;
}
```

## Step 3: Create the DTOs

```java
// dto/distribution/request/CreateDistributionTrainingRequestDTO.java
package com.example.distribution.dto.distribution.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
public class CreateDistributionTrainingRequestDTO {
    @NotNull(message = "distributor ID is required")
    private Long distributorId;

    @NotNull(message = "member ID is required")
    private Long memberId;

    @NotBlank(message = "training type is required")
    private String trainingType;

    @NotBlank(message = "training name is required")
    private String trainingName;

    private Date trainingDate;
    private Integer score;
    private String remark;
}
```

```java
// dto/distribution/response/DistributionTrainingDTO.java
package com.example.distribution.dto.distribution.response;

import lombok.Data;
import java.util.Date;

@Data
public class DistributionTrainingDTO {
    private Long id;
    private Long distributorId;
    private String distributorName;  // Joined query
    private Long memberId;
    private String memberName;       // Joined query
    private String trainingType;
    private String trainingName;
    private Date trainingDate;
    private Integer score;
    private String status;
    private String remark;
    private Date createTime;
}
```

## Step 4: Create the Mapper Interface

```java
// mapper/distribution/DistributionTrainingMapper.java
package com.example.distribution.mapper.distribution;

import com.example.distribution.entity.distribution.DistributionTrainingEntity;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface DistributionTrainingMapper {
    Long countByCondition(@Param("distributorId") Long distributorId,
                          @Param("memberId") Long memberId,
                          @Param("status") String status);

    List<DistributionTrainingEntity> selectByCondition(@Param("distributorId") Long distributorId,
                                                       @Param("memberId") Long memberId,
                                                       @Param("status") String status,
                                                       @Param("offset") int offset,
                                                       @Param("limit") int limit);

    Long countByConditionWithScope(@Param("distributorId") Long distributorId,
                                   @Param("memberId") Long memberId,
                                   @Param("status") String status,
                                   @Param("authorizedDistributorIds") List<Long> authorizedDistributorIds,
                                   @Param("authorizedMemberId") Long authorizedMemberId);

    List<DistributionTrainingEntity> selectByConditionWithScope(@Param("distributorId") Long distributorId,
                                                                @Param("memberId") Long memberId,
                                                                @Param("status") String status,
                                                                @Param("authorizedDistributorIds") List<Long> authorizedDistributorIds,
                                                                @Param("authorizedMemberId") Long authorizedMemberId,
                                                                @Param("offset") int offset,
                                                                @Param("limit") int limit);

    DistributionTrainingEntity selectByPrimaryKey(@Param("id") Long id);

    int insertSelective(DistributionTrainingEntity entity);

    int updateByPrimaryKeySelective(DistributionTrainingEntity entity);
}
```

## Step 5: Create the Mapper XML

This is the most critical step. Follow the project's three-section pattern:

```xml
<!-- resources/mapper/distribution/DistributionTrainingMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.distribution.mapper.distribution.DistributionTrainingMapper">

    <resultMap id="BaseResultMap" type="com.example.distribution.entity.distribution.DistributionTrainingEntity">
        <id column="id" property="id"/>
        <result column="distributor_id" property="distributorId"/>
        <result column="member_id" property="memberId"/>
        <result column="training_type" property="trainingType"/>
        <result column="training_name" property="trainingName"/>
        <result column="training_date" property="trainingDate"/>
        <result column="score" property="score"/>
        <result column="status" property="status"/>
        <result column="remark" property="remark"/>
        <result column="created_by" property="createdBy"/>
        <result column="updated_by" property="updatedBy"/>
        <result column="create_time" property="createTime"/>
        <result column="modify_time" property="modifyTime"/>
        <result column="deleted" property="deleted"/>
    </resultMap>

    <sql id="BaseColumnList">
        id, distributor_id, member_id, training_type, training_name, training_date,
        score, status, remark, created_by, updated_by, create_time, modify_time, deleted
    </sql>

    <sql id="ConditionWhere">
        where deleted = 0
        <if test="distributorId != null">
            and distributor_id = #{distributorId}
        </if>
        <if test="memberId != null">
            and member_id = #{memberId}
        </if>
        <if test="status != null and status != ''">
            and status = #{status}
        </if>
    </sql>

    <!-- Data permissions: ScopeCondition pattern consistent with other mappers in this project -->
    <sql id="ScopeCondition">
        <if test="authorizedDistributorIds != null and authorizedDistributorIds.size() > 0">
            and distributor_id in
            <foreach collection="authorizedDistributorIds" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
        </if>
        <if test="authorizedMemberId != null">
            and member_id = #{authorizedMemberId}
        </if>
    </sql>

    <!-- Dual query: without permissions -->
    <select id="countByCondition" resultType="java.lang.Long">
        select count(*) from distribution_training
        <include refid="ConditionWhere"/>
    </select>

    <select id="selectByCondition" resultMap="BaseResultMap">
        select <include refid="BaseColumnList"/> from distribution_training
        <include refid="ConditionWhere"/>
        order by create_time desc, id desc
        limit #{offset}, #{limit}
    </select>

    <!-- Dual query: with permissions -->
    <select id="countByConditionWithScope" resultType="java.lang.Long">
        select count(*) from distribution_training
        <include refid="ConditionWhere"/>
        <include refid="ScopeCondition"/>
    </select>

    <select id="selectByConditionWithScope" resultMap="BaseResultMap">
        select <include refid="BaseColumnList"/> from distribution_training
        <include refid="ConditionWhere"/>
        <include refid="ScopeCondition"/>
        order by create_time desc, id desc
        limit #{offset}, #{limit}
    </select>

    <!-- Single-record query -->
    <select id="selectByPrimaryKey" resultMap="BaseResultMap">
        select <include refid="BaseColumnList"/> from distribution_training
        where id = #{id} and deleted = 0
    </select>

    <!-- Write operation: partial update -->
    <insert id="insertSelective" useGeneratedKeys="true" keyProperty="id">
        insert into distribution_training
        <trim prefix="(" suffix=")" suffixOverrides=",">
            <if test="distributorId != null">distributor_id,</if>
            <if test="memberId != null">member_id,</if>
            <if test="trainingType != null">training_type,</if>
            <if test="trainingName != null">training_name,</if>
            <if test="trainingDate != null">training_date,</if>
            <if test="score != null">score,</if>
            <if test="status != null">status,</if>
            <if test="remark != null">remark,</if>
            <if test="createdBy != null">created_by,</if>
            <if test="updatedBy != null">updated_by,</if>
            <if test="deleted != null">deleted,</if>
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
            <if test="distributorId != null">#{distributorId},</if>
            <if test="memberId != null">#{memberId},</if>
            <if test="trainingType != null">#{trainingType},</if>
            <if test="trainingName != null">#{trainingName},</if>
            <if test="trainingDate != null">#{trainingDate},</if>
            <if test="score != null">#{score},</if>
            <if test="status != null">#{status},</if>
            <if test="remark != null">#{remark},</if>
            <if test="createdBy != null">#{createdBy},</if>
            <if test="updatedBy != null">#{updatedBy},</if>
            <if test="deleted != null">#{deleted},</if>
        </trim>
    </insert>

    <update id="updateByPrimaryKeySelective">
        update distribution_training
        <set>
            <if test="trainingType != null">training_type = #{trainingType},</if>
            <if test="trainingName != null">training_name = #{trainingName},</if>
            <if test="trainingDate != null">training_date = #{trainingDate},</if>
            <if test="score != null">score = #{score},</if>
            <if test="status != null">status = #{status},</if>
            <if test="remark != null">remark = #{remark},</if>
            <if test="updatedBy != null">updated_by = #{updatedBy},</if>
            <if test="deleted != null">deleted = #{deleted},</if>
        </set>
        where id = #{id} and deleted = 0
    </update>
</mapper>
```

## Step 6: Create the Service Interface and Implementation

```java
// service/distribution/DistributionTrainingService.java
package com.example.distribution.service.distribution;

import com.example.distribution.dto.PageResponseDTO;
import com.example.distribution.dto.distribution.request.CreateDistributionTrainingRequestDTO;
import com.example.distribution.dto.distribution.response.DistributionTrainingDTO;

public interface DistributionTrainingService {
    PageResponseDTO<DistributionTrainingDTO> listTrainings(Long distributorId, Long memberId, String status, Integer page, Integer pageSize);
    DistributionTrainingDTO getTraining(Long id);
    Long createTraining(CreateDistributionTrainingRequestDTO request);
    void updateTrainingStatus(Long id, String status);
    void deleteTraining(Long id);
}
```

The Service implementation follows the project's standard pattern (data permissions + audit log + transactions):

```java
// service/distribution/impl/DistributionTrainingServiceImpl.java
package com.example.distribution.service.distribution.impl;

// ... imports ...

@Service
public class DistributionTrainingServiceImpl implements DistributionTrainingService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired private DistributionTrainingMapper trainingMapper;
    @Autowired private DistributionDistributorMapper distributorMapper;
    @Autowired private DistributionDistributorMemberMapper memberMapper;
    @Autowired private DistributionOperatorService operatorService;
    @Autowired private DistributionAuditLogService auditLogService;
    @Autowired private DistributionDataPermissionService dataPermissionService;

    @Override
    public PageResponseDTO<DistributionTrainingDTO> listTrainings(Long distributorId, Long memberId, String status, Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        // ★ Standard pattern: resolve data permissions
        DistributionDataPermissionService.DistributionDataAccessScope scope =
            dataPermissionService.resolveCurrentAccessScope();
        List<Long> authorizedDistributorIds = scope.isAllScope() || scope.isSelfScope()
            ? null : scope.getAuthorizedDistributorIds();
        if (!scope.isAllScope() && !scope.isSelfScope()
            && (authorizedDistributorIds == null || authorizedDistributorIds.isEmpty())) {
            return new PageResponseDTO<>(Collections.emptyList(), 0, safePage, safePageSize);
        }
        Long authorizedMemberId = scope.isSelfScope() ? scope.getMemberId() : null;

        long total = trainingMapper.countByConditionWithScope(distributorId, memberId, status, authorizedDistributorIds, authorizedMemberId);
        List<DistributionTrainingEntity> entities = trainingMapper.selectByConditionWithScope(distributorId, memberId, status, authorizedDistributorIds, authorizedMemberId, (safePage - 1) * safePageSize, safePageSize);
        return new PageResponseDTO<>(buildDTOList(entities), total, safePage, safePageSize);
    }

    @Override
    public DistributionTrainingDTO getTraining(Long id) {
        DistributionTrainingEntity entity = getTrainingEntity(id);
        // ★ Standard pattern: permission check
        dataPermissionService.checkAccessPermission(entity.getDistributorId(), entity.getMemberId(), null);
        return toDTO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTraining(CreateDistributionTrainingRequestDTO request) {
        // Validation
        validateDistributor(request.getDistributorId());
        validateMember(request.getMemberId(), request.getDistributorId());
        dataPermissionService.checkAccessPermission(request.getDistributorId(), request.getMemberId(), null);

        Long operatorUserId = operatorService.getCurrentOperatorUserId();
        DistributionTrainingEntity entity = new DistributionTrainingEntity();
        entity.setDistributorId(request.getDistributorId());
        entity.setMemberId(request.getMemberId());
        entity.setTrainingType(request.getTrainingType().trim());
        entity.setTrainingName(request.getTrainingName().trim());
        entity.setTrainingDate(request.getTrainingDate());
        entity.setScore(request.getScore());
        entity.setStatus("pending");
        entity.setRemark(request.getRemark());
        entity.setCreatedBy(operatorUserId);
        entity.setUpdatedBy(operatorUserId);
        entity.setDeleted(0);
        trainingMapper.insertSelective(entity);

        // ★ Standard pattern: audit log
        auditLogService.record("training", entity.getId(), "create", null, entity, "create training record");
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTrainingStatus(Long id, String status) {
        DistributionTrainingEntity existing = getTrainingEntity(id);
        dataPermissionService.checkAccessPermission(existing.getDistributorId(), existing.getMemberId(), null);

        DistributionTrainingEntity update = new DistributionTrainingEntity();
        update.setId(id);
        update.setStatus(status.trim());
        update.setUpdatedBy(operatorService.getCurrentOperatorUserId());
        trainingMapper.updateByPrimaryKeySelective(update);

        auditLogService.record("training", id, "update_status", existing, getTrainingEntity(id), "update training status");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTraining(Long id) {
        DistributionTrainingEntity existing = getTrainingEntity(id);
        dataPermissionService.checkAccessPermission(existing.getDistributorId(), existing.getMemberId(), null);

        DistributionTrainingEntity update = new DistributionTrainingEntity();
        update.setId(id);
        update.setDeleted(1);
        update.setUpdatedBy(operatorService.getCurrentOperatorUserId());
        trainingMapper.updateByPrimaryKeySelective(update);

        auditLogService.record("training", id, "delete", existing, null, "delete training record");
    }

    // ... private helper methods (getTrainingEntity, validateDistributor, validateMember, toDTO, buildDTOList) ...
}
```

## Step 7: Create the Controller

```java
// controller/admin/distribution/DistributionTrainingController.java
package com.example.distribution.controller.admin.distribution;

// ... imports ...

@Slf4j
@RestController
@OpsApi
@RequestMapping("/api/manage/distribution/trainings")
@Tag(name = "Distribution Training Management API")
public class DistributionTrainingController {

    @Autowired private DistributionTrainingService distributionTrainingService;

    @GetMapping
    @SessionAuth
    @Operation(summary = "List training records with pagination")
    public Result<PageResponseDTO<DistributionTrainingDTO>> listTrainings(
            @Parameter(description = "Distributor ID") @RequestParam(required = false) Long distributorId,
            @Parameter(description = "Member ID") @RequestParam(required = false) Long memberId,
            @Parameter(description = "Status") @RequestParam(required = false) String status,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            return Result.success(distributionTrainingService.listTrainings(distributorId, memberId, status, page, pageSize));
        } catch (BizException e) {
            return new Result<>(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed to list training records", e);
            return Result.fail();
        }
    }

    // ... getTraining, createTraining, updateTrainingStatus, deleteTraining ...
}
```

## Checklist

After completing the new module, confirm the following:

- [ ] Entity fields match the database table and use the `@Data` annotation
- [ ] Mapper XML follows the three-section pattern (`BaseColumnList` + `ConditionWhere` + `ScopeCondition`)
- [ ] Mapper interface has dual methods (`selectByCondition` / `selectByConditionWithScope`)
- [ ] Service implementation performs data permission checks (`resolveCurrentAccessScope` / `checkAccessPermission`)
- [ ] Write operations have audit logs (`auditLogService.record()`)
- [ ] Write operations have `@Transactional(rollbackFor = Exception.class)`
- [ ] Controller uses the `@SessionAuth`, `@OpsApi`, and `@Tag` annotations
- [ ] Every Controller endpoint has try-catch for `BizException` and `Exception`
- [ ] Pagination parameters have safety checks (defaults, maximum limits)
- [ ] Input parameters are validated (`@NotNull`, `@NotBlank`, or manual `validate`)

---

*Refer to existing modules:*
- *Distributor management: `DistributorController` + `DistributorServiceImpl` + `DistributionDistributorMapper.xml`*
- *Lead management: `DistributionLeadController` + `DistributionLeadServiceImpl` + `DistributionLeadMapper.xml`*
