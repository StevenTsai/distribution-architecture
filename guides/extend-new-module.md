# 扩展新模块：从零添加一个完整的业务模块

> 以"渠道培训管理"模块为例，演示如何基于现有架构添加一个完整的 CRUD + 权限 + 审计的业务模块。

---

## 目标

添加一个"渠道培训管理"模块，支持：
- 创建/编辑/删除培训记录
- 按渠道和成员查询培训列表
- 培训状态管理（待培训 → 已完成 → 已过期）
- 数据权限控制
- 审计日志记录

## 总览：需要创建/修改的文件

```
需要新建的文件（7 个）：
├── entity/distribution/DistributionTrainingEntity.java
├── dto/distribution/request/CreateDistributionTrainingRequestDTO.java
├── dto/distribution/response/DistributionTrainingDTO.java
├── mapper/distribution/DistributionTrainingMapper.java
├── resources/mapper/distribution/DistributionTrainingMapper.xml
├── service/distribution/DistributionTrainingService.java
└── service/distribution/impl/DistributionTrainingServiceImpl.java

需要修改的文件（2 个）：
├── controller/admin/distribution/DistributionTrainingController.java  ← 新建
└── 03_training_tables.sql                        ← 新建
```

## 第一步：设计数据库表

创建 `03_training_tables.sql`：

```sql
CREATE TABLE IF NOT EXISTS `distribution_training` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `distributor_id` BIGINT NOT NULL COMMENT '所属渠道ID',
  `member_id` BIGINT NOT NULL COMMENT '培训成员ID',
  `training_type` VARCHAR(32) NOT NULL COMMENT '培训类型：online/offline/exam',
  `training_name` VARCHAR(128) NOT NULL COMMENT '培训名称',
  `training_date` DATE DEFAULT NULL COMMENT '培训日期',
  `score` INT DEFAULT NULL COMMENT '考核分数',
  `status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/completed/expired',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人user_id',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人user_id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modify_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  KEY idx_training_distributor_id (`distributor_id`),
  KEY idx_training_member_id (`member_id`),
  KEY idx_training_status (`status`),
  KEY idx_training_create_time (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道培训记录表';
```

执行建表：

```bash
mysql -u root -p distribution_server < 03_training_tables.sql
```

## 第二步：创建 Entity

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

## 第三步：创建 DTO

```java
// dto/distribution/request/CreateDistributionTrainingRequestDTO.java
package com.example.distribution.dto.distribution.request;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
public class CreateDistributionTrainingRequestDTO {
    @NotNull(message = "渠道ID不能为空")
    private Long distributorId;

    @NotNull(message = "成员ID不能为空")
    private Long memberId;

    @NotBlank(message = "培训类型不能为空")
    private String trainingType;

    @NotBlank(message = "培训名称不能为空")
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
    private String distributorName;  // 关联查询
    private Long memberId;
    private String memberName;       // 关联查询
    private String trainingType;
    private String trainingName;
    private Date trainingDate;
    private Integer score;
    private String status;
    private String remark;
    private Date createTime;
}
```

## 第四步：创建 Mapper 接口

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

## 第五步：创建 Mapper XML

这是最关键的一步，遵循项目的三段式模式：

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

    <!-- 数据权限：与项目其他 Mapper 保持一致的 ScopeCondition 模式 -->
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

    <!-- 对偶查询：无权限 -->
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

    <!-- 对偶查询：带权限 -->
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

    <!-- 单条查询 -->
    <select id="selectByPrimaryKey" resultMap="BaseResultMap">
        select <include refid="BaseColumnList"/> from distribution_training
        where id = #{id} and deleted = 0
    </select>

    <!-- 写操作：部分更新 -->
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

## 第六步：创建 Service 接口和实现

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

Service 实现遵循项目标准模式（数据权限 + 审计日志 + 事务）：

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

        // ★ 标准模式：解析数据权限
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
        // ★ 标准模式：权限校验
        dataPermissionService.checkAccessPermission(entity.getDistributorId(), entity.getMemberId(), null);
        return toDTO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTraining(CreateDistributionTrainingRequestDTO request) {
        // 校验
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

        // ★ 标准模式：审计日志
        auditLogService.record("training", entity.getId(), "create", null, entity, "创建培训记录");
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

        auditLogService.record("training", id, "update_status", existing, getTrainingEntity(id), "更新培训状态");
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

        auditLogService.record("training", id, "delete", existing, null, "删除培训记录");
    }

    // ... 私有辅助方法（getTrainingEntity, validateDistributor, validateMember, toDTO, buildDTOList）...
}
```

## 第七步：创建 Controller

```java
// controller/admin/distribution/DistributionTrainingController.java
package com.example.distribution.controller.admin.distribution;

// ... imports ...

@Slf4j
@RestController
@OpsApi
@RequestMapping("/api/manage/distribution/trainings")
@Tag(name = "渠道培训管理接口")
public class DistributionTrainingController {

    @Autowired private DistributionTrainingService distributionTrainingService;

    @GetMapping
    @SessionAuth
    @Operation(summary = "分页查询培训列表")
    public Result<PageResponseDTO<DistributionTrainingDTO>> listTrainings(
            @Parameter(description = "渠道ID") @RequestParam(required = false) Long distributorId,
            @Parameter(description = "成员ID") @RequestParam(required = false) Long memberId,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            return Result.success(distributionTrainingService.listTrainings(distributorId, memberId, status, page, pageSize));
        } catch (BizException e) {
            return new Result<>(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("查询培训列表失败", e);
            return Result.fail();
        }
    }

    // ... getTraining, createTraining, updateTrainingStatus, deleteTraining ...
}
```

## 检查清单

新模块完成后，确认以下几点：

- [ ] Entity 字段与数据库表一致，使用 `@Data` 注解
- [ ] Mapper XML 遵循三段式模式（`BaseColumnList` + `ConditionWhere` + `ScopeCondition`）
- [ ] Mapper 接口有对偶方法（`selectByCondition` / `selectByConditionWithScope`）
- [ ] Service 实现有数据权限校验（`resolveCurrentAccessScope` / `checkAccessPermission`）
- [ ] 写操作有审计日志（`auditLogService.record()`）
- [ ] 写操作有 `@Transactional(rollbackFor = Exception.class)`
- [ ] Controller 使用 `@SessionAuth`、`@OpsApi`、`@Tag` 注解
- [ ] Controller 的每个端点都有 try-catch，捕获 `BizException` 和 `Exception`
- [ ] 分页参数有安全校验（默认值、最大值限制）
- [ ] 输入参数有校验（`@NotNull`、`@NotBlank` 或手动 `validate`）

---

*参考现有模块：*
- *渠道管理：`DistributorController` + `DistributorServiceImpl` + `DistributionDistributorMapper.xml`*
- *线索管理：`DistributionLeadController` + `DistributionLeadServiceImpl` + `DistributionLeadMapper.xml`*
