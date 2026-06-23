# Step 03: CRUD 脚手架 — 从 Mapper 到 Controller 的完整链路

> 有了表和权限，现在搭建标准的三层架构：Mapper → Service → Controller。

---

## 本章目标

以"线索管理"为例，搭建完整的 CRUD 链路：
- Mapper 接口 + XML（数据访问层）
- Service 接口 + 实现（业务逻辑层）
- Controller（API 层）
- DTO（数据传输对象）

## 分层架构

```
┌─────────────────────────────────────────────────┐
│  Controller  (薄层：路由 + 异常捕获)              │
│  @Slf4j @RestController @OpsApi @SessionAuth    │
├─────────────────────────────────────────────────┤
│  Service     (厚层：业务逻辑 + 权限 + 校验)       │
│  接口 + 实现，@Transactional                     │
├─────────────────────────────────────────────────┤
│  Mapper      (薄层：SQL 映射)                    │
│  接口 + XML，三段式 SQL 片段                      │
├─────────────────────────────────────────────────┤
│  Entity      (纯数据容器)                        │
│  @Data，无业务逻辑                               │
└─────────────────────────────────────────────────┘
```

## 第一步：Mapper 层

**接口**：

```java
public interface DistributionLeadMapper {
    // 对偶设计：无权限 / 有权限
    Long countByCondition(@Param("leadNo") String leadNo, ...);
    List<DistributionLeadEntity> selectByCondition(..., @Param("offset") int offset, @Param("limit") int limit);

    Long countByConditionWithScope(...,
        @Param("authorizedDistributorIds") List<Long> authorizedDistributorIds,
        @Param("authorizedMemberId") Long authorizedMemberId,
        @Param("authorizedOwnerUserId") Long authorizedOwnerUserId);
    List<DistributionLeadEntity> selectByConditionWithScope(...,
        @Param("authorizedDistributorIds") List<Long> authorizedDistributorIds,
        @Param("authorizedMemberId") Long authorizedMemberId,
        @Param("authorizedOwnerUserId") Long authorizedOwnerUserId,
        @Param("offset") int offset, @Param("limit") int limit);

    DistributionLeadEntity selectByPrimaryKey(@Param("id") Long id);
    int insertSelective(DistributionLeadEntity entity);
    int updateByPrimaryKeySelective(DistributionLeadEntity entity);
}
```

**XML 三段式结构**：

```xml
<mapper namespace="...DistributionLeadMapper">

    <!-- 1. 结果映射 -->
    <resultMap id="BaseResultMap" type="...DistributionLeadEntity">
        <id column="id" property="id"/>
        <result column="lead_no" property="leadNo"/>
        <!-- ... -->
    </resultMap>

    <!-- 2. 可复用片段 -->
    <sql id="BaseColumnList">id, lead_no, patient_name, ...</sql>

    <sql id="ConditionWhere">
        where deleted = 0
        <if test="leadNo != null and leadNo != ''">and lead_no = #{leadNo}</if>
        <if test="stage != null and stage != ''">and stage = #{stage}</if>
        <!-- ... -->
    </sql>

    <sql id="ScopeCondition">
        <if test="authorizedDistributorIds != null and authorizedDistributorIds.size() > 0">
            and source_distributor_id in
            <foreach collection="authorizedDistributorIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        </if>
        <if test="authorizedMemberId != null or authorizedOwnerUserId != null">
            and (
            <if test="authorizedMemberId != null">source_member_id = #{authorizedMemberId}</if>
            <if test="authorizedMemberId != null and authorizedOwnerUserId != null"> or </if>
            <if test="authorizedOwnerUserId != null">owner_user_id = #{authorizedOwnerUserId}</if>
            )
        </if>
    </sql>

    <!-- 3. 查询方法（对偶） -->
    <select id="selectByConditionWithScope" resultMap="BaseResultMap">
        select <include refid="BaseColumnList"/> from distribution_lead
        <include refid="ConditionWhere"/>
        <include refid="ScopeCondition"/>
        order by create_time desc, id desc
        limit #{offset}, #{limit}
    </select>
</mapper>
```

## 第二步：Service 层

**接口**：

```java
public interface DistributionLeadService {
    PageResponseDTO<DistributionLeadDTO> listLeads(String leadNo, String patientKeyword,
        Long sourceDistributorId, String intentProductLine, Long ownerUserId, String stage,
        Integer page, Integer pageSize);
    DistributionLeadDetailDTO getLead(Long id);
    Long createLead(CreateDistributionLeadRequestDTO request);
    void updateLead(Long id, UpdateDistributionLeadRequestDTO request);
}
```

**实现的标准模式**：

```java
@Service
public class DistributionLeadServiceImpl implements DistributionLeadService {

    @Autowired private DistributionLeadMapper leadMapper;
    @Autowired private DistributionOperatorService operatorService;
    @Autowired private DistributionAuditLogService auditLogService;
    @Autowired private DistributionDataPermissionService dataPermissionService;

    @Override
    public PageResponseDTO<DistributionLeadDTO> listLeads(...) {
        // 1. 分页参数安全处理
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);

        // 2. 解析权限
        DistributionDataAccessScope scope = dataPermissionService.resolveCurrentAccessScope();
        List<Long> authorizedIds = scope.isAllScope() || scope.isSelfScope()
            ? null : scope.getAuthorizedDistributorIds();

        // 3. 查询（带权限）
        long total = leadMapper.countByConditionWithScope(
            ..., authorizedIds, authorizedMemberId, authorizedOwnerUserId);
        List<DistributionLeadEntity> entities = leadMapper.selectByConditionWithScope(
            ..., authorizedIds, authorizedMemberId, authorizedOwnerUserId,
            (safePage - 1) * safePageSize, safePageSize);

        // 4. 组装 DTO（批量加载关联数据，避免 N+1）
        return new PageResponseDTO<>(buildLeadDTOList(entities), total, safePage, safePageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLead(CreateDistributionLeadRequestDTO request) {
        // 1. 校验
        validateDistributor(request.getSourceDistributorId());
        validateProductLine(request.getIntentProductLine());

        // 2. 权限校验
        dataPermissionService.checkAccessPermission(
            request.getSourceDistributorId(), request.getSourceMemberId(), null);

        // 3. 构建实体
        Long operatorUserId = operatorService.getCurrentOperatorUserId();
        DistributionLeadEntity entity = new DistributionLeadEntity();
        entity.setLeadNo(generateLeadNo());
        // ... 设置其他字段
        entity.setCreatedBy(operatorUserId);
        entity.setDeleted(0);

        // 4. 插入
        leadMapper.insertSelective(entity);

        // 5. 审计日志
        auditLogService.record("lead", entity.getId(), "create", null, entity, "创建线索");

        return entity.getId();
    }
}
```

**关键模式总结**：

| 步骤 | 代码 | 说明 |
|------|------|------|
| 分页安全处理 | `Math.min(pageSize, 100)` | 防止一次查太多 |
| 权限解析 | `resolveCurrentAccessScope()` | 每次查询都走权限 |
| 事务注解 | `@Transactional(rollbackFor = Exception.class)` | 所有写操作必须有 |
| 操作人获取 | `operatorService.getCurrentOperatorUserId()` | 从 ThreadLocal 获取 |
| 审计日志 | `auditLogService.record(...)` | 所有写操作必须记录 |
| 逻辑删除 | `entity.setDeleted(0)` | 新建时显式设为 0 |

## 第三步：Controller 层

Controller 是**薄层**，只做三件事：路由、参数绑定、异常捕获。

```java
@Slf4j
@RestController
@OpsApi
@RequestMapping("/api/manage/distribution/leads")
@Tag(name = "分销线索管理接口")
public class DistributionLeadController {

    @Autowired private DistributionLeadService distributionLeadService;

    @GetMapping
    @SessionAuth
    @Operation(summary = "分页查询线索列表")
    public Result<PageResponseDTO<DistributionLeadDTO>> listLeads(
            @Parameter(description = "线索编号") @RequestParam(required = false) String leadNo,
            @Parameter(description = "患者姓名或手机号") @RequestParam(required = false) String patientKeyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            return Result.success(distributionLeadService.listLeads(
                leadNo, patientKeyword, null, null, null, null, page, pageSize));
        } catch (BizException e) {
            return new Result<>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("查询线索列表失败", e);
            return Result.fail();
        }
    }

    @PostMapping
    @SessionAuth
    @Operation(summary = "创建线索")
    public Result<Long> createLead(@Valid @RequestBody CreateDistributionLeadRequestDTO request) {
        try {
            return Result.success(distributionLeadService.createLead(request));
        } catch (BizException e) {
            return new Result<>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("创建线索失败", e);
            return Result.fail();
        }
    }
    // ... 其他端点
}
```

**Controller 的注解约定**：

| 注解 | 作用 | 位置 |
|------|------|------|
| `@Slf4j` | 日志 | 类上 |
| `@RestController` | 声明 REST 控制器 | 类上 |
| `@OpsApi` | 自定义注解，标记运维 API | 类上 |
| `@SessionAuth` | 需要认证 | 类上或方法上 |
| `@Tag` | Swagger 文档标签 | 类上 |
| `@Operation` | Swagger 端点描述 | 方法上 |
| `@Parameter` | Swagger 参数描述 | 参数上 |
| `@Valid` | 触发 DTO 的校验注解 | 参数上 |

## 第四步：DTO 设计

**请求 DTO**：只包含客户端需要传的字段，带校验注解。

```java
@Data
public class CreateDistributionLeadRequestDTO {
    @NotNull(message = "渠道ID不能为空")
    private Long sourceDistributorId;

    private Long sourceMemberId;

    @NotBlank(message = "患者姓名不能为空")
    private String patientName;

    @NotBlank(message = "患者手机号不能为空")
    private String patientPhone;

    @NotBlank(message = "意向产品线不能为空")
    private String intentProductLine;

    private String remark;
}
```

**响应 DTO**：包含关联数据的名称（而非只返回 ID）。

```java
@Data
public class DistributionLeadDTO {
    private Long id;
    private String leadNo;
    private String patientName;
    private String patientPhone;
    private Long sourceDistributorId;
    private String sourceDistributorName;  // 关联查询的名称
    private Long sourceMemberId;
    private String sourceMemberName;       // 关联查询的名称
    private String intentProductLine;
    private String stage;
    private Date createTime;
}
```

**批量加载关联数据**（避免 N+1 查询）：

```java
private List<DistributionLeadDTO> buildLeadDTOList(List<DistributionLeadEntity> entities) {
    // 1. 收集所有需要关联的 ID
    Set<Long> distributorIds = new HashSet<>();
    Set<Long> memberIds = new HashSet<>();
    for (DistributionLeadEntity e : entities) {
        if (e.getSourceDistributorId() != null) distributorIds.add(e.getSourceDistributorId());
        if (e.getSourceMemberId() != null) memberIds.add(e.getSourceMemberId());
    }

    // 2. 批量查询（1 次 SQL 而非 N 次）
    Map<Long, String> distributorNameMap = loadDistributorNameMap(distributorIds);
    Map<Long, String> memberNameMap = loadMemberNameMap(memberIds);

    // 3. 组装 DTO
    List<DistributionLeadDTO> list = new ArrayList<>();
    for (DistributionLeadEntity entity : entities) {
        DistributionLeadDTO dto = new DistributionLeadDTO();
        BeanUtils.copyProperties(entity, dto);
        dto.setSourceDistributorName(distributorNameMap.get(entity.getSourceDistributorId()));
        dto.setSourceMemberName(memberNameMap.get(entity.getSourceMemberId()));
        list.add(dto);
    }
    return list;
}
```

## 第五步：统一响应格式

```java
@Data
@NoArgsConstructor
public class Result<T> {
    private int code;    // 200=成功, 10000=失败, 401=认证失败
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, data);
    }
    public static <T> Result<T> fail() {
        return new Result<>(10000);
    }
}
```

## 验

启动应用后，用 Swagger UI 测试：

```
http://localhost:9030/swagger-ui.html
```

或者用 curl：

```bash
# 查询线索列表
curl -H "Authorization: test-token-123" \
     -H "x-biz: medical-chaperon" \
     "http://localhost:9030/api/manage/distribution/leads?page=1&pageSize=10"

# 创建线索
curl -X POST \
     -H "Authorization: test-token-123" \
     -H "x-biz: medical-chaperon" \
     -H "Content-Type: application/json" \
     -d '{"sourceDistributorId":1,"patientName":"测试患者","patientPhone":"13900000001","intentProductLine":"gene"}' \
     http://localhost:9030/api/manage/distribution/leads
```

## 下一步

CRUD 能跑了，但没有审计日志。下一步加入审计层，记录"谁在什么时候改了什么"。

→ [Step 04: 审计日志](04-audit-logging.md)

---

