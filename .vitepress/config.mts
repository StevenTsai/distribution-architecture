import { withMermaid } from 'vitepress-plugin-mermaid'

export default withMermaid({
  // 导航栏使用短书名，完整书名在首页 hero 和 description 中展示
  title: '架构炼金术',
  description: '《架构炼金术：从真实系统提炼可复用的 Java 架构模式》—— Java / Spring Boot / MyBatis 实战，7 个可复用模式 + 6 篇 ADR + 可运行代码与测试',
  lang: 'zh-CN',

  // GitHub Pages 部署路径
  base: '/distribution-architecture/',

  // 以仓库根目录作为文档源
  srcDir: '.',

  // 只处理文档目录，排除代码和其他文件
  srcExclude: [
    'en/**',           // 英文版（单独维护）
    'node_modules/**',
    'distribution-starter/**',  // 代码示例
    'case-studies/*/src/**',    // 案例代码
    'case-studies/*/docs/**',   // 案例演进文档（单独链接）
    'doc/**',          // 项目文档
    'scripts/**',
    'README.md',       // 仓库 README 不作为书籍页面
    'CONTRIBUTING.md',
    'guides/**',       // 实操指南（可后续加入）
  ],

  // 案例目录只有 README.md，重写为 index，使 /case-studies/xxx/ 可以直接访问
  rewrites: {
    'case-studies/mto-workflow-engine/README.md': 'case-studies/mto-workflow-engine/index.md',
    'case-studies/piecework-wage-engine/README.md': 'case-studies/piecework-wage-engine/index.md',
    'case-studies/spring-events-decoupling/README.md': 'case-studies/spring-events-decoupling/index.md',
    'case-studies/production-guardrails/README.md': 'case-studies/production-guardrails/index.md',
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
