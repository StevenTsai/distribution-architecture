# Step 03: CRUD Scaffolding — The Complete Path from Mapper to Controller

[中文](../../tutorial/03-crud-scaffolding.md) | English

> With tables and permissions in place, we now build the standard three-tier architecture: Mapper → Service → Controller.

---

## Chapter Goals

Using "lead management" as an example, build the complete CRUD path:
- Mapper interface + XML (data access layer)
- Service interface + implementation (business logic layer)
- Controller (API layer)
- DTOs (data transfer objects)

## Layered Architecture

```
┌─────────────────────────────────────────────────┐
│  Controller  (thin layer: routing + exception handling) │
│  @Slf4j @RestController @OpsApi @SessionAuth    │
├─────────────────────────────────────────────────┤
│  Service     (thick layer: business logic + permissions + validation) │
│  interface + implementation, @Transactional     │
├─────────────────────────────────────────────────┤
│  Mapper      (thin layer: SQL mapping)          │
│  interface + XML, three-part SQL fragment       │
├─────────────────────────────────────────────────┤
│  Entity      (pure data container)              │
│  @Data, no business logic                       │
└─────────────────────────────────────────────────┘
```

## Step 1: Mapper Layer

**Interface**:

```java
public interface DistributionLeadMapper {
    // Dual design: without permissions / with permissions
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

**XML three-part structure**:

```xml
<mapper namespace="...DistributionLeadMapper">

    <!-- 1. Result mapping -->
    <resultMap id="BaseResultMap" type="...DistributionLeadEntity">
        <id column="id" property="id"/>
        <result column="lead_no" property="leadNo"/>
        <!-- ... -->
    </resultMap>

    <!-- 2. Reusable fragments -->
    <sql id="BaseColumnList">id, lead_no, contact_name, ...</sql>

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

    <!-- 3. Query methods (dual) -->
    <select id="selectByConditionWithScope" resultMap="BaseResultMap">
        select <include refid="BaseColumnList"/> from distribution_lead
        <include refid="ConditionWhere"/>
        <include refid="ScopeCondition"/>
        order by create_time desc, id desc
        limit #{offset}, #{limit}
    </select>
</mapper>
```

## Step 2: Service Layer

**Interface**:

```java
public interface DistributionLeadService {
    PageResponseDTO<DistributionLeadDTO> listLeads(String leadNo, String contactKeyword,
        Long sourceDistributorId, String intentProductLine, Long ownerUserId, String stage,
        Integer page, Integer pageSize);
    DistributionLeadDetailDTO getLead(Long id);
    Long createLead(CreateDistributionLeadRequestDTO request);
    void updateLead(Long id, UpdateDistributionLeadRequestDTO request);
}
```

**Standard implementation pattern**:

```java
@Service
public class DistributionLeadServiceImpl implements DistributionLeadService {

    @Autowired private DistributionLeadMapper leadMapper;
    @Autowired private DistributionOperatorService operatorService;
    @Autowired private DistributionAuditLogService auditLogService;
    @Autowired private DistributionDataPermissionService dataPermissionService;

    @Override
    public PageResponseDTO<DistributionLeadDTO> listLeads(...) {
        // 1. Safely handle pagination parameters
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);

        // 2. Resolve permissions
        DistributionDataAccessScope scope = dataPermissionService.resolveCurrentAccessScope();
        List<Long> authorizedIds = scope.isAllScope() || scope.isSelfScope()
            ? null : scope.getAuthorizedDistributorIds();

        // 3. Query (with permissions)
        long total = leadMapper.countByConditionWithScope(
            ..., authorizedIds, authorizedMemberId, authorizedOwnerUserId);
        List<DistributionLeadEntity> entities = leadMapper.selectByConditionWithScope(
            ..., authorizedIds, authorizedMemberId, authorizedOwnerUserId,
            (safePage - 1) * safePageSize, safePageSize);

        // 4. Assemble DTOs (batch-load related data to avoid N+1)
        return new PageResponseDTO<>(buildLeadDTOList(entities), total, safePage, safePageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLead(CreateDistributionLeadRequestDTO request) {
        // 1. Validate
        validateDistributor(request.getSourceDistributorId());
        validateProductLine(request.getIntentProductLine());

        // 2. Permission check
        dataPermissionService.checkAccessPermission(
            request.getSourceDistributorId(), request.getSourceMemberId(), null);

        // 3. Build the entity
        Long operatorUserId = operatorService.getCurrentOperatorUserId();
        DistributionLeadEntity entity = new DistributionLeadEntity();
        entity.setLeadNo(generateLeadNo());
        // ... set other fields
        entity.setCreatedBy(operatorUserId);
        entity.setDeleted(0);

        // 4. Insert
        leadMapper.insertSelective(entity);

        // 5. Audit log
        auditLogService.record("lead", entity.getId(), "create", null, entity, "Create lead");

        return entity.getId();
    }
}
```

**Key patterns summary**:

| Step | Code | Description |
|------|------|-------------|
| Safe pagination handling | `Math.min(pageSize, 100)` | Prevent querying too much at once |
| Permission resolution | `resolveCurrentAccessScope()` | Every query goes through permissions |
| Transaction annotation | `@Transactional(rollbackFor = Exception.class)` | Required for all write operations |
| Get operator | `operatorService.getCurrentOperatorUserId()` | Obtain from ThreadLocal |
| Audit log | `auditLogService.record(...)` | All write operations must be recorded |
| Logical delete | `entity.setDeleted(0)` | Explicitly set to 0 on creation |

## Step 3: Controller Layer

The Controller is a **thin layer** and does only three things: routing, parameter binding, and exception handling.

```java
@Slf4j
@RestController
@OpsApi
@RequestMapping("/api/manage/distribution/leads")
@Tag(name = "Distribution lead management API")
public class DistributionLeadController {

    @Autowired private DistributionLeadService distributionLeadService;

    @GetMapping
    @SessionAuth
    @Operation(summary = "Query lead list with pagination")
    public Result<PageResponseDTO<DistributionLeadDTO>> listLeads(
            @Parameter(description = "Lead number") @RequestParam(required = false) String leadNo,
            @Parameter(description = "Contact name or phone number") @RequestParam(required = false) String contactKeyword,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            return Result.success(distributionLeadService.listLeads(
                leadNo, contactKeyword, null, null, null, null, page, pageSize));
        } catch (BizException e) {
            return new Result<>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("Failed to query lead list", e);
            return Result.fail();
        }
    }

    @PostMapping
    @SessionAuth
    @Operation(summary = "Create lead")
    public Result<Long> createLead(@Valid @RequestBody CreateDistributionLeadRequestDTO request) {
        try {
            return Result.success(distributionLeadService.createLead(request));
        } catch (BizException e) {
            return new Result<>(e.getCode(), e.getMessage(), null);
        } catch (Exception e) {
            log.error("Failed to create lead", e);
            return Result.fail();
        }
    }
    // ... other endpoints
}
```

**Controller annotation conventions**:

| Annotation | Purpose | Location |
|------------|---------|----------|
| `@Slf4j` | Logging | On the class |
| `@RestController` | Declare a REST controller | On the class |
| `@OpsApi` | Custom annotation marking an operations API | On the class |
| `@SessionAuth` | Authentication required | On the class or method |
| `@Tag` | Swagger documentation tag | On the class |
| `@Operation` | Swagger endpoint description | On the method |
| `@Parameter` | Swagger parameter description | On the parameter |
| `@Valid` | Trigger the DTO's validation annotations | On the parameter |

## Step 4: DTO Design

**Request DTO**: contains only the fields the client needs to pass, with validation annotations.

```java
@Data
public class CreateDistributionLeadRequestDTO {
    @NotNull(message = "Distributor ID cannot be empty")
    private Long sourceDistributorId;

    private Long sourceMemberId;

    @NotBlank(message = "Contact name cannot be empty")
    private String contactName;

    @NotBlank(message = "Contact phone number cannot be empty")
    private String contactPhone;

    @NotBlank(message = "Intended product line cannot be empty")
    private String intentProductLine;

    private String remark;
}
```

**Response DTO**: contains the names of related data (rather than returning only IDs).

```java
@Data
public class DistributionLeadDTO {
    private Long id;
    private String leadNo;
    private String contactName;
    private String contactPhone;
    private Long sourceDistributorId;
    private String sourceDistributorName;  // Name from the related query
    private Long sourceMemberId;
    private String sourceMemberName;       // Name from the related query
    private String intentProductLine;
    private String stage;
    private Date createTime;
}
```

**Batch-load related data** (avoid N+1 queries):

```java
private List<DistributionLeadDTO> buildLeadDTOList(List<DistributionLeadEntity> entities) {
    // 1. Collect all IDs that need to be related
    Set<Long> distributorIds = new HashSet<>();
    Set<Long> memberIds = new HashSet<>();
    for (DistributionLeadEntity e : entities) {
        if (e.getSourceDistributorId() != null) distributorIds.add(e.getSourceDistributorId());
        if (e.getSourceMemberId() != null) memberIds.add(e.getSourceMemberId());
    }

    // 2. Batch query (1 SQL instead of N)
    Map<Long, String> distributorNameMap = loadDistributorNameMap(distributorIds);
    Map<Long, String> memberNameMap = loadMemberNameMap(memberIds);

    // 3. Assemble DTOs
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

## Step 5: Unified Response Format

```java
@Data
@NoArgsConstructor
public class Result<T> {
    private int code;    // 200=success, 10000=failure, 401=authentication failure
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

## Verification

After starting the application, test with Swagger UI:

```
http://localhost:9030/swagger-ui.html
```

Or use curl:

```bash
# Query the lead list
curl -H "Authorization: test-token-123" \
     -H "x-biz: distribution-starter" \
     "http://localhost:9030/api/manage/distribution/leads?page=1&pageSize=10"

# Create a lead
curl -X POST \
     -H "Authorization: test-token-123" \
     -H "x-biz: distribution-starter" \
     -H "Content-Type: application/json" \
     -d '{"sourceDistributorId":1,"contactName":"Test Contact","contactPhone":"13900000001","intentProductLine":"gene"}' \
     http://localhost:9030/api/manage/distribution/leads
```

## Next Step

CRUD works, but there is no audit log. Next we add an audit layer to record "who changed what, and when".

→ [Step 04: Audit Log](04-audit-logging.md)

---
