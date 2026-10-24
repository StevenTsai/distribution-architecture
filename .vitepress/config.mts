import { withMermaid } from 'vitepress-plugin-mermaid'

export default withMermaid({
  // 导航栏使用短书名，完整书名在首页 hero 和 description 中展示
  title: '架构炼金术',
  description: '《架构炼金术：从真实系统提炼可复用的架构模式》—— Java / Spring Boot / MyBatis 实战，7 个可复用模式 + 6 篇 ADR + 可运行代码与测试，以及跨语言的交付方法论',
  lang: 'zh-CN',

  locales: {
    root: { label: '中文', lang: 'zh-CN' },
    en: {
      label: 'English',
      lang: 'en',
      link: '/en/',
      title: 'Architecture Alchemy',
      description:
        'Java / Spring Boot / MyBatis architecture patterns extracted from production systems',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/en/' },
          { text: 'GitHub', link: 'https://github.com/StevenTsai/distribution-architecture' },
        ],

        sidebar: [
          {
            text: 'Overview',
            items: [{ text: 'Preface', link: '/en/preface' }],
          },
          {
            text: 'Part I: Foundations',
            items: [
              { text: 'Data Permissions at the SQL Layer', link: '/en/architecture/data-permission-model' },
              { text: 'Event-Driven State Machine', link: '/en/architecture/commission-pipeline' },
              { text: 'Audit Log & Compliance Records', link: '/en/architecture/audit-and-compliance' },
              { text: 'MyBatis Engineering', link: '/en/architecture/mybatis-patterns' },
            ],
          },
          {
            text: 'Part II: Advanced Patterns',
            items: [
              { text: 'PII Encryption & Masking', link: '/en/architecture/pii-encryption' },
              { text: 'Admin RBAC', link: '/en/architecture/admin-rbac' },
              { text: 'Cross-Language Contract Testing', link: '/en/architecture/cross-language-contract-testing' },
            ],
          },
          {
            text: 'Part III: Decisions',
            items: [
              { text: 'Session vs JWT', link: '/en/decisions/why-session-not-jwt' },
              { text: 'MyBatis vs JPA', link: '/en/decisions/why-mybatis-not-jpa' },
              { text: 'Logical vs Physical Delete', link: '/en/decisions/why-logical-delete' },
              { text: 'Spring Boot vs NestJS', link: '/en/decisions/why-spring-boot-not-nestjs' },
              { text: 'Redis Optional', link: '/en/decisions/why-redis-optional' },
              { text: 'JavaScript vs TypeScript', link: '/en/decisions/why-javascript-not-typescript' },
            ],
          },
          {
            text: 'Part IV: Case Studies',
            items: [
              { text: 'MTO Workflow Engine', link: '/en/case-studies/mto-workflow-engine/' },
              { text: 'Piecework Wage Engine', link: '/en/case-studies/piecework-wage-engine/' },
              { text: 'Spring Events Decoupling', link: '/en/case-studies/spring-events-decoupling/' },
              { text: 'Production Guardrails', link: '/en/case-studies/production-guardrails/' },
            ],
          },
          {
            text: 'Part V: Build from Scratch',
            items: [
              { text: '01 Domain Model', link: '/en/tutorial/01-domain-model' },
              { text: '02 Data Permissions', link: '/en/tutorial/02-data-permission' },
              { text: '03 CRUD Scaffolding', link: '/en/tutorial/03-crud-scaffolding' },
              { text: '04 Audit Logging', link: '/en/tutorial/04-audit-logging' },
              { text: '05 Commission Flow', link: '/en/tutorial/05-commission-flow' },
              { text: '06 Settlement', link: '/en/tutorial/06-settlement' },
              { text: '07 Compliance', link: '/en/tutorial/07-compliance' },
            ],
          },
          {
            text: 'Guides',
            items: [
              { text: 'Quick Start', link: '/en/guides/quick-start' },
              { text: 'Extend a New Module', link: '/en/guides/extend-new-module' },
              { text: 'Customize Permissions', link: '/en/guides/customize-permission' },
              { text: 'Spring Boot 3 Migration', link: '/en/guides/spring-boot-3-migration' },
            ],
          },
        ],

        outline: {
          level: [2, 3],
          label: 'On this page',
        },

        docFooter: {
          prev: 'Previous',
          next: 'Next',
        },

        search: {
          provider: 'local',
          options: {
            translations: {
              button: { buttonText: 'Search', buttonAriaLabel: 'Search' },
              modal: {
                noResultsText: 'No results found',
                resetButtonTitle: 'Clear query',
                footer: { selectText: 'Select', navigateText: 'Navigate' },
              },
            },
          },
        },

        footer: {
          message: 'Released under the Apache License 2.0',
          copyright: 'Copyright © 2026 Steven Tsai',
        },

        socialLinks: [
          { icon: 'github', link: 'https://github.com/StevenTsai/distribution-architecture' },
        ],

        editLink: {
          pattern: 'https://github.com/StevenTsai/distribution-architecture/edit/main/:path',
          text: 'Edit this page on GitHub',
        },
      },
    },
  },

  // GitHub Pages 部署路径
  base: '/distribution-architecture/',

  // 以仓库根目录作为文档源
  srcDir: '.',

  // 只处理文档目录，排除代码和其他文件
  srcExclude: [
    'node_modules/**',
    'distribution-starter/src/**',    // 代码示例
    'distribution-starter/target/**',
    'distribution-starter/pom.xml',
    'distribution-starter/.gitignore',
    'case-studies/*/src/**',          // 案例代码
    'doc/**',          // 项目文档
    'scripts/**',
    'README.md',       // 仓库 README 不作为书籍页面
  ],

  // 案例目录只有 README.md，重写为 index，使 /case-studies/xxx/ 可以直接访问
  rewrites: {
    'case-studies/mto-workflow-engine/README.md': 'case-studies/mto-workflow-engine/index.md',
    'case-studies/piecework-wage-engine/README.md': 'case-studies/piecework-wage-engine/index.md',
    'case-studies/spring-events-decoupling/README.md': 'case-studies/spring-events-decoupling/index.md',
    'case-studies/production-guardrails/README.md': 'case-studies/production-guardrails/index.md',
    'playbooks/takeover-engineering/README.md': 'playbooks/takeover-engineering/index.md',
    'playbooks/system-portfolio-governance/README.md': 'playbooks/system-portfolio-governance/index.md',
    'en/README.md': 'en/index.md',
    'en/case-studies/mto-workflow-engine/README.md': 'en/case-studies/mto-workflow-engine/index.md',
    'en/case-studies/piecework-wage-engine/README.md': 'en/case-studies/piecework-wage-engine/index.md',
    'en/case-studies/spring-events-decoupling/README.md': 'en/case-studies/spring-events-decoupling/index.md',
    'en/case-studies/production-guardrails/README.md': 'en/case-studies/production-guardrails/index.md',
  },

  // Mermaid 图表配置（插件会自动跟随明暗主题）
  mermaid: {
    theme: 'default',
    flowchart: {
      curve: 'basis',
      padding: 12,
      nodeSpacing: 30,
      rankSpacing: 40,
      htmlLabels: true,
    },
    // 统一节点圆角，避免默认直角方框显得粗糙
    themeCSS: `
      .node rect { rx: 6px; ry: 6px; }
      .node polygon { stroke-linejoin: round; }
      .cluster rect { rx: 8px; ry: 8px; }
    `,
  },

  // 忽略死链接（README 中的相对链接在 VitePress 中不适用）
  ignoreDeadLinks: true,

  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: 'GitHub', link: 'https://github.com/StevenTsai/distribution-architecture' },
    ],

    sidebar: [
      {
        text: '第一部分：基础模式',
        items: [
          { text: '数据权限下沉到 SQL 层', link: '/architecture/data-permission-model' },
          { text: '事件驱动状态机', link: '/architecture/commission-pipeline' },
          { text: '审计日志与合规记录', link: '/architecture/audit-and-compliance' },
          { text: 'MyBatis 工程化', link: '/architecture/mybatis-patterns' },
        ],
      },
      {
        text: '第二部分：进阶模式',
        items: [
          { text: 'PII 加密与脱敏', link: '/architecture/pii-encryption' },
          { text: '管理端 RBAC', link: '/architecture/admin-rbac' },
          { text: '跨语言契约测试', link: '/architecture/cross-language-contract-testing' },
        ],
      },
      {
        text: '第三部分：技术决策',
        items: [
          { text: 'Session vs JWT', link: '/decisions/why-session-not-jwt' },
          { text: 'MyBatis vs JPA', link: '/decisions/why-mybatis-not-jpa' },
          { text: '逻辑删除 vs 物理删除', link: '/decisions/why-logical-delete' },
          { text: 'Spring Boot 单轨 vs NestJS', link: '/decisions/why-spring-boot-not-nestjs' },
          { text: 'Redis 可选化', link: '/decisions/why-redis-optional' },
          { text: '零构建链：JS vs TS', link: '/decisions/why-javascript-not-typescript' },
        ],
      },
      {
        text: '第四部分：实战案例',
        items: [
          { text: 'MTO 工作流引擎', link: '/case-studies/mto-workflow-engine/' },
          { text: '计件工资引擎', link: '/case-studies/piecework-wage-engine/' },
          { text: 'Spring Events 解耦', link: '/case-studies/spring-events-decoupling/' },
          { text: '生产护栏', link: '/case-studies/production-guardrails/' },
        ],
      },
      {
        text: '第五部分：从零构建',
        items: [
          { text: '01 领域模型', link: '/tutorial/01-domain-model' },
          { text: '02 数据权限', link: '/tutorial/02-data-permission' },
          { text: '03 CRUD 脚手架', link: '/tutorial/03-crud-scaffolding' },
          { text: '04 审计日志', link: '/tutorial/04-audit-logging' },
          { text: '05 佣金流转', link: '/tutorial/05-commission-flow' },
          { text: '06 结算流程', link: '/tutorial/06-settlement' },
          { text: '07 合规检查', link: '/tutorial/07-compliance' },
        ],
      },
      {
        text: '第六部分：交付方法论',
        items: [
          { text: '接手工程工具箱', link: '/playbooks/takeover-engineering/' },
          { text: '方法论总纲', link: '/playbooks/takeover-engineering/00-methodology' },
          { text: '签约前投入纪律', link: '/playbooks/takeover-engineering/01-investment-discipline' },
          {
            text: '模板 01–10',
            collapsed: false,
            items: [
              { text: '01 接手指南与资产清单', link: '/playbooks/takeover-engineering/templates/01-asset-inventory' },
              { text: '02 复用度评估表', link: '/playbooks/takeover-engineering/templates/02-reuse-assessment' },
              { text: '03 缺口与风险台账', link: '/playbooks/takeover-engineering/templates/03-gap-risk-ledger' },
              { text: '04 软硬件职责边界矩阵', link: '/playbooks/takeover-engineering/templates/04-responsibility-matrix' },
              { text: '05 止损与收尾清单', link: '/playbooks/takeover-engineering/templates/05-exit-checklist' },
              { text: '06 增量报价模型', link: '/playbooks/takeover-engineering/templates/06-pricing-model' },
              { text: '07 契约优先重建方案', link: '/playbooks/takeover-engineering/templates/07-contract-first-rebuild' },
              { text: '08 验收追溯矩阵', link: '/playbooks/takeover-engineering/templates/08-acceptance-traceability' },
              { text: '09 质量审查台账', link: '/playbooks/takeover-engineering/templates/09-quality-review-ledger' },
              { text: '10 文档体系模板', link: '/playbooks/takeover-engineering/templates/10-documentation-system' },
            ],
          },
          { text: '钩子：21 个问题', link: '/playbooks/takeover-engineering/hooks/21-questions' },
          { text: '附录：虚构案例串跑', link: '/playbooks/takeover-engineering/case-walkthrough' },
          {
            text: '架构组合治理',
            collapsed: false,
            items: [
              { text: '导言', link: '/playbooks/system-portfolio-governance/' },
              { text: '方法论总纲', link: '/playbooks/system-portfolio-governance/00-methodology' },
              { text: '模板 01 系统组合清单', link: '/playbooks/system-portfolio-governance/templates/01-portfolio-inventory' },
              { text: '模板 02 单系统诊断卡', link: '/playbooks/system-portfolio-governance/templates/02-system-diagnosis' },
              { text: '模板 03 治理决策记录', link: '/playbooks/system-portfolio-governance/templates/03-governance-decision' },
              { text: '模板 04 路线图与防回潮', link: '/playbooks/system-portfolio-governance/templates/04-governance-roadmap' },
            ],
          },
          { text: '工程一致性规范', link: '/playbooks/engineering-consistency-standards' },
        ],
      },
      {
        text: '实操指南',
        items: [
          { text: '快速开始', link: '/guides/quick-start' },
          { text: '扩展新模块', link: '/guides/extend-new-module' },
          { text: '自定义权限', link: '/guides/customize-permission' },
          { text: 'Spring Boot 3 迁移', link: '/guides/spring-boot-3-migration' },
        ],
      },
    ],

    outline: {
      level: [2, 3],
      label: '本页目录',
    },

    docFooter: {
      prev: '上一篇',
      next: '下一篇',
    },

    search: {
      provider: 'local',
      options: {
        translations: {
          button: {
            buttonText: '搜索文档',
            buttonAriaLabel: '搜索文档',
          },
          modal: {
            noResultsText: '无法找到相关结果',
            resetButtonTitle: '清除查询条件',
            footer: {
              selectText: '选择',
              navigateText: '切换',
            },
          },
        },
      },
    },

    footer: {
      message: '基于 Apache License 2.0 开源',
      copyright: 'Copyright © 2026 Steven Tsai',
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/StevenTsai/distribution-architecture' },
    ],

    editLink: {
      pattern: 'https://github.com/StevenTsai/distribution-architecture/edit/main/:path',
      text: '在 GitHub 上编辑此页',
    },
  },
})
